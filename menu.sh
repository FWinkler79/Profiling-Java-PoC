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
echo "── jfr-file-based-app (traditional, port 8080) ──────────────────────────"
echo "  1) Build jfr-file-based-app application"
echo "  2) Start continuous profiling (auto-dump every 15s)"
echo "  3) Generate load (port 8080, requires app running)"
echo "  4) Merge JFR snapshots into single file"
echo "  5) Analyze existing snapshots"
echo "  6) Test endpoints (port 8080)"
echo ""
echo "── jfr-stream-app (programmatic streaming, port 8081) ───────────────────"
echo "  7) Build jfr-stream-app application"
echo "  8) Run jfr-stream-app (auto-exports every 15s)"
echo "  9) Generate load (port 8081, requires app running)"
echo " 10) Check stream status / health (port 8081)"
echo " 11) Merge JFR exports into single file"
echo ""
echo "── Shared tools ─────────────────────────────────────────────────────────"
echo " 12) Visualize JFR files (interactive)"
echo " 13) View JFR configuration"
echo " 14) Clean up recordings and build artifacts"
echo "  0) Exit"
echo ""
read -p "Enter choice (0-14): " choice

case $choice in
    # ── jfr-file-based-app (traditional, port 8080) ──
    1)
        echo ""
        echo "Building jfr-file-based-app application..."
        cd apps/jfr-file-based-app
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
        echo "  • Save to apps/jfr-file-based-app/jfr-exports/ directory"
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
        echo "This will assemble all .jfr files in apps/jfr-file-based-app/jfr-exports/ into"
        echo "a single file using 'jfr assemble'. Merged file will be saved to"
        echo "apps/jfr-file-based-app/jfr-merged/."
        echo ""
        ./merge-jfr-files.sh apps/jfr-file-based-app/jfr-exports
        ;;

    5)
        echo ""
        python3 analyze-snapshots.py apps/jfr-file-based-app/jfr-exports
        ;;

    6)
        echo ""
        echo "Testing jfr-file-based-app endpoints (port 8080)..."
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

    # ── jfr-stream-app (programmatic streaming, port 8081) ──
    7)
        echo ""
        echo "Building jfr-stream-app application..."
        cd apps/jfr-stream-app
        mvn clean package -DskipTests
        cd ../..
        echo ""
        echo "Build complete! Run option 8 to start the streaming app."
        ;;

    8)
        echo ""
        echo "Starting jfr-stream-app..."
        echo ""
        echo "This will:"
        echo "  • Start a continuous JFR recording"
        echo "  • Export snapshots every 15 seconds to apps/jfr-stream-app/jfr-exports/"
        echo "  • Run on port 8081"
        echo ""
        echo "Press Ctrl+C to stop the app"
        echo ""
        sleep 2
        ./apps/jfr-stream-app/run.sh
        ;;

    9)
        echo ""
        echo "Generating load for jfr-stream-app (port 8081)..."
        echo ""
        ./apps/jfr-stream-app/generate-load.sh
        ;;

    10)
        echo ""
        echo "Checking jfr-stream-app status / health (port 8081)..."
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
        echo "Merging jfr-stream-app JFR exports..."
        echo ""
        echo "This will assemble all .jfr files in apps/jfr-stream-app/jfr-exports/ into"
        echo "a single file using 'jfr assemble'. Merged file will be saved to"
        echo "apps/jfr-stream-app/jfr-merged/."
        echo ""
        ./merge-jfr-files.sh apps/jfr-stream-app/jfr-exports
        ;;

    # ── Shared tools ──
    12)
        echo ""
        ./view-jfr.sh
        ;;

    13)
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

    14)
        echo ""
        echo "Cleaning up..."
        read -p "Delete jfr-file-based-app build artifacts (apps/jfr-file-based-app/target/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-file-based-app/target/
            echo "✓ Deleted apps/jfr-file-based-app/target/"
        fi

        read -p "Delete jfr-stream-app build artifacts (apps/jfr-stream-app/target/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-stream-app/target/
            echo "✓ Deleted apps/jfr-stream-app/target/"
        fi

        read -p "Delete jfr-file-based-app exports (apps/jfr-file-based-app/jfr-exports/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-file-based-app/jfr-exports/
            echo "✓ Deleted apps/jfr-file-based-app/jfr-exports/"
        fi

        read -p "Delete jfr-file-based-app merged files (apps/jfr-file-based-app/jfr-merged/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-file-based-app/jfr-merged/
            echo "✓ Deleted apps/jfr-file-based-app/jfr-merged/"
        fi

        read -p "Delete jfr-stream-app exports (apps/jfr-stream-app/jfr-exports/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-stream-app/jfr-exports/
            echo "✓ Deleted apps/jfr-stream-app/jfr-exports/"
        fi

        read -p "Delete jfr-stream-app merged files (apps/jfr-stream-app/jfr-merged/)? [y/N]: " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf apps/jfr-stream-app/jfr-merged/
            echo "✓ Deleted apps/jfr-stream-app/jfr-merged/"
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
