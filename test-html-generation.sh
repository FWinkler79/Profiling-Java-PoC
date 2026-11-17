#!/bin/bash

# Test HTML report generation for multiple JFR files

files=(jfr-snapshots/*.jfr jfr-recordings/*.jfr)
output="jfr-combined-report-test.html"

echo "Testing HTML generation with ${#files[@]} files..."

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

# Process first 3 files as test
count=0
for file in "${files[@]}"; do
    [ -f "$file" ] || continue
    [ $count -ge 3 ] && break

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
            </pre>
        </div>
        <div class="section">
            <h3>Summary</h3>
            <pre>
HTMLEOF

    jfr summary "$file" 2>&1 | head -20 >> "$output"

    echo '            </pre>
        </div>
    </div>' >> "$output"

    count=$((count + 1))
done

# Close HTML
echo '</body>
</html>' >> "$output"

echo ""
echo "✓ Generated: $output"
ls -lh "$output"

echo ""
echo "First 10 lines of HTML:"
head -10 "$output"

echo ""
echo "Last 10 lines of HTML:"
tail -10 "$output"

echo ""
echo "Opening in browser..."
open "$output"
