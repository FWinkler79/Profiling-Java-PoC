#!/bin/bash

# JFR File Viewer Helper
# Quick launcher for various JFR visualization tools
#
# Usage: ./shell-scripts/view-jfr.sh <folder>
#   folder   Directory containing the .jfr files to view, e.g.:
#     apps/jfr-profiler-based-app/jfr-exports
#     apps/jfr-jdk-api-based-app/jfr-exports

JFR_DIR="$1"

if [ -z "$JFR_DIR" ]; then
    echo "Usage: ./shell-scripts/view-jfr.sh <folder>"
    echo "  e.g.: ./shell-scripts/view-jfr.sh apps/jfr-profiler-based-app/jfr-exports"
    echo "        ./shell-scripts/view-jfr.sh apps/jfr-jdk-api-based-app/jfr-exports"
    exit 1
fi

if [ ! -d "$JFR_DIR" ]; then
    echo "Error: Folder '$JFR_DIR' does not exist."
    exit 1
fi

show_menu() {
    cat << "EOF"
╔═══════════════════════════════════════════════════════════════════════╗
║                    JFR File Visualization Tools                       ║
╚═══════════════════════════════════════════════════════════════════════╝

EOF

    echo "Select a visualization method:"
    echo ""
    echo "  1) JDK Mission Control (JMC) - RECOMMENDED"
    echo "  2) Command Line Summary (jfr summary)"
    echo "  3) Command Line Detailed (jfr print)"
    echo "  4) Export to JSON"
    echo "  5) Generate HTML Report"
    echo "  6) List all .jfr files"
    echo "  7) Compare two snapshots"
    echo "  8) View event types in recording"
    echo "  9) Install JMC (macOS/Linux)"
    echo "  0) Exit"
    echo ""
}

find_jfr_files() {
    local files=()
    for f in "$JFR_DIR"/*.jfr; do
        [ -f "$f" ] && files+=("$f")
    done
    echo "${files[@]}"
}

select_jfr_file() {
    local files=($(find_jfr_files))

    if [ ${#files[@]} -eq 0 ]; then
        echo "No .jfr files found in '$JFR_DIR'"
        return 1
    fi

    echo ""
    echo "Available JFR files:"
    echo "─────────────────────────────────────────────────────────────"
    for i in "${!files[@]}"; do
        local size=$(du -h "${files[$i]}" | cut -f1)
        printf "%2d) %s (%s)\n" $((i+1)) "$(basename "${files[$i]}")" "$size"
    done
    echo ""

    read -p "Select file number (or 'a' for all): " selection

    if [ "$selection" = "a" ] || [ "$selection" = "A" ]; then
        echo "${files[@]}"
    else
        local idx=$((selection-1))
        if [ $idx -ge 0 ] && [ $idx -lt ${#files[@]} ]; then
            echo "${files[$idx]}"
        else
            echo "Invalid selection"
            return 1
        fi
    fi
}

open_jmc() {
    echo ""
    echo "Opening JDK Mission Control..."
    echo ""

    local file=$(select_jfr_file)
    if [ -z "$file" ]; then
        return 1
    fi

    # Try different JMC commands
    if command -v jmc &> /dev/null; then
        jmc -open "$file" &
        echo "✓ JMC launched"
    elif command -v jmc.app &> /dev/null; then
        jmc.app -open "$file" &
        echo "✓ JMC launched"
    elif [ -d "/Applications/JDK Mission Control.app" ]; then
        open -a "JDK Mission Control" "$file"
        echo "✓ JMC launched"
    else
        echo "✗ JMC not found. Install it with option 9 or download from:"
        echo "  https://jdk.java.net/jmc/"
    fi
}

jfr_summary() {
    echo ""
    local file=$(select_jfr_file)
    if [ -z "$file" ]; then
        return 1
    fi

    echo ""
    echo "JFR Summary for: $(basename "$file")"
    echo "═══════════════════════════════════════════════════════════════"
    jfr summary "$file" | head -100
    echo ""
    echo "(Showing first 100 lines. Full output available with: jfr summary $file)"
}

jfr_detailed() {
    echo ""
    local file=$(select_jfr_file)
    if [ -z "$file" ]; then
        return 1
    fi

    echo ""
    echo "What would you like to view?"
    echo "  1) Method profiling (ExecutionSample)"
    echo "  2) Memory allocations"
    echo "  3) Garbage collection"
    echo "  4) Thread events"
    echo "  5) I/O events"
    echo "  6) All events"
    echo ""
    read -p "Select (1-6): " choice

    echo ""
    case $choice in
        1) jfr print --events jdk.ExecutionSample "$file" | less ;;
        2) jfr print --events jdk.ObjectAllocationInNewTLAB,jdk.ObjectAllocationOutsideTLAB "$file" | less ;;
        3) jfr print --events jdk.GarbageCollection,jdk.YoungGarbageCollection,jdk.OldGarbageCollection "$file" | less ;;
        4) jfr print --events jdk.ThreadStart,jdk.ThreadEnd,jdk.ThreadPark "$file" | less ;;
        5) jfr print --events jdk.FileRead,jdk.FileWrite,jdk.SocketRead,jdk.SocketWrite "$file" | less ;;
        6) jfr print "$file" | less ;;
        *) echo "Invalid choice" ;;
    esac
}

export_json() {
    echo ""
    local file=$(select_jfr_file)
    if [ -z "$file" ]; then
        return 1
    fi

    local output="${file%.jfr}.json"

    echo ""
    echo "Exporting to JSON..."
    jfr print --json "$file" > "$output"

    echo "✓ Exported to: $output"
    echo ""
    echo "File size: $(du -h "$output" | cut -f1)"
    echo ""
    read -p "Open in default JSON viewer? [y/N]: " confirm

    if [[ $confirm =~ ^[Yy]$ ]]; then
        if [[ "$OSTYPE" == "darwin"* ]]; then
            open "$output"
        elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
            xdg-open "$output"
        fi
    fi
}

generate_html_report() {
    echo ""
    echo "Note: HTML report generation requires additional tools."
    echo "This feature creates a basic HTML visualization using jfr print output."
    echo ""

    local selection=$(select_jfr_file)
    if [ -z "$selection" ]; then
        return 1
    fi

    # Convert selection to array
    local files=($selection)
    local output

    # Determine output filename based on number of files
    if [ ${#files[@]} -eq 1 ]; then
        output="${files[0]%.jfr}.html"
    else
        output="jfr-combined-report-$(date +%Y%m%d-%H%M%S).html"
    fi

    echo "Generating HTML report for ${#files[@]} file(s)..."

    # Create HTML header
    cat > "$output" << 'HTMLEOF'
<!DOCTYPE html>
<html>
<head>
    <title>JFR Report</title>
    <style>
        body { font-family: monospace; padding: 20px; background: #1e1e1e; color: #d4d4d4; }
        pre { background: #2d2d2d; padding: 15px; overflow-x: auto; border-radius: 5px; }
        h1, h2 { color: #4ec9b0; }
        h3 { color: #569cd6; }
        .section { margin: 20px 0; }
        .file-section { border-left: 3px solid #4ec9b0; padding-left: 15px; margin: 30px 0; }
    </style>
</head>
<body>
    <h1>JFR Recording Report</h1>
HTMLEOF

    # Process each file
    for file in "${files[@]}"; do
        echo "  Processing: $(basename "$file")"

        cat >> "$output" << HTMLEOF
    <div class="file-section">
        <h2>$(basename "$file")</h2>
        <div class="section">
            <h3>File Information</h3>
            <pre>
File: $(basename "$file")
Path: $file
Size: $(du -h "$file" | cut -f1)
Generated: $(date)
            </pre>
        </div>
        <div class="section">
            <h3>Summary</h3>
            <pre>
HTMLEOF

        jfr summary "$file" >> "$output" 2>&1

        cat >> "$output" << 'HTMLEOF'
            </pre>
        </div>
    </div>
HTMLEOF
    done

    # Close HTML
    cat >> "$output" << 'HTMLEOF'
</body>
</html>
HTMLEOF

    echo ""
    echo "✓ Generated: $output"
    echo ""
    read -p "Open in browser? [y/N]: " confirm

    if [[ $confirm =~ ^[Yy]$ ]]; then
        if [[ "$OSTYPE" == "darwin"* ]]; then
            open "$output"
        elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
            xdg-open "$output"
        fi
    fi
}

list_files() {
    echo ""
    echo "Available JFR files:"
    echo "═══════════════════════════════════════════════════════════════"

    local files=($(find_jfr_files))

    if [ ${#files[@]} -eq 0 ]; then
        echo "No .jfr files found"
        return
    fi

    for f in "${files[@]}"; do
        local size=$(du -h "$f" | cut -f1)
        local date=$(stat -f "%Sm" -t "%Y-%m-%d %H:%M:%S" "$f" 2>/dev/null || stat -c "%y" "$f" 2>/dev/null | cut -d'.' -f1)
        printf "%-50s %10s  %s\n" "$(basename "$f")" "$size" "$date"
    done
    echo ""
    echo "Total: ${#files[@]} files"
}

compare_snapshots() {
    echo ""
    echo "Select first snapshot:"
    local file1=$(select_jfr_file)
    if [ -z "$file1" ]; then
        return 1
    fi

    echo ""
    echo "Select second snapshot:"
    local file2=$(select_jfr_file)
    if [ -z "$file2" ]; then
        return 1
    fi

    echo ""
    echo "Comparison:"
    echo "═══════════════════════════════════════════════════════════════"

    local size1=$(stat -f "%z" "$file1" 2>/dev/null || stat -c "%s" "$file1")
    local size2=$(stat -f "%z" "$file2" 2>/dev/null || stat -c "%s" "$file2")
    local diff=$((size2 - size1))
    local percent=$(echo "scale=1; ($diff * 100) / $size1" | bc)

    echo "File 1: $(basename "$file1")"
    echo "  Size: $(du -h "$file1" | cut -f1)"
    echo ""
    echo "File 2: $(basename "$file2")"
    echo "  Size: $(du -h "$file2" | cut -f1)"
    echo ""
    echo "Difference: $diff bytes ($percent%)"
    echo ""

    read -p "Show detailed comparison? [y/N]: " confirm
    if [[ $confirm =~ ^[Yy]$ ]]; then
        echo ""
        echo "Events in File 1:"
        jfr metadata "$file1" | grep "jdk\." | wc -l
        echo ""
        echo "Events in File 2:"
        jfr metadata "$file2" | grep "jdk\." | wc -l
    fi
}

view_event_types() {
    echo ""
    local file=$(select_jfr_file)
    if [ -z "$file" ]; then
        return 1
    fi

    echo ""
    echo "Event types in: $(basename "$file")"
    echo "═══════════════════════════════════════════════════════════════"
    jfr metadata "$file" | grep "jdk\." | sort
}

install_jmc() {
    echo ""
    echo "Installing JDK Mission Control..."
    echo ""

    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "Installing via Homebrew..."
        brew install --cask jdk-mission-control
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        echo "For Linux, please download from:"
        echo "https://jdk.java.net/jmc/"
        echo ""
        echo "Or install via package manager:"
        echo "  Ubuntu/Debian: sudo apt install jmc"
        echo "  Fedora: sudo dnf install jmc"
    else
        echo "Please download from: https://jdk.java.net/jmc/"
    fi
}

# Main loop
while true; do
    show_menu
    read -p "Enter choice (0-9): " choice

    case $choice in
        1) open_jmc ;;
        2) jfr_summary ;;
        3) jfr_detailed ;;
        4) export_json ;;
        5) generate_html_report ;;
        6) list_files ;;
        7) compare_snapshots ;;
        8) view_event_types ;;
        9) install_jmc ;;
        0) echo "Goodbye!"; exit 0 ;;
        *) echo "Invalid choice" ;;
    esac

    echo ""
    read -p "Press Enter to continue..."
done
