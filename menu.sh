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
echo "── jfr-jdk-api-based-app (programmatic streaming, port 8081) ───────────────────"
echo "  7) Build jfr-jdk-api-based-app application"
echo "  8) Run jfr-jdk-api-based-app (auto-exports every 15s)"
echo "  9) Generate load (port 8081, requires app running)"
echo " 10) Check stream status / health (port 8081)"
echo " 11) Merge JFR exports into single file"
echo ""
echo "── jfr-stream-based-otel-app (OTLP streaming, port 8082) ───────────────"
echo " 12) Build jfr-stream-based-otel-app application"
echo " 13) Start OTLP Collector (Docker)"
echo " 14) Run jfr-stream-based-otel-app (streams to OTLP)"
echo " 15) View OTLP Collector logs (live)"
echo " 16) Check OTLP app status / health (port 8082)"
echo " 17) Stop OTLP Collector"
echo ""
echo "── jfr-file-upload-stream-app (file-upload OTLP streaming, port 8083) ─────"
echo " 21) Build jfr-file-upload-stream-app application"
echo " 22) Start OTLP Collector for file-upload app (Docker)"
echo " 23) Run jfr-file-upload-stream-app (streams to OTLP)"
echo " 24) View OTLP Collector logs for file-upload app (live)"
echo " 25) Check file-upload app status / health (port 8083)"
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
        ./start-continuous-profiling.sh
        ;;

    3)
        echo ""
        ./load-generator.sh
        ;;

    4)
        echo ""
        echo "Merging JFR snapshots..."
        echo ""
        echo "This will assemble all .jfr files in apps/jfr-profiler-based-app/jfr-exports/ into"
        echo "a single file using 'jfr assemble'. Merged file will be saved to"
        echo "apps/jfr-profiler-based-app/jfr-merged/."
        echo ""
        ./merge-jfr-files.sh apps/jfr-profiler-based-app/jfr-exports
        ;;

    5)
        echo ""
        python3 analyze-snapshots.py apps/jfr-profiler-based-app/jfr-exports
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

    # ── jfr-jdk-api-based-app (programmatic streaming, port 8081) ──
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
        echo "  • Run on port 8081"
        echo ""
        echo "Press Ctrl+C to stop the app"
        echo ""
        sleep 2
        ./apps/jfr-jdk-api-based-app/run.sh
        ;;

    9)
        echo ""
        echo "Generating load for jfr-jdk-api-based-app (port 8081)..."
        echo ""
        ./apps/jfr-jdk-api-based-app/generate-load.sh
        ;;

    10)
        echo ""
        echo "Checking jfr-jdk-api-based-app status / health (port 8081)..."
        echo ""
        echo "1. Health:"
        echo "---"
        curl -s "http://localhost:8081/api/jfr/health" | python3 -m json.tool || echo "Error: Is the app running?"
        echo ""
        echo ""
        echo "2. Status:"
        echo "---"
        curl -s "http://localhost:8081/api/jfr/status" | python3 -m json.tool || echo "Error: Is the app running?"
        ;;

    11)
        echo ""
        echo "Merging jfr-jdk-api-based-app JFR exports..."
        echo ""
        echo "This will assemble all .jfr files in apps/jfr-jdk-api-based-app/jfr-exports/ into"
        echo "a single file using 'jfr assemble'. Merged file will be saved to"
        echo "apps/jfr-jdk-api-based-app/jfr-merged/."
        echo ""
        ./merge-jfr-files.sh apps/jfr-jdk-api-based-app/jfr-exports
        ;;

    # ── jfr-stream-based-otel-app (OTLP streaming, port 8082) ──
    12)
        echo ""
        echo "Building jfr-stream-based-otel-app application..."
        cd apps/jfr-stream-based-otel-app
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
        cd apps/jfr-stream-based-otel-app
        ./docker/start-collector.sh
        cd ../..
        echo ""
        echo "Collector started! Run option 14 to start the app."
        ;;

    14)
        echo ""
        echo "Starting jfr-stream-based-otel-app..."
        echo ""
        echo "This will:"
        echo "  • Start continuous JFR recording"
        echo "  • Export profiling events to OTLP Collector every 15s"
        echo "  • Run on port 8082"
        echo "  • No local files - streams directly to collector"
        echo ""
        echo "View collector logs with option 15"
        echo ""
        echo "Press Ctrl+C to stop the app"
        echo ""
        sleep 2
        cd apps/jfr-stream-based-otel-app
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
        cd apps/jfr-stream-based-otel-app/docker
        docker-compose logs -f
        cd ../../..
        ;;

    16)
        echo ""
        echo "Checking jfr-stream-based-otel-app status / health (port 8082)..."
        echo ""
        echo "1. Health:"
        echo "---"
        curl -s "http://localhost:8082/api/jfr/health" | python3 -m json.tool || echo "Error: Is the app running?"
        echo ""
        echo ""
        echo "2. Status:"
        echo "---"
        curl -s "http://localhost:8082/api/jfr/status" | python3 -m json.tool || echo "Error: Is the app running?"
        echo ""
        echo ""
        echo "3. Collector Health:"
        echo "---"
        curl -s "http://localhost:13133" | python3 -m json.tool || echo "Error: Is the collector running?"
        ;;

    17)
        echo ""
        echo "Stopping OTLP Collector..."
        cd apps/jfr-stream-based-otel-app/docker
        docker-compose down
        cd ../../..
        echo ""
        echo "Collector stopped!"
        ;;

    # ── jfr-file-upload-stream-app (file-upload OTLP streaming, port 8083) ──
    21)
        echo ""
        echo "Building jfr-file-upload-stream-app application..."
        cd apps/jfr-file-upload-stream-app
        mvn clean package -DskipTests
        cd ../..
        echo ""
        echo "Build complete! Run option 22 to start the OTLP collector first."
        ;;

    22)
        echo ""
        echo "Starting OTLP Collector for jfr-file-upload-stream-app (Docker)..."
        echo ""
        echo "This will:"
        echo "  • Start OpenTelemetry Collector in Docker"
        echo "  • Listen on port 4317 (OTLP gRPC)"
        echo "  • Display received JFR profiling logs on console"
        echo ""
        cd apps/jfr-file-upload-stream-app
        ./docker/start-collector.sh
        cd ../..
        echo ""
        echo "Collector started! Run option 23 to start the app."
        ;;

    23)
        echo ""
        echo "Starting jfr-file-upload-stream-app..."
        echo ""
        echo "This will:"
        echo "  • Start continuous JFR recording"
        echo "  • Export profiling events to OTLP Collector every 15s"
        echo "  • Run on port 8083"
        echo "  • No local files - streams directly to collector"
        echo ""
        echo "View collector logs with option 24"
        echo ""
        echo "Press Ctrl+C to stop the app"
        echo ""
        sleep 2
        cd apps/jfr-file-upload-stream-app
        ./run.sh
        cd ../..
        ;;

    24)
        echo ""
        echo "Viewing OTLP Collector logs for jfr-file-upload-stream-app (live)..."
        echo ""
        echo "This will show JFR profiling events as they arrive."
        echo "Press Ctrl+C to stop viewing (collector keeps running)"
        echo ""
        sleep 2
        cd apps/jfr-file-upload-stream-app/docker
        docker-compose logs -f
        cd ../../..
        ;;

    25)
        echo ""
        echo "Checking jfr-file-upload-stream-app status / health (port 8083)..."
        echo ""
        echo "1. Health:"
        echo "---"
        curl -s "http://localhost:8083/api/jfr/health" | python3 -m json.tool || echo "Error: Is the app running?"
        echo ""
        echo ""
        echo "2. Status:"
        echo "---"
        curl -s "http://localhost:8083/api/jfr/status" | python3 -m json.tool || echo "Error: Is the app running?"
        echo ""
        echo ""
        echo "3. Collector Health:"
        echo "---"
        curl -s "http://localhost:13133" | python3 -m json.tool || echo "Error: Is the collector running?"
        ;;

    26)
        echo ""
        echo "Stopping OTLP Collector for jfr-file-upload-stream-app..."
        cd apps/jfr-file-upload-stream-app/docker
        docker-compose down
        cd ../../..
        echo ""
        echo "Collector stopped!"
        ;;

    # ── Shared tools ──
    27)
        echo ""
        ./view-jfr.sh
        ;;

    28)
        echo ""
        echo "JFR Configuration (jfr-config.jfc):"
        echo "─────────────────────────────────────────────────────────────"
        cat jfr-config.jfc | head -50
        echo ""
        echo "... (showing first 50 lines, open jfr-config.jfc to see full config)"
        echo ""
        echo "Key settings:"
        echo "  - Execution sampling: every 10ms"
        echo "  - CPU load: every 1s"
        echo "  - GC events: all captured"
        echo "  - Method allocations: enabled with stack traces"
        echo "  - I/O events: threshold 10ms"
        echo ""
        echo "Used by: start-continuous-profiling.sh"
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

        read -p "Delete jfr-stream-based-otel-app build artifacts (apps/jfr-stream-based-otel-app/target/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-stream-based-otel-app/target/
            echo "✓ Deleted apps/jfr-stream-based-otel-app/target/"
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

        read -p "Delete jfr-file-upload-stream-app build artifacts (apps/jfr-file-upload-stream-app/target/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-file-upload-stream-app/target/
            echo "✓ Deleted apps/jfr-file-upload-stream-app/target/"
        fi

        read -p "Stop and remove jfr-stream-based-otel-app OTLP Collector? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            cd apps/jfr-stream-based-otel-app/docker
            docker-compose down
            cd ../../..
            echo "✓ Stopped jfr-stream-based-otel-app OTLP Collector"
        fi

        read -p "Stop and remove jfr-file-upload-stream-app OTLP Collector? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            cd apps/jfr-file-upload-stream-app/docker
            docker-compose down
            cd ../../..
            echo "✓ Stopped jfr-file-upload-stream-app OTLP Collector"
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
