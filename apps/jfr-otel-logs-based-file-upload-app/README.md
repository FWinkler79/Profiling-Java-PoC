# JFR OpenTelemetry Logs File Upload Application

A Spring Boot application that continuously records JFR events and periodically exports each recording slice as a
raw `.jfr` binary payload over OTLP.

> **This application is a demonstration of a flawed approach.** It misuses the OpenTelemetry Logs signal as a
> file-transfer channel — something OTLP was not designed for. The failures you encounter when running it (rejected
> exports, incompatible backends) are intentional teaching moments. Read
> [Why this approach is wrong](#why-this-approach-is-wrong) before drawing conclusions from the behaviour you
> observe.

**Port:** 8083

## What this application does

Every 15 seconds the application:

1. Dumps the running JFR recording to a temporary file
2. Reads the raw `.jfr` bytes
3. Emits a single OpenTelemetry log record whose **body is the raw binary** (`bytes_value` in the OTLP protobuf
   `AnyValue` oneof)
4. Deletes the temporary file

The log record carries three attributes:

- `jfr.recording.format` — always `"jfr"`
- `jfr.recording.size_bytes` — size of the payload in bytes
- `jfr.slice.duration_seconds` — duration of the recording slice in seconds

## Why this approach is wrong

A `.jfr` recording is a **profiling artifact** — a binary file meant to be ingested whole by a tool such as JDK
Mission Control, Grafana Pyroscope, or Elastic Universal Profiling. Sending it as an OTLP log body is a category
error:

| What OTel Logs are for | What this app does |
| ---------------------- | ------------------ |
| Discrete, queryable, human-readable events | Opaque multi-hundred-KB binary blobs |
| Indexed and searched in a log analytics service | Must be extracted and opened in a profiling tool |
| Many small records per second | One large record per 15 seconds |
| `string_value` or `kvlist_value` body | `bytes_value` body |

In short: **this is file transfer dressed up as observability telemetry.** The OTel Logs signal is the wrong
protocol for it.

### The SAP Cloud Logging failure is a direct consequence

When the OTel Collector forwards these records to SAP Cloud Logging, the service rejects them immediately:

```text
rpc error: code = InvalidArgument desc = Unknown case
```

"Unknown case" means SAP Cloud Logging received an `AnyValue` whose `bytes_value` field was set — a case it does
not handle, because log analytics services are designed for structured text, not binary files. This is not a
configuration problem; it is a fundamental incompatibility between the payload type and the backend.

See [docker/README.md](docker/README.md) for the full error and collector-side details.

### The right approaches

### Option 1 — Direct file transfer (no OTLP involved)

Write each `.jfr` slice directly to object storage (S3-compatible, SAP Object Store) or POST it to a profiling
backend's HTTP ingest API. Simple, correct, no protocol mismatch.

### Option 2 — OTel Profiles signal

The [OpenTelemetry Profiles signal](https://opentelemetry.io/docs/specs/otel/profiles/) is the purpose-built OTLP
signal for profiling data. It is still experimental, but it is exactly the right abstraction — profiling data
belongs in a profile, not in a log. The `jfr-otel-logs-app` in this project pursues that path.

## Architecture

```text
JFR Recording → Temporary Snapshot → Read raw bytes → Single OTLP log record → gRPC Export → OTLP Backend
                  (disk file)         (Files.readAllBytes)  (bytes_value body)   (port 4317)    (rejects it)
```

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- An OTLP-compatible backend on port 4317, or the included Docker Compose collector (see
  [docker/README.md](docker/README.md))

## Quick Start

### Option 1: With Docker Compose (Recommended for observing the failure)

```bash
cd apps/jfr-otel-logs-based-file-upload-app

# Start the collector
cd docker
./start-collector.sh

# In another terminal, start the app
cd ..
./run.sh

# Watch the collector logs — the debug exporter confirms receipt, the SAP CLS exporter shows the rejection
cd docker
docker-compose logs -f
```

See [docker/README.md](docker/README.md) for details on what to expect.

### Option 2: With your own OTLP backend

Configure any OTLP-compatible endpoint that accepts `bytes_value` log bodies (most general-purpose collectors will
accept and pass through the records, but downstream analytics backends will likely reject them):

```yaml
jfr:
  otlp:
    endpoint: http://localhost:4317
```

### Monitor status

```bash
curl http://localhost:8083/api/jfr/status
curl http://localhost:8083/api/jfr/health
```

## Configuration

All settings are in `src/main/resources/application.yaml`:

```yaml
server:
  port: 8083

jfr:
  otlp:
    endpoint: http://localhost:4317
  export:
    interval:
      seconds: 15
  recording:
    max-age:
      seconds: 15
  instrumentation:
    scope:
      name: jfr-otel-logs-based-file-upload-app
      version: 1.0.0
```

## API Endpoints

### GET /api/jfr/status

```json
{
  "recording_state": "RUNNING",
  "otlp_endpoint": "http://localhost:4317",
  "total_exports": 42,
  "successful_exports": 40,
  "failed_exports": 2,
  "total_events_exported": 123456
}
```

### GET /api/jfr/health

```json
{
  "status": "UP",
  "service": "JFR OTLP Export Service"
}
```

## Monitored JFR Events

The recording captures the same event set as the other apps in this project:

- **Method Profiling**: `jdk.ExecutionSample` (10ms sampling)
- **CPU & Threads**: `jdk.CPULoad`, `jdk.JavaThreadStatistics`
- **Garbage Collection**: All GC events
- **Memory Allocation**: TLAB and non-TLAB allocations
- **Thread Lifecycle**: Start/end events
- **Exceptions**: Java exceptions and errors
- **I/O Events**: File and socket operations
- **Class Loading**: Class load events
- **Compilation**: JIT compilation
- **Monitor Events**: Lock contention and thread parking
- **Environment**: JVM configuration

All of these events are bundled into the raw `.jfr` binary and sent as a single opaque blob — none of them are
individually addressable via OTLP attributes.

## Building and Running

```bash
mvn clean package

# Using the convenience script
./run.sh

# Or directly
java -jar target/jfr-otel-logs-based-file-upload-app-1.0.0.jar

# With a custom OTLP endpoint
java -jar target/jfr-otel-logs-based-file-upload-app-1.0.0.jar \
  --jfr.otlp.endpoint=http://my-collector:4317
```

## Comparison with Other Apps

| Feature | jfr-profiler-based-app | jfr-stream-app | jfr-otel-logs-based-file-upload-app |
| ------- | ---------------------- | -------------- | -------------------------- |
| **Port** | 8080 | 8081 | 8083 |
| **JFR Control** | External tools | Programmatic | Programmatic |
| **Recording** | On-demand | Continuous | Continuous |
| **Output** | Manual files | Local files | OTLP export (flawed) |
| **Storage** | Manual | `./jfr-exports/` | None (streaming) |
| **Backend** | JMC/jcmd | File analysis | OTLP collector |
| **Use Case** | Interactive profiling | Continuous monitoring | Demonstrating misuse of OTel Logs |

## Troubleshooting

### The SAP Cloud Logging exporter always fails

This is expected. See [Why this approach is wrong](#why-this-approach-is-wrong) and
[docker/README.md](docker/README.md).

### Connection refused to OTLP endpoint

Ensure the collector is running before starting the app:

```bash
docker compose ps   # should show jfr-otel-collector as "Up"
curl http://localhost:13133  # collector health check
```

### No exports happening

Check that the 30-second initial delay has passed, then verify:

```bash
curl http://localhost:8083/api/jfr/status
```

Look for `total_exports` incrementing. If it stays at 0, check application logs for recording startup errors.

## Performance Impact

- JFR recording overhead: <2% CPU (standard JFR)
- Export overhead: ~200–500 ms every 15 seconds
- Network bandwidth: typically 100 KB–1 MB per export slice

## Related Documentation

- [docker/README.md](docker/README.md) — Collector setup and the SAP Cloud Logging failure in detail
- [Main Project README](../../README.md)
- [PROJECT-OVERVIEW.md](../../PROJECT-OVERVIEW.md)
- [jfr-otel-logs-app](../jfr-otel-logs-app/README.md) — The correct OTel Profiles approach
- [OpenTelemetry Profiles Specification](https://opentelemetry.io/docs/specs/otel/profiles/)

## License

Educational demonstration project.
