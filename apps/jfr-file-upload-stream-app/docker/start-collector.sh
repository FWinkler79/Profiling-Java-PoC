#!/bin/bash

# Start the complete JFR OTLP profiling stack

set -e

echo "=========================================="
echo "JFR OTLP Profiling Stack Startup"
echo "=========================================="
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running. Please start Docker first."
    exit 1
fi

echo "✅ Docker is running"
echo ""

# Start the collector
echo "🚀 Starting OpenTelemetry Collector..."
cd docker
docker-compose up -d

# Wait for collector to be ready
echo "⏳ Waiting for collector to be ready..."
for i in {1..30}; do
    if curl -s http://localhost:13133 > /dev/null 2>&1; then
        echo "✅ Collector is ready"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ Collector failed to start within 30 seconds"
        docker-compose logs
        exit 1
    fi
    sleep 1
done

cd ..

echo ""
echo "=========================================="
echo "Stack is ready!"
echo "=========================================="
echo ""
echo "📊 OpenTelemetry Collector:"
echo "   - OTLP gRPC:  http://localhost:4317"
echo "   - OTLP HTTP:  http://localhost:4318"
echo "   - Metrics:    http://localhost:8888/metrics"
echo "   - Health:     http://localhost:13133"
echo ""
echo "To view collector logs in real-time:"
echo "   cd docker && docker-compose logs -f"
echo ""
echo "To start the application:"
echo "   ./run.sh"
echo ""
echo "To stop the collector:"
echo "   cd docker && docker-compose down"
echo ""
