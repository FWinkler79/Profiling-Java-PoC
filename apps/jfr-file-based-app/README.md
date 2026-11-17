# JFR File-Based Application

A basic Spring Boot application demonstrating traditional JFR profiling approaches using external tools and JVM flags.

## Overview

This is a simple Spring Boot web application that can be profiled using Java Flight Recorder (JFR) through traditional methods:
- JVM command-line flags
- JDK Mission Control (JMC)
- `jcmd` command-line tool
- External monitoring tools

## Requirements

- Java 17 or higher
- Maven 3.6+

## Build and Run

### Build the application

```bash
mvn clean package
```

### Run normally (without JFR)

```bash
mvn spring-boot:run
```

Or run the JAR:

```bash
java -jar target/jfr-test-1.0.0.jar
```

The application starts on **port 8080**.

## Using JFR with This Application

### Method 1: Start with JFR enabled via JVM flags

```bash
java -XX:StartFlightRecording=filename=recording.jfr,duration=60s,settings=profile \
     -jar target/jfr-test-1.0.0.jar
```

This will:
- Start the application
- Begin recording JFR events
- Save to `recording.jfr` after 60 seconds
- Use the "profile" settings (more detailed than "default")

### Method 2: Start recording with jcmd (application already running)

First, find the process ID:

```bash
jps -l | grep jfr-test
```

Then start a recording:

```bash
jcmd <PID> JFR.start name=MyRecording settings=profile filename=recording.jfr duration=60s
```

Check recording status:

```bash
jcmd <PID> JFR.check
```

Stop and dump the recording:

```bash
jcmd <PID> JFR.stop name=MyRecording
```

### Method 3: Use JDK Mission Control (JMC)

1. Start the application normally
2. Open JMC
3. Connect to the running JVM
4. Start a Flight Recording from the UI
5. Stop and analyze when done

## API Endpoints

### Hello Endpoint
```bash
curl http://localhost:8080/api/hello
```

Response:
```json
{
  "message": "Hello from JFR Test Application!",
  "timestamp": "2026-11-17T10:30:00"
}
```

### Health Check (Actuator)
```bash
curl http://localhost:8080/actuator/health
```

## Analyzing JFR Files

### Using jfr command-line tool

Print summary:
```bash
jfr print recording.jfr
```

Print specific events:
```bash
jfr print --events jdk.CPULoad recording.jfr
jfr print --events jdk.GarbageCollection recording.jfr
```

Convert to JSON:
```bash
jfr print --json recording.jfr > events.json
```

### Using JDK Mission Control

1. Open JMC
2. File → Open File
3. Select the `.jfr` file
4. Explore the various tabs:
   - General (overview)
   - Memory (heap, GC)
   - Code (hot methods)
   - Threads
   - I/O
   - System

## Configuration

The application uses standard Spring Boot configuration in `src/main/resources/application.properties`:

```properties
server.port=8080
spring.application.name=JFR Test Application
```

## Project Structure

```
jfr-file-based-app/
├── src/
│   └── main/
│       ├── java/com/example/jfr/
│       │   ├── JfrTestApplication.java       # Main application
│       │   └── controller/
│       │       └── HelloController.java       # REST controller
│       └── resources/
│           └── application.properties         # Configuration
├── jfr-exports/                               # Recorded snapshots (generated)
├── jfr-merged/                                # Merged recordings (generated)
├── pom.xml                                    # Maven build file
└── README.md                                  # This file
```

JFR output stays within this folder: continuous-profiling snapshots are written
to `jfr-exports/`, and merging them (menu option 4) produces a single file in
`jfr-merged/`. Both folders are generated at runtime and git-ignored.

## Comparison with jfr-stream-app

| Feature | jfr-file-based-app (this app) | jfr-stream-app |
|---------|-------------------------------|----------------|
| JFR Control | External (JVM flags, jcmd, JMC) | Programmatic (jdk.jfr API) |
| Recording Start | Manual or at JVM startup | Automatic on app startup |
| Continuous Recording | Requires manual setup | Built-in |
| Periodic Exports | Not supported | Every 15 seconds |
| File Management | Manual | Automatic |
| Use Case | On-demand profiling | Continuous monitoring |
| Complexity | Simple | More complex |

## Use Cases

This application is ideal for:
- **One-time profiling sessions** - Start recording when needed
- **Interactive analysis** - Use JMC for deep-dive investigation
- **Learning JFR** - Understand basic JFR concepts
- **Manual control** - Full control over when to record
- **Traditional workflows** - Fits existing JFR tooling and processes

For **continuous monitoring** with automatic exports, see the `jfr-stream-app` application.

## Troubleshooting

### Port 8080 already in use
Change the port in `src/main/resources/application.properties`:
```properties
server.port=8082
```

### JFR not available
JFR is included by default in OpenJDK 11+ and Oracle JDK 11+. For older versions, it may require commercial features to be enabled.

Check JFR availability:
```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal | grep FlightRecorder
```

## Dependencies

- Spring Boot 3.2.0
- Spring Web
- Spring Boot Actuator

## License

This is a demonstration application for educational purposes.
