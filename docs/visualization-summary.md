╔═══════════════════════════════════════════════════════════════════════╗
║                  HOW TO VISUALIZE JFR FILES                           ║
║                     Quick Reference Card                              ║
╚═══════════════════════════════════════════════════════════════════════╝


QUICK START
═══════════

After collecting JFR snapshots, visualize them with:

  ./shell-scripts/visualize-jfr.sh apps/jfr-profiler-based-app/jfr-exports


3 WAYS TO VISUALIZE
═══════════════════

1. 🎯 Interactive Menu (No Install)
   ./shell-scripts/visualize-jfr.sh apps/jfr-profiler-based-app/jfr-exports
   → 10-option menu: ASCII stats, JMC, CLI summary, JSON, HTML, compare, and more
   → Great for all skill levels

2. 🔥 JDK Mission Control (Best - One-time Install)
   brew install --cask jdk-mission-control
   jmc -open your-file.jfr
   → Professional flame graphs
   → Most powerful analysis

3. ⚡ Command Line (No Install)
   jfr summary your-file.jfr
   → Fast, built-in
   → Good for quick checks


WHAT EACH TOOL SHOWS YOU
═════════════════════════

visualize-jfr.sh:
  ✓ Interactive 10-option menu
  ✓ ASCII stats: event distribution, hot methods, allocation hotspots
  ✓ CLI summary and detailed event print
  ✓ JSON export
  ✓ HTML report generation
  ✓ File listing and comparison
  ✓ Event type inspection
  ✓ Can launch JMC directly

JDK Mission Control:
  ✓ Flame graphs (BEST for finding bottlenecks!)
  ✓ Method profiling with call trees
  ✓ Memory allocations by class
  ✓ GC pause times
  ✓ Thread activity timeline
  ✓ I/O operations
  ✓ Lock contention

Command Line (jfr):
  ✓ Recording summary
  ✓ Event counts
  ✓ Specific event details
  ✓ JSON export


RECOMMENDED WORKFLOW
════════════════════

Step 1: Quick Check
  jfr summary apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr
  → See if file has data

Step 2: Interactive Menu
  ./shell-scripts/visualize-jfr.sh apps/jfr-profiler-based-app/jfr-exports
  → Option 1 (ASCII stats) identifies hot methods
  → Option 9 opens directly in JMC

Step 3: Deep Analysis
  jmc -open apps/jfr-profiler-based-app/jfr-exports/snapshot-001-*.jfr
  → Flame graph shows call stacks
  → Memory tab shows allocations


INSTALLATION (JMC)
══════════════════

macOS:
  brew install --cask jdk-mission-control

Linux:
  Download from: https://jdk.java.net/jmc/

Windows:
  Download from: https://jdk.java.net/jmc/


COMMON TASKS
════════════

Find hottest methods:
  jfr print --events jdk.ExecutionSample file.jfr | \
    grep "at " | sort | uniq -c | sort -rn | head -20

Find memory-intensive classes:
  jfr print --events jdk.ObjectAllocationInNewTLAB file.jfr | \
    grep "class" | sort | uniq -c | sort -rn | head -20

Compare two snapshots:
  ./shell-scripts/visualize-jfr.sh apps/jfr-profiler-based-app/jfr-exports
  → Option 7

Export to JSON:
  jfr print --json file.jfr > output.json


FILES IN THIS PROJECT
═════════════════════

Visualization Tools:
  visualize-jfr.sh         Interactive menu (ASCII stats, JMC, compare, export, …)
  analyze-snapshots.sh     Snapshot size and event count analysis

Documentation:
  docs/visualization-guide.md           Complete guide
  docs/quickstart-visualization.md      Quick start
  docs/visualization-summary.md         This file


READING A FLAME GRAPH
═════════════════════

In JMC Method Profiling → Flame Graph:

         ┌──────────────────────────────┐
         │        main()                │  ← Entry point
         └────────┬──────────┬──────────┘
                  │          │
         ┌────────▼────┐  ┌──▼─────────┐
         │ handleReq() │  │ doWork()   │  ← Methods called
         └──────┬──────┘  └────────────┘
                │
         ┌──────▼──────┐
         │  compute()  │  ← Nested call
         └─────────────┘

  Width = Time spent (wider = hotter = optimize this!)
  Height = Call depth
  Colors = Different packages


WHAT TO LOOK FOR
════════════════

In Flame Graphs:
  • Wide bars at bottom = Hot methods (CPU bottlenecks)
  • Many thin bars = Too many method calls
  • Repeated patterns = Caching opportunity

In Memory View:
  • Top allocating classes
  • Short-lived objects (GC pressure)
  • Allocation rate spikes

In Thread View:
  • Threads mostly waiting = I/O bottleneck
  • Lock contention = Synchronization issues


TROUBLESHOOTING
═══════════════

"jfr command not found"
  → Ensure JDK 11+ is installed
  → Add to PATH: export PATH=$JAVA_HOME/bin:$PATH

"JMC won't open file"
  → JMC version must match JDK version
  → Try: jfr summary file.jfr (verify file valid)

"No data in charts"
  → Application was idle during recording
  → Use ./apps/jfr-profiler-based-app/load-generator.sh next time

"File too large"
  → Reduce maxage in apps/jfr-profiler-based-app/start-continuous-profiling.sh
  → Or use command line: jfr print file.jfr | less


MORE INFORMATION
════════════════

Full guides:
  cat docs/visualization-guide.md
  cat docs/quickstart-visualization.md

JMC Download:
  https://jdk.java.net/jmc/

JFR Documentation:
  https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/


YOUR TURN!
══════════

Try it now:
  ./shell-scripts/visualize-jfr.sh apps/jfr-profiler-based-app/jfr-exports

Happy profiling! 🚀

