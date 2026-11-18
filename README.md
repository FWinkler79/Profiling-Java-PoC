# JFR Test Project

A comprehensive demonstration of Java Flight Recorder (JFR) usage in Spring Boot applications, showcasing both traditional and programmatic approaches.

## Overview

This project contains **three Spring Boot applications** that demonstrate different ways to use JFR for performance monitoring and profiling:

1. **jfr-file-based-app** - Traditional JFR approach using external tools
2. **jfr-stream-app** - Programmatic JFR with continuous streaming to files
3. **jfr-stream-based-otel-app** - Programmatic JFR with OTLP export (no files)

## Project Structure

```
JFR-Test/
├── apps/
│   ├── jfr-file-based-app/  # Traditional JFR (port 8080)
│   │   ├── src/             # Spring Boot application
│   │   ├── pom.xml          # Maven configuration
│   │   ├── run.sh           # Start script
│   │   ├── jfr-exports/     # Recorded snapshots (generated)
│   │   ├── jfr-merged/      # Merged recordings (generated)
│   │   └── README.md        # Detailed documentation
│   │
│   ├── jfr-stream-app/      # Streaming JFR (port 8081)
│   │   ├── src/             # Spring Boot application
│   │   ├── pom.xml          # Maven configuration
│   │   ├── run.sh           # Start script
│   │   ├── generate-load.sh # Load generator
│   │   ├── jfr-exports/     # Exported snapshots (generated)
│   │   ├── jfr-merged/      # Merged recordings (generated)
│   │   ├── README.md        # Detailed documentation
│   │   └── ARCHITECTURE.md  # Architecture details
│   │
│   └── jfr-stream-based-otel-app/  # OTLP Export (port 8082)
│       ├── src/             # Spring Boot application
│       ├── pom.xml          # Maven configuration
│       ├── run.sh           # Start script
│       ├── README.md        # Detailed documentation
│       └── QUICKSTART.md    # Quick start guide
│
├── PROJECT-OVERVIEW.md      # Detailed comparison
└── README.md               # This file
```

> Each application writes its JFR output **inside its own folder**:
> - **jfr-file-based-app**: Manual JFR files via jcmd
> - **jfr-stream-app**: Automatic exports to `jfr-exports/`, merged to `jfr-merged/`
> - **jfr-stream-based-otel-app**: No files, streams to OTLP backend
> Both file-based apps write to directories that are generated at runtime and git-ignored.

## Quick Start

### Using the Interactive Menu (Recommended)

The easiest way to use all three applications is through the interactive menu:

```bash
./menu.sh
```

The menu provides:
- **Options 1-6**: jfr-file-based-app (traditional JFR, port 8080)
- **Options 7-11**: jfr-stream-app (streaming to files, port 8081)
- **Options 12-17**: jfr-stream-based-otel-app (OTLP streaming, port 8082)
- **Options 18-20**: Shared tools (visualization, configuration, cleanup)

See [MENU.md](MENU.md) for detailed menu documentation.

### Manual Quick Start

#### Option 1: Traditional JFR (jfr-file-based-app)

```bash
cd apps/jfr-file-based-app
./run.sh

# In another terminal, profile with jcmd:
jps -l | grep jfr-test
jcmd <PID> JFR.start name=MyRecording
# ... wait and generate load ...
jcmd <PID> JFR.dump name=MyRecording filename=recording.jfr
```

#### Option 2: Streaming JFR (jfr-stream-app)

```bash
cd apps/jfr-stream-app
./run.sh

# Check status
curl http://localhost:8081/api/jfr/status

# Wait 15 seconds, then check exports
ls -lh jfr-exports/
```

#### Option 3: OTLP Export (jfr-stream-based-otel-app)

```bash
# Start OTLP Collector with Docker Compose
cd apps/jfr-stream-based-otel-app/docker
./start-collector.sh

# In another terminal
cd apps/jfr-stream-based-otel-app
./run.sh

# Check status
curl http://localhost:8082/api/jfr/status

# View collector logs
cd docker
docker-compose logs -f
```

## Applications Comparison

| Feature | jfr-file-based-app | jfr-stream-app | jfr-stream-based-otel-app |
|---------|-------------------|----------------|---------------------------|
| **Port** | 8080 | 8081 | 8082 |
| **JFR Control** | External tools | Programmatic API | Programmatic API |
| **Recording** | On-demand | Continuous | Continuous |
| **Exports** | Manual | Automatic (every 15s) | Automatic (every 15s) |
| **Output** | Manual files | Local files | OTLP/gRPC |
| **Storage** | Manual | `./jfr-exports/` | None (streaming) |
| **Use Case** | Interactive profiling | Continuous monitoring | Production APM/OTLP |
| **Complexity** | Simple | Advanced | Advanced |

## Key Features

### jfr-file-based-app
✅ Simple Spring Boot application  
✅ Traditional JFR profiling  
✅ Works with JMC, jcmd, JVM flags  
✅ Ideal for learning and one-time profiling  

### jfr-stream-app
✅ Continuous JFR recording  
✅ Automatic exports every 15 seconds  
✅ REST API for status monitoring  
✅ Rolling 15-second event buffer  
✅ Perfect for production monitoring  

### jfr-stream-based-otel-app
✅ Continuous JFR recording  
✅ OpenTelemetry Profile Signal export  
✅ OTLP gRPC protocol  
✅ No local file storage  
✅ Direct integration with APM systems  
✅ Production-ready OTLP streaming  

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- (Optional) JDK Mission Control for GUI analysis

## Building All Applications

```bash
# Build jfr-file-based-app
cd apps/jfr-file-based-app
mvn clean package

# Build jfr-stream-app
cd ../jfr-stream-app
mvn clean package

# Build jfr-stream-based-otel-app
cd ../jfr-stream-based-otel-app
mvn clean package
```

## Running All Applications Simultaneously

Since they use different ports, you can run all three at the same time:

```bash
# Terminal 1
cd apps/jfr-file-based-app
./run.sh

# Terminal 2
cd apps/jfr-stream-app
./run.sh

# Terminal 3 (after starting an OTLP backend)
cd apps/jfr-stream-based-otel-app
./run.sh
```

Then compare the approaches side-by-side!

## Analyzing JFR Data

### For jfr-file-based-app and jfr-stream-app

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

### For jfr-stream-based-otel-app

Profiles are exported to the OTLP backend and can be viewed in:
- Your APM system's UI
- OpenTelemetry Collector with visualization
- Elastic DevFiler UI
- Jaeger, Grafana Tempo, or similar backends

## Documentation

- **[PROJECT-OVERVIEW.md](PROJECT-OVERVIEW.md)** - Detailed comparison and architecture
- **[apps/jfr-file-based-app/README.md](apps/jfr-file-based-app/README.md)** - Traditional JFR documentation
- **[apps/jfr-stream-app/README.md](apps/jfr-stream-app/README.md)** - Streaming JFR documentation
- **[apps/jfr-stream-app/ARCHITECTURE.md](apps/jfr-stream-app/ARCHITECTURE.md)** - Architecture diagrams
- **[apps/jfr-stream-based-otel-app/README.md](apps/jfr-stream-based-otel-app/README.md)** - OTLP export documentation
- **[apps/jfr-stream-based-otel-app/QUICKSTART.md](apps/jfr-stream-based-otel-app/QUICKSTART.md)** - Quick start guide

## Use Cases

### Use jfr-file-based-app when:
- Doing interactive profiling sessions
- Learning JFR concepts
- Troubleshooting specific issues
- Working with JMC GUI

### Use jfr-stream-app when:
- Running continuous monitoring in production
- Building custom APM systems
- Needing automatic data collection
- Integrating with monitoring pipelines
- Analyzing profiles locally with JMC

### Use jfr-stream-based-otel-app when:
- Integrating with OpenTelemetry infrastructure
- Using OTLP-compatible APM systems
- Streaming profiles to a central backend
- Avoiding local file storage
- Production observability with OTLP

## API Endpoints

### jfr-file-based-app (port 8080)
- `GET /api/hello` - Test endpoint
- `GET /actuator/health` - Health check

### jfr-stream-app (port 8081)
- `GET /api/jfr/status` - JFR recording status and statistics
- `GET /api/jfr/health` - Health check

### jfr-stream-based-otel-app (port 8082)
- `GET /api/jfr/status` - JFR recording and OTLP export status
- `GET /api/jfr/health` - Health check

## Configuration

Applications can be configured via their respective `application.yaml` files:

- **jfr-file-based-app**: Basic Spring Boot settings  
- **jfr-stream-app**: JFR export directory, intervals, buffer size
- **jfr-stream-based-otel-app**: OTLP endpoint, export intervals, instrumentation scope

See individual README files for details.

## Troubleshooting

### Port already in use
Change ports in `application.yaml`:
- jfr-file-based-app: default 8080
- jfr-stream-app: default 8081
- jfr-stream-based-otel-app: default 8082

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
- jfr-stream-app export overhead: ~100-200ms every 15 seconds
- jfr-stream-based-otel-app export overhead: ~200-500ms every 15 seconds (includes network)

## Contributing

This is a demonstration project for educational purposes.

## Resources

- [JDK Flight Recorder Documentation](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/package-summary.html)
- [JFR Event Reference](https://bestsolution-at.github.io/jfr-doc/)
- [JDK Mission Control](https://www.oracle.com/java/technologies/jdk-mission-control.html)
- [Spring Boot](https://spring.io/projects/spring-boot)

## License

Educational demonstration project.
