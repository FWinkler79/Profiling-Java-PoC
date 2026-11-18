# Docker Setup Summary

## What This Provides

A Docker Compose setup that runs an OpenTelemetry Collector. The collector receives JFR profiling logs from the
application, prints them to the console for inspection, and forwards them to **SAP Cloud Logging** over an
mTLS-secured OTLP connection.

### Files

```text
docker/
├── .gitignore                      # Ignores logs, overrides, certs/, etc.
├── docker-compose.yaml             # Docker Compose configuration
├── otel-collector-config.yaml      # Collector pipeline configuration
├── certs/                          # mTLS client cert + key (gitignored, not committed)
│   ├── ingest-client.crt
│   └── ingest-client.key
├── start-collector.sh              # Convenience startup script
├── README.md                       # Comprehensive documentation
└── DOCKER-SETUP-SUMMARY.md         # This summary
```

## Configuration Details

### Docker Compose Setup

**Service:** `jfr-otel-collector`

- Image: `otel/opentelemetry-collector-contrib:0.165.0` (the `contrib` distribution is required)
- Container name: `jfr-otel-collector`
- Network: `jfr-otel-network` (bridge)
- Volumes: collector config, plus `./certs` mounted read-only at `/etc/otel/certs`

**Exposed Ports:**

- `4317` — OTLP gRPC receiver (for the Java app)
- `4318` — OTLP HTTP receiver (optional)
- `8888` — Prometheus metrics endpoint
- `13133` — Health check endpoint

### Collector Pipeline Configuration

**Receivers:**

- `otlp` — receives OTLP logs via gRPC and HTTP

**Processors:**

- `resource` — adds the `service.namespace: jfr-profiling` attribute

(A `batch` processor is defined in the config but not currently wired into the logs pipeline.)

**Exporters:**

- `debug` — detailed console output
- `otlp/sap-cloud-logging` — forwards logs to SAP Cloud Logging over mTLS (client-certificate auth)

**Pipeline:**

```text
otlp receiver → resource → [debug, otlp/sap-cloud-logging] exporters
```

### SAP Cloud Logging Export (mTLS)

The OTLP exporter presents a client certificate instead of a username/password. The certificate and key live in
`./certs` (gitignored) and are mounted into the container:

```yaml
exporters:
  otlp/sap-cloud-logging:
    endpoint: <your-instance>.ingest-otlp.cls-NN.cloud.logs.services.sap.hana.ondemand.com:443
    tls:
      insecure: false
      cert_file: /etc/otel/certs/ingest-client.crt
      key_file: /etc/otel/certs/ingest-client.key
```

See `README.md` for how to provide the certificate files.

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
./start-collector.sh

# 2. Start the app (in another terminal)
cd ..
./run.sh

# 3. View logs
cd docker
docker compose logs -f
```

### What You Will See

The collector outputs JFR events as structured logs (and forwards them to SAP Cloud Logging):

```text
ResourceLog #0
Resource attributes:
     -> service.name: Str(jfr-otel-app)
     -> service.namespace: Str(jfr-profiling)
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
# Start the collector
cd docker && ./start-collector.sh

# View logs in real time
docker compose logs -f

# View logs with timestamps
docker compose logs -f --timestamps

# Stop the collector
docker compose down

# Restart the collector (after config changes)
docker compose restart

# Check collector health
curl http://localhost:13133
```

## Architecture

```text
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
│  ┌──────────────────┐  │
│  │ OTLP Receiver    │  │
│  │ :4317 :4318      │  │
│  └────────┬─────────┘  │
│           │            │
│  ┌────────▼─────────┐  │
│  │ Resource Attrs   │  │
│  └────────┬─────────┘  │
│           │            │
│  ┌────────▼─────────┐  │
│  │ Debug Exporter   │  │
│  │ OTLP Exporter    │  │
│  └───┬───────────┬──┘  │
└──────┼───────────┼─────┘
       │           │
       ↓           ↓
  Docker Logs   SAP Cloud Logging
  (console)     (ingest-otlp, mTLS)
```

## Benefits

1. **Immediate feedback** — see profiling data in the console via the debug exporter.
2. **Real backend** — logs are shipped to SAP Cloud Logging over a secure mTLS connection.
3. **Secrets stay out of git** — the client certificate and key live in a gitignored `certs/` directory.
4. **Pinned, reproducible image** — the collector version is pinned rather than tracking `latest`.
