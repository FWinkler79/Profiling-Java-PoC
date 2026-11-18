# Architecture: JFR OpenTelemetry Profiles Application

## Overview

This document describes the architecture of `jfr-otel-profiles-app`, a Spring Boot application that
reproduces the OpenTelemetry `opentelemetry-jfr-profiles-shim` approach. It continuously records
`jdk.ExecutionSample` JFR events via a `RecordingStream` and exports them as the **OTLP profiles signal**
(pprof-style `ProfileData`, dictionary-encoded).

## System Architecture

```text
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                       │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │           RecordingStream (JFR live stream)               │  │
│  │  - Event: jdk.ExecutionSample (10 ms period)              │  │
│  │  - onEvent callback → converterRef.get().accept(e)        │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│                   │ each ExecutionSample                         │
│                   ▼                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │      JfrExecutionSampleEventConverter (AtomicRef)         │  │
│  │                                                            │  │
│  │  Accumulates dictionary-encoded stacks per interval:      │  │
│  │    - Strings dictionary (function/class names)            │  │
│  │    - Functions list                                        │  │
│  │    - Locations list                                        │  │
│  │    - SampleData list (location refs + value counts)       │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│                   │ atomic swap every 15 s (scheduler thread)   │
│                   ▼                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │        JfrProfilesExportService (@Scheduled)              │  │
│  │                                                            │  │
│  │  1. swapConverter()   → atomically take drained converter │  │
│  │  2. settleAfterSwap() → 50 ms drain-race guard            │  │
│  │  3. snapshotDrained() → materialise samples + dictionary  │  │
│  │  4. rawRecording.dump(tempFile) → embed raw JFR bytes     │  │
│  │  5. ProfileAssembler.assemble() → build ProfileData       │  │
│  │  6. exporter.export(List.of(profile))                     │  │
│  │  7. delete temp file                                       │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│                   ▼                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │           OtlpGrpcProfileExporter                         │  │
│  │  - Protocol: gRPC (OTLP profiles)                        │  │
│  │  - Endpoint: configurable (default http://localhost:4317) │  │
│  │  - Timeout: 30 seconds per export                        │  │
│  └────────────────┬─────────────────────────────────────────┘  │
└────────────────────┼────────────────────────────────────────────┘
                     │
                     │ gRPC / OTLP profiles
                     │ Port 4317
                     ▼
       ┌──────────────────────────────────────┐
       │    OTLP Backend                       │
       │  - OTel Collector Contrib             │
       │    (--feature-gates=+service.         │
       │     profilesSupport)                  │
       │  - Elastic devfiler (:11000)          │
       │  - Grafana Pyroscope                  │
       └──────────────────────────────────────┘
```

## Component Details

### 1. JFR Recording Layer

Two JFR facilities run in parallel inside `JfrProfilesExportService.startStreaming()`:

#### RecordingStream (live feed)

- `RecordingStream.enable("jdk.ExecutionSample").withStackTrace().withPeriod(10 ms)`
- `onEvent` callback: `converterRef.get().accept(e)` — feeds each sample to the *current* converter
- `startAsync()` — runs on JFR's internal thread pool

#### Raw Recording (disk-backed)

- `Recording.enable("jdk.ExecutionSample").withStackTrace().withPeriod(10 ms)`
- `setToDisk(true)` — buffers the raw JFR stream to disk
- Dumped once per interval via `rawRecording.dump(tempFile)` so the raw bytes can be embedded in
  the exported `ProfileData`

**Only `jdk.ExecutionSample` is recorded.** This is intentional: the profiles signal is designed for
stack-based profiling data, not general JVM telemetry.

### 2. Converter and Assembler Layer

#### JfrExecutionSampleEventConverter

Converts each `RecordedEvent` to the pprof dictionary structure:

| pprof concept  | OTel type                        | Description                              |
| -------------- | -------------------------------- | ---------------------------------------- |
| Strings dict   | `ProfilesDictionaryCompositor`   | Intern strings (function/class names)    |
| Function       | `FunctionData`                   | Method name + class name                 |
| Location       | `LocationData`                   | Stack frame (function + line)            |
| Sample         | `SampleData`                     | Location indices + sample count          |

#### ProfileAssembler

Consumes the materialized `DrainedSnapshot` (samples, dictionary, value type) and the raw JFR bytes,
and returns a single `ProfileData` with:

- `startEpochNanos` / `durationNanos` — interval window
- `periodNanos` — sample period (10 ms)
- `resource` + `scope` — OTel resource and instrumentation scope
- `rawProfile` — raw JFR bytes embedded as the profile payload

### 3. Concurrency Model

The JFR stream callback thread and the Spring scheduler thread share the
`AtomicReference<JfrExecutionSampleEventConverter> converterRef`. The hand-off protocol is:

1. **Scheduler** calls `converterRef.getAndSet(new converter)` — atomically installs a fresh converter
   and takes the drained one.
2. **Sleep 50 ms** — lets any JFR callback that read the old reference before the swap finish calling
   `drained.accept(e)` (the converter's maps are not thread-safe).
3. **snapshotDrained()** — reads the drained converter exactly once, with one CME retry. Returns `null`
   on persistent failure so the interval is skipped cleanly.

### 4. OpenTelemetry Layer

**Dependencies:**

- `opentelemetry-sdk` `1.68.0` — stable SDK
- `opentelemetry-sdk-profiles` `1.68.0-alpha` — incubating profiles data model
- `opentelemetry-exporter-otlp-profiles` `1.68.0-alpha` — `OtlpGrpcProfileExporter`

**Resource Attributes:**

```java
service.name     = jfr-otel-app   (configurable via jfr.instrumentation.scope.name)
service.version  = 1.0.0          (configurable via jfr.instrumentation.scope.version)
```

### 5. REST API Layer

**Controller:** `JfrProfilesController` — `GET /api/jfr/status`, `GET /api/jfr/health`

**Status response fields:**

| Field                     | Description                                                          |
| ------------------------- | -------------------------------------------------------------------- |
| `recording_state`         | State of the disk-backed `Recording` (proxy for stream health)       |
| `otlp_endpoint`           | Configured OTLP gRPC endpoint                                        |
| `total_exports`           | Total export attempts (including skipped-empty intervals)            |
| `successful_exports`      | Exports where `exporter.export(...)` returned success                |
| `failed_exports`          | Exports that failed or were skipped due to drain errors              |
| `total_samples_exported`  | Cumulative `jdk.ExecutionSample` count across all successful exports |

## Configuration

All settings in `application.yaml`:

```yaml
server:
  port: 8082

jfr:
  otlp:
    endpoint: http://localhost:4317
  export:
    interval:
      seconds: 15
  sample:
    period:
      millis: 10
  instrumentation:
    scope:
      name: jfr-otel-app
      version: 1.0.0
```

## Data Flow Sequence

```text
T=0:  RecordingStream started + rawRecording started
         │
         │   jdk.ExecutionSample events at 10 ms → converterRef.get().accept(e)
         │
T=30: First export interval fires (30 s initial delay)
         │
         ├─ swapConverter() → drained = old converter, fresh converter installed
         ├─ sleep 50 ms (drain-race guard)
         ├─ snapshotDrained() → materialise samples + dictionary
         ├─ rawRecording.dump(tempFile) → write raw JFR bytes
         ├─ ProfileAssembler.assemble(...) → ProfileData
         ├─ OtlpGrpcProfileExporter.export(List.of(profile))
         └─ delete tempFile
         │
T=45: Next export interval fires (15 s fixed delay)
         │
         └─ [repeat swap → assemble → export]
```

## Performance Characteristics

### Overhead

| Component                                    | Overhead                                    |
| -------------------------------------------- | ------------------------------------------- |
| `RecordingStream` + `jdk.ExecutionSample`    | <2% CPU (JFR is production-grade)           |
| Converter accumulation                       | Negligible — one map insertion per sample   |
| Export per interval                          | 200–500 ms (network + serialisation)        |
| Raw JFR dump                                 | Proportional to recording size (~KB range)  |

### Network

- Approximately 1 `ProfileData` message per 15-second interval
- Size depends on stack depth and unique function count (typically tens of KB)

## Error Handling

### Export failures

- Failed exports increment `failedExports` and log at WARN/ERROR
- JFR recording continues — the next interval starts a fresh converter
- Lost samples from the failed interval are not retried

### Drain race (ConcurrentModificationException)

- If the post-swap settle sleep is not enough, `snapshotDrained()` retries once
- On persistent failure the interval is skipped (`failedExports` incremented, no partial export)

### Shutdown (`@PreDestroy`)

1. `RecordingStream.close()` — stops the JFR stream
2. `exportInterval()` — final export of accumulated samples
3. `rawRecording.stop()` + `rawRecording.close()`
4. `exporter.shutdown()` — flushes and closes the gRPC channel

## Deployment Considerations

### Collector Requirements

The OpenTelemetry Collector must:

- Be the **Contrib** distribution (`otel/opentelemetry-collector-contrib`)
- Be started with `--feature-gates=+service.profilesSupport`
- Have a `profiles` pipeline configured (receiver `otlp` → exporter `debug` or other)

The `docker/` directory in this app provides a ready-to-use `docker-compose.yaml` and
`otel-collector-config.yaml` that satisfy all these requirements.

### Network Bandwidth

- Outbound gRPC to the OTLP endpoint (default port 4317)
- Approximately a few KB per export; 15-second intervals

### Monitoring

Key metrics to track:

- `successful_exports` / `failed_exports` ratio via `GET /api/jfr/status`
- `total_samples_exported` growth rate (should increase every interval)

## References

- [OpenTelemetry Profiles Specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/profiles/profiles.md)
- [opentelemetry-jfr-profiles-shim](https://github.com/open-telemetry/opentelemetry-java/tree/main/opentelemetry-jfr-profiles-shim)
- [JFR Documentation](https://docs.oracle.com/en/java/javase/17/jfapi/)
- [OTLP Specification](https://github.com/open-telemetry/opentelemetry-proto)
- [OTel Collector Contrib](https://github.com/open-telemetry/opentelemetry-collector-contrib)
