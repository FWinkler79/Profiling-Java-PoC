#!/bin/bash

# Build and run the JFR OTLP Export Application

set -e

echo "Building JFR OTLP Export Application..."
mvn clean package -DskipTests

echo ""
echo "Starting JFR OTLP Export Application on port 8080..."
echo "OTLP endpoint: http://localhost:4317 (configurable via jfr.otlp.endpoint)"
echo ""
echo "Available endpoints:"
echo "  - http://localhost:8080/api/jfr/status - JFR recording and export status"
echo "  - http://localhost:8080/api/jfr/health - Health check"
echo "  - http://localhost:8080/actuator/health - Spring actuator health"
echo ""
echo "JFR profiles will be exported to the OTLP endpoint every 15 seconds"
echo "Press Ctrl+C to stop"
echo ""

java -jar target/jfr-otel-logs-app-1.0.0.jar
