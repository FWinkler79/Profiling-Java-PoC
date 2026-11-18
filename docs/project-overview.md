# JFR Test Project - Overview

This project contains five Spring Boot applications demonstrating different approaches to Java Flight Recorder (JFR) usage, from traditional external-tool profiling to OpenTelemetry-native profiles export.

## Project Structure

```text
JFR-Test/
├── apps/
│   ├── jfr-profiler-based-app/          # Traditional JFR (port 8080)
│   │   ├── src/                         # Spring Boot application
│   │   ├── run.sh                       # Start script
│   │   ├── start-continuous-profiling.sh # Auto-snapshot script
│   │   ├── load-generator.sh            # Load generator
│   │   ├── jfr-config.jfc              # JFR recording configuration
│   │   ├── jfr-exports/                 # Recorded snapshots (generated)
│   │   └── jfr-merged/                  # Merged recordings (generated)
│   ├── jfr-jdk-api-based-app/           # Streaming JFR to files (port 8080)
│   │   ├── src/
│   │   ├── run.sh
│   │   ├── generate-load.sh
│   │   ├── jfr-exports/                 # Exported snapshots (generated)
│   │   └── jfr-merged/                  # Merged recordings (generated)
│   ├── jfr-otel-logs-app/               # OTLP logs export (port 8080)
│   │   ├── src/
│   │   ├── run.sh
│   │   └── docker/                      # OTLP Collector setup
│   ├── jfr-otel-logs-based-file-upload-app/  # OTLP file-upload (port 8080)
│   │   ├── src/
│   │   ├── run.sh
│   │   └── docker/
│   └── jfr-otel-profiles-app/           # OTLP profiles signal (port 8080)
│       ├── src/
│       ├── run.sh
│       └── docker/
├── shell-scripts/
│   ├── merge-jfr-files.sh
│   └── visualize-jfr.sh
├── python-scripts/
│   ├── analyze-snapshots.py
│   └── visualize-jfr.py
├── docs/project-overview.md  # This file
└── README.md                 # Main project README
```

Each file-based app keeps JFR output within its own folder: raw recordings in
`jfr-exports/` and merged recordings in `jfr-merged/` (both generated at runtime and git-ignored).

## Applications

### 1. jfr-profiler-based-app (Traditional Approach)

**Location**: `apps/jfr-profiler-based-app/`
**Port**: 8080

A basic Spring Boot application demonstrating traditional JFR profiling using external tools and JVM flags.

**Key Characteristics**:

- Manual JFR control via JVM flags, `jcmd`, or JMC
- On-demand profiling sessions
- Auto-snapshot script (`start-continuous-profiling.sh`) for periodic 15s snapshots
- Suitable for interactive analysis and troubleshooting

**Running**:

```bash
cd apps/jfr-profiler-based-app
./run.sh
# Or with automatic snapshots:
./start-continuous-profiling.sh
```

**Profiling Methods**:

- JVM flags: `java -XX:StartFlightRecording=settings=jfr-config.jfc,...`
- jcmd: `jcmd <PID> JFR.start`
- JDK Mission Control (JMC GUI)

---

### 2. jfr-jdk-api-based-app (Programmatic Streaming to Files)

**Location**: `apps/jfr-jdk-api-based-app/`
**Port**: 8080

An advanced Spring Boot application using the `jdk.jfr` API for continuous monitoring with automatic file exports.

**Key Characteristics**:

- Continuous JFR recording (starts automatically)
- Periodic exports every 15 seconds to `jfr-exports/`
- Rolling buffer (15-second window)
- REST API for monitoring

**Running**:

```bash
cd apps/jfr-jdk-api-based-app
./run.sh
```

**API Endpoints**:

- Status: `http://localhost:8080/api/jfr/status`
- Health: `http://localhost:8080/api/jfr/health`

---

### 3. jfr-otel-logs-app (OTLP Logs Export)

**Location**: `apps/jfr-otel-logs-app/`
**Port**: 8080

Streams JFR profiling events directly to an OpenTelemetry Collector via OTLP gRPC as log records. No local files are produced.

**Key Characteristics**:

- Continuous JFR recording
- Exports ~20 event types to OTLP every 15 seconds
- No local file storage — streams directly to collector
- Requires the OTLP Collector (Docker)

**Running**:

```bash
cd apps/jfr-otel-logs-app
./docker/start-collector.sh   # start collector first
./run.sh
```

**API Endpoints**:

- Status: `http://localhost:8080/api/jfr/status`
- Health: `http://localhost:8080/api/jfr/health`

---

### 4. jfr-otel-logs-based-file-upload-app (OTLP File Upload)

**Location**: `apps/jfr-otel-logs-based-file-upload-app/`
**Port**: 8080

Similar to `jfr-otel-logs-app` but serializes JFR snapshots to a temporary file before uploading to OTLP, enabling a different transport path for environments where streaming is unavailable.

**Key Characteristics**:

- Writes a temporary `.jfr` file, uploads it as OTLP logs, then deletes it
- Same event coverage as `jfr-otel-logs-app`
- Requires the OTLP Collector (Docker)

**Running**:

```bash
cd apps/jfr-otel-logs-based-file-upload-app
./docker/start-collector.sh
./run.sh
```

**API Endpoints**:

- Status: `http://localhost:8080/api/jfr/status`
- Health: `http://localhost:8080/api/jfr/health`

---

### 5. jfr-otel-profiles-app (OTLP Profiles Signal)

**Location**: `apps/jfr-otel-profiles-app/`
**Port**: 8080

Exports JFR `ExecutionSample` events as the native OpenTelemetry **profiles** signal (not logs). Uses the `opentelemetry-java` 1.68.0-alpha profiles API shim.

**Key Characteristics**:

- Exports only `jdk.ExecutionSample` events (method profiling)
- Uses the OTLP profiles signal (`ExportProfilesServiceRequest`)
- 10 ms sampling period
- Requires the OTLP Collector with profiles support (Docker)

**Running**:

```bash
cd apps/jfr-otel-profiles-app
./docker/start-collector.sh
./run.sh
```

**API Endpoints**:

- Status: `http://localhost:8080/api/jfr/status`
- Health: `http://localhost:8080/api/jfr/health`

---

## Feature Comparison

| Feature | profiler-based | jdk-api-based | otel-logs | otel-logs-file-upload | otel-profiles |
|---|---|---|---|---|---|
| **JFR Control** | External (jcmd/JMC) | Programmatic API | Programmatic API | Programmatic API | Programmatic API |
| **Recording Start** | Manual | Automatic | Automatic | Automatic | Automatic |
| **Exports** | Manual / every 15s | Every 15s to files | Every 15s to OTLP | Every 15s to OTLP | Every 15s to OTLP |
| **Output** | Local `.jfr` files | Local `.jfr` files | OTLP logs/gRPC | OTLP logs/gRPC | OTLP profiles/gRPC |
| **Event Coverage** | All configured events | ~20 event types | ~20 event types | ~20 event types | ExecutionSample only |
| **Docker needed** | No | No | Yes | Yes | Yes |
| **Use Case** | Interactive profiling | Continuous file-based monitoring | Production APM/OTLP | Production APM/OTLP | OTel profiles signal |

## Monitored JFR Events

The file-based apps (`jfr-profiler-based-app`, `jfr-jdk-api-based-app`) and the OTLP logs apps capture the following events (configured in `apps/jfr-profiler-based-app/jfr-config.jfc`):

- **jdk.ExecutionSample** — Method profiling (every 10 ms)
- **jdk.CPULoad** — CPU usage (every 1 second)
- **jdk.JavaThreadStatistics** — Thread statistics (every 1 second)
- **jdk.GarbageCollection / YoungGarbageCollection / OldGarbageCollection** — GC events (threshold 0 ms)
- **jdk.ObjectAllocationInNewTLAB / ObjectAllocationOutsideTLAB** — Allocation (with stack traces)
- **jdk.ThreadStart / jdk.ThreadEnd** — Thread lifecycle (with stack traces)
- **jdk.JavaExceptionThrow / jdk.JavaErrorThrow** — Exceptions and errors (with stack traces)
- **jdk.FileRead / FileWrite / SocketRead / SocketWrite** — I/O (>10 ms, with stack traces)
- **jdk.ClassLoad** — Class loading (with stack traces)
- **jdk.Compilation** — JIT compilation (>100 ms)
- **jdk.JavaMonitorEnter** — Monitor contention (>10 ms, with stack traces)
- **jdk.ThreadPark** — Thread park/unpark (>10 ms, with stack traces)
- **jdk.InitialEnvironmentVariable** — Environment variables (at recording start)

`jfr-otel-profiles-app` captures only **jdk.ExecutionSample** (10 ms period).

## Architecture

### Traditional JFR (jfr-profiler-based-app)

```text
┌─────────────────┐
│   Application   │
│   (Port 8080)   │
└────────┬────────┘
         │ JVM flags or jcmd
         ▼
┌─────────────────┐
│   JFR Runtime   │
└────────┬────────┘
         │ Manual dump / start-continuous-profiling.sh
         ▼
┌─────────────────────────────┐
│  apps/jfr-profiler-based-app│
│  └── jfr-exports/           │
└─────────────────────────────┘
```

### Programmatic Streaming (jfr-jdk-api-based-app)

```text
┌──────────────────────────────────┐
│  Application (Port 8080)         │
│  ┌────────────────────────────┐  │
│  │  JfrJdkApiService          │  │
│  │  • Continuous recording    │  │
│  │  • Scheduled exports (15s) │  │
│  │  • Rolling buffer (15s)    │  │
│  └────────────┬───────────────┘  │
└───────────────┼──────────────────┘
                │ Every 15s
                ▼
┌──────────────────────────────────┐
│  apps/jfr-jdk-api-based-app/     │
│  └── jfr-exports/                │
└──────────────────────────────────┘
```

### OTLP Streaming (jfr-otel-logs-app / jfr-otel-logs-based-file-upload-app / jfr-otel-profiles-app)

```text
┌──────────────────────────────────┐
│  Application (Port 8080)         │
│  • Continuous JFR recording      │
│  • Scheduled export (15s)        │
└────────────────┬─────────────────┘
                 │ OTLP gRPC (port 4317)
                 ▼
┌──────────────────────────────────┐
│  OpenTelemetry Collector (Docker)│
│  • Receives logs or profiles     │
│  • Displays on console           │
└──────────────────────────────────┘
```

## Configuration

All apps are configured via `src/main/resources/application.yaml`. Key settings:

```yaml
server:
  port: 8080

jfr:
  export:
    interval:
      seconds: 15
```

Change the port if 8080 is already in use:

```yaml
server:
  port: 8081  # or any free port
```

## Analyzing JFR Files

Applies to `jfr-profiler-based-app` and `jfr-jdk-api-based-app` which produce local `.jfr` files.

### Command-Line Tools

```bash
jfr print recording.jfr
jfr print --events jdk.CPULoad recording.jfr
jfr print --json recording.jfr > events.json
```

### JDK Mission Control (JMC)

1. Download from [Oracle](https://www.oracle.com/java/technologies/jdk-mission-control.html)
2. File → Open File → select `.jfr` file
3. Explore tabs: General, Memory, Code, Threads, I/O, System

### Python Analysis Tools

```bash
./shell-scripts/analyze-snapshots.sh apps/jfr-jdk-api-based-app/jfr-exports
./shell-scripts/visualize-jfr.sh apps/jfr-jdk-api-based-app/jfr-exports
```

## Performance Considerations

- JFR overhead: typically <2% CPU (designed for production)
- `jfr-jdk-api-based-app` export overhead: ~100-200ms every 15 seconds
- OTLP apps export overhead: ~200-500ms every 15 seconds (includes network)
- `jfr-otel-profiles-app` is lightest: captures only ExecutionSample

## Troubleshooting

### Port already in use

All apps default to 8080; only one can run at a time. Change the port in `application.yaml` if needed.

### JFR not available

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal | grep FlightRecorder
```

JFR is included in OpenJDK 11+ and Oracle JDK 11+.

### Build issues

```bash
cd apps/<app-name>
mvn clean install
```

## References

- [JDK Flight Recorder Documentation](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/package-summary.html)
- [JFR Event Reference](https://bestsolution-at.github.io/jfr-doc/)
- [JDK Mission Control](https://www.oracle.com/java/technologies/jdk-mission-control.html)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [OpenTelemetry Java](https://github.com/open-telemetry/opentelemetry-java)
