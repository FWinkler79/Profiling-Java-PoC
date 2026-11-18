# JFR Test Project - Summary

## What This Project Demonstrates

A comprehensive demonstration of Java Flight Recorder (JFR) usage in Spring Boot applications,
showcasing both traditional and programmatic approaches.

## Project Structure

This project contains **two independent Spring Boot applications**:

### 1. jfr-profiler-based-app (Traditional Approach)

Located in: `apps/jfr-profiler-based-app/`

A basic Spring Boot application demonstrating traditional JFR profiling using external tools:

- JVM flags (`-XX:StartFlightRecording`)
- `jcmd` command-line tool
- JDK Mission Control (JMC)

**Key Files**:

- `pom.xml` - Maven configuration
- `src/main/java/com/example/jfr/` - Application code
  - `JfrTestApplication.java` - Main class
  - `controller/HelloController.java` - REST endpoints
- `src/main/resources/application.properties` - Configuration
- `run.sh` - Startup script
- `README.md` - Complete documentation

### 2. jfr-jdk-api-based-app (Programmatic Approach)

Located in: `apps/jfr-jdk-api-based-app/`

An advanced Spring Boot application using the `jdk.jfr` API for continuous monitoring:

- Automatic JFR recording on startup
- Periodic exports every 15 seconds
- Rolling 15-second event buffer
- REST API for monitoring

**Key Files**:

- `pom.xml` - Maven configuration
- `src/main/java/com/example/jfrjdkapi/` - Application code
  - `JfrJdkApiApplication.java` - Main class
  - `service/JfrJdkApiService.java` - JFR streaming service
  - `controller/JfrController.java` - REST API
- `src/main/resources/application.properties` - Configuration
- `run.sh` - Startup script
- `generate-load.sh` - Load generator
- `README.md` - Complete documentation
- `ARCHITECTURE.md` - Architecture details

## Key Differences

| Aspect | jfr-profiler-based-app | jfr-jdk-api-based-app |
| -------- | -------------------- | ---------------- |
| **Port** | 8080 | 8081 |
| **JFR Control** | External (jcmd/JMC) | Programmatic API |
| **Recording** | Manual start | Automatic |
| **Exports** | On-demand dump | Every 15 seconds |
| **Buffer** | Manual config | Rolling 15s |
| **Monitoring** | Basic Actuator | Custom REST API |
| **Use Case** | Interactive profiling | Continuous monitoring |

## Quick Start

### Traditional Approach (jfr-profiler-based-app)

```bash
cd apps/jfr-profiler-based-app
./run.sh

# In another terminal:
jps -l | grep jfr-profiler-based-app
jcmd <PID> JFR.start name=MyRecording settings=profile
# ... wait and generate load ...
jcmd <PID> JFR.dump name=MyRecording filename=recording.jfr
jfr print recording.jfr
```

### Streaming Approach (jfr-jdk-api-based-app)

```bash
cd apps/jfr-jdk-api-based-app
./run.sh

# Check status
curl http://localhost:8081/api/jfr/status

# Wait 30 seconds
ls -lh jfr-exports/

# Analyze
jfr print jfr-exports/jfr-export-*.jfr
```

## How It Works

### jfr-profiler-based-app

1. Application runs normally
2. JFR is started via external command (jcmd or JVM flag)
3. Events are captured while recording is active
4. Recording is dumped to file manually
5. File is analyzed with JMC or jfr CLI

### jfr-jdk-api-based-app

1. Application starts JFR recording automatically
2. Events are continuously captured in a 15-second rolling buffer
3. Every 15 seconds, a snapshot is exported to a `.jfr` file
4. Recording continues without interruption
5. Files can be analyzed immediately or later

## Monitored Events (jfr-jdk-api-based-app)

Mirrors the settings in the root `jfr-config.jfc` file:

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

## Use Cases

### Use jfr-profiler-based-app For

✅ Learning JFR basics  
✅ One-time profiling sessions  
✅ Interactive analysis with JMC  
✅ Troubleshooting specific issues  
✅ Development and testing  

### Use jfr-jdk-api-based-app For

✅ Production monitoring  
✅ Continuous performance tracking  
✅ Automated data collection  
✅ Building custom APM systems  
✅ Time-series performance analysis  

## Analyzing JFR Files

Both applications produce `.jfr` files that can be analyzed with:

### Command-Line Tools

```bash
# Summary
jfr print recording.jfr

# Specific events
jfr print --events jdk.CPULoad recording.jfr
jfr print --events jdk.GarbageCollection recording.jfr

# JSON export
jfr print --json recording.jfr > events.json
```

### JDK Mission Control (GUI)

1. Download from Oracle
2. File → Open File
3. Select `.jfr` file
4. Explore tabs: General, Memory, Code, Threads, I/O, System

## Expected Results

### jfr-profiler-based-app output

- Manual control over recording duration
- File size depends on recording duration and activity
- Typically 1-10 MB for a few minutes of recording

### jfr-jdk-api-based-app output

- Automatic exports every 15 seconds
- Each file: 1-5 MB depending on activity
- Consistent file sizes due to rolling buffer

## Performance Impact

Both approaches use the same underlying JFR technology:

- Typical overhead: <2% CPU
- Designed for production use
- jfr-jdk-api-based-app adds ~100-200ms overhead per export

## Documentation Files

- **README.md** - Main project overview
- **PROJECT-OVERVIEW.md** - Detailed comparison and architecture
- **apps/jfr-profiler-based-app/README.md** - Traditional JFR documentation
- **apps/jfr-jdk-api-based-app/README.md** - Streaming JFR documentation
- **apps/jfr-jdk-api-based-app/ARCHITECTURE.md** - Architecture diagrams

## Testing Both Applications

Since they use different ports (8080 and 8081), you can run both simultaneously to compare approaches side-by-side.

## Next Steps

1. **Run jfr-profiler-based-app** to understand traditional JFR
2. **Run jfr-jdk-api-based-app** to see continuous streaming
3. **Compare the approaches** for your use case
4. **Customize configurations** as needed
5. **Integrate with your monitoring stack**

## Requirements

- Java 17 or higher
- Maven 3.6+
- (Optional) JDK Mission Control for GUI analysis

## License

Educational demonstration project.
