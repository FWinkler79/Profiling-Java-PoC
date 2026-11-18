# JFR Test Project - Overview

This project contains two Spring Boot applications demonstrating different approaches to Java Flight Recorder (JFR) usage.

## Project Structure

```text
JFR-Test/
├── apps/
│   ├── jfr-profiler-based-app/  # Traditional JFR approach (external tools)
│   │   ├── jfr-exports/     # Recorded snapshots (generated)
│   │   └── jfr-merged/      # Merged recordings (generated)
│   └── jfr-jdk-api-based-app/      # Programmatic JFR with continuous streaming
│       ├── jfr-exports/     # Exported snapshots (generated)
│       └── jfr-merged/      # Merged recordings (generated)
├── PROJECT-OVERVIEW.md      # This file
└── README.md                # Main project README
```

Each application keeps its JFR output within its own folder: raw recordings in
`jfr-exports/` and merged recordings in `jfr-merged/` (both generated at runtime).

## Applications

### 1. JFR File-Based Application

**Location**: `/Users/D056076/temp/JFR-Test/apps/jfr-profiler-based-app/`  
**Port**: 8080

A basic Spring Boot application demonstrating traditional JFR profiling using external tools and JVM flags.

**Key Characteristics**:

- Manual JFR control via JVM flags, `jcmd`, or JMC
- On-demand profiling sessions
- Suitable for interactive analysis and troubleshooting

**Running**:

```bash
cd apps/jfr-profiler-based-app
./run.sh
```

**Profiling Methods**:

- JVM flags: `java -XX:StartFlightRecording=...`
- jcmd: `jcmd <PID> JFR.start`
- JDK Mission Control (JMC GUI)

---

### 2. JFR JDK API Application

**Location**: `/Users/D056076/temp/JFR-Test/apps/jfr-jdk-api-based-app/`  
**Port**: 8080

An advanced Spring Boot application using the `jdk.jfr` API for programmatic control.

**Key Characteristics**:

- Continuous JFR recording (starts automatically)
- Periodic exports every 15 seconds
- Rolling buffer (15-second window)
- REST API for monitoring
- No external tools required

**Running**:

```bash
cd apps/jfr-jdk-api-based-app
./run.sh
```

**API Endpoints**:

- Status: `http://localhost:8080/api/jfr/status`
- Health: `http://localhost:8080/api/jfr/health`

## Feature Comparison

| Feature | jfr-profiler-based-app | jfr-jdk-api-based-app |
| --------- | -------------------- | ---------------- |
| **JFR Control** | External (JVM flags, jcmd, JMC) | Programmatic (jdk.jfr API) |
| **Recording Start** | Manual or at JVM startup | Automatic on app startup |
| **Continuous Recording** | Requires manual setup | Built-in |
| **Periodic Exports** | Not supported | Every 15 seconds |
| **File Management** | Manual | Automatic with timestamps |
| **REST API** | Basic (Spring Actuator) | Custom JFR status endpoints |
| **Rolling Buffer** | Manual configuration | Built-in (15 seconds) |
| **Use Case** | On-demand profiling | Continuous monitoring |
| **Complexity** | Simple | More complex |
| **Best For** | Interactive analysis | Automated monitoring |

## Quick Start Guide

### Option 1: Traditional Profiling (jfr-profiler-based-app)

```bash
# Step 1: Start the application
cd apps/jfr-profiler-based-app
./run.sh

# Step 2: In another terminal, find the PID
jps -l | grep jfr-profiler-based-app

# Step 3: Start JFR recording
jcmd <PID> JFR.start name=MyRecording settings=profile duration=60s

# Step 4: Generate some load
curl http://localhost:8080/api/hello

# Step 5: Stop and dump
jcmd <PID> JFR.dump name=MyRecording filename=recording.jfr

# Step 6: Analyze
jfr print recording.jfr
```

### Option 2: Continuous Streaming (jfr-jdk-api-based-app)

```bash
# Step 1: Start the application
cd apps/jfr-jdk-api-based-app
./run.sh

# Step 2: Verify it's running
curl http://localhost:8080/api/jfr/status

# Step 3: Wait 30 seconds for first export

# Step 4: Check exported files
ls -lh jfr-exports/

# Step 5: Generate load (optional)
./generate-load.sh

# Step 6: Analyze any export
jfr print jfr-exports/jfr-export-*.jfr
```

## Monitored JFR Events (jfr-jdk-api-based-app)

The streaming application automatically captures the following events. This list
mirrors the settings in the jfr-profiler-based-app's `jfr-config.jfc` file:

- **jdk.ExecutionSample** - Method profiling (every 10 ms)
- **jdk.CPULoad** - CPU usage (every 1 second)
- **jdk.JavaThreadStatistics** - Thread statistics (every 1 second)
- **jdk.GarbageCollection / YoungGarbageCollection / OldGarbageCollection** - GC events (threshold 0 ms)
- **jdk.ObjectAllocationInNewTLAB / ObjectAllocationOutsideTLAB** - Allocation (with stack traces)
- **jdk.ThreadStart** (with stack traces) **/ jdk.ThreadEnd** - Thread lifecycle
- **jdk.JavaExceptionThrow** (threshold 0 ms) **/ jdk.JavaErrorThrow** - Exceptions and errors (with stack traces)
- **jdk.FileRead / FileWrite / SocketRead / SocketWrite** - I/O (>10 ms threshold, with stack traces)
- **jdk.ClassLoad** - Class loading (with stack traces)
- **jdk.Compilation** - JIT compilation (>100 ms threshold)
- **jdk.JavaMonitorEnter** - Monitor contention (>10 ms, with stack traces)
- **jdk.ThreadPark** - Thread park/unpark (>10 ms, with stack traces)
- **jdk.InitialEnvironmentVariable** - Environment variables (captured at recording start)

## Architecture Comparison

### Traditional JFR Architecture (jfr-profiler-based-app)

```text
┌─────────────────┐
│   Application   │
│   (Port 8080)   │
└────────┬────────┘
         │
         │ JVM flags or jcmd
         ▼
┌─────────────────┐
│   JFR Runtime   │
│  (JVM built-in) │
└────────┬────────┘
         │
         │ Manual dump
         ▼
┌─────────────────┐
│   .jfr File     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  JMC / jfr CLI  │
│   (Analysis)    │
└─────────────────┘
```

### Programmatic JFR Architecture (jfr-jdk-api-based-app)

```text
┌─────────────────────────────────┐
│        Application              │
│        (Port 8080)              │
│  ┌───────────────────────────┐  │
│  │  JfrJdkApiService         │  │
│  │  • Continuous recording   │  │
│  │  • Scheduled exports      │  │
│  │  • Rolling buffer (15s)   │  │
│  └───────────┬───────────────┘  │
│              │                   │
│              ▼                   │
│  ┌───────────────────────────┐  │
│  │  JFR Recording API        │  │
│  │  (jdk.jfr.Recording)      │  │
│  └───────────┬───────────────┘  │
└──────────────┼───────────────────┘
               │
               │ Every 15s: recording.dump()
               ▼
┌───────────────────────────────┐
│  ./jfr-exports/               │
│  ├── jfr-export-...-0001.jfr │
│  ├── jfr-export-...-0002.jfr │
│  └── jfr-export-...-0003.jfr │
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│  Analysis Tools               │
│  • JMC                        │
│  • jfr CLI                    │
│  • Custom processing          │
└───────────────────────────────┘
```

## Use Cases

### When to Use jfr-profiler-based-app

✅ **One-time profiling sessions**

- Investigating a specific performance issue
- Analyzing application behavior during a particular operation
- Deep-dive analysis with JMC GUI

✅ **Interactive analysis**

- Comparing different scenarios
- Manual control over recording timing
- Working with existing JFR tools and workflows

✅ **Development and testing**

- Profiling during development
- Learning JFR concepts
- Quick ad-hoc profiling

### When to Use jfr-jdk-api-based-app

✅ **Continuous monitoring**

- Production environment monitoring
- Always-on performance tracking
- Historical performance analysis

✅ **Automated data collection**

- Building custom APM systems
- Integrating with monitoring pipelines
- Exporting to external storage (S3, Elasticsearch, etc.)

✅ **Time-series analysis**

- Tracking performance trends over time
- Correlating with deployment events
- Baseline performance tracking

✅ **Automated alerting**

- Processing events in real-time
- Triggering alerts on anomalies
- Custom metric extraction

## Configuration

### jfr-profiler-based-app Configuration

Edit `apps/jfr-profiler-based-app/src/main/resources/application.properties`:

```properties
server.port=8080
spring.application.name=JFR Test Application
```

### jfr-jdk-api-based-app Configuration

Edit `apps/jfr-jdk-api-based-app/src/main/resources/application.properties`:

```properties
# Server port
server.port=8080

# Export directory
jfr.export.directory=./jfr-exports

# Export interval (seconds)
jfr.export.interval.seconds=15

# Recording buffer size (seconds)
jfr.recording.max-age.seconds=15
```

## Analyzing JFR Files

Both applications produce `.jfr` files that can be analyzed using:

### JDK Command-Line Tools

```bash
# Print summary
jfr print recording.jfr

# Print specific events
jfr print --events jdk.CPULoad recording.jfr
jfr print --events jdk.GarbageCollection recording.jfr

# List all event types
jfr print --events recording.jfr | grep "Event:" | sort -u

# Convert to JSON
jfr print --json recording.jfr > events.json

# Print with categories
jfr print --categories "Java Application" recording.jfr
```

### JDK Mission Control (JMC)

1. Download from [Oracle](https://www.oracle.com/java/technologies/jdk-mission-control.html)
2. Open JMC
3. File → Open File → Select `.jfr` file
4. Explore tabs:
   - **General** - Overview and timeline
   - **Memory** - Heap usage, GC analysis
   - **Code** - Hot methods, compilation
   - **Threads** - Thread activity, contention
   - **I/O** - File and socket I/O
   - **System** - CPU, processes

### Custom Processing

```python
# Example: Parse JFR JSON output
import json

with open('events.json') as f:
    events = json.load(f)

# Filter CPU load events
cpu_events = [e for e in events if e['type'] == 'jdk.CPULoad']

# Calculate average CPU
avg_cpu = sum(e['machineTotal'] for e in cpu_events) / len(cpu_events)
print(f"Average CPU: {avg_cpu:.2%}")
```

## Performance Considerations

### jfr-profiler-based-app

- JFR overhead: typically <2% CPU
- No additional overhead from application code
- File size depends on recording duration and event rate

### jfr-jdk-api-based-app

- JFR overhead: typically <2% CPU
- Export overhead: ~100-200ms every 15 seconds
- Disk space: 1-5 MB per export (depends on activity)
- Memory: rolling buffer keeps only 15 seconds

## Troubleshooting

### Port conflicts

**jfr-profiler-based-app (port 8080)**:

```properties
# Change in application.properties
server.port=8080
```

**jfr-jdk-api-based-app (port 8080)**:

```properties
# Change in application.properties
server.port=8080
```

### JFR not available

Check JFR support:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal | grep FlightRecorder
```

JFR is included by default in:

- OpenJDK 11+
- Oracle JDK 11+

### Build issues

Clean and rebuild:

```bash
# jfr-profiler-based-app
cd apps/jfr-profiler-based-app
mvn clean install

# jfr-jdk-api-based-app
cd apps/jfr-jdk-api-based-app
mvn clean install
```

## Next Steps

1. **Run both applications** to understand the differences
2. **Compare the approaches** for your use case
3. **Customize event types** in jfr-jdk-api-based-app
4. **Integrate with monitoring** systems
5. **Build custom analysis** tools

## References

- [JDK Flight Recorder Documentation](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/package-summary.html)
- [JFR Event Reference](https://bestsolution-at.github.io/jfr-doc/)
- [JDK Mission Control](https://www.oracle.com/java/technologies/jdk-mission-control.html)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)

## License

These are demonstration applications for educational purposes.
