# JFR Test Project - Summary

## What This Project Demonstrates

A comprehensive demonstration of Java Flight Recorder (JFR) usage in Spring Boot applications, covering the full spectrum from traditional external-tool profiling through to OpenTelemetry-native profiles export.

## Five Applications

### 1. jfr-profiler-based-app — Traditional Approach

`apps/jfr-profiler-based-app/` · Port 8080

Controls JFR via JVM flags, `jcmd`, or JMC. Includes `start-continuous-profiling.sh` for automatic 15-second snapshot dumps.

**Key files**: `run.sh`, `start-continuous-profiling.sh`, `load-generator.sh`, `jfr-config.jfc`

### 2. jfr-jdk-api-based-app — Programmatic Streaming to Files

`apps/jfr-jdk-api-based-app/` · Port 8080

Uses the `jdk.jfr` API to start a continuous recording automatically and export snapshots every 15 seconds to `jfr-exports/`.

**Key files**: `run.sh`, `generate-load.sh`

### 3. jfr-otel-logs-app — OTLP Logs Export

`apps/jfr-otel-logs-app/` · Port 8080

Streams ~20 JFR event types to an OpenTelemetry Collector as OTLP log records every 15 seconds. No local files produced. Requires Docker.

**Key files**: `run.sh`, `docker/`

### 4. jfr-otel-logs-based-file-upload-app — OTLP File Upload

`apps/jfr-otel-logs-based-file-upload-app/` · Port 8080

Same OTLP logs export as above but serializes to a temporary `.jfr` file before uploading and deleting it. Requires Docker.

**Key files**: `run.sh`, `docker/`

### 5. jfr-otel-profiles-app — OTLP Profiles Signal

`apps/jfr-otel-profiles-app/` · Port 8080

Exports `jdk.ExecutionSample` events as the native OpenTelemetry profiles signal using the opentelemetry-java 1.68.0-alpha API. Requires Docker.

**Key files**: `run.sh`, `docker/`

## Key Differences

| Aspect | profiler-based | jdk-api-based | otel-logs | otel-logs-file-upload | otel-profiles |
|---|---|---|---|---|---|
| **JFR Control** | External | Programmatic | Programmatic | Programmatic | Programmatic |
| **Output** | Local `.jfr` | Local `.jfr` | OTLP logs | OTLP logs | OTLP profiles |
| **Event Coverage** | All configured | ~20 types | ~20 types | ~20 types | ExecutionSample only |
| **Docker needed** | No | No | Yes | Yes | Yes |
| **Use Case** | Interactive profiling | Continuous monitoring | Production APM | Production APM | OTel profiles signal |

## Quick Start

### Traditional (jfr-profiler-based-app)

```bash
cd apps/jfr-profiler-based-app
./run.sh
# or with automatic snapshots:
./start-continuous-profiling.sh
```

### Streaming to files (jfr-jdk-api-based-app)

```bash
cd apps/jfr-jdk-api-based-app
./run.sh
curl http://localhost:8080/api/jfr/status
ls -lh jfr-exports/
```

### OTLP streaming (jfr-otel-logs-app)

```bash
cd apps/jfr-otel-logs-app
./docker/start-collector.sh
./run.sh
```

### OTLP profiles (jfr-otel-profiles-app)

```bash
cd apps/jfr-otel-profiles-app
./docker/start-collector.sh
./run.sh
```

## Monitored Events (file-based and OTLP logs apps)

Configured via `apps/jfr-profiler-based-app/jfr-config.jfc`:

- `jdk.ExecutionSample` — Method profiling (every 10 ms)
- `jdk.CPULoad` — CPU usage (every 1 second)
- `jdk.JavaThreadStatistics` — Thread statistics
- `jdk.GarbageCollection` variants — GC events
- `jdk.ObjectAllocation*` — Allocation with stack traces
- `jdk.ThreadStart / ThreadEnd` — Thread lifecycle
- `jdk.JavaExceptionThrow / JavaErrorThrow` — Exceptions
- `jdk.FileRead / FileWrite / SocketRead / SocketWrite` — I/O (>10 ms)
- `jdk.ClassLoad / Compilation / JavaMonitorEnter / ThreadPark`

`jfr-otel-profiles-app` captures only `jdk.ExecutionSample` (10 ms period).

## Analyzing JFR Files

Applies to the two file-based apps (`jfr-profiler-based-app` and `jfr-jdk-api-based-app`):

```bash
# Command line
jfr print apps/jfr-jdk-api-based-app/jfr-exports/jfr-export-*.jfr
jfr print --events jdk.CPULoad apps/jfr-jdk-api-based-app/jfr-exports/jfr-export-*.jfr
jfr print --json apps/jfr-jdk-api-based-app/jfr-exports/jfr-export-*.jfr > events.json

# Python tools
./shell-scripts/analyze-snapshots.sh apps/jfr-jdk-api-based-app/jfr-exports
./shell-scripts/visualize-jfr.sh apps/jfr-jdk-api-based-app/jfr-exports

# GUI
jmc -open apps/jfr-jdk-api-based-app/jfr-exports/jfr-export-*.jfr
```

## Performance Impact

- All apps: JFR overhead typically <2% CPU
- File-based export: ~100-200ms every 15 seconds
- OTLP export: ~200-500ms every 15 seconds (includes network)

## Documentation Files

- **[README.md](../README.md)** — Main entry point
- **[docs/project-overview.md](project-overview.md)** — Detailed per-app description and architecture
- **apps/jfr-profiler-based-app/README.md** — Traditional JFR
- **apps/jfr-jdk-api-based-app/README.md** — Streaming JFR
- **apps/jfr-otel-logs-app/README.md** — OTLP logs export
- **apps/jfr-otel-logs-based-file-upload-app/README.md** — OTLP file upload
- **apps/jfr-otel-profiles-app/README.md** — OTLP profiles signal

## Requirements

- Java 17+
- Maven 3.6+
- Docker (for OTLP apps)
- (Optional) JDK Mission Control for GUI analysis
