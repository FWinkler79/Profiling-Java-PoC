#!/bin/bash

# Build and run the JFR OpenTelemetry Logs File Upload Application

set -e

echo "Building JFR OpenTelemetry Logs File Upload Application..."
mvn clean package -DskipTests

echo ""
echo "Starting JFR OpenTelemetry Logs File Upload Application on port 8083..."
echo "OTLP endpoint: http://localhost:4317 (configurable via jfr.otlp.endpoint)"
echo ""
echo "Available endpoints:"
echo "  - http://localhost:8083/api/jfr/status - JFR recording and export status"
echo "  - http://localhost:8083/api/jfr/health - Health check"
echo "  - http://localhost:8083/actuator/health - Spring actuator health"
echo ""
echo "JFR profiles will be exported to the OTLP endpoint every 15 seconds"
echo "Press Ctrl+C to stop"
echo ""

java -jar target/jfr-otel-logs-based-file-upload-app-1.0.0.jar
