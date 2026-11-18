# Quick Start: Visualizing JFR Files

## 🚀 Fastest Methods

### Method 1: Interactive Menu (Easiest)
```bash
./view-jfr.sh
```
This launches an interactive menu with all visualization options.

### Method 2: Python Visualizer (No Install Required)
```bash
python3 visualize-jfr.py
```
Creates ASCII charts showing:
- Event distribution
- Hot methods
- Allocation hotspots
- File statistics

### Method 3: Command Line (Built-in)
```bash
# Quick summary
jfr summary jfr-snapshots/snapshot-001-*.jfr

# Detailed events
jfr print jfr-snapshots/snapshot-001-*.jfr | less
```

---

## 📊 Best Tool: JDK Mission Control (JMC)

### Install JMC
```bash
# macOS
brew install --cask jdk-mission-control

# Or download from:
https://jdk.java.net/jmc/
```

### Open a Recording
```bash
jmc -open jfr-snapshots/snapshot-001-*.jfr
```

### What You'll See in JMC

**Overview Tab:**
- Automated analysis with issue detection
- CPU usage graphs
- Memory usage graphs
- GC activity

**Method Profiling Tab:**
- Flame graphs (best for finding hotspots!)
- Top methods by CPU time
- Call trees
- Package-level aggregation

**Memory Tab:**
- Allocation by class
- Allocation by stack trace
- Allocation rate over time
- GC statistics

**Threads Tab:**
- Thread activity timeline
- Lock contention
- Thread states
- Blocking events

**I/O Tab:**
- File read/write operations
- Socket operations
- Slowest operations
- I/O timeline

---

## 🎯 Common Tasks

### "Show me the hottest methods"

**Command Line:**
```bash
jfr print --events jdk.ExecutionSample jfr-snapshots/snapshot-001-*.jfr | \
  grep "at " | sort | uniq -c | sort -rn | head -20
```

**JMC:**
1. Open file in JMC
2. Go to "Method Profiling" tab
3. Click "Flame Graph" view
4. Wider bars = more CPU time

**Python Script:**
```bash
python3 visualize-jfr.py
# Shows top methods automatically
```

### "What's allocating the most memory?"

**Command Line:**
```bash
jfr print --events jdk.ObjectAllocationInNewTLAB jfr-snapshots/snapshot-001-*.jfr | \
  grep "class" | sort | uniq -c | sort -rn | head -20
```

**JMC:**
1. Memory tab
2. "Allocations" subtab
3. Sort by "Total"

### "Show me GC activity"

**Command Line:**
```bash
jfr print --events jdk.GarbageCollection jfr-snapshots/snapshot-001-*.jfr
```

**JMC:**
1. Automated Analysis tab
2. Garbage Collection section
3. See pause times and causes

### "Compare two snapshots"

**Using our tool:**
```bash
./view-jfr.sh
# Choose option 7 (Compare)
```

**JMC:**
1. Open both files in separate windows
2. Compare tabs side-by-side

---

## 📁 Example Workflow

### 1. Collect Baseline Data
```bash
# Terminal 1: Run app
./start-with-jfr.sh

# Terminal 2: Collect snapshots
python3 jfr-snapshot-manager.py

# Terminal 3: Generate light load
./load-generator.sh
# Choose option 1 (Light load)
```

Let it run for ~5 snapshots (75 seconds), then stop.

### 2. Quick Analysis
```bash
# Check sizes first
python3 analyze-snapshots.py

# Visualize the latest
python3 visualize-jfr.py
```

### 3. Deep Dive in JMC
```bash
# Open the largest snapshot
jmc -open jfr-snapshots/snapshot-005-*.jfr
```

In JMC:
1. Check Automated Analysis for issues
2. Method Profiling → Flame Graph (identify hotspots)
3. Memory → Allocations (see what's being created)
4. Threads (check for contention)

### 4. Optimize and Retry
Based on findings:
- Modify `apps/jfr-profiler-based-app/jfr-config.jfc` to reduce sampling
- Adjust `maxage` if snapshots too large
- Re-run and compare

---

## 🎨 Understanding JMC Views

### Flame Graph (Method Profiling)
```
┌─────────────────────────────────────────────────┐
│                   main()                        │  ← Entry point
├──────────────┬─────────────────┬────────────────┤
│ processReq() │  handleIO()     │  doWork()      │  ← Direct calls
├──────┬───────┼─────┬───────────┼────────────────┤
│parse │serial │read │  write    │  compute       │  ← Nested calls
└──────┴───────┴─────┴───────────┴────────────────┘
  ↑                   ↑
  Wider = More CPU    Colors = Different packages
```

**How to read:**
- Width = time spent (wider = hotter)
- Height = call stack depth
- Click to zoom in
- Tooltip shows exact percentage

### Timeline View (Threads)
```
Thread 1: ═══════════════════════════════════════
Thread 2: ══════░░░░░░══════░░░░░░══════░░░░░░══
Thread 3: ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░

Legend:
  ═ = Running
  ░ = Waiting/Blocked
```

**Look for:**
- Threads mostly waiting (optimization opportunity)
- All threads blocked (deadlock potential)
- One thread 100% busy (bottleneck)

---

## 🔍 Troubleshooting Visualization

### "JMC won't open"
```bash
# Check version
jmc --version

# If not found:
brew install --cask jdk-mission-control  # macOS
# Or download from https://jdk.java.net/jmc/
```

### "File won't load in JMC"
- Ensure JMC version matches JDK version
- Try command line first: `jfr summary file.jfr`
- File might be corrupted - collect a new one

### "No method profiling data"
- Check `apps/jfr-profiler-based-app/jfr-config.jfc` has `jdk.ExecutionSample` enabled
- Ensure application was actually doing work
- Try `/api/heavy` endpoint for more activity

### "Charts are empty"
- Application might have been idle
- Check event counts: `jfr summary file.jfr`
- Generate load and recollect

---

## 📚 Next Steps

1. **Practice with the tools:**
   ```bash
   ./view-jfr.sh
   ```

2. **Read the full guide:**
   ```bash
   cat VISUALIZATION-GUIDE.md
   ```

3. **Install JMC for best experience:**
   - Download: https://jdk.java.net/jmc/
   - Or: `brew install --cask jdk-mission-control`

4. **Experiment:**
   - Try different load patterns
   - Modify JFR config
   - Compare snapshots before/after changes

---

## 🆘 Quick Reference

| Task | Command |
|------|---------|
| Interactive menu | `./view-jfr.sh` |
| ASCII visualization | `python3 visualize-jfr.py` |
| Quick summary | `jfr summary file.jfr` |
| Open in JMC | `jmc -open file.jfr` |
| Export to JSON | `jfr print --json file.jfr > output.json` |
| List events | `jfr metadata file.jfr` |
| Show specific event | `jfr print --events jdk.ExecutionSample file.jfr` |

Happy profiling! 🎉
