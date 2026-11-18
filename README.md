# JFR Test Project

A comprehensive demonstration of Java Flight Recorder (JFR) usage in Spring Boot applications,
showcasing both traditional and programmatic approaches.

## Overview

This project contains **five Spring Boot applications** that demonstrate different ways
to use JFR for performance monitoring and profiling:

1. **jfr-profiler-based-app** — Traditional JFR using external tools (jcmd, JVM flags, JMC)
2. **jfr-jdk-api-based-app** — Programmatic JFR with continuous streaming to local files
3. **jfr-otel-logs-app** — Programmatic JFR with OTLP logs export (no local files)
4. **jfr-otel-logs-based-file-upload-app** — OTLP logs export via file upload
5. **jfr-otel-profiles-app** — OpenTelemetry native profiles signal export

## Project Structure

```text
JFR-Test/
├── apps/
│   ├── jfr-profiler-based-app/          # Traditional JFR (port 8080)
│   │   ├── src/                         # Spring Boot application
│   │   ├── pom.xml                      # Maven configuration
│   │   ├── run.sh                       # Start script
│   │   ├── start-continuous-profiling.sh # Auto-snapshot script
│   │   ├── load-generator.sh            # Load generator
│   │   ├── jfr-config.jfc              # JFR recording configuration
│   │   ├── jfr-exports/                 # Recorded snapshots (generated)
│   │   ├── jfr-merged/                  # Merged recordings (generated)
│   │   └── README.md                    # Detailed documentation
│   │
│   ├── jfr-jdk-api-based-app/           # Streaming JFR (port 8080)
│   │   ├── src/                         # Spring Boot application
│   │   ├── pom.xml                      # Maven configuration
│   │   ├── run.sh                       # Start script
│   │   ├── generate-load.sh             # Load generator
│   │   ├── jfr-exports/                 # Exported snapshots (generated)
│   │   ├── jfr-merged/                  # Merged recordings (generated)
│   │   ├── README.md                    # Detailed documentation
│   │   └── ARCHITECTURE.md             # Architecture details
│   │
│   ├── jfr-otel-logs-app/               # OTLP logs export (port 8080)
│   │   ├── src/                         # Spring Boot application
│   │   ├── pom.xml                      # Maven configuration
│   │   ├── run.sh                       # Start script
│   │   ├── docker/                      # OTLP Collector Docker setup
│   │   ├── README.md                    # Detailed documentation
│   │   └── QUICKSTART.md               # Quick start guide
│   │
│   ├── jfr-otel-logs-based-file-upload-app/  # OTLP file-upload (port 8080)
│   │   ├── src/                         # Spring Boot application
│   │   ├── pom.xml                      # Maven configuration
│   │   ├── run.sh                       # Start script
│   │   ├── docker/                      # OTLP Collector Docker setup
│   │   ├── README.md                    # Detailed documentation
│   │   └── QUICKSTART.md               # Quick start guide
│   │
│   └── jfr-otel-profiles-app/           # OTLP profiles export (port 8080)
│       ├── src/                         # Spring Boot application
│       ├── pom.xml                      # Maven configuration
│       ├── run.sh                       # Start script
│       ├── docker/                      # OTLP Collector Docker setup
│       ├── README.md                    # Detailed documentation
│       └── QUICKSTART.md               # Quick start guide
│
├── docs/
│   ├── menu.md                      # Menu script reference
│   ├── project-overview.md          # Detailed comparison and architecture
│   ├── project-summary.md           # Project summary
│   ├── quick-reference.md           # JFR quick reference
│   ├── quickstart-visualization.md  # Visualization quick start
│   ├── visual-guide.md              # JFR snapshot mechanism
│   ├── visualization-guide.md       # JFR visualization guide
│   ├── visualization-summary.md     # Visualization summary
│   └── jfr-on-the-fly-snapshots.md  # On-the-fly snapshot notes
├── shell-scripts/
│   ├── merge-jfr-files.sh           # Merge JFR exports (general purpose)
│   └── view-jfr.sh                  # Interactive JFR viewer (general purpose)
├── python-scripts/
│   ├── analyze-snapshots.py         # Snapshot analyzer (general purpose)
│   └── visualize-jfr.py             # JFR visualizer (general purpose)
├── menu.sh                      # Interactive control panel
└── README.md                    # This file
```

> Each application writes its JFR output **inside its own folder**:
>
> - **jfr-profiler-based-app**: Manual JFR files via jcmd
> - **jfr-jdk-api-based-app**: Automatic exports to `jfr-exports/`, merged to `jfr-merged/`
> - **jfr-otel-logs-app**: No files, streams to OTLP backend
> Both file-based apps write to directories that are generated at runtime and git-ignored.

## Quick Start

### Using the Interactive Menu (Recommended)

The easiest way to explore the applications is through the interactive menu:

```bash
./menu.sh
```

The menu provides:

- **Options 1-6**: jfr-profiler-based-app (traditional JFR, port 8080)
- **Options 7-11**: jfr-jdk-api-based-app (streaming to files, port 8080)
- **Options 12-17**: jfr-otel-logs-app (OTLP logs streaming, port 8080)
- **Options 21-26**: jfr-otel-logs-based-file-upload-app (OTLP file upload, port 8080)
- **Options 27-29**: Shared tools (visualization, configuration, cleanup)

The `jfr-otel-profiles-app` is run directly via `apps/jfr-otel-profiles-app/run.sh` (not yet in the menu).

See [docs/menu.md](docs/menu.md) for detailed menu documentation.

### Manual Quick Start

#### Option 1: Traditional JFR (jfr-profiler-based-app)

```bash
cd apps/jfr-profiler-based-app
./run.sh
# or with automatic 15s snapshots:
./start-continuous-profiling.sh
```

#### Option 2: Streaming JFR (jfr-jdk-api-based-app)

```bash
cd apps/jfr-jdk-api-based-app
./run.sh

# Check status
curl http://localhost:8080/api/jfr/status

# Wait 15 seconds, then check exports
ls -lh jfr-exports/
```

#### Option 3: OTLP Logs (jfr-otel-logs-app)

```bash
# Start OTLP Collector with Docker Compose
cd apps/jfr-otel-logs-app/docker
./start-collector.sh

# In another terminal
cd apps/jfr-otel-logs-app
./run.sh

# Check status
curl http://localhost:8080/api/jfr/status

# View collector logs
cd docker
docker-compose logs -f
```

#### Option 4: OTLP File Upload (jfr-otel-logs-based-file-upload-app)

```bash
cd apps/jfr-otel-logs-based-file-upload-app/docker
./start-collector.sh

cd apps/jfr-otel-logs-based-file-upload-app
./run.sh
```

#### Option 5: OTLP Profiles (jfr-otel-profiles-app)

```bash
cd apps/jfr-otel-profiles-app/docker
./start-collector.sh

cd apps/jfr-otel-profiles-app
./run.sh
```

## Applications Comparison

| Feature | jfr-profiler-based-app | jfr-jdk-api-based-app | jfr-otel-logs-app | jfr-otel-logs-based-file-upload-app | jfr-otel-profiles-app |
| --------- | ------------------- | ---------------- | --------------------------- | ----------------------------- | --------------------- |
| **Port** | 8080 | 8080 | 8080 | 8080 | 8080 |
| **JFR Control** | External tools | Programmatic API | Programmatic API | Programmatic API | Programmatic API |
| **Recording** | On-demand | Continuous | Continuous | Continuous | Continuous |
| **Exports** | Manual | Automatic (every 15s) | Automatic (every 15s) | Automatic (every 15s) | Automatic (every 15s) |
| **Output** | Manual files | Local files | OTLP logs/gRPC | OTLP logs/gRPC (file upload) | OTLP profiles/gRPC |
| **Storage** | Manual | `./jfr-exports/` | None (streaming) | None (streaming) | None (streaming) |
| **Use Case** | Interactive profiling | Continuous monitoring | Production APM/OTLP | Production APM/OTLP | OTel profiles signal |
| **Complexity** | Simple | Advanced | Advanced | Advanced | Advanced |

## Key Features

### jfr-profiler-based-app

✅ Simple Spring Boot application
✅ Traditional JFR profiling via jcmd / JVM flags / JMC
✅ `start-continuous-profiling.sh` for automatic 15s snapshots
✅ Ideal for learning and one-time profiling

### jfr-jdk-api-based-app

✅ Continuous JFR recording via `jdk.jfr` API
✅ Automatic exports every 15 seconds to `jfr-exports/`
✅ REST API for status monitoring
✅ Perfect for file-based continuous monitoring

### jfr-otel-logs-app

✅ Streams ~20 JFR event types to OTLP Collector as log records
✅ No local file storage
✅ OTLP gRPC protocol

### jfr-otel-logs-based-file-upload-app

✅ Same OTLP logs export as above
✅ File-upload transport path (write → upload → delete)
✅ OTLP gRPC protocol

### jfr-otel-profiles-app

✅ Native OpenTelemetry profiles signal
✅ `jdk.ExecutionSample` → `ExportProfilesServiceRequest`
✅ Uses opentelemetry-java 1.68.0-alpha profiles API

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- (Optional) JDK Mission Control for GUI analysis

## Building All Applications

```bash
# Build jfr-profiler-based-app
cd apps/jfr-profiler-based-app
mvn clean package

# Build jfr-jdk-api-based-app
cd ../jfr-jdk-api-based-app
mvn clean package

# Build jfr-otel-logs-app
cd ../jfr-otel-logs-app
mvn clean package
```

## Running Applications

All applications use port 8080, so only one can run at a time. Use `menu.sh` to start any of the first four apps, or run directly:

```bash
# Traditional JFR
cd apps/jfr-profiler-based-app && ./run.sh

# Streaming to files
cd apps/jfr-jdk-api-based-app && ./run.sh

# OTLP streaming (start collector first)
cd apps/jfr-otel-logs-app && ./docker/start-collector.sh && ./run.sh

# OTLP file upload
cd apps/jfr-otel-logs-based-file-upload-app && ./docker/start-collector.sh && ./run.sh

# OTLP profiles
cd apps/jfr-otel-profiles-app && ./docker/start-collector.sh && ./run.sh
```

## Analyzing JFR Data

### For jfr-profiler-based-app and jfr-jdk-api-based-app

Both applications produce `.jfr` files that can be analyzed with:

### Command-Line Tools

```bash
# Summary
jfr print recording.jfr

# Specific events
jfr print --events jdk.CPULoad recording.jfr

# JSON export
jfr print --json recording.jfr > events.json
```

### JDK Mission Control (GUI)

1. Download JMC
2. Open the `.jfr` file
3. Explore the various analysis tabs

### For jfr-otel-logs-app

Profiles are exported to the OTLP backend and can be viewed in:

- Your APM system's UI
- OpenTelemetry Collector with visualization
- Elastic DevFiler UI
- Jaeger, Grafana Tempo, or similar backends

## Documentation

### Project

- **[docs/project-overview.md](docs/project-overview.md)** — Detailed comparison and architecture of all apps
- **[docs/project-summary.md](docs/project-summary.md)** — High-level project summary
- **[docs/menu.md](docs/menu.md)** — Full menu.sh reference (all options, workflows, tips)

### JFR Analysis

- **[docs/quick-reference.md](docs/quick-reference.md)** — JFR snapshot size control and key concepts
- **[docs/visual-guide.md](docs/visual-guide.md)** — JFR snapshot mechanism explained visually
- **[docs/visualization-guide.md](docs/visualization-guide.md)** — Complete guide to visualizing JFR files (JMC, CLI, JSON)
- **[docs/quickstart-visualization.md](docs/quickstart-visualization.md)** — Fastest paths to visualizing JFR data
- **[docs/visualization-summary.md](docs/visualization-summary.md)** — Quick reference card for visualization methods
- **[docs/jfr-on-the-fly-snapshots.md](docs/jfr-on-the-fly-snapshots.md)** — Notes on in-memory JFR snapshot access

### Per-Application

- **[apps/jfr-profiler-based-app/README.md](apps/jfr-profiler-based-app/README.md)** — Traditional JFR
- **[apps/jfr-jdk-api-based-app/README.md](apps/jfr-jdk-api-based-app/README.md)** — Streaming JFR to files
- **[apps/jfr-jdk-api-based-app/ARCHITECTURE.md](apps/jfr-jdk-api-based-app/ARCHITECTURE.md)** — Architecture diagrams
- **[apps/jfr-otel-logs-app/README.md](apps/jfr-otel-logs-app/README.md)** — OTLP logs export
- **[apps/jfr-otel-logs-based-file-upload-app/README.md](apps/jfr-otel-logs-based-file-upload-app/README.md)** — OTLP file-upload
- **[apps/jfr-otel-profiles-app/README.md](apps/jfr-otel-profiles-app/README.md)** — OTLP profiles signal

## Use Cases

### Use jfr-profiler-based-app when

- Doing interactive profiling sessions
- Learning JFR concepts
- Troubleshooting specific issues
- Working with JMC GUI

### Use jfr-jdk-api-based-app when

- Running continuous monitoring in production
- Building custom APM systems
- Needing automatic data collection
- Integrating with monitoring pipelines
- Analyzing profiles locally with JMC

### Use jfr-otel-logs-app when

- Integrating with OpenTelemetry infrastructure
- Using OTLP-compatible APM systems
- Streaming profiles to a central backend
- Avoiding local file storage
- Production observability with OTLP

## API Endpoints

### jfr-profiler-based-app (port 8080)

- `GET /api/hello` - Test endpoint
- `GET /actuator/health` - Health check

### jfr-jdk-api-based-app (port 8080)

- `GET /api/jfr/status` - JFR recording status and statistics
- `GET /api/jfr/health` - Health check

### jfr-otel-logs-app (port 8080)

- `GET /api/jfr/status` - JFR recording and OTLP export status
- `GET /api/jfr/health` - Health check

## Configuration

Applications can be configured via their respective `application.yaml` files:

- **jfr-profiler-based-app**: Basic Spring Boot settings  
- **jfr-jdk-api-based-app**: JFR export directory, intervals, buffer size
- **jfr-otel-logs-app**: OTLP endpoint, export intervals, instrumentation scope

See individual README files for details.

## Troubleshooting

### Port already in use

All applications default to port 8080. Change it in the app's `application.yaml`:

```yaml
server:
  port: 8081  # or any free port
```

### JFR not available

JFR is included in OpenJDK 11+ and Oracle JDK 11+. Check with:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal | grep FlightRecorder
```

### Build failures

Clean and rebuild:

```bash
mvn clean install
```

## Performance Impact

JFR is designed for production use with minimal overhead:

- Typical overhead: <2% CPU
- jfr-jdk-api-based-app export overhead: ~100-200ms every 15 seconds
- jfr-otel-logs-app export overhead: ~200-500ms every 15 seconds (includes network)

## Contributing

This is a demonstration project for educational purposes.

## Resources

- [JDK Flight Recorder Documentation](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/package-summary.html)
- [JFR Event Reference](https://bestsolution-at.github.io/jfr-doc/)
- [JDK Mission Control](https://www.oracle.com/java/technologies/jdk-mission-control.html)
- [Spring Boot](https://spring.io/projects/spring-boot)

## License

Educational demonstration project.
