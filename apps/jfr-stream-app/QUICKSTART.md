# JFR Stream Application - Quick Start

## What Was Created

A complete Spring Boot application that demonstrates continuous JFR event streaming with automatic file exports.

### Project Structure

```
jfr-stream-app/
├── src/main/java/com/example/jfrstream/
│   ├── JfrStreamApplication.java          # Main Spring Boot application
│   ├── service/
│   │   └── JfrStreamService.java          # Core JFR streaming service
│   └── controller/
│       └── JfrController.java              # REST API endpoints
├── src/main/resources/
│   └── application.properties              # Configuration
├── pom.xml                                 # Maven build file
├── run.sh                                  # Convenience start script
├── generate-load.sh                        # Load generation script
├── jfr-exports/                            # Exported snapshots (generated)
├── jfr-merged/                             # Merged recordings (generated)
├── README.md                              # Detailed documentation
├── ARCHITECTURE.md                        # Architecture diagrams
└── .gitignore                             # Git ignore rules
```

## How It Works

1. **On Startup**: Creates a continuous JFR recording that captures various JVM events
2. **Every 15 seconds**: Exports a snapshot of the last 15 seconds of events to a `.jfr` file
3. **Continuous Operation**: The recording never stops, providing ongoing monitoring
4. **REST API**: Provides status endpoints to check recording state and statistics

## Running the Application

### Option 1: Using the run script (recommended)
```bash
./run.sh
```

### Option 2: Using Maven
```bash
mvn spring-boot:run
```

### Option 3: Running the JAR
```bash
mvn clean package
java -jar target/jfr-stream-app-1.0.0.jar
```

## Testing the Application

### 1. Check it's running
```bash
curl http://localhost:8081/api/jfr/health
```

### 2. Check status
```bash
curl http://localhost:8081/api/jfr/status
```

Expected output:
```json
{
  "recordingState": "RUNNING",
  "totalExports": 5,
  "totalEventsProcessed": 15234,
  "message": "JFR continuous streaming is active"
}
```

### 3. Generate load (optional)
In a separate terminal:
```bash
./generate-load.sh
```

### 4. Check exported files
```bash
ls -lh jfr-exports/
```

You should see files like:
```
jfr-export-20261116-163045-0001.jfr
jfr-export-20261116-163115-0002.jfr
jfr-export-20261116-163145-0003.jfr
```

### 5. Analyze a JFR file
```bash
# Summary
jfr print jfr-exports/jfr-export-*.jfr | head -50

# CPU events
jfr print --events jdk.CPULoad jfr-exports/jfr-export-*.jfr

# Convert to JSON
jfr print --json jfr-exports/jfr-export-*.jfr > events.json
```

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Export directory
jfr.export.directory=./jfr-exports

# Export interval (seconds)
jfr.export.interval.seconds=15

# Recording buffer size (seconds)  
jfr.recording.max-age.seconds=15

# Server port
server.port=8081
```

## Key Features

### Monitored JFR Events
- CPU Load (every 1 second)
- Thread Allocation Statistics
- Garbage Collection events
- GC Heap Summary
- Monitor Contention (>10ms)
- Monitor Waits (>10ms)
- Thread Start/End
- Exception Statistics
- Class Loading Statistics

### Automatic Export
- Non-blocking: Recording continues while exporting
- Rolling buffer: Only keeps last 15 seconds in memory
- Timestamped files: Easy to identify when events occurred
- Event counting: Logs number of events in each export

## What Makes This Different

### Traditional JFR Approach
- Requires JVM flags: `-XX:StartFlightRecording`
- Manual control via JMC or command-line
- Typically dump entire recording when stopping
- Good for one-time profiling sessions

### This Application (Programmatic JFR)
- No JVM flags needed
- Automatic recording on startup
- Continuous operation with periodic snapshots
- Perfect for continuous monitoring
- Uses `jdk.jfr` API directly

## Expected Log Output

```
2026-11-16 16:36:15 - JFR export directory: /Users/user/jfr-exports
2026-11-16 16:36:15 - JFR continuous recording started - exporting every 15 seconds
2026-11-16 16:36:45 - Exported JFR snapshot #1 to jfr-export-20261116-163645-0001.jfr (3214 events, 125 ms)
2026-11-16 16:37:15 - Exported JFR snapshot #2 to jfr-export-20261116-163715-0002.jfr (3189 events, 118 ms)
2026-11-16 16:37:45 - Exported JFR snapshot #3 to jfr-export-20261116-163745-0003.jfr (3201 events, 121 ms)
```

## Troubleshooting

### Port 8081 already in use
Change the port in `application.properties`:
```properties
server.port=8082
```

### No events in exports
- Wait at least 30 seconds after startup
- Generate some load with `./generate-load.sh`
- Check that the recording started (look for startup log messages)

### Export directory not created
- Check write permissions in the current directory
- Use an absolute path in configuration

## Next Steps

1. **Run the application**: `./run.sh`
2. **Wait 30 seconds** for the first export
3. **Check the exports**: `ls jfr-exports/`
4. **Analyze a file**: Use JMC or `jfr print`
5. **Generate load**: `./generate-load.sh` in another terminal
6. **Customize events**: Edit `JfrStreamService.java` to enable/disable event types

## Documentation

- `README.md` - Full documentation
- `ARCHITECTURE.md` - Architecture diagrams and data flow
- `/Users/D056076/temp/JFR-Test/PROJECT-OVERVIEW.md` - Comparison with the first application

## Built Successfully

The application has been built and is ready to run:
```
✓ Compilation successful
✓ JAR created: target/jfr-stream-app-1.0.0.jar
✓ All files in place
```

Run it now with: `./run.sh`
