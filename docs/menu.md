# Menu.sh - Interactive Control Panel

The `menu.sh` script provides a unified interface for managing four of the five JFR applications and their associated tools. (`jfr-otel-profiles-app` is run directly via its own `run.sh`.)

## Overview

```bash
./menu.sh
```

The menu is organized into five sections:

1. **jfr-profiler-based-app** (options 1-6) - Traditional JFR approach
2. **jfr-jdk-api-based-app** (options 7-11) - Streaming to local files
3. **jfr-otel-logs-app** (options 12-17) - Streaming to OTLP collector
4. **jfr-otel-logs-based-file-upload-app** (options 21-26) - OTLP file-upload
5. **Shared tools** (options 27-29) - Common utilities

## Menu Options

### jfr-profiler-based-app (Traditional, port 8080)

#### Option 1: Build jfr-profiler-based-app application

Compiles the application using Maven.

```bash
cd apps/jfr-profiler-based-app
mvn clean package -DskipTests
```

#### Option 2: Start continuous profiling

Starts the app with automatic JFR profiling:

- Background process with JFR enabled
- Auto-dump snapshots every 15 seconds
- Saves to `apps/jfr-profiler-based-app/jfr-exports/`
- Each snapshot represents a true 15-second delta
- Press Ctrl+C to stop both profiling and the app

#### Option 3: Generate load

Runs the load generator against port 8080:

- Hits `/api/hello` and `/api/heavy` endpoints
- Creates CPU and memory activity for profiling
- Requires the app to be running

#### Option 4: Merge JFR snapshots

Assembles all `.jfr` files in `apps/jfr-profiler-based-app/jfr-exports/` into a single file:

- Uses `jfr assemble` command
- Output saved to `apps/jfr-profiler-based-app/jfr-merged/`
- Useful for viewing all data in JMC at once

#### Option 5: Analyze existing snapshots

Runs `shell-scripts/analyze-snapshots.sh` on exported snapshots:

- Displays statistics about recorded events
- Shows event counts and types
- Provides quick overview without opening JMC

#### Option 6: Test endpoints

Tests the application endpoints:

- Curls `/api/hello?name=TestUser`
- Curls `/api/heavy` (CPU-intensive endpoint)
- Formats JSON output for readability
- Checks if the app is running

### jfr-jdk-api-based-app (Programmatic streaming, port 8080)

#### Option 7: Build jfr-jdk-api-based-app application

Compiles the application using Maven.

```bash
cd apps/jfr-jdk-api-based-app
mvn clean package -DskipTests
```

#### Option 8: Run jfr-jdk-api-based-app

Starts the streaming application:

- Continuous JFR recording
- Auto-exports every 15 seconds to `apps/jfr-jdk-api-based-app/jfr-exports/`
- Runs on port 8080
- Press Ctrl+C to stop

#### Option 9: Generate load

Runs the load generator against port 8080:

- Creates traffic to produce profiling events
- Requires the app to be running

#### Option 10: Check stream status / health

Queries the application's REST API:

- **Health**: `/api/jfr/health` - Basic health check
- **Status**: `/api/jfr/status` - Recording state, export statistics, event counts

#### Option 11: Merge JFR exports

Assembles all `.jfr` files in `apps/jfr-jdk-api-based-app/jfr-exports/` into a single file:

- Uses `jfr assemble` command
- Output saved to `apps/jfr-jdk-api-based-app/jfr-merged/`

### jfr-otel-logs-app (OTLP streaming, port 8080)

#### Option 12: Build jfr-otel-logs-app application

Compiles the application using Maven.

```bash
cd apps/jfr-otel-logs-app
mvn clean package -DskipTests
```

#### Option 13: Start OTLP Collector (Docker)

Launches OpenTelemetry Collector in Docker:

- Listens on port 4317 (OTLP gRPC)
- Configured to display received JFR profiling logs on console
- Uses `apps/jfr-otel-logs-app/docker/start-collector.sh`
- Start this **before** running the app

#### Option 14: Run jfr-otel-logs-app

Starts the OTLP streaming application:

- Continuous JFR recording
- Exports profiling events to OTLP Collector every 15 seconds
- Runs on port 8080
- **No local files** - streams directly to collector
- View collector logs with option 15
- Press Ctrl+C to stop

#### Option 15: View OTLP Collector logs (live)

Shows real-time collector output:

- Displays JFR profiling events as they arrive
- Structured log format with attributes
- Press Ctrl+C to stop viewing (collector keeps running)
- Uses `docker-compose logs -f`

#### Option 16: Check OTLP app status / health

Queries multiple endpoints:

- **App Health**: `http://localhost:8080/api/jfr/health`
- **App Status**: `http://localhost:8080/api/jfr/status`
- **Collector Health**: `http://localhost:13133` (collector's health endpoint)

#### Option 17: Stop OTLP Collector

Shuts down the Docker collector:

- Stops and removes the container
- Uses `docker-compose down`

### Shared Tools

#### Option 27: Visualize JFR files (interactive)

Prompts for which app's export directory to view, then launches `./shell-scripts/visualize-jfr.sh <folder>`:

- Opens in JDK Mission Control (if installed)
- Provides command-line summary, detailed print, JSON export, and comparison options

#### Option 28: View JFR configuration

Displays the JFR configuration file:

- Shows first 50 lines of `apps/jfr-profiler-based-app/jfr-config.jfc`
- Lists key settings (execution sampling, CPU load, GC events, etc.)
- Used by `apps/jfr-profiler-based-app/start-continuous-profiling.sh`

#### Option 29: Clean up recordings and build artifacts

Interactive cleanup with confirmations for each:

- `apps/jfr-profiler-based-app/target/` (Maven build artifacts)
- `apps/jfr-jdk-api-based-app/target/` (Maven build artifacts)
- `apps/jfr-otel-logs-app/target/` (Maven build artifacts)
- `apps/jfr-otel-logs-based-file-upload-app/target/` (Maven build artifacts)
- `apps/jfr-profiler-based-app/jfr-exports/` (recorded snapshots)
- `apps/jfr-profiler-based-app/jfr-merged/` (merged files)
- `apps/jfr-jdk-api-based-app/jfr-exports/` (exported snapshots)
- `apps/jfr-jdk-api-based-app/jfr-merged/` (merged files)
- jfr-otel-logs-app OTLP Collector (Docker container)
- jfr-otel-logs-based-file-upload-app OTLP Collector (Docker container)

Each item requires individual confirmation (`[y/N]`).

#### Option 0: Exit

Exits the menu.

## Typical Workflows

### Workflow 1: Traditional JFR Analysis

```text
1. Option 1  - Build jfr-profiler-based-app
2. Option 2  - Start continuous profiling (runs in foreground)
   - In another terminal: Option 3 (Generate load)
   - Wait for several snapshots
   - Press Ctrl+C
3. Option 4  - Merge snapshots
4. Option 27 - Visualize in JMC
```

### Workflow 2: Streaming JFR to Files

```text
1. Option 7  - Build jfr-jdk-api-based-app
2. Option 8  - Run app (runs in foreground)
   - In another terminal: Option 9 (Generate load)
   - Wait for exports
   - Press Ctrl+C
3. Option 11 - Merge exports
4. Option 27 - Visualize in JMC
```

### Workflow 3: OTLP Streaming

```text
1. Option 12 - Build jfr-otel-logs-app
2. Option 13 - Start OTLP Collector
3. Option 14 - Run app (runs in foreground)
   - In another terminal: Option 15 (View collector logs)
   - Watch JFR events stream in real-time
   - Press Ctrl+C in app terminal
4. Option 17 - Stop collector
```

### Workflow 4: Compare Apps Sequentially

Since all apps use port 8080, run one at a time. Use the menu to stop one app then start another:

```bash
./menu.sh
→ Option 2 (jfr-profiler-based-app: continuous profiling) — then Ctrl+C when done
→ Option 8 (jfr-jdk-api-based-app: streaming to files)   — then Ctrl+C when done
→ Option 13, then 14 (jfr-otel-logs-app: OTLP streaming) — then Ctrl+C when done
→ Option 22, then 23 (jfr-otel-logs-based-file-upload-app: OTLP file upload) — then Ctrl+C when done
```

## Menu Features

### Auto-Return

After each option completes, the menu displays:

```text
Press Enter to return to menu or Ctrl+C to exit...
```

Pressing Enter re-runs `./menu.sh` to show the menu again.

### Safe Defaults

- Build commands skip tests (`-DskipTests`) for speed
- Cleanup requires explicit confirmation for each item
- Foreground processes clearly indicate "Press Ctrl+C to stop"

### Script Locations

The menu calls these scripts:

- `./apps/jfr-profiler-based-app/start-continuous-profiling.sh` - Option 2
- `./apps/jfr-profiler-based-app/load-generator.sh` - Option 3
- `./shell-scripts/merge-jfr-files.sh` - Options 4, 11
- `./shell-scripts/analyze-snapshots.sh <folder>` - Option 5
- `./apps/jfr-jdk-api-based-app/run.sh` - Option 8
- `./apps/jfr-jdk-api-based-app/generate-load.sh` - Option 9
- `./apps/jfr-otel-logs-app/docker/start-collector.sh` - Option 13
- `./apps/jfr-otel-logs-app/run.sh` - Option 14
- `./apps/jfr-otel-logs-based-file-upload-app/docker/start-collector.sh` - Option 22
- `./apps/jfr-otel-logs-based-file-upload-app/run.sh` - Option 23
- `./shell-scripts/visualize-jfr.sh <folder>` - Option 27

## Docker Integration

Options 13/15/17 and 22/24/26 use Docker Compose for the OTLP Collector in each respective app's `docker/` folder.

### jfr-otel-logs-app

- `apps/jfr-otel-logs-app/docker/docker-compose.yaml`
- `apps/jfr-otel-logs-app/docker/otel-collector-config.yaml`
- `apps/jfr-otel-logs-app/docker/start-collector.sh`

### jfr-otel-logs-based-file-upload-app

- `apps/jfr-otel-logs-based-file-upload-app/docker/docker-compose.yaml`
- `apps/jfr-otel-logs-based-file-upload-app/docker/otel-collector-config.yaml`
- `apps/jfr-otel-logs-based-file-upload-app/docker/start-collector.sh`

### Collector Ports Exposed

- `4317` - OTLP gRPC receiver
- `4318` - OTLP HTTP receiver
- `8888` - Prometheus metrics
- `13133` - Health check

See [apps/jfr-otel-logs-app/docker/README.md](apps/jfr-otel-logs-app/docker/README.md) for details.

## Troubleshooting

### "Is the app running?" errors

The app isn't started yet. Run the corresponding "Build" and "Run" options first.

### "Port already in use"

All apps use port 8080. Only one can run at a time. Check what's occupying it:

```bash
lsof -i :8080
```

### Docker errors (option 13)

Ensure Docker is running:

```bash
docker info
```

### "jfr command not found"

JFR CLI tools require JDK 11+ (not just JRE). Check:

```bash
jfr --version
```

### Maven build failures

Clean and retry:

```bash
cd apps/<app-name>
mvn clean install
```

## Tips

1. **Start with option 27** after generating recordings to visualize data
2. **Use option 29 regularly** to clean up disk space from JFR exports
3. **Run option 16** to verify the jfr-otel-logs-app is successfully exporting (option 25 for jfr-otel-logs-based-file-upload-app)
4. **Keep option 15 open** in a dedicated terminal to watch events stream
5. **Press Ctrl+C twice** if a foreground process doesn't stop immediately

## See Also

- [README.md](../README.md) - Main project documentation
- [docs/project-overview.md](project-overview.md) - Detailed comparison of approaches
- Individual app READMEs for detailed configuration
