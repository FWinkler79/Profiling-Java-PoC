#!/bin/bash

# Interactive JFR visualization tool.
# Presents a menu to inspect, compare, and export JFR recording files.
#
# Usage: ./shell-scripts/visualize-jfr.sh <folder>
#   e.g.: ./shell-scripts/visualize-jfr.sh apps/jfr-profiler-based-app/jfr-exports
#         ./shell-scripts/visualize-jfr.sh apps/jfr-jdk-api-based-app/jfr-exports

FOLDER="$1"

if [ -z "$FOLDER" ]; then
    echo "Usage: ./shell-scripts/visualize-jfr.sh <folder>"
    echo "  e.g.: ./shell-scripts/visualize-jfr.sh apps/jfr-profiler-based-app/jfr-exports"
    echo "        ./shell-scripts/visualize-jfr.sh apps/jfr-jdk-api-based-app/jfr-exports"
    exit 1
fi

if [ ! -d "$FOLDER" ]; then
    echo "Error: Folder '$FOLDER' does not exist."
    exit 1
fi

python3 "$(dirname "$0")/../python-scripts/visualize-jfr.py" "$FOLDER"
