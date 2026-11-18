# Creation Summary: jfr-otel-profiles-app

## What Was Created

A Spring Boot application, **`jfr-otel-profiles-app`**, that reproduces the OpenTelemetry
`opentelemetry-jfr-profiles-shim` approach: record `jdk.ExecutionSample` JFR events and export them as
the **OTLP profiles signal** (pprof-style `ProfileData`, dictionary-encoded).

This app is a sibling of `jfr-otel-logs-app` (which exports JFR data as OTLP *logs*).

### Location

```text
apps/jfr-otel-profiles-app/
```

### Key Design Decisions

1. **OTLP profiles signal** — uses the incubating `opentelemetry-sdk-profiles` + `opentelemetry-exporter-otlp-profiles`
   at version `1.68.0-alpha`, plus the stable `opentelemetry-sdk` `1.68.0`.
2. **`jdk.ExecutionSample` only** — unlike the logs-based app (~20 event types), this app records only CPU
   method-profiling samples. The profiles signal is designed for stack-based data.
3. **Streaming, not snapshotting** — a `RecordingStream` feeds samples to the converter live. A parallel
   disk-backed `Recording` is dumped once per interval to embed raw JFR bytes in the `ProfileData`.
4. **Atomic converter swap** — the concurrency boundary between the JFR callback thread and the Spring
   scheduler is a single `AtomicReference.getAndSet(...)`. No lock guards `accept()`.

## Files Created

### Source Code

```text
src/main/java/com/example/jfrotelprofiles/
├── JfrProfilesApplication.java                   Main Spring Boot entry point
├── controller/
│   └── JfrProfilesController.java                REST API (/api/jfr/status, /api/jfr/health)
├── jfr/
│   ├── JfrExecutionSampleEventConverter.java      Accumulates pprof-style samples per interval
│   ├── JfrLocationDataCompositor.java             Builds dictionary-encoded Location/Function data
│   └── ProfileAssembler.java                     Assembles final ProfileData from converter snapshot
└── service/
    └── JfrProfilesExportService.java             Core service: stream, swap, export
```

### Configuration

```text
src/main/resources/application.yaml              Server port, OTLP endpoint, JFR config
pom.xml                                          Maven dependencies (OTel 1.68.0 / 1.68.0-alpha)
```

### Docker / Collector

```text
docker/
├── docker-compose.yaml                          OTel Collector Contrib, profiles pipeline
├── otel-collector-config.yaml                   profiles: [otlp] → [debug]
├── start-collector.sh                           Convenience startup script
└── README.md / DOCKER-SETUP-SUMMARY.md          Collector documentation
```

### Documentation

```text
README.md                                        Comprehensive user documentation
QUICKSTART.md                                    5-minute quick start guide
ARCHITECTURE.md                                  Detailed architecture documentation
CREATION-SUMMARY.md                              This file
run.sh                                           Build and run script
```

## Implementation Details

### OTel Profiles Stack

```xml
<!-- Stable OTel SDK -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
    <version>1.68.0</version>
</dependency>

<!-- Incubating profiles data model -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-profiles</artifactId>
    <version>1.68.0-alpha</version>
</dependency>

<!-- Incubating OTLP profiles exporter -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp-profiles</artifactId>
    <version>1.68.0-alpha</version>
</dependency>
```

### Export Flow

```text
RecordingStream → JfrExecutionSampleEventConverter (AtomicRef)
                             │  every 15 s
                     atomic swap (scheduler)
                             │
                   snapshotDrained() → samples + dictionary
                             │
                   rawRecording.dump(tempFile) → raw JFR bytes
                             │
                   ProfileAssembler.assemble() → ProfileData
                             │
                   OtlpGrpcProfileExporter.export(List.of(profile))
```

### Collector Configuration

```yaml
# otel-collector-config.yaml
service:
  pipelines:
    profiles:
      receivers: [otlp]
      exporters: [debug]
```

```yaml
# docker-compose.yaml
command:
  - "--config=/etc/otel-collector-config.yaml"
  - "--feature-gates=+service.profilesSupport"
```

## App Configuration

### Port

**8080** (same as the sibling `jfr-otel-logs-app`)

### Key Config Keys

| Key                                  | Default                  | Description                          |
| ------------------------------------ | ------------------------ | ------------------------------------ |
| `jfr.otlp.endpoint`                  | `http://localhost:4317`  | OTLP gRPC collector endpoint         |
| `jfr.export.interval.seconds`        | `15`                     | Export interval                      |
| `jfr.sample.period.millis`           | `10`                     | ExecutionSample period in ms         |
| `jfr.instrumentation.scope.name`     | `jfr-otel-app`           | OTel instrumentation scope name      |
| `jfr.instrumentation.scope.version`  | `1.0.0`                  | OTel instrumentation scope version   |

## Status Response

```json
{
  "recording_state": "RUNNING",
  "otlp_endpoint": "http://localhost:4317",
  "total_exports": 1,
  "successful_exports": 1,
  "failed_exports": 0,
  "total_samples_exported": 1234
}
```

## How It Differs from jfr-otel-logs-app

| Aspect          | jfr-otel-logs-app          | jfr-otel-profiles-app                         |
| --------------- | ---------------------------------- | --------------------------------------------- |
| **Signal**      | OTLP Logs                          | OTLP Profiles                                 |
| **Events**      | ~20 JFR event types                | `jdk.ExecutionSample` only                    |
| **OTel SDK**    | 1.33.0 (stable)                    | 1.68.0 + 1.68.0-alpha                         |
| **Exporter**    | `OtlpGrpcLogRecordExporter`        | `OtlpGrpcProfileExporter`                     |
| **Data model**  | Structured log records             | pprof-style `ProfileData`                     |
| **Collector**   | No feature gate needed             | `--feature-gates=+service.profilesSupport`    |

## Status

All files created, documented, and build-verified (`mvn clean package -DskipTests`).
