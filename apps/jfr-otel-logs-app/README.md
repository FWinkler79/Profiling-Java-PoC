# JFR OpenTelemetry Logs Application

A Spring Boot application that continuously streams Java Flight Recorder (JFR) events and exports them to an OpenTelemetry (OTLP) backend in real-time.

## Overview

This application demonstrates how to:
- Continuously record JFR events programmatically
- Convert JFR events to OpenTelemetry structured logs
- Export profiling data to an OTLP-compatible backend (gRPC)
- Monitor export status via REST API

**Note:** This implementation uses OpenTelemetry Logs API to export JFR events as structured logs.
When the official OpenTelemetry Profile Signal API becomes available in Maven Central, 
you can upgrade to use native profile export format.

**Port:** 8082

## Key Features

✅ Continuous JFR recording with programmatic API  
✅ Automatic OTLP export every 15 seconds  
✅ JFR events exported as OpenTelemetry structured logs  
✅ No local file storage (streams directly to backend)  
✅ REST API for monitoring export status  
✅ Compatible with OpenTelemetry collectors and backends  
✅ Rolling 15-second event buffer  

## Architecture

```
JFR Recording → Temporary Snapshot → Event Conversion → gRPC Export → OTLP Backend
                  (in-memory)         (OTLP Logs)       (port 4317)
```

The application:
1. Starts a continuous JFR recording with all standard events
2. Every 15 seconds, creates a temporary snapshot
3. Reads each JFR event and converts it to an OpenTelemetry log record with structured attributes
4. Exports the log records to the configured OTLP endpoint via gRPC
5. Deletes the temporary file (no persistent storage)

**Implementation Note:** JFR events are currently exported as OpenTelemetry Logs with rich
attributes. This provides full event data to OTLP backends until the official Profile Signal
API is widely available.

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- An OTLP-compatible backend running on port 4317 (or configure a different endpoint)

### OTLP Backend Options

For testing, you can use:
- [Elastic DevFiler](https://github.com/elastic/devfiler/) - Standalone OTLP profile receiver
- [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/) - With OTLP receiver configured
- [Jaeger](https://www.jaegertracing.io/) - With OTLP support
- [Grafana Tempo](https://grafana.com/oss/tempo/) - Distributed tracing backend

## Quick Start

### Option 1: With Docker Compose (Recommended)

The easiest way to get started is using the included OpenTelemetry Collector:

```bash
cd apps/jfr-otel-logs-app

# Start the collector
cd docker
./start-collector.sh

# In another terminal, start the app
cd ..
./run.sh

# View collector logs to see profiling data
cd docker
docker-compose logs -f
```

See [docker/README.md](docker/README.md) for detailed Docker setup documentation.

### Option 2: With Your Own OTLP Backend

### Option 2: With Your Own OTLP Backend

```bash
# Using Docker (Example: DevFiler)
docker run -p 11000:11000 elastic/devfiler

# Or follow: https://github.com/elastic/devfiler/
```

### 2. Configure the OTLP Endpoint (if not using default)

Edit `src/main/resources/application.yaml`:

```properties
jfr.otlp.endpoint=http://localhost:11000
```

### 3. Build and Run

```bash
cd apps/jfr-otel-logs-app
./run.sh
```

The application will:
- Start on port 8082
- Begin continuous JFR recording
- Export profiles to the OTLP endpoint every 15 seconds (after 30-second initial delay)

### 4. Monitor Status

```bash
# Check export status
curl http://localhost:8082/api/jfr/status

# Health check
curl http://localhost:8082/api/jfr/health
```

## Configuration

All settings are in `src/main/resources/application.yaml`:

```yaml
server:
  port: 8082

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
      name: jfr-otel-app
      version: 1.0.0
```

## API Endpoints

### GET /api/jfr/status

Returns JFR recording and export statistics:

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

Health check endpoint:

```json
{
  "status": "UP",
  "service": "JFR OTLP Export Service"
}
```

## Monitored JFR Events

The application monitors the same events as the other apps in this project:

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

## Building

```bash
mvn clean package
```

## Running

```bash
# Using the convenience script
./run.sh

# Or directly
java -jar target/jfr-otel-logs-app-1.0.0.jar

# With custom OTLP endpoint
java -jar target/jfr-otel-logs-app-1.0.0.jar \
  --jfr.otlp.endpoint=http://my-collector:4317
```

## Comparison with Other Apps

| Feature | jfr-profiler-based-app | jfr-stream-app | jfr-otel-logs-app |
|---------|-------------------|----------------|---------------------------|
| **Port** | 8080 | 8081 | 8082 |
| **JFR Control** | External tools | Programmatic | Programmatic |
| **Recording** | On-demand | Continuous | Continuous |
| **Output** | Manual files | Local files | OTLP export |
| **Storage** | Manual | `./jfr-exports/` | None (streaming) |
| **Backend** | JMC/jcmd | File analysis | OTLP collector |
| **Use Case** | Interactive profiling | Continuous monitoring | Production APM |

## Troubleshooting

### Connection refused to OTLP endpoint

Ensure your OTLP backend is running:

```bash
# Test with curl (should fail but show connection attempt)
curl -v http://localhost:4317

# Check logs for connection errors
tail -f logs/application.log
```

### No exports happening

Check the logs for:
- Recording startup messages
- Export attempts and results
- Any error messages

Verify the initial delay has passed (30 seconds after startup).

### High failure rate

Check:
- OTLP backend is accepting connections
- Network connectivity to the endpoint
- Backend has sufficient resources
- Endpoint URL is correct (must include protocol: `http://`)

## Performance Impact

- JFR recording overhead: <2% CPU (standard JFR)
- Export overhead: ~200-500ms every 15 seconds
- Network bandwidth depends on profile size (typically 100KB-1MB per export)

## OpenTelemetry Integration

This application exports JFR events as OpenTelemetry structured logs with the following attributes:

- `jfr.event.type` - JFR event type (e.g., "jdk.ExecutionSample", "jdk.CPULoad")
- `jfr.event.duration` - Event duration in nanoseconds
- `thread.name` - Thread name (when available)
- `thread.id` - Thread ID (when available)
- `code.function` - Method name from stack trace
- `code.namespace` - Class name from stack trace
- `stack.depth` - Stack trace depth
- Event-specific attributes (CPU metrics, GC info, exceptions, etc.)

These structured logs can be queried and analyzed in any OTLP-compatible backend.

## Dependencies

- Spring Boot 3.2.0
- OpenTelemetry SDK 1.33.0
- OpenTelemetry OTLP Log Exporter
- Google Protocol Buffers 3.25.1

## Future Enhancements

When the OpenTelemetry Profile Signal API is released to Maven Central, this application
can be upgraded to use native profile export:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-extension-jfr-profiles</artifactId>
    <version>1.x.x</version>
</dependency>
```

The current log-based approach provides full functionality and is production-ready.

## Related Documentation

- [Main Project README](../../README.md)
- [PROJECT-OVERVIEW.md](../../PROJECT-OVERVIEW.md)
- [jfr-stream-app](../jfr-stream-app/README.md) - File-based streaming version
- [OpenTelemetry Profiles Specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/profiles/profiles.md)

## License

Educational demonstration project.
