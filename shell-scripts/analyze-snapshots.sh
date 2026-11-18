#!/bin/bash

# Analyze JFR snapshot files in a folder.
# Shows file size statistics and event distribution.
#
# Usage: ./shell-scripts/analyze-snapshots.sh <folder>
#   e.g.: ./shell-scripts/analyze-snapshots.sh apps/jfr-profiler-based-app/jfr-exports
#         ./shell-scripts/analyze-snapshots.sh apps/jfr-jdk-api-based-app/jfr-exports

FOLDER="$1"

if [ -z "$FOLDER" ]; then
    echo "Usage: ./shell-scripts/analyze-snapshots.sh <folder>"
    echo "  e.g.: ./shell-scripts/analyze-snapshots.sh apps/jfr-profiler-based-app/jfr-exports"
    echo "        ./shell-scripts/analyze-snapshots.sh apps/jfr-jdk-api-based-app/jfr-exports"
    exit 1
fi

if [ ! -d "$FOLDER" ]; then
    echo "Error: Folder '$FOLDER' does not exist."
    exit 1
fi

python3 "$(dirname "$0")/../python-scripts/analyze-snapshots.py" "$FOLDER"
