# JFR OpenTelemetry Profiles Application

A Spring Boot application that reproduces the OpenTelemetry
[`opentelemetry-jfr-profiles-shim`](https://github.com/open-telemetry/opentelemetry-java/tree/main/opentelemetry-jfr-profiles-shim)
approach: it records `jdk.ExecutionSample` JFR events via a live `RecordingStream` and exports them as the
**OTLP profiles signal** (pprof-style dictionary-encoded Functions / Locations / Stacks / Samples).

This is a sibling of `jfr-otel-logs-app`, which exports the same JFR data as OTLP *logs*. This app
demonstrates the native profiles signal now available in OpenTelemetry.

**Port:** 8080

## Overview

This application demonstrates how to:

- Continuously stream `jdk.ExecutionSample` JFR events with a `RecordingStream`
- Convert each sample to a pprof-style `ProfileData` (dictionary-encoded stacks, locations, functions)
- Export profiles to an OTLP-compatible backend via `OtlpGrpcProfileExporter` (gRPC, port 4317)
- Monitor export status via REST API

## Key Features

- Continuous JFR streaming — `RecordingStream` feeds a converter live (no snapshot files)
- `jdk.ExecutionSample` only — CPU method-profiling samples at 10 ms intervals
- OTLP profiles signal — pprof-style `ProfileData` with dictionary-encoded stacks
- One `ProfileData` flushed per export interval (default 15 seconds)
- REST API for monitoring export status on port 8080

## Architecture

```text
RecordingStream (jdk.ExecutionSample)
       │
       │ onEvent callback (each sample)
       ▼
JfrExecutionSampleEventConverter (accumulates dictionary-encoded stacks)
       │
       │ atomic swap every 15 s  (scheduler thread)
       ▼
ProfileAssembler → ProfileData (pprof layout)
       │
       │ OtlpGrpcProfileExporter
       │ gRPC port 4317
       ▼
   OTLP Backend (Collector / devfiler / Pyroscope)
```

The service runs two JFR facilities in parallel:

1. A `RecordingStream` whose `onEvent` callback feeds each `ExecutionSample` into the current
   `JfrExecutionSampleEventConverter`.
2. A disk-backed `Recording` dumped each interval so the raw JFR bytes can be embedded in the exported
   `ProfileData`.

Every 15 seconds the scheduler atomically swaps the converter, materialises the accumulated samples into a
single `ProfileData`, and calls `exporter.export(List.of(profile))`.

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- An OTLP profiles-capable backend on port 4317 (or configure a different endpoint)

### OTLP Backend Options

- **OpenTelemetry Collector Contrib** (included in this repo's `docker/` setup) — the `debug` exporter
  prints profiles to stdout with zero additional setup; start with
  `--feature-gates=+service.profilesSupport`.
- **[Elastic devfiler](https://github.com/elastic/devfiler/)** — standalone OTLP profile receiver on
  port 11000 (the port the OTel reference example targets).
- **[Grafana Pyroscope](https://grafana.com/oss/pyroscope/)** — flamegraph UI for continuous profiling.

## Quick Start

### Option 1: With Docker Compose (Recommended)

The easiest way to get started uses the included OpenTelemetry Collector:

```bash
cd apps/jfr-otel-profiles-app/docker
./start-collector.sh      # starts jfr-otel-profiles-collector

# In another terminal, build and start the app
cd ..
./run.sh

# View collector output (profiles print via the debug exporter)
cd docker
docker compose logs -f otel-collector
```

See [docker/README.md](docker/README.md) for detailed Docker setup documentation.

### Option 2: With devfiler

```bash
# Start devfiler on its default port
docker run -p 11000:11000 elastic/devfiler

# Configure the app to send to devfiler
# Edit src/main/resources/application.yaml:
#   jfr.otlp.endpoint: http://localhost:11000
```

### Build and Run

```bash
cd apps/jfr-otel-profiles-app
./run.sh
```

The application will:

- Start on port 8080
- Begin continuous JFR `RecordingStream` for `jdk.ExecutionSample`
- Export one `ProfileData` to the OTLP endpoint every 15 seconds (after 30-second initial delay)

### Monitor Status

```bash
# Check export status
curl http://localhost:8080/api/jfr/status

# Health check
curl http://localhost:8080/api/jfr/health
```

## Configuration

All settings are in `src/main/resources/application.yaml`:

```yaml
server:
  port: 8080

jfr:
  otlp:
    endpoint: http://localhost:4317  # OTLP gRPC collector endpoint
  export:
    interval:
      seconds: 15                    # How often to export a ProfileData
  sample:
    period:
      millis: 10                     # ExecutionSample recording period
  instrumentation:
    scope:
      name: jfr-otel-app
      version: 1.0.0
```

Config keys:

| Key                                 | Default                  | Description             |
| ----------------------------------- | ------------------------ | ----------------------- |
| `jfr.otlp.endpoint`                 | `http://localhost:4317`  | OTLP gRPC endpoint      |
| `jfr.export.interval.seconds`       | `15`                     | Export interval         |
| `jfr.sample.period.millis`          | `10`                     | ExecutionSample period  |
| `jfr.instrumentation.scope.name`    | `jfr-otel-app`           | OTel scope name         |
| `jfr.instrumentation.scope.version` | `1.0.0`                  | OTel scope version      |

## API Endpoints

### GET /api/jfr/status

Returns JFR recording and export statistics:

```json
{
  "recording_state": "RUNNING",
  "otlp_endpoint": "http://localhost:4317",
  "total_exports": 42,
  "successful_exports": 40,
  "failed_exports": 2,
  "total_samples_exported": 12345
}
```

### GET /api/jfr/health

Health check endpoint:

```json
{
  "status": "UP",
  "service": "JFR OTLP Profiles Export Service"
}
```

## JFR Events Recorded

This application records **only** `jdk.ExecutionSample` (CPU method-profiling / call-stack sampling).

| Event                    | Period | Purpose                                              |
| ------------------------ | ------ | ---------------------------------------------------- |
| `jdk.ExecutionSample`    | 10 ms  | CPU call-stack sample — top frame + full stack trace |

This is intentionally narrow: the profiles signal is designed for stack-based profiling data, not general
JVM telemetry. For broader JFR event coverage (CPU load, GC, I/O, exceptions, …) see the sibling app
`jfr-otel-logs-app`.

## Building

```bash
mvn clean package
```

## Running

```bash
# Using the convenience script (builds and runs)
./run.sh

# Or directly
java -jar target/jfr-otel-profiles-app-1.0.0.jar

# With custom OTLP endpoint
java -jar target/jfr-otel-profiles-app-1.0.0.jar \
  --jfr.otlp.endpoint=http://my-collector:4317
```

## Comparison with Other Apps

| Feature             | jfr-otel-logs-app         | jfr-otel-profiles-app                         |
| ------------------- | --------------------------------- | --------------------------------------------- |
| **Port**            | 8080                              | 8080                                          |
| **Signal**          | OTLP Logs                         | OTLP Profiles                                 |
| **JFR Events**      | ~20 event types                   | `jdk.ExecutionSample` only                    |
| **OTel version**    | 1.33.0                            | 1.68.0 / 1.68.0-alpha                         |
| **Exporter**        | `OtlpGrpcLogRecordExporter`       | `OtlpGrpcProfileExporter`                     |
| **Format**          | Structured log records            | pprof-style `ProfileData`                     |
| **Collector gate**  | none                              | `--feature-gates=+service.profilesSupport`    |

## Troubleshooting

### Connection refused to OTLP endpoint

Ensure your OTLP backend is running:

```bash
# Check Docker collector is up
docker compose -f apps/jfr-otel-profiles-app/docker/docker-compose.yaml ps

# Check collector health
curl http://localhost:13133

# Check export status
curl http://localhost:8080/api/jfr/status
```

### No exports happening

Check the logs for:

- Recording startup messages (`JFR profile streaming started`)
- Export attempts and results
- Any error messages

Verify the initial delay has passed (30 seconds after startup).

### Profiles not visible in the collector logs

Ensure the collector was started with `--feature-gates=+service.profilesSupport`. Without this flag the
collector does not enable the profiles pipeline and will reject incoming profiles data silently.

### High failure rate

Check:

- OTLP backend is accepting profiles connections
- Network connectivity to the endpoint
- Endpoint URL is correct (must include protocol: `http://`)
- Collector was started with the profiles feature gate

## OpenTelemetry Dependencies

- **OpenTelemetry SDK** `1.68.0` (stable)
- **`opentelemetry-sdk-profiles`** `1.68.0-alpha` (incubating)
- **`opentelemetry-exporter-otlp-profiles`** `1.68.0-alpha` (incubating)

The profiles API is incubating / alpha; the artifact coordinates and API surface may change in future
OpenTelemetry releases.

## Related Documentation

- [Main Project README](../../README.md)
- [QUICKSTART.md](QUICKSTART.md) — 5-minute quick start
- [ARCHITECTURE.md](ARCHITECTURE.md) — detailed architecture
- [docker/README.md](docker/README.md) — Docker / Collector setup
- [jfr-otel-logs-app](../jfr-otel-logs-app/README.md) — logs-signal sibling app
- [OpenTelemetry Profiles Specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/profiles/profiles.md)

## License

Educational demonstration project.
