#!/bin/bash

# Visualize a JFR file: event distribution, hottest methods, allocation hotspots.
#
# Usage: ./shell-scripts/visualize-jfr.sh <file.jfr>
#   e.g.: ./shell-scripts/visualize-jfr.sh apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr
#         ./shell-scripts/visualize-jfr.sh apps/jfr-jdk-api-based-app/jfr-exports/jfr-export-*.jfr

FILE="$1"

if [ -z "$FILE" ]; then
    echo "Usage: ./shell-scripts/visualize-jfr.sh <file.jfr>"
    echo "  e.g.: ./shell-scripts/visualize-jfr.sh apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr"
    echo "        ./shell-scripts/visualize-jfr.sh apps/jfr-jdk-api-based-app/jfr-exports/jfr-export-*.jfr"
    exit 1
fi

python3 "$(dirname "$0")/../python-scripts/visualize-jfr.py" "$FILE"
