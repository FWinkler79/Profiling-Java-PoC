#!/bin/bash

# JFR Test Applications - Main Menu
# Options are grouped per application plus shared tooling.

cat << "EOF"
╔═══════════════════════════════════════════════════════════════════════╗
║           JFR Test Applications - Control Panel                       ║
╚═══════════════════════════════════════════════════════════════════════╝
EOF

echo ""
echo "What would you like to do?"
echo ""
echo "── jfr-profiler-based-app (traditional, port 8080) ──────────────────────────"
echo "  1) Build jfr-profiler-based-app application"
echo "  2) Start continuous profiling (auto-dump every 15s)"
echo "  3) Generate load (port 8080, requires app running)"
echo "  4) Merge JFR snapshots into single file"
echo "  5) Analyze existing snapshots"
echo "  6) Test endpoints (port 8080)"
echo ""
echo "── jfr-jdk-api-based-app (programmatic streaming, port 8080) ───────────────────"
echo "  7) Build jfr-jdk-api-based-app application"
echo "  8) Run jfr-jdk-api-based-app (auto-exports every 15s)"
echo "  9) Generate load (port 8080, requires app running)"
echo " 10) Check stream status / health (port 8080)"
echo " 11) Merge JFR exports into single file"
echo ""
echo "── jfr-otel-logs-app (OTLP streaming, port 8080) ───────────────"
echo " 12) Build jfr-otel-logs-app application"
echo " 13) Start OTLP Collector (Docker)"
echo " 14) Run jfr-otel-logs-app (streams to OTLP)"
echo " 15) View OTLP Collector logs (live)"
echo " 16) Check OTLP app status / health (port 8080)"
echo " 17) Stop OTLP Collector"
echo ""
echo "── jfr-otel-logs-based-file-upload-app (file-upload OTLP streaming, port 8080) ─────"
echo " 21) Build jfr-otel-logs-based-file-upload-app application"
echo " 22) Start OTLP Collector for file-upload app (Docker)"
echo " 23) Run jfr-otel-logs-based-file-upload-app (streams to OTLP)"
echo " 24) View OTLP Collector logs for file-upload app (live)"
echo " 25) Check file-upload app status / health (port 8080)"
echo " 26) Stop OTLP Collector for file-upload app"
echo ""
echo "── Shared tools ─────────────────────────────────────────────────────────"
echo " 27) Visualize JFR files (interactive)"
echo " 28) View JFR configuration"
echo " 29) Clean up recordings and build artifacts"
echo "  0) Exit"
echo ""
read -p "Enter choice (0-29): " choice

case $choice in
    # ── jfr-profiler-based-app (traditional, port 8080) ──
    1)
        echo ""
        echo "Building jfr-profiler-based-app application..."
        cd apps/jfr-profiler-based-app
        mvn clean package -DskipTests
        cd ../..
        echo ""
        echo "Build complete! Run option 2 to start continuous profiling."
        ;;

    2)
        echo ""
        echo "Starting continuous profiling..."
        echo ""
        echo "This will:"
        echo "  • Start the app in background with JFR"
        echo "  • Automatically dump snapshots every 15 seconds"
        echo "  • Save to apps/jfr-profiler-based-app/jfr-exports/ directory"
        echo "  • Each snapshot = true 15s delta"
        echo ""
        echo "Press Ctrl+C to stop profiling and app"
        echo ""
        sleep 2
        ./apps/jfr-profiler-based-app/start-continuous-profiling.sh
        ;;

    3)
        echo ""
        ./apps/jfr-profiler-based-app/load-generator.sh
        ;;

    4)
        echo ""
        echo "Merging JFR snapshots..."
        echo ""
        echo "This will assemble all .jfr files in apps/jfr-profiler-based-app/jfr-exports/ into"
        echo "a single file using 'jfr assemble'. Merged file will be saved to"
        echo "apps/jfr-profiler-based-app/jfr-merged/."
        echo ""
        ./shell-scripts/merge-jfr-files.sh apps/jfr-profiler-based-app/jfr-exports
        ;;

    5)
        echo ""
        ./shell-scripts/analyze-snapshots.sh apps/jfr-profiler-based-app/jfr-exports
        ;;

    6)
        echo ""
        echo "Testing jfr-profiler-based-app endpoints (port 8080)..."
        echo ""
        echo "1. Testing /api/hello:"
        echo "---"
        curl -s "http://localhost:8080/api/hello?name=TestUser" | python3 -m json.tool || echo "Error: Is the app running?"
        echo ""
        echo ""
        echo "2. Testing /api/heavy:"
        echo "---"
        curl -s "http://localhost:8080/api/heavy" | python3 -m json.tool || echo "Error: Is the app running?"
        echo ""
        echo ""
        echo "To test from browser, open:"
        echo "  http://localhost:8080/api/hello?name=YourName"
        echo "  http://localhost:8080/api/heavy"
        ;;

    # ── jfr-jdk-api-based-app (programmatic streaming, port 8080) ──
    7)
        echo ""
        echo "Building jfr-jdk-api-based-app application..."
        cd apps/jfr-jdk-api-based-app
        mvn clean package -DskipTests
        cd ../..
        echo ""
        echo "Build complete! Run option 8 to start the streaming app."
        ;;

    8)
        echo ""
        echo "Starting jfr-jdk-api-based-app..."
        echo ""
        echo "This will:"
        echo "  • Start a continuous JFR recording"
        echo "  • Export snapshots every 15 seconds to apps/jfr-jdk-api-based-app/jfr-exports/"
        echo "  • Run on port 8080"
        echo ""
        echo "Press Ctrl+C to stop the app"
        echo ""
        sleep 2
        ./apps/jfr-jdk-api-based-app/run.sh
        ;;

    9)
        echo ""
        echo "Generating load for jfr-jdk-api-based-app (port 8080)..."
        echo ""
        ./apps/jfr-jdk-api-based-app/generate-load.sh
        ;;

    10)
        echo ""
        echo "Checking jfr-jdk-api-based-app status / health (port 8080)..."
        echo ""
        echo "1. Health:"
        echo "---"
        curl -s "http://localhost:8080/api/jfr/health" | python3 -m json.tool || echo "Error: Is the app running?"
        echo ""
        echo ""
        echo "2. Status:"
        echo "---"
        curl -s "http://localhost:8080/api/jfr/status" | python3 -m json.tool || echo "Error: Is the app running?"
        ;;

    11)
        echo ""
        echo "Merging jfr-jdk-api-based-app JFR exports..."
        echo ""
        echo "This will assemble all .jfr files in apps/jfr-jdk-api-based-app/jfr-exports/ into"
        echo "a single file using 'jfr assemble'. Merged file will be saved to"
        echo "apps/jfr-jdk-api-based-app/jfr-merged/."
        echo ""
        ./shell-scripts/merge-jfr-files.sh apps/jfr-jdk-api-based-app/jfr-exports
        ;;

    # ── jfr-otel-logs-app (OTLP streaming, port 8080) ──
    12)
        echo ""
        echo "Building jfr-otel-logs-app application..."
        cd apps/jfr-otel-logs-app
        mvn clean package -DskipTests
        cd ../..
        echo ""
        echo "Build complete! Run option 13 to start the OTLP collector first."
        ;;

    13)
        echo ""
        echo "Starting OTLP Collector (Docker)..."
        echo ""
        echo "This will:"
        echo "  • Start OpenTelemetry Collector in Docker"
        echo "  • Listen on port 4317 (OTLP gRPC)"
        echo "  • Display received JFR profiling logs on console"
        echo ""
        cd apps/jfr-otel-logs-app
        ./docker/start-collector.sh
        cd ../..
        echo ""
        echo "Collector started! Run option 14 to start the app."
        ;;

    14)
        echo ""
        echo "Starting jfr-otel-logs-app..."
        echo ""
        echo "This will:"
        echo "  • Start continuous JFR recording"
        echo "  • Export profiling events to OTLP Collector every 15s"
        echo "  • Run on port 8080"
        echo "  • No local files - streams directly to collector"
        echo ""
        echo "View collector logs with option 15"
        echo ""
        echo "Press Ctrl+C to stop the app"
        echo ""
        sleep 2
        cd apps/jfr-otel-logs-app
        ./run.sh
        cd ../..
        ;;

    15)
        echo ""
        echo "Viewing OTLP Collector logs (live)..."
        echo ""
        echo "This will show JFR profiling events as they arrive."
        echo "Press Ctrl+C to stop viewing (collector keeps running)"
        echo ""
        sleep 2
        cd apps/jfr-otel-logs-app/docker
        docker-compose logs -f
        cd ../../..
        ;;

    16)
        echo ""
        echo "Checking jfr-otel-logs-app status / health (port 8080)..."
        echo ""
        echo "1. Health:"
        echo "---"
        curl -s "http://localhost:8080/api/jfr/health" | python3 -m json.tool || echo "Error: Is the app running?"
        echo ""
        echo ""
        echo "2. Status:"
        echo "---"
        curl -s "http://localhost:8080/api/jfr/status" | python3 -m json.tool || echo "Error: Is the app running?"
        echo ""
        echo ""
        echo "3. Collector Health:"
        echo "---"
        curl -s "http://localhost:13133" | python3 -m json.tool || echo "Error: Is the collector running?"
        ;;

    17)
        echo ""
        echo "Stopping OTLP Collector..."
        cd apps/jfr-otel-logs-app/docker
        docker-compose down
        cd ../../..
        echo ""
        echo "Collector stopped!"
        ;;

    # ── jfr-otel-logs-based-file-upload-app (file-upload OTLP streaming, port 8080) ──
    21)
        echo ""
        echo "Building jfr-otel-logs-based-file-upload-app application..."
        cd apps/jfr-otel-logs-based-file-upload-app
        mvn clean package -DskipTests
        cd ../..
        echo ""
        echo "Build complete! Run option 22 to start the OTLP collector first."
        ;;

    22)
        echo ""
        echo "Starting OTLP Collector for jfr-otel-logs-based-file-upload-app (Docker)..."
        echo ""
        echo "This will:"
        echo "  • Start OpenTelemetry Collector in Docker"
        echo "  • Listen on port 4317 (OTLP gRPC)"
        echo "  • Display received JFR profiling logs on console"
        echo ""
        cd apps/jfr-otel-logs-based-file-upload-app
        ./docker/start-collector.sh
        cd ../..
        echo ""
        echo "Collector started! Run option 23 to start the app."
        ;;

    23)
        echo ""
        echo "Starting jfr-otel-logs-based-file-upload-app..."
        echo ""
        echo "This will:"
        echo "  • Start continuous JFR recording"
        echo "  • Export profiling events to OTLP Collector every 15s"
        echo "  • Run on port 8080"
        echo "  • No local files - streams directly to collector"
        echo ""
        echo "View collector logs with option 24"
        echo ""
        echo "Press Ctrl+C to stop the app"
        echo ""
        sleep 2
        cd apps/jfr-otel-logs-based-file-upload-app
        ./run.sh
        cd ../..
        ;;

    24)
        echo ""
        echo "Viewing OTLP Collector logs for jfr-otel-logs-based-file-upload-app (live)..."
        echo ""
        echo "This will show JFR profiling events as they arrive."
        echo "Press Ctrl+C to stop viewing (collector keeps running)"
        echo ""
        sleep 2
        cd apps/jfr-otel-logs-based-file-upload-app/docker
        docker-compose logs -f
        cd ../../..
        ;;

    25)
        echo ""
        echo "Checking jfr-otel-logs-based-file-upload-app status / health (port 8080)..."
        echo ""
        echo "1. Health:"
        echo "---"
        curl -s "http://localhost:8080/api/jfr/health" | python3 -m json.tool || echo "Error: Is the app running?"
        echo ""
        echo ""
        echo "2. Status:"
        echo "---"
        curl -s "http://localhost:8080/api/jfr/status" | python3 -m json.tool || echo "Error: Is the app running?"
        echo ""
        echo ""
        echo "3. Collector Health:"
        echo "---"
        curl -s "http://localhost:13133" | python3 -m json.tool || echo "Error: Is the collector running?"
        ;;

    26)
        echo ""
        echo "Stopping OTLP Collector for jfr-otel-logs-based-file-upload-app..."
        cd apps/jfr-otel-logs-based-file-upload-app/docker
        docker-compose down
        cd ../../..
        echo ""
        echo "Collector stopped!"
        ;;

    # ── Shared tools ──
    27)
        echo ""
        echo "Select which app's JFR exports to view:"
        echo "  1) jfr-profiler-based-app  (jfr-exports/)"
        echo "  2) jfr-profiler-based-app  (jfr-merged/)"
        echo "  3) jfr-jdk-api-based-app   (jfr-exports/)"
        echo "  4) jfr-jdk-api-based-app   (jfr-merged/)"
        echo ""
        read -p "Enter choice (1-4): " jfr_dir_choice
        case $jfr_dir_choice in
            1) ./shell-scripts/view-jfr.sh apps/jfr-profiler-based-app/jfr-exports ;;
            2) ./shell-scripts/view-jfr.sh apps/jfr-profiler-based-app/jfr-merged ;;
            3) ./shell-scripts/view-jfr.sh apps/jfr-jdk-api-based-app/jfr-exports ;;
            4) ./shell-scripts/view-jfr.sh apps/jfr-jdk-api-based-app/jfr-merged ;;
            *) echo "Invalid choice" ;;
        esac
        ;;

    28)
        echo ""
        echo "JFR Configuration (apps/jfr-profiler-based-app/jfr-config.jfc):"
        echo "─────────────────────────────────────────────────────────────"
        cat apps/jfr-profiler-based-app/jfr-config.jfc | head -50
        echo ""
        echo "... (showing first 50 lines, open apps/jfr-profiler-based-app/jfr-config.jfc to see full config)"
        echo ""
        echo "Key settings:"
        echo "  - Execution sampling: every 10ms"
        echo "  - CPU load: every 1s"
        echo "  - GC events: all captured"
        echo "  - Method allocations: enabled with stack traces"
        echo "  - I/O events: threshold 10ms"
        echo ""
        echo "  Used by: apps/jfr-profiler-based-app/start-continuous-profiling.sh"
        ;;

    29)
        echo ""
        echo "Cleaning up..."
        read -p "Delete jfr-profiler-based-app build artifacts (apps/jfr-profiler-based-app/target/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-profiler-based-app/target/
            echo "✓ Deleted apps/jfr-profiler-based-app/target/"
        fi

        read -p "Delete jfr-jdk-api-based-app build artifacts (apps/jfr-jdk-api-based-app/target/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-jdk-api-based-app/target/
            echo "✓ Deleted apps/jfr-jdk-api-based-app/target/"
        fi

        read -p "Delete jfr-otel-logs-app build artifacts (apps/jfr-otel-logs-app/target/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-otel-logs-app/target/
            echo "✓ Deleted apps/jfr-otel-logs-app/target/"
        fi

        read -p "Delete jfr-profiler-based-app exports (apps/jfr-profiler-based-app/jfr-exports/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-profiler-based-app/jfr-exports/
            echo "✓ Deleted apps/jfr-profiler-based-app/jfr-exports/"
        fi

        read -p "Delete jfr-profiler-based-app merged files (apps/jfr-profiler-based-app/jfr-merged/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-profiler-based-app/jfr-merged/
            echo "✓ Deleted apps/jfr-profiler-based-app/jfr-merged/"
        fi

        read -p "Delete jfr-jdk-api-based-app exports (apps/jfr-jdk-api-based-app/jfr-exports/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-jdk-api-based-app/jfr-exports/
            echo "✓ Deleted apps/jfr-jdk-api-based-app/jfr-exports/"
        fi

        read -p "Delete jfr-jdk-api-based-app merged files (apps/jfr-jdk-api-based-app/jfr-merged/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-jdk-api-based-app/jfr-merged/
            echo "✓ Deleted apps/jfr-jdk-api-based-app/jfr-merged/"
        fi

        read -p "Delete jfr-otel-logs-based-file-upload-app build artifacts (apps/jfr-otel-logs-based-file-upload-app/target/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-otel-logs-based-file-upload-app/target/
            echo "✓ Deleted apps/jfr-otel-logs-based-file-upload-app/target/"
        fi

        read -p "Stop and remove jfr-otel-logs-app OTLP Collector? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            cd apps/jfr-otel-logs-app/docker
            docker-compose down
            cd ../../..
            echo "✓ Stopped jfr-otel-logs-app OTLP Collector"
        fi

        read -p "Stop and remove jfr-otel-logs-based-file-upload-app OTLP Collector? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            cd apps/jfr-otel-logs-based-file-upload-app/docker
            docker-compose down
            cd ../../..
            echo "✓ Stopped jfr-otel-logs-based-file-upload-app OTLP Collector"
        fi

        echo ""
        echo "Cleanup complete!"
        ;;

    0)
        echo "Goodbye!"
        exit 0
        ;;

    *)
        echo "Invalid choice"
        exit 1
        ;;
esac

echo ""
read -p "Press Enter to return to menu or Ctrl+C to exit..."
exec $0
