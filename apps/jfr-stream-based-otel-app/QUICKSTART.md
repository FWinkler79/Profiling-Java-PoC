# Quick Start: JFR Stream-Based OTLP Application

Get the JFR OTLP export application up and running in 5 minutes.

## Prerequisites Check

```bash
# Check Java version (need 17+)
java -version

# Check Maven
mvn -version
```

## Step 1: Start an OTLP Backend

### Option A: Using Docker Compose (Easiest)

```bash
cd apps/jfr-stream-based-otel-app/docker
./start-collector.sh
```

This starts an OpenTelemetry Collector that will display profiling logs in the console.

### Option B: Using DevFiler

```bash
# Using Docker
docker run -p 11000:11000 elastic/devfiler

# Or download from: https://github.com/elastic/devfiler/
```

## Step 2: Configure Endpoint (Skip if using Docker Compose)

Only needed if using a custom backend. The app defaults to `http://localhost:4317` which works with the Docker Compose setup.

Edit `src/main/resources/application.yaml`:

```yaml
jfr:
  otlp:
    endpoint: http://localhost:11000
```

## Step 3: Run the Application

```bash
cd apps/jfr-stream-based-otel-app
./run.sh
```

## Step 4: Verify It's Working

```bash
# Wait 30 seconds for initial delay, then check status
sleep 35

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
  "total_events_exported": 1234
}
```

## Step 5: View Profiling Logs

If using Docker Compose, view the collector output:

```bash
cd docker
docker-compose logs -f
```

You'll see JFR events being received and displayed as structured logs with attributes like:
- Event type (jdk.ExecutionSample, jdk.CPULoad, etc.)
- Thread information
- Stack traces
- Event-specific metrics

## What Happens Next

- JFR continuously records application events
- Every 15 seconds, a profile snapshot is exported to the OTLP backend
- Check the backend (DevFiler) to see the received profiles
- Monitor export statistics at `http://localhost:8082/api/jfr/status`

## Troubleshooting

### "Connection refused" errors

Your OTLP backend isn't running. Start DevFiler or another OTLP-compatible collector.

### Exports showing as failed

Check the endpoint configuration in `application.yaml` matches your backend.

### No exports after 45 seconds

Check the application logs for errors:

```bash
# Look for ERROR lines
grep ERROR logs/application.log
```

## Next Steps

- View profiles in your OTLP backend's UI
- Integrate with your existing OpenTelemetry pipeline
- Configure export interval and buffer size
- Add custom resource attributes

## Compare with Other Apps

This app is **production-ready** for streaming profiles to APM systems.

For local analysis, use:
- `jfr-stream-app` (exports to files)
- `jfr-file-based-app` (manual profiling with jcmd)
