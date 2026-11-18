# Docker Setup Summary

## What This Provides

A Docker Compose setup that runs an OpenTelemetry Collector Contrib. The collector receives OTLP profiles
from `jfr-otel-profiles-app` and prints them to the console via the `debug` exporter.

### Files

```text
docker/
├── .gitignore                      # Ignores logs, local overrides, etc.
├── docker-compose.yaml             # Docker Compose configuration
├── otel-collector-config.yaml      # Collector profiles-pipeline configuration
├── start-collector.sh              # Convenience startup script
├── README.md                       # Comprehensive documentation
└── DOCKER-SETUP-SUMMARY.md         # This file
```

## Configuration Details

### Docker Compose Setup

**Service key:** `otel-collector`
**Container name:** `jfr-otel-profiles-collector`

- Image: `otel/opentelemetry-collector-contrib:0.165.0`
- Network: `jfr-otel-network` (bridge)
- Command flags: `--config=... --feature-gates=+service.profilesSupport`
- Volume: `./otel-collector-config.yaml` mounted at `/etc/otel-collector-config.yaml`

**Exposed Ports:**

- `4317` — OTLP gRPC receiver (for the Java app)
- `4318` — OTLP HTTP receiver (optional)
- `8888` — Prometheus metrics endpoint
- `13133` — Health check endpoint

### Collector Pipeline Configuration

**Receivers:**

- `otlp` — receives OTLP profiles via gRPC and HTTP

**Exporters:**

- `debug` — detailed console output

**Pipeline type:** `profiles` (not `logs`)

```text
otlp receiver → [debug] exporter
```

## Application Configuration

The app's `application.yaml` points at the collector:

```yaml
jfr:
  otlp:
    endpoint: http://localhost:4317  # Points to the Docker collector
```

## Usage

### Quick Start

```bash
# 1. Start the collector
cd docker
docker compose up -d

# 2. Start the app (in another terminal)
cd ..
./run.sh

# 3. View profiles in the collector output
cd docker
docker compose logs -f otel-collector
```

### What You Will See

The collector outputs received profiles via the `debug` exporter:

```text
ResourceProfiles #0
Resource attributes:
     -> service.name: Str(jfr-otel-app)
     -> service.version: Str(1.0.0)
ScopeProfiles #0
InstrumentationScope jfr-otel-app 1.0.0
Profile #0
  DurationNanos: 15000000000
  Samples: 1234  (jdk.ExecutionSample @ 10 ms)
```

### Management Commands

```bash
# Start the collector (detached)
docker compose up -d

# Start and watch logs immediately
docker compose up

# View logs in real time
docker compose logs -f otel-collector

# Stop the collector
docker compose down

# Restart after config changes
docker compose restart otel-collector

# Check collector health
curl http://localhost:13133
```

> **Tip:** `docker compose logs` uses the *service* key (`otel-collector`).
> `docker logs` uses the *container* name (`jfr-otel-profiles-collector`).

## Architecture

```text
┌────────────────────────┐
│  Spring Boot App       │
│  (port 8080)           │
│  - RecordingStream     │
│    jdk.ExecutionSample │
│  - ProfileData export  │
└──────────┬─────────────┘
           │
           │ OTLP profiles / gRPC
           │ localhost:4317
           ▼
┌────────────────────────┐
│  Docker Container      │
│  jfr-otel-profiles-    │
│  collector             │
│                        │
│  --feature-gates=      │
│  +service.             │
│  profilesSupport       │
│                        │
│  ┌──────────────────┐  │
│  │ OTLP Receiver    │  │
│  │ :4317  :4318     │  │
│  └────────┬─────────┘  │
│           │            │
│  ┌────────▼─────────┐  │
│  │ Debug Exporter   │  │
│  └────────┬─────────┘  │
└───────────┼────────────┘
            │
            ▼
       Docker logs
       (console)
```

## Key Facts

- The collector feature gate `+service.profilesSupport` is **required**. Without it the profiles pipeline
  is not enabled and the collector will reject incoming profiles data.
- No SAP Cloud Logging, no mTLS, no client certificates — this is a local development setup only.
- The `debug` exporter is the primary way to verify profiles are flowing. For a richer UI, use
  [devfiler](https://github.com/elastic/devfiler/) or [Grafana Pyroscope](https://grafana.com/oss/pyroscope/).
