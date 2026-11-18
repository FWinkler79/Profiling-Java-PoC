#!/bin/bash

# Merge JFR Snapshots
# Assembles all JFR files in a folder into a single recording file
# using the JDK's built-in `jfr assemble` command.
#
# Usage: ./shell-scripts/merge-jfr-files.sh [source-folder]
#   source-folder   Directory containing the .jfr files
#                    (default: apps/jfr-profiler-based-app/jfr-exports)
#
# The merged file is written to a sibling "jfr-merged" folder next to the
# source, so each app's merged output stays within that app's directory
# (e.g. source apps/jfr-jdk-api-based-app/jfr-exports -> apps/jfr-jdk-api-based-app/jfr-merged).

SOURCE_DIR="${1:-apps/jfr-profiler-based-app/jfr-exports}"
OUTPUT_DIR="$(dirname "$SOURCE_DIR")/jfr-merged"

if [ ! -d "$SOURCE_DIR" ]; then
    echo "Error: Source folder '$SOURCE_DIR' does not exist."
    echo "Run continuous profiling first (menu option 2) to create snapshots."
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

DATE=$(date +%Y%m%d-%H%M%S)
OUTPUT_FILE="$OUTPUT_DIR/merged-$DATE.jfr"

echo "Merging JFR files from '$SOURCE_DIR' into '$OUTPUT_FILE'..."
echo ""

jfr assemble "$SOURCE_DIR" "$OUTPUT_FILE"
ASSEMBLE_STATUS=$?

if [ $ASSEMBLE_STATUS -eq 0 ] && [ -s "$OUTPUT_FILE" ]; then
    SIZE=$(ls -lh "$OUTPUT_FILE" | awk '{print $5}')
    echo ""
    echo "✓ Merged file created: $OUTPUT_FILE ($SIZE)"
else
    # jfr assemble creates the output file even when it finds nothing,
    # so clean up the empty/partial file to avoid leaving a misleading 0B recording.
    rm -f "$OUTPUT_FILE"
    echo ""
    echo "Error: Merge failed - no JFR chunks were assembled from '$SOURCE_DIR'."
    exit 1
fi
