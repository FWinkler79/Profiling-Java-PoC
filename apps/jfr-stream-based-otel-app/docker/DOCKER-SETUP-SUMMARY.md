# Docker Setup Complete

## What Was Created

A complete Docker Compose setup for running an OpenTelemetry Collector that receives and displays JFR profiling logs.

### Files Created

```
docker/
├── .gitignore                      # Ignore logs and overrides
├── docker-compose.yaml             # Docker Compose configuration
├── otel-collector-config.yaml      # Collector pipeline configuration
├── start-collector.sh              # Convenience startup script
└── README.md                       # Comprehensive documentation
```

## Configuration Details

### Docker Compose Setup

**Service:** `jfr-otel-collector`
- Image: `otel/opentelemetry-collector:0.91.0`
- Container name: `jfr-otel-collector`
- Network: `jfr-otel-network` (bridge)

**Exposed Ports:**
- `4317` - OTLP gRPC receiver (for the Java app)
- `4318` - OTLP HTTP receiver (optional)
- `8888` - Prometheus metrics endpoint
- `13133` - Health check endpoint

### Collector Pipeline Configuration

**Receivers:**
- `otlp` - Receives OTLP logs via gRPC and HTTP

**Processors:**
- `batch` - Batches logs every 5 seconds (1024 logs per batch)
- `resource` - Adds `service.namespace: jfr-profiling` attribute

**Exporters:**
- `logging` - Detailed console output (100% sampling)
- `debug` - Very detailed output (1 in 200 sampling after first 5)

**Pipeline:**
```
otlp receiver → batch → resource → [logging, debug] exporters → stdout
```

## Application Configuration

The app's `application.yaml` is already configured:

```yaml
jfr:
  otlp:
    endpoint: http://localhost:4317  # Points to Docker collector
```

## Usage

### Quick Start

```bash
# 1. Start collector
cd docker
./start-collector.sh

# 2. Start app (in another terminal)
cd ..
./run.sh

# 3. View logs
cd docker
docker-compose logs -f
```

### What You'll See

The collector will output JFR events as structured logs:

```
ResourceLog #0
Resource attributes:
     -> service.name: Str(jfr-otel-app)
     -> service.namespace: Str(jfr-profiling)
     -> telemetry.sdk.language: Str(java)
ScopeLogs #0
InstrumentationScope jfr-otel-app 1.0.0
LogRecord #0
Body: Str(jdk.ExecutionSample)
Attributes:
     -> jfr.event.type: Str(jdk.ExecutionSample)
     -> thread.name: Str(main)
     -> code.function: Str(handleRequest)
     -> stack.depth: Int(15)
```

### Management Commands

```bash
# Start collector
cd docker && ./start-collector.sh

# View logs in real-time
docker-compose logs -f

# View logs with timestamps
docker-compose logs -f --timestamps

# Stop collector
docker-compose down

# Restart collector (after config changes)
docker-compose restart

# Check collector health
curl http://localhost:13133
```

## Documentation Updates

Updated the following files to reference Docker setup:

1. **README.md** - Added "Option 1: With Docker Compose (Recommended)"
2. **QUICKSTART.md** - Added Docker Compose as primary option
3. **docker/README.md** - Comprehensive Docker documentation

## Testing

✅ Docker Compose configuration validated
✅ No warnings or errors
✅ Ready to use

## Next Steps for Users

1. **Basic Usage:**
   ```bash
   cd docker
   ./start-collector.sh
   # In another terminal
   cd .. && ./run.sh
   # In another terminal
   cd docker && docker-compose logs -f
   ```

2. **Customize Output:**
   - Edit `otel-collector-config.yaml` to adjust sampling
   - Change verbosity from `detailed` to `normal`
   - Modify processors for filtering

3. **Add Backends:**
   - Add additional exporters (Jaeger, Prometheus, etc.)
   - See docker/README.md for examples

4. **Production Deployment:**
   - Add TLS configuration
   - Configure authentication
   - Set up persistent storage
   - Use proper resource limits

## Architecture

```
┌────────────────────────┐
│  Spring Boot App       │
│  (port 8082)           │
│  - JFR Recording       │
│  - OTLP Log Export     │
└──────────┬─────────────┘
           │
           │ OTLP/gRPC
           │ localhost:4317
           ↓
┌────────────────────────┐
│  Docker Container      │
│  jfr-otel-collector    │
│                        │
│  ┌──────────────────┐ │
│  │ OTLP Receiver    │ │
│  │ :4317 :4318      │ │
│  └────────┬─────────┘ │
│           │            │
│  ┌────────▼─────────┐ │
│  │ Batch Processor  │ │
│  └────────┬─────────┘ │
│           │            │
│  ┌────────▼─────────┐ │
│  │ Resource Attrs   │ │
│  └────────┬─────────┘ │
│           │            │
│  ┌────────▼─────────┐ │
│  │ Logging Exporter │ │
│  │ Debug Exporter   │ │
│  └────────┬─────────┘ │
└───────────┼───────────┘
            │
            ↓
      Docker Logs
      (console output)
```

## Benefits

1. **Easy Setup** - One script to start everything
2. **Immediate Feedback** - See profiling data in console
3. **No External Dependencies** - Everything in Docker
4. **Production-Ready Foundation** - Easy to extend to real backends
5. **Educational** - See exactly what data is being sent
