# Docker Setup for JFR OTLP Export

This directory contains a Docker Compose configuration that runs an OpenTelemetry Collector alongside the
`jfr-otel-logs-based-file-upload-app`. Its primary purpose is to make two things observable:

1. **The collector receives the binary records** — confirmed by the `debug` exporter printing them to stdout.
2. **SAP Cloud Logging rejects them** — the `otlp/sap-cloud-logging` exporter fails with a permanent
   `InvalidArgument` error every time.

Both outcomes are intentional. They illustrate why sending raw `.jfr` binary blobs as OTLP log bodies is a misuse
of the Logs signal. See
[Known Limitation: OTel Logs is the wrong signal for JFR binaries](#known-limitation-otel-logs-is-the-wrong-signal-for-jfr-binaries)
for the full explanation.

## Overview

- **OpenTelemetry Collector** (`otel/opentelemetry-collector-contrib`) — receives OTLP logs via gRPC (4317) and
  HTTP (4318).
- **Debug exporter** — prints received records to stdout (visible via `docker compose logs`).
- **OTLP exporter** (`otlp/sap-cloud-logging`) — attempts to forward records to SAP Cloud Logging over mTLS.
  **This export always fails by design.**

The `contrib` distribution is required: the plain `otel/opentelemetry-collector` (Core) image does not bundle all
components this configuration uses.

## Prerequisites

The `otlp/sap-cloud-logging` exporter authenticates with a **client certificate and private key** (mTLS). Even
though the export fails due to payload incompatibility, the TLS handshake must succeed first. Place the certificate
and key in a `certs/` directory next to the compose file:

```text
docker/
└── certs/
    ├── ingest-client.crt   # client certificate (PEM)
    └── ingest-client.key   # client private key (PEM)
```

The `certs/` directory is gitignored — the certificate and key must never be committed. Obtain them from your SAP
Cloud Logging service instance.

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
cd ..  # Back to the jfr-otel-logs-based-file-upload-app directory
./run.sh
```

### 3. Observe the Results

After the app's 30-second initial delay, you will see two things in the collector logs.

**The debug exporter confirms receipt** — one record per 15-second slice:

```text
2026-11-18T10:30:15.123Z  info  Logs  {...}
ResourceLog #0
Resource attributes:
     -> service.name: Str(jfr-otel-logs-based-file-upload-app)
     -> service.version: Str(1.0.0)
     -> service.namespace: Str(jfr-profiling)
ScopeLogs #0
InstrumentationScope jfr-otel-logs-based-file-upload-app 1.0.0
LogRecord #0
Body: Bytes(<raw .jfr binary, typically 100 KB – 1 MB>)
Attributes:
     -> jfr.recording.format: Str(jfr)
     -> jfr.recording.size_bytes: Int(524288)
     -> jfr.slice.duration_seconds: Int(15)
```

**The SAP Cloud Logging exporter fails permanently** — immediately after each receipt:

```text
error  Exporting failed. Dropping data.
  "error": "not retryable error: Permanent error: rpc error: code = InvalidArgument desc = Unknown case"
```

This is the expected outcome. See
[Known Limitation](#known-limitation-otel-logs-is-the-wrong-signal-for-jfr-binaries) for why.

## Configuration

### Collector Configuration (`otel-collector-config.yaml`)

**Receivers:** OTLP gRPC on port 4317, OTLP HTTP on port 4318.

**Processors:** `resource` — adds `service.namespace: jfr-profiling`. (A `batch` processor is defined but not
wired into the pipeline.)

**Exporters:**

- `debug` — detailed console output.
- `otlp/sap-cloud-logging` — forwards to SAP Cloud Logging over mTLS (fails with `InvalidArgument`).

**Pipeline:**

```text
otlp receiver → resource → [debug, otlp/sap-cloud-logging] exporters
```

### SAP Cloud Logging Exporter (mTLS)

```yaml
exporters:
  otlp/sap-cloud-logging:
    endpoint: <your-instance>.ingest-otlp.cls-NN.cloud.logs.services.sap.hana.ondemand.com:443
    tls:
      insecure: false
      cert_file: /etc/otel/certs/ingest-client.crt
      key_file: /etc/otel/certs/ingest-client.key
```

The `docker-compose.yaml` mounts `./certs` read-only into the container at `/etc/otel/certs`.

### Application Configuration

```yaml
jfr:
  otlp:
    endpoint: http://localhost:4317
```

## Known Limitation: OTel Logs is the wrong signal for JFR binaries

### What happens

The application sends each 15-second JFR slice as a **single OTLP log record whose body is the raw `.jfr`
binary** (`bytes_value` in the `AnyValue` protobuf oneof). SAP Cloud Logging responds:

```text
rpc error: code = InvalidArgument desc = Unknown case
```

"Unknown case" means the service received a `bytes_value` field in the log body — a case it does not handle. The
error is non-retryable and the record is dropped immediately.

### Why this is a fundamental mismatch, not a configuration problem

OTel Logs exist for discrete, queryable, human-readable observability events. A `.jfr` recording is a profiling
artifact — an opaque binary file that must be ingested whole by a specialised tool. Sending it as a log body is a
category error:

| OTel Logs are designed for | This app sends |
| -------------------------- | -------------- |
| Structured, indexed events | Opaque binary blobs |
| `string_value` / `kvlist_value` bodies | `bytes_value` body |
| Many small records per second | One large file per 15 seconds |
| Queryable in a log analytics UI | Must be extracted and opened in JMC / Pyroscope |

SAP Cloud Logging's rejection is a symptom of this deeper mismatch. Any log analytics backend will have the same
problem. Even backends that technically accept `bytes_value` cannot do anything useful with the data.

### The right approaches

**Direct file transfer** — write each `.jfr` slice to object storage (S3, SAP Object Store) or POST it to a
profiling backend's HTTP ingest API. OTLP is not involved.

**OTel Profiles signal** — the [OpenTelemetry Profiles specification](https://opentelemetry.io/docs/specs/otel/profiles/)
is the purpose-built signal for profiling data. Profiling data belongs in a profile, not in a log.

## Stopping the Collector

```bash
# Ctrl+C in the compose terminal, or:
docker compose down
```

## Troubleshooting

### Collector not receiving records at all

1. Check the collector is running: `docker compose ps`
2. Check collector health: `curl http://localhost:13133`
3. Verify the app is exporting: `curl http://localhost:8080/api/jfr/status` — look for `total_exports` > 0.
4. Check that the app's 30-second initial delay has passed.

### SAP Cloud Logging export fails with `InvalidArgument`

Expected — see [Known Limitation](#known-limitation-otel-logs-is-the-wrong-signal-for-jfr-binaries).

### SAP Cloud Logging export fails with a TLS/handshake error

This is a separate problem from the `InvalidArgument` rejection. Check:

```bash
docker compose logs | grep -i "sap-cloud-logging\|tls\|certificate\|handshake"
```

Common causes:

- `certs/ingest-client.crt` / `ingest-client.key` missing, swapped, or not a matching pair.
- The client certificate has expired or been revoked.
- The `endpoint` host or port is wrong (SAP ingest endpoint uses gRPC on port `443`).

Verify the certificate and key match:

```bash
diff <(openssl x509 -in certs/ingest-client.crt -noout -modulus) \
     <(openssl rsa  -in certs/ingest-client.key -noout -modulus)
# No output means they match.
```

### Too much output

```yaml
exporters:
  debug:
    verbosity: normal  # Change from 'detailed'
```

Then `docker compose restart`.

## Architecture

```text
┌─────────────────────────────┐
│  JFR Upload Stream App      │
│  port 8080                  │
│                             │
│  Dumps .jfr slice every 15s │
│  Sends as OTLP log          │
│  (bytes_value body)         │
└──────────────┬──────────────┘
               │ gRPC OTLP
               │ localhost:4317
               ↓
┌─────────────────────────────┐
│  OpenTelemetry Collector    │
│  (Docker)                   │
│                             │
│  → debug exporter      ✓    │
│    (prints to stdout)       │
│                             │
│  → otlp/sap-cloud-logging ✗ │
│    (InvalidArgument,        │
│     bytes_value rejected)   │
└─────────────────────────────┘
```

## References

- [OpenTelemetry Collector Documentation](https://opentelemetry.io/docs/collector/)
- [OTLP Specification](https://github.com/open-telemetry/opentelemetry-proto)
- [OpenTelemetry Profiles Specification](https://opentelemetry.io/docs/specs/otel/profiles/)
- [SAP Cloud Logging — OpenTelemetry API ingestion](https://help.sap.com/docs/cloud-logging)
