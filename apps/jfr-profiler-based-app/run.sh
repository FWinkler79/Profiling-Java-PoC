#!/bin/bash

# Script to run the JFR File-Based Application

echo "=========================================="
echo "Starting JFR File-Based Application"
echo "=========================================="
echo ""
echo "The application will start on port 8080"
echo ""
echo "To profile with JFR, use one of these methods:"
echo ""
echo "1. Start with JFR enabled:"
echo "   java -XX:StartFlightRecording=filename=recording.jfr,duration=60s \\"
echo "        -jar target/jfr-profiler-based-app-1.0.0.jar"
echo ""
echo "2. Use jcmd while running:"
echo "   jcmd <PID> JFR.start name=MyRecording"
echo ""
echo "3. Connect with JDK Mission Control (JMC)"
echo ""
echo "=========================================="
echo ""

cd "$(dirname "$0")"

if [ ! -f "target/jfr-profiler-based-app-1.0.0.jar" ]; then
    echo "Building application..."
    mvn clean package
    echo ""
fi

echo "Starting application..."
mvn spring-boot:run
