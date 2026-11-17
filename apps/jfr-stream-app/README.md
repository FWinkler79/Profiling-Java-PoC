# JFR Stream Application

A Spring Boot application that demonstrates continuous JFR (Java Flight Recorder) event streaming with periodic exports to files.

## Overview

This application uses the `jdk.jfr` API to create a continuous recording that:

- Runs continuously in the background
- Captures various JVM events (CPU, GC, threads, monitors, exceptions, etc.)
- Exports snapshots to `.jfr` files at regular intervals (default: every 15 seconds)
- Continues recording while taking snapshots (non-blocking)
- Maintains a rolling buffer of events (default: last 15 seconds)

## Features

- **Continuous Recording**: Uses `jdk.jfr.Recording` with `maxAge` to maintain a rolling buffer
- **Periodic Exports**: Spring `@Scheduled` task exports snapshots at configurable intervals
- **Event Counting**: Tracks the number of events exported in each snapshot
- **REST API**: Endpoints to check recording status and statistics
- **Configurable**: All parameters can be adjusted via `application.properties`

## Requirements

- Java 17 or higher (JFR API is available in Java 11+, but this app uses Spring Boot 3.x)
- Maven 3.6+

## Build and Run

### Build the application

```bash
cd apps/jfr-stream-app
mvn clean package
```

### Run the application

```bash
mvn spring-boot:run
```

Or run the JAR directly:

```bash
java -jar target/jfr-stream-app-1.0.0.jar
```

## Configuration

Edit `src/main/resources/application.properties` to customize:

```properties
# Directory where JFR files will be exported
jfr.export.directory=./jfr-exports

# How often to export JFR snapshots (in seconds)
jfr.export.interval.seconds=15

# Maximum age of events to keep in the recording buffer (in seconds)
jfr.recording.max-age.seconds=15

# Server port
server.port=8081
```

## API Endpoints

### Check JFR Status

```bash
curl http://localhost:8081/api/jfr/status
```

Response:

```json
{
  "recordingState": "RUNNING",
  "totalExports": 5,
  "totalEventsProcessed": 15234,
  "message": "JFR continuous streaming is active"
}
```

### Health Check

```bash
curl http://localhost:8081/api/jfr/health
```

## Monitored Events

The application captures the following JFR event types. This list mirrors the
settings in the root `jfr-config.jfc` file (keep the two in sync):

- **jdk.ExecutionSample** - Method profiling (every 10 ms)
- **jdk.CPULoad** - CPU usage (every 1 second)
- **jdk.JavaThreadStatistics** - Thread statistics (every 1 second)
- **jdk.GarbageCollection** - GC events (threshold 0 ms)
- **jdk.YoungGarbageCollection** - Young-generation GC (threshold 0 ms)
- **jdk.OldGarbageCollection** - Old-generation GC (threshold 0 ms)
- **jdk.ObjectAllocationInNewTLAB** - Allocation inside a new TLAB (with stack traces)
- **jdk.ObjectAllocationOutsideTLAB** - Allocation outside a TLAB (with stack traces)
- **jdk.ThreadStart** - Thread creation (with stack traces)
- **jdk.ThreadEnd** - Thread termination
- **jdk.JavaExceptionThrow** - Exceptions thrown (threshold 0 ms, with stack traces)
- **jdk.JavaErrorThrow** - Errors thrown (with stack traces)
- **jdk.FileRead** - File reads (>10 ms threshold, with stack traces)
- **jdk.FileWrite** - File writes (>10 ms threshold, with stack traces)
- **jdk.SocketRead** - Socket reads (>10 ms threshold, with stack traces)
- **jdk.SocketWrite** - Socket writes (>10 ms threshold, with stack traces)
- **jdk.ClassLoad** - Class loading (with stack traces)
- **jdk.Compilation** - JIT compilation (>100 ms threshold)
- **jdk.JavaMonitorEnter** - Monitor contention (>10 ms threshold, with stack traces)
- **jdk.ThreadPark** - Thread park/unpark (>10 ms threshold, with stack traces)
- **jdk.InitialEnvironmentVariable** - Environment variables (captured at recording start)

## Output Files

JFR files are exported to the configured directory (default: `./jfr-exports`) with filenames like:

```
jfr-export-20261116-143025-0001.jfr
jfr-export-20261116-143055-0002.jfr
jfr-export-20261116-143125-0003.jfr
```

### Analyzing JFR Files

You can analyze the exported `.jfr` files using:

1. **JDK Mission Control (JMC)** - GUI tool for JFR analysis
2. **jfr command-line tool**:

```bash
# Print summary
jfr print jfr-export-20261116-143025-0001.jfr

# Print specific events
jfr print --events jdk.CPULoad jfr-export-20261116-143025-0001.jfr

# Convert to JSON
jfr print --json jfr-export-20261116-143025-0001.jfr > events.json
```

## Architecture

The application consists of:

1. **JfrStreamService** - Core service that:
   - Starts a continuous JFR recording on application startup
   - Periodically exports snapshots using `Recording.copy()`
   - Counts events in each export
   - Cleans up recording on shutdown

2. **JfrController** - REST controller providing:
   - Status endpoint showing recording state and statistics
   - Health check endpoint

3. **JfrStreamApplication** - Main Spring Boot application class with `@EnableScheduling`

## How It Works

1. On startup, `JfrStreamService` creates a `Recording` instance and enables various event types
2. The recording runs continuously with a rolling buffer (maxAge)
3. Every N seconds (configurable), `@Scheduled` method calls `recording.copy()` to create a snapshot
4. The snapshot is saved to a `.jfr` file without stopping the main recording
5. Events in the snapshot are counted for statistics
6. The main recording continues capturing new events

## Key Differences from Traditional JFR

- **No Application Restart Required**: Recording starts automatically with the app
- **Continuous Operation**: Uses `copy()` instead of `dump()` to avoid stopping
- **Rolling Buffer**: Only keeps recent events (configurable via maxAge)
- **Programmatic Control**: No need for JVM flags or external tools

## Logging

The application logs:

- Recording start and configuration
- Each export operation with event count and duration
- Export directory location
- Shutdown summary with total statistics

Example log output:

```
2026-11-16 14:30:15 - JFR export directory: /Users/user/jfr-exports
2026-11-16 14:30:15 - JFR continuous recording started - exporting every 15 seconds
2026-11-16 14:30:45 - Exported JFR snapshot #1 to jfr-export-20261116-143045-0001.jfr (3214 events, 125 ms)
2026-11-16 14:31:15 - Exported JFR snapshot #2 to jfr-export-20261116-143115-0002.jfr (3189 events, 118 ms)
```

## Troubleshooting

**Recording not starting?**

- Ensure you're using Java 11 or higher
- Check that JFR is enabled (it's enabled by default in OpenJDK 11+)

**No events in exports?**

- The application needs to be running long enough to generate events
- Try generating some load (e.g., call the API endpoints repeatedly)

**Export directory permission issues?**

- Check write permissions for the configured export directory
- Use an absolute path in `application.properties` if needed

## Performance Impact

JFR is designed for production use with minimal overhead (typically <2% CPU impact). The periodic exports add a small additional cost proportional to the number of events being processed.

## License

This is a demonstration application for educational purposes.
