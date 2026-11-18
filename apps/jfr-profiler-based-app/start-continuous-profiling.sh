#!/bin/bash

# Continuous JFR Snapshot Creator
# Creates periodic 15-second snapshots instead of using chunking
# This gives you true "delta" files - each contains only its 15-second window

# Run from this script's own directory so all relative paths resolve correctly
cd "$(dirname "$0")"

JFR_SNAPSHOT_DIR="jfr-exports"
mkdir -p "$JFR_SNAPSHOT_DIR"

echo "Starting continuous JFR profiling with 15-second snapshots..."
echo "Snapshots will be saved to: $JFR_SNAPSHOT_DIR"
echo ""
echo "Starting application with JFR recording..."
echo ""

# Additional JVM options
JAVA_OPTS="-Xmx512m -Xms256m"

# JFR Options with maxage=15s to keep snapshot sizes small
JFR_OPTS="-XX:StartFlightRecording=\
settings=jfr-config.jfc,\
name=continuous-recording,\
maxage=15s,\
disk=true"

# Build if needed
if [ ! -f "target/jfr-profiler-based-app-1.0.0.jar" ]; then
    echo "Building jfr-profiler-based-app application..."
    mvn clean package -DskipTests
    echo ""
fi

# Start the application in background
echo "Starting application..."
java $JAVA_OPTS $JFR_OPTS -jar target/jfr-profiler-based-app-1.0.0.jar &
APP_PID=$!

echo "Application started with PID: $APP_PID"
echo ""

# Wait for app to start
sleep 5

# Check if app is running
if ! kill -0 $APP_PID 2>/dev/null; then
    echo "Error: Application failed to start"
    exit 1
fi

echo "Application is running at: http://localhost:8080"
echo ""
echo "Starting continuous snapshot collection (every 15 seconds)..."
echo "Press Ctrl+C to stop"
echo ""

# Cleanup function
cleanup() {
    echo ""
    echo ""
    echo "Stopping continuous profiling..."
    echo "Stopping application (PID: $APP_PID)..."
    kill $APP_PID 2>/dev/null
    wait $APP_PID 2>/dev/null

    echo ""
    echo "Snapshot collection complete!"
    echo "Snapshots saved in: $JFR_SNAPSHOT_DIR"
    echo ""

    # Show summary
    COUNT=$(ls -1 "$JFR_SNAPSHOT_DIR"/*.jfr 2>/dev/null | wc -l)
    if [ $COUNT -gt 0 ]; then
        TOTAL_SIZE=$(du -sh "$JFR_SNAPSHOT_DIR" | awk '{print $1}')
        echo "Collected $COUNT snapshots (total size: $TOTAL_SIZE)"
        echo ""
        echo "To analyze:"
        echo "  python3 ../../analyze-snapshots.py $JFR_SNAPSHOT_DIR"
        echo "  python3 ../../visualize-jfr.py $JFR_SNAPSHOT_DIR/snapshot-*.jfr"
        echo "  jmc -open $JFR_SNAPSHOT_DIR/snapshot-001-*.jfr"
    fi

    exit 0
}

trap cleanup SIGINT SIGTERM

# Continuous snapshot loop
COUNTER=1
while true; do
    TIMESTAMP=$(date +%Y%m%d-%H%M%S)
    SNAPSHOT_FILE="$JFR_SNAPSHOT_DIR/snapshot-$(printf '%03d' $COUNTER)-$TIMESTAMP.jfr"

    # Dump current recording
    jcmd $APP_PID JFR.dump name=continuous-recording filename="$SNAPSHOT_FILE" 2>&1 | grep -v "^$"

    if [ -f "$SNAPSHOT_FILE" ]; then
        SIZE=$(ls -lh "$SNAPSHOT_FILE" | awk '{print $5}')
        printf "[%s] Snapshot %03d created: %s (%s)\n" "$(date +%H:%M:%S)" $COUNTER "$(basename $SNAPSHOT_FILE)" "$SIZE"
    else
        echo "[$(date +%H:%M:%S)] Warning: Snapshot $COUNTER failed"
    fi

    COUNTER=$((COUNTER + 1))

    # Wait 15 seconds
    sleep 15
done
