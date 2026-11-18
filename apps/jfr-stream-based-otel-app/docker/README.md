# Docker Setup for JFR OTLP Export

This directory contains Docker Compose configuration to run an OpenTelemetry Collector that receives JFR profiling logs and displays them on the console.

## Overview

The setup includes:
- **OpenTelemetry Collector** - Receives OTLP logs via gRPC on port 4317
- **Console Exporters** - Outputs received logs to stdout for inspection

## Quick Start

### 1. Start the Collector

```bash
cd docker
docker-compose up
```

The collector will start and listen on:
- `4317` - OTLP gRPC (for the Java app)
- `4318` - OTLP HTTP (optional)
- `8888` - Prometheus metrics
- `13133` - Health check endpoint

### 2. Start the Application

In another terminal:

```bash
cd ..  # Back to jfr-stream-based-otel-app directory
./run.sh
```

The application is already configured to send to `http://localhost:4317`.

### 3. View the Profiling Logs

Watch the Docker Compose terminal - you'll see JFR events as structured logs:

```
2024-11-18T10:30:15.123Z  info  Logs  {...}
ResourceLog #0
Resource SchemaURL: 
Resource attributes:
     -> service.name: Str(jfr-otel-app)
     -> service.version: Str(1.0.0)
     -> service.namespace: Str(jfr-profiling)
     -> telemetry.sdk.name: Str(jfr-otel-exporter)
     -> telemetry.sdk.language: Str(java)
ScopeLogs #0
ScopeLogs SchemaURL: 
InstrumentationScope jfr-otel-app 1.0.0
LogRecord #0
Timestamp: 2024-11-18 10:30:15.123456789 +0000 UTC
Severity: Info
Body: Str(jdk.ExecutionSample)
Attributes:
     -> jfr.event.type: Str(jdk.ExecutionSample)
     -> jfr.event.duration: Int(1234567)
     -> thread.name: Str(main)
     -> thread.id: Int(1)
     -> code.function: Str(handleRequest)
     -> code.namespace: Str(com.example.Controller)
     -> stack.depth: Int(15)
```

## Configuration

### Collector Configuration

The collector is configured in `otel-collector-config.yaml`:

**Receivers:**
- OTLP gRPC on port 4317
- OTLP HTTP on port 4318

**Processors:**
- `batch` - Batches logs for efficiency
- `resource` - Adds service.namespace attribute

**Exporters:**
- `logging` - Detailed output to console
- `debug` - Very detailed output with sampling

### Application Configuration

The app's `application.yaml` is configured to send to the collector:

```yaml
jfr:
  otlp:
    endpoint: http://localhost:4317
```

## Viewing Different Event Types

The logs will show various JFR event types:

- `jdk.ExecutionSample` - Method profiling samples
- `jdk.CPULoad` - CPU usage metrics
- `jdk.GarbageCollection` - GC events
- `jdk.JavaExceptionThrow` - Exception tracking
- `jdk.FileRead/Write` - I/O operations
- `jdk.SocketRead/Write` - Network I/O
- And more...

Each event includes:
- Event type and duration
- Thread information
- Stack traces (when available)
- Event-specific attributes

## Stopping the Collector

```bash
# In the docker terminal, press Ctrl+C, or:
docker-compose down
```

## Troubleshooting

### Collector not receiving logs

1. Check collector is running:
   ```bash
   docker-compose ps
   ```

2. Check collector health:
   ```bash
   curl http://localhost:13133
   ```

3. Verify app is sending:
   ```bash
   curl http://localhost:8082/api/jfr/status
   ```
   
   Look for `successful_exports` > 0 and `failed_exports` = 0

### Connection refused errors

Ensure the collector is running before starting the app:
```bash
docker-compose ps
# Should show jfr-otel-collector as "Up"
```

### Too much output

Reduce sampling in `otel-collector-config.yaml`:

```yaml
exporters:
  logging:
    verbosity: normal  # Change from 'detailed'
    sampling_thereafter: 10  # Sample 1 in 10
```

Then restart:
```bash
docker-compose restart
```

## Advanced Configuration

### Send to Multiple Backends

Edit `otel-collector-config.yaml` to add additional exporters:

```yaml
exporters:
  logging:
    verbosity: detailed
  
  # Add Jaeger exporter
  otlp/jaeger:
    endpoint: jaeger:4317
    tls:
      insecure: true

service:
  pipelines:
    logs:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [logging, otlp/jaeger]  # Multiple exporters
```

### Filter Specific Event Types

Add a filter processor:

```yaml
processors:
  filter:
    logs:
      include:
        match_type: regexp
        resource_attributes:
          - key: jfr.event.type
            value: "jdk\\.(ExecutionSample|CPULoad|GarbageCollection)"

service:
  pipelines:
    logs:
      receivers: [otlp]
      processors: [filter, batch, resource]
      exporters: [logging]
```

### Persistent Storage

To save logs to a file, add the file exporter:

```yaml
exporters:
  file:
    path: /logs/jfr-events.json
    rotation:
      max_megabytes: 100
      max_backups: 3

service:
  pipelines:
    logs:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [logging, file]
```

And mount a volume in `docker-compose.yaml`:

```yaml
services:
  otel-collector:
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
      - ./logs:/logs  # Add this
```

## Architecture

```
┌─────────────────────────────┐
│  JFR OTLP App (port 8082)   │
│  - Records JFR events       │
│  - Converts to OTLP logs    │
└──────────────┬──────────────┘
               │
               │ gRPC (OTLP)
               │ localhost:4317
               ↓
┌─────────────────────────────┐
│  OpenTelemetry Collector    │
│  (Docker container)         │
│                             │
│  Receivers:                 │
│    - OTLP gRPC :4317       │
│    - OTLP HTTP :4318       │
│                             │
│  Processors:                │
│    - Batch                  │
│    - Resource attributes    │
│                             │
│  Exporters:                 │
│    - Logging (console)      │
│    - Debug (console)        │
└──────────────┬──────────────┘
               │
               ↓
         Docker logs
      (visible with docker-compose up)
```

## Next Steps

- Add additional exporters (Jaeger, Prometheus, etc.)
- Configure processors for filtering or transformation
- Set up persistent storage for logs
- Deploy the collector to production with proper security

## References

- [OpenTelemetry Collector Documentation](https://opentelemetry.io/docs/collector/)
- [OTLP Specification](https://github.com/open-telemetry/opentelemetry-proto)
- [Collector Configuration Reference](https://opentelemetry.io/docs/collector/configuration/)
