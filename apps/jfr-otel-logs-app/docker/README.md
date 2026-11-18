# Docker Setup for JFR OTLP Export

This directory contains a Docker Compose configuration that runs an OpenTelemetry Collector. The collector receives JFR
profiling data from the application as OTLP logs, prints them to the console for inspection, and forwards them to
**SAP Cloud Logging** over an mTLS-secured OTLP connection.

## Overview

The setup includes:

- **OpenTelemetry Collector** (`otel/opentelemetry-collector-contrib`) — receives OTLP logs via gRPC (4317) and HTTP
  (4318).
- **Debug exporter** — prints received logs to stdout (visible via `docker compose logs`).
- **OTLP exporter** — forwards logs to the SAP Cloud Logging ingest endpoint, authenticated with a client certificate
  (mutual TLS).

The `contrib` distribution is required: the plain `otel/opentelemetry-collector` (Core) image does not bundle all of the
components this configuration uses.

## Prerequisites

The OTLP exporter authenticates to SAP Cloud Logging with a **client certificate and private key** (mTLS). Place them in
a `certs/` directory next to the compose file:

```text
docker/
└── certs/
    ├── ingest-client.crt   # client certificate (PEM)
    └── ingest-client.key   # client private key (PEM)
```

The `certs/` directory is gitignored — the certificate and key must never be committed. Obtain the ingest client
certificate and key from your SAP Cloud Logging service instance and write them into these two files.

## Quick Start

### 1. Start the Collector

```bash
cd docker
docker compose up
```

The collector listens on:

- `4317` — OTLP gRPC (for the Java app)
- `4318` — OTLP HTTP (optional)
- `8888` — Prometheus metrics
- `13133` — Health check endpoint

### 2. Start the Application

In another terminal:

```bash
cd ..  # Back to the jfr-otel-logs-app directory
./run.sh
```

The application is already configured to send to `http://localhost:4317`.

### 3. View the Profiling Logs

Watch the Docker Compose terminal — you will see JFR events as structured logs from the `debug` exporter:

```text
2026-11-18T10:30:15.123Z  info  Logs  {...}
ResourceLog #0
Resource attributes:
     -> service.name: Str(jfr-otel-app)
     -> service.version: Str(1.0.0)
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

The same records are simultaneously exported to SAP Cloud Logging.

## Configuration

### Collector Configuration

The collector is configured in `otel-collector-config.yaml`.

**Receivers:**

- OTLP gRPC on port 4317
- OTLP HTTP on port 4318

**Processors:**

- `resource` — adds the `service.namespace: jfr-profiling` attribute.

(A `batch` processor is also defined in the file but is not currently wired into the logs pipeline.)

**Exporters:**

- `debug` — detailed output to the console.
- `otlp/sap-cloud-logging` — forwards logs to SAP Cloud Logging over mTLS.

**Pipeline:**

```text
otlp receiver → resource → [debug, otlp/sap-cloud-logging] exporters
```

### SAP Cloud Logging Export (mTLS)

The OTLP exporter points at the SAP Cloud Logging ingest endpoint and presents a client certificate:

```yaml
exporters:
  otlp/sap-cloud-logging:
    endpoint: <your-instance>.ingest-otlp.cls-NN.cloud.logs.services.sap.hana.ondemand.com:443
    tls:
      insecure: false
      cert_file: /etc/otel/certs/ingest-client.crt
      key_file: /etc/otel/certs/ingest-client.key
```

The `cert_file` and `key_file` paths are inside the container. The `docker-compose.yaml` mounts the local `./certs`
directory read-only at `/etc/otel/certs`:

```yaml
volumes:
  - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
  - ./certs:/etc/otel/certs:ro
```

### Application Configuration

The app's `application.yaml` is configured to send to the collector:

```yaml
jfr:
  otlp:
    endpoint: http://localhost:4317
```

## Viewing Different Event Types

The logs will show various JFR event types:

- `jdk.ExecutionSample` — method profiling samples
- `jdk.CPULoad` — CPU usage metrics
- `jdk.GarbageCollection` — GC events
- `jdk.JavaExceptionThrow` — exception tracking
- `jdk.FileRead` / `jdk.FileWrite` — I/O operations
- `jdk.SocketRead` / `jdk.SocketWrite` — network I/O
- And more...

Each event includes:

- Event type and duration
- Thread information
- Stack traces (when available)
- Event-specific attributes

## Stopping the Collector

```bash
# In the docker terminal, press Ctrl+C, or:
docker compose down
```

## Troubleshooting

### Collector not receiving logs

1. Check the collector is running:

   ```bash
   docker compose ps
   ```

2. Check collector health:

   ```bash
   curl http://localhost:13133
   ```

3. Verify the app is sending:

   ```bash
   curl http://localhost:8082/api/jfr/status
   ```

   Look for `successful_exports` > 0 and `failed_exports` = 0.

### Export to SAP Cloud Logging fails

If the console shows the records (via the `debug` exporter) but they do not arrive in SAP Cloud Logging, check the
`otlp/sap-cloud-logging` exporter logs for TLS or handshake errors:

```bash
docker compose logs | grep -i "sap-cloud-logging\|tls\|certificate\|handshake"
```

Common causes:

- The `certs/ingest-client.crt` / `ingest-client.key` files are missing, swapped, or not a matching pair.
- The client certificate has expired or been revoked.
- The `endpoint` host or port is wrong (the SAP ingest endpoint uses gRPC on port `443`).

Verify the certificate and key match:

```bash
diff <(openssl x509 -in certs/ingest-client.crt -noout -modulus) \
     <(openssl rsa  -in certs/ingest-client.key -noout -modulus)
# No output means they match.
```

### Connection refused errors

Ensure the collector is running before starting the app:

```bash
docker compose ps
# Should show jfr-otel-collector as "Up"
```

### Too much output

Reduce the console verbosity in `otel-collector-config.yaml`:

```yaml
exporters:
  debug:
    verbosity: normal  # Change from 'detailed'
```

Then restart:

```bash
docker compose restart
```

## Advanced Configuration

### Send to Additional Backends

Edit `otel-collector-config.yaml` to add more exporters and wire them into the pipeline:

```yaml
exporters:
  debug:
    verbosity: detailed

  # Add a second OTLP backend
  otlp/jaeger:
    endpoint: jaeger:4317
    tls:
      insecure: true

service:
  pipelines:
    logs:
      receivers: [otlp]
      processors: [resource]
      exporters: [debug, otlp/sap-cloud-logging, otlp/jaeger]
```

### Filter Specific Event Types

Add a filter processor:

```yaml
processors:
  filter:
    logs:
      include:
        match_type: regexp
        record_attributes:
          - key: jfr.event.type
            value: "jdk\\.(ExecutionSample|CPULoad|GarbageCollection)"

service:
  pipelines:
    logs:
      receivers: [otlp]
      processors: [filter, resource]
      exporters: [debug, otlp/sap-cloud-logging]
```

### Persistent Storage

To also save logs to a file, add the file exporter:

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
      processors: [resource]
      exporters: [debug, otlp/sap-cloud-logging, file]
```

And mount a volume in `docker-compose.yaml`:

```yaml
services:
  otel-collector:
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
      - ./certs:/etc/otel/certs:ro
      - ./logs:/logs  # Add this
```

## Architecture

```text
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
│    - OTLP gRPC :4317        │
│    - OTLP HTTP :4318        │
│                             │
│  Processors:                │
│    - Resource attributes    │
│                             │
│  Exporters:                 │
│    - Debug (console)        │
│    - OTLP → SAP Cloud       │
│      Logging (mTLS)         │
└───────┬──────────────┬──────┘
        │              │
        ↓              ↓
   Docker logs    SAP Cloud Logging
   (console)      (ingest-otlp, mTLS)
```

## Next Steps

- Add additional exporters (Jaeger, Prometheus, etc.)
- Configure processors for filtering or transformation
- Set up persistent storage for logs
- Rotate the SAP Cloud Logging client certificate periodically

## References

- [OpenTelemetry Collector Documentation](https://opentelemetry.io/docs/collector/)
- [OTLP Specification](https://github.com/open-telemetry/opentelemetry-proto)
- [Collector Configuration Reference](https://opentelemetry.io/docs/collector/configuration/)
- [SAP Cloud Logging — OpenTelemetry API ingestion](https://help.sap.com/docs/cloud-logging)
