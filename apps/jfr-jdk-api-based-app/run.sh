#!/bin/bash

# Script to run the JFR JDK API Application

echo "=========================================="
echo "Starting JFR JDK API Application"
echo "=========================================="
echo ""
echo "The application will:"
echo "  - Start a continuous JFR recording"
echo "  - Export snapshots every 15 seconds"
echo "  - Save .jfr files to ./jfr-exports/"
echo "  - Run on port 8081"
echo ""
echo "API Endpoints:"
echo "  - Status: http://localhost:8081/api/jfr/status"
echo "  - Health: http://localhost:8081/api/jfr/health"
echo ""
echo "=========================================="
echo ""

cd "$(dirname "$0")"

if [ ! -f "target/jfr-jdk-api-based-app-1.0.0.jar" ]; then
    echo "Building application..."
    mvn clean package
    echo ""
fi

echo "Starting application..."
java -jar target/jfr-jdk-api-based-app-1.0.0.jar
