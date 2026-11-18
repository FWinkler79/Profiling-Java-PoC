# Quick Start: JFR OpenTelemetry Profiles Application

Get the JFR profiles-signal export application up and running in 5 minutes.

## Prerequisites Check

```bash
# Check Java version (need 17+)
java -version

# Check Maven
mvn -version

# Check Docker (for the included Collector)
docker info
```

## Step 1: Start an OTLP Backend

### Option A: Docker Compose (Easiest)

The `docker/` directory contains a pre-configured OpenTelemetry Collector Contrib that has the profiles
pipeline enabled. It prints received profiles to stdout via the `debug` exporter.

```bash
cd apps/jfr-otel-profiles-app/docker
docker compose up -d
```

Wait for the collector health check to respond:

```bash
curl http://localhost:13133   # should return HTTP 200
```

### Option B: devfiler

[Elastic devfiler](https://github.com/elastic/devfiler/) is a standalone OTLP profile receiver with a
simple UI. The OTel JFR profiles reference example targets devfiler's default port (11000).

```bash
docker run -p 11000:11000 elastic/devfiler
```

Then set the endpoint in `src/main/resources/application.yaml`:

```yaml
jfr:
  otlp:
    endpoint: http://localhost:11000
```

### Option C: Grafana Pyroscope

[Grafana Pyroscope](https://grafana.com/oss/pyroscope/) provides a flamegraph UI for continuous profiling
and accepts OTLP profiles via gRPC on port 4040.

## Step 2: Build and Run the Application

```bash
cd apps/jfr-otel-profiles-app
./run.sh
```

The script builds the JAR (`target/jfr-otel-profiles-app-1.0.0.jar`) and starts the app on port 8082.

## Step 3: Verify It Is Working

Wait ~30 seconds for the first export (initial delay), then check the status endpoint:

```bash
curl http://localhost:8082/api/jfr/status
```

Expected output:

```json
{
  "recording_state": "RUNNING",
  "otlp_endpoint": "http://localhost:4317",
  "total_exports": 1,
  "successful_exports": 1,
  "failed_exports": 0,
  "total_samples_exported": 1234
}
```

`successful_exports` > 0 and `failed_exports` = 0 means profiles are flowing.

## Step 4: Inspect the Profiles

### Via the Collector debug exporter (zero setup)

If you used Option A, watch the Docker Compose terminal — the collector prints each received profile:

```bash
docker compose logs -f otel-collector
```

You will see output like:

```text
2026-11-18T10:30:15.123Z info  Profiles  {...}
ResourceProfiles #0
Resource attributes:
     -> service.name: Str(jfr-otel-app)
     -> service.version: Str(1.0.0)
ScopeProfiles #0
InstrumentationScope jfr-otel-app 1.0.0
Profile #0
  Duration: 15s
  Samples: 1234  (jdk.ExecutionSample @ 10 ms)
```

### Via devfiler UI (Option B)

Open `http://localhost:11000` in a browser to browse profiles and flamegraphs.

### Via Pyroscope UI (Option C)

Open `http://localhost:4040` in a browser.

## What Happens Under the Hood

1. A `RecordingStream` records `jdk.ExecutionSample` events at 10 ms intervals.
2. Each event is accumulated into a `JfrExecutionSampleEventConverter` (pprof-style dictionary).
3. Every 15 seconds the scheduler atomically swaps the converter, assembles a `ProfileData`, and calls
   `OtlpGrpcProfileExporter.export(...)`.
4. The collector receives the profile on gRPC port 4317 and prints it via the `debug` exporter.

## Troubleshooting

### "Connection refused" or failed exports

Your OTLP backend is not running or is not reachable. Check:

```bash
# Collector running?
docker compose -f apps/jfr-otel-profiles-app/docker/docker-compose.yaml ps

# Collector healthy?
curl http://localhost:13133

# Export status
curl http://localhost:8082/api/jfr/status
```

### Collector starts but profiles are not accepted

The profiles pipeline requires a feature gate. Verify the collector was started with
`--feature-gates=+service.profilesSupport`. The `docker-compose.yaml` in this repo passes this flag
automatically.

### No exports after 45 seconds

Check the application logs:

```bash
# Look for ERROR lines in the application output
# or inspect the status endpoint
curl http://localhost:8082/api/jfr/status
```

## Next Steps

- Explore profiles in devfiler or Grafana Pyroscope
- Adjust the export interval and sample period in `application.yaml`
- Compare with `jfr-otel-logs-app` which exports the same JFR data as OTLP logs
