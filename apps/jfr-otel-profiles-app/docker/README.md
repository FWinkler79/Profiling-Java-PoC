# Docker Setup for JFR OTLP Profiles Export

This directory contains a Docker Compose configuration that runs an OpenTelemetry Collector Contrib. The
collector receives OTLP profiles from `jfr-otel-profiles-app` and prints them to the console via the
`debug` exporter.

## Overview

The setup includes:

- **OpenTelemetry Collector Contrib** (`otel/opentelemetry-collector-contrib:0.165.0`) — receives OTLP
  profiles via gRPC (4317) and HTTP (4318).
- **`--feature-gates=+service.profilesSupport`** — required command-line flag; without it the collector
  does not enable the profiles pipeline.
- **Debug exporter** — prints received profiles to stdout (visible via `docker compose logs`).
- **Profiles pipeline** — `[otlp]` receiver → `[debug]` exporter.

The `contrib` distribution is required: the plain `otel/opentelemetry-collector` (Core) image does not
include profiles pipeline support.

## Docker Details

| Item                  | Value                                           |
| --------------------- | ----------------------------------------------- |
| Compose service key   | `otel-collector`                                |
| Container name        | `jfr-otel-profiles-collector`                   |
| Image                 | `otel/opentelemetry-collector-contrib:0.165.0`  |
| Network               | `jfr-otel-network` (bridge)                     |

> **Note:** Use the *service* key (`otel-collector`) with `docker compose` commands, and the *container*
> name (`jfr-otel-profiles-collector`) with plain `docker` commands.
>
> ```bash
> docker compose logs otel-collector        # compose service key
> docker logs jfr-otel-profiles-collector   # container name
> ```

## Quick Start

### 1. Start the Collector

```bash
cd docker
docker compose up -d
```

The collector listens on:

- `4317` — OTLP gRPC (for the Java app)
- `4318` — OTLP HTTP (optional)
- `8888` — Prometheus metrics
- `13133` — Health check endpoint

Wait for the health check to respond before starting the app:

```bash
curl http://localhost:13133   # should return HTTP 200
```

### 2. Start the Application

In another terminal:

```bash
cd ..  # Back to the jfr-otel-profiles-app directory
./run.sh
```

The application is configured to send to `http://localhost:4317`.

### 3. View the Profiles

Watch the Docker Compose terminal — you will see profiles from the `debug` exporter:

```bash
docker compose logs -f otel-collector
```

Example output:

```text
2026-11-18T10:30:15.123Z  info  Profiles  {...}
ResourceProfiles #0
Resource attributes:
     -> service.name: Str(jfr-otel-app)
     -> service.version: Str(1.0.0)
ScopeProfiles #0
InstrumentationScope jfr-otel-app 1.0.0
Profile #0
  DurationNanos: 15000000000
  Period: 10000000 (10 ms — ExecutionSample interval)
  Samples: 1234
```

## Collector Configuration

The collector is configured in `otel-collector-config.yaml`.

### Receivers

- OTLP gRPC on port 4317
- OTLP HTTP on port 4318

### Exporters

- `debug` — detailed output to the console

### Pipeline

```text
otlp receiver → [debug] exporter
```

This is a **profiles** pipeline (not a logs pipeline). The pipeline type is `profiles`:

```yaml
service:
  pipelines:
    profiles:
      receivers: [otlp]
      exporters: [debug]
```

## Application Configuration

The app's `application.yaml` points at the collector:

```yaml
jfr:
  otlp:
    endpoint: http://localhost:4317
```

## Optional: Alternative Profile Viewers

The collector `debug` exporter is the simplest way to verify profiles are flowing. For a richer UI, two
alternatives work with OTLP profiles:

### devfiler (Elastic)

[devfiler](https://github.com/elastic/devfiler/) is a standalone OTLP profile receiver targeted by the
OTel JFR reference example (port 11000).

```bash
docker run -p 11000:11000 elastic/devfiler
```

Then update the app endpoint:

```yaml
jfr:
  otlp:
    endpoint: http://localhost:11000
```

### Grafana Pyroscope

[Grafana Pyroscope](https://grafana.com/oss/pyroscope/) accepts OTLP profiles and provides a flamegraph
UI.

## Stopping the Collector

```bash
# Graceful stop
docker compose down

# Or press Ctrl+C if running in the foreground
```

## Troubleshooting

### Collector not receiving profiles

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

### "profiles pipeline not supported" or profiles silently rejected

Verify the feature gate flag is present in the collector command. Check `docker-compose.yaml`:

```yaml
command:
  - "--config=/etc/otel-collector-config.yaml"
  - "--feature-gates=+service.profilesSupport"
```

If the flag is missing, the profiles pipeline is disabled and the collector will reject incoming profiles
data. Stop the collector, ensure the flag is present, and restart with `docker compose up`.

### Connection refused errors

Ensure the collector is running before starting the app:

```bash
docker compose ps
# Service otel-collector should be listed as running

docker logs jfr-otel-profiles-collector
# Check for startup errors
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
docker compose restart otel-collector
```

## Advanced Configuration

### Add a Second Backend

Edit `otel-collector-config.yaml` to add more exporters and wire them into the profiles pipeline:

```yaml
exporters:
  debug:
    verbosity: detailed

  # Add devfiler as a second backend
  otlp/devfiler:
    endpoint: devfiler:11000
    tls:
      insecure: true

service:
  pipelines:
    profiles:
      receivers: [otlp]
      exporters: [debug, otlp/devfiler]
```

## Architecture

```text
┌─────────────────────────────┐
│  jfr-otel-profiles-app      │
│  (port 8082)                │
│                             │
│  RecordingStream            │
│  (jdk.ExecutionSample)      │
│    → ProfileData            │
│    → OtlpGrpcProfileExporter│
└──────────────┬──────────────┘
               │
               │ gRPC (OTLP profiles)
               │ localhost:4317
               ▼
┌─────────────────────────────┐
│  OpenTelemetry Collector    │
│  (Docker: otel-collector)   │
│                             │
│  Receivers:                 │
│    - OTLP gRPC :4317        │
│    - OTLP HTTP :4318        │
│                             │
│  Profiles Pipeline:         │
│    otlp → debug             │
│                             │
│  Flag:                      │
│    --feature-gates=          │
│    +service.profilesSupport  │
└──────────────┬──────────────┘
               │
               ▼
         Docker logs
         (console output)
```

## References

- [OpenTelemetry Collector Documentation](https://opentelemetry.io/docs/collector/)
- [OTel Collector Contrib releases](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases)
- [OTLP Profiles Specification](https://github.com/open-telemetry/opentelemetry-proto)
- [opentelemetry-jfr-profiles-shim](https://github.com/open-telemetry/opentelemetry-java/tree/main/opentelemetry-jfr-profiles-shim)
