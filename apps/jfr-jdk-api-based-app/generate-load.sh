#!/bin/bash

# Script to generate load for the JFR Stream Application

echo "Generating load for JFR Stream Application..."
echo "This will call the API endpoints repeatedly to generate JFR events"
echo ""

BASE_URL="http://localhost:8081"

# Check if the app is running
if ! curl -s -f "$BASE_URL/api/jfr/health" > /dev/null 2>&1; then
    echo "Error: Application is not running on port 8081"
    echo "Please start the application first with: ./run.sh"
    exit 1
fi

echo "Application is running. Starting load generation..."
echo "Press Ctrl+C to stop"
echo ""

# Counter
COUNT=0

while true; do
    COUNT=$((COUNT + 1))

    # Call health endpoint
    curl -s "$BASE_URL/api/jfr/health" > /dev/null

    # Call status endpoint every 10 requests
    if [ $((COUNT % 10)) -eq 0 ]; then
        echo -n "Request #$COUNT - "
        curl -s "$BASE_URL/api/jfr/status" | grep -o '"totalExports":[0-9]*,"totalEventsProcessed":[0-9]*' | sed 's/"//g' | sed 's/,/ | /g'
    fi

    # Small delay
    sleep 0.1
done
