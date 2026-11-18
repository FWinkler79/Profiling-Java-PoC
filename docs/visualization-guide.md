# Visualizing JFR Files - Complete Guide

## Option 1: JDK Mission Control (JMC) - RECOMMENDED ⭐

JMC is the official GUI tool from Oracle for analyzing JFR recordings.

### Installation

#### Download JMC
```bash
# Latest version (JMC 9+)
https://jdk.java.net/jmc/

# Or via package managers:
# macOS
brew install --cask jdk-mission-control

# Linux (Ubuntu/Debian)
sudo apt install jmc

# Windows
# Download from https://jdk.java.net/jmc/
```

### Usage

```bash
# Launch JMC
jmc

# Or open a file directly
jmc -open apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr
```

### What You'll See in JMC

**Main Views:**
1. **Automated Analysis** - Quick overview of issues
2. **Method Profiling** - Hot methods and call trees
3. **Memory** - Allocations, GC behavior, heap usage
4. **Threads** - Thread activity, locks, contention
5. **I/O** - File and socket operations
6. **Events** - All captured events in detail

**Key Features:**
- Flame graphs for method profiling
- Memory allocation hot spots
- GC pause analysis
- Thread contention visualization
- Time-based filtering and zooming

---

## Option 2: Command Line Tools

### A. JFR Print (Built into JDK)

```bash
# Summary of the recording
jfr print apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr

# Print specific event types
jfr print --events jdk.ExecutionSample apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr

# Print with categories
jfr print --categories "Java Application" apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr

# Export to JSON
jfr print --json apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr > snapshot.json

# Human-readable format
jfr print --format default apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr | less
```

### B. JFR Summary

```bash
# Get a quick summary
jfr summary apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr
```

### C. JFR Metadata

```bash
# See all event types in the recording
jfr metadata apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr
```

---

## Option 3: Online Viewers

### JFR Report (HTML)

The `shell-scripts/view-jfr.sh` script can generate a basic HTML report from any `.jfr` file (option 5 in its menu). For richer output use JMC.

```bash
./shell-scripts/view-jfr.sh apps/jfr-profiler-based-app/jfr-exports
# → Option 5: Generate HTML Report

# Then open in browser
open jfr-combined-report-*.html  # macOS
```

---

## Option 4: IntelliJ IDEA

IntelliJ IDEA Ultimate has built-in JFR support.

### Steps:
1. Open IntelliJ IDEA Ultimate
2. Go to **Tools → Profiler → Open Snapshot**
3. Select your `.jfr` file
4. View flame graphs, method lists, allocations

**Note:** This requires IntelliJ IDEA Ultimate (paid version)

---

## Option 5: VisualVM with JFR Plugin

VisualVM is a free profiling tool that can view JFR files.

### Installation

```bash
# Download VisualVM
https://visualvm.github.io/

# Or via package managers:
# macOS
brew install --cask visualvm

# Linux
# Download from https://visualvm.github.io/
```

### Install JFR Plugin:
1. Launch VisualVM
2. Go to **Tools → Plugins**
3. Search for "VisualVM-JFR"
4. Install and restart

### Usage:
1. **File → Load** → Select your `.jfr` file
2. Navigate through tabs for different views

---

## Option 6: VS Code with Extension

There's a VS Code extension for viewing JFR files.

### Installation:
1. Open VS Code
2. Install extension: "Java Flight Recorder Viewer"
3. Open `.jfr` files directly in VS Code

---

## Option 7: Custom Analysis with Scripts

I'll create Python scripts for basic analysis below.

---

## Quick Comparison

| Tool | Pros | Cons | Best For |
|------|------|------|----------|
| **JMC** | Most powerful, official tool | Large download | Deep analysis |
| **Command Line** | Fast, scriptable, built-in | Text-only | Quick checks, automation |
| **IntelliJ** | Integrated with IDE | Requires Ultimate | Development workflow |
| **VisualVM** | Free, lightweight | Less features than JMC | Quick visualization |
| **VS Code** | Convenient if using VS Code | Basic features | Quick preview |

---

## My Recommendation

For your use case (understanding snapshot sizes and profiling):

### 1. Start with Command Line (Quick Checks)
```bash
jfr summary snapshot.jfr
```

### 2. Use JMC for Deep Analysis
Download and install JMC for detailed visualization:
- Flame graphs show method hotspots
- Memory view shows allocation patterns
- Easy to compare multiple recordings

### 3. Automate Analysis with Scripts

Use the Python scripts in `python-scripts/` for size and event analysis:

```bash
./shell-scripts/analyze-snapshots.sh apps/jfr-profiler-based-app/jfr-exports
./shell-scripts/visualize-jfr.sh apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr
```

---

## Next Steps

1. **Install JMC** (recommended):
   ```bash
   # macOS
   brew install --cask jdk-mission-control
   
   # Or download from:
   https://jdk.java.net/jmc/
   ```

2. **Try command line first**:
   ```bash
   # After generating snapshots:
   jfr summary apps/jfr-profiler-based-app/jfr-exports/*.jfr | head -50
   ```

3. **Open in JMC**:
   ```bash
   jmc -open apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr
   ```

---

## Troubleshooting

### "jfr command not found"
```bash
# Verify JDK installation
java -version  # Should be 11+

# Find jfr command
which jfr

# If not found, add JDK bin to PATH:
export PATH=$JAVA_HOME/bin:$PATH
```

### "Cannot open .jfr file in JMC"
- Ensure JMC version matches JDK version used to create recording
- JMC 8.x works with JDK 11+
- JMC 9.x works with JDK 17+

### "File is too large to open"
- Use command line tools instead
- Filter events when creating recording
- Use shorter maxage duration
