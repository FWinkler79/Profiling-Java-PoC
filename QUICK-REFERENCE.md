# JFR Snapshot Size Control - Quick Reference

## Key Concepts

### The `maxage` Parameter
The most important parameter for controlling snapshot sizes:

```bash
-XX:StartFlightRecording=maxage=15s
```

This creates a **circular buffer** that:
- Keeps only the last 15 seconds of events
- Automatically discards older events
- Prevents unbounded memory growth
- Caps snapshot file sizes

### How It Works

```
Time:     0s ──────────> 15s ──────────> 30s ──────────> 45s
Buffer:   [Building up]   [Full - 15s]   [Full - 15s]   [Full - 15s]
          Events 0-15s     Events 15-30s  Events 30-45s  Events 45-60s
          (growing)        (stable size)  (stable size)  (stable size)
```

After the first 15 seconds, snapshot sizes stabilize because JFR discards events older than 15 seconds.

## Snapshot Size Factors

### 1. Time Window (`maxage`)
- **15 seconds**: Good balance (500 KB - 3 MB typical)
- **30 seconds**: Twice the data (~1-6 MB typical)
- **5 seconds**: Minimal data (~200-800 KB typical)

### 2. Application Activity
- **Idle**: Minimal events, small snapshots
- **Active**: More method calls, allocations, I/O → larger snapshots
- **Heavy load**: Maximum event generation

### 3. JFR Configuration

#### High-Impact Events (adjust these first):

```xml
<!-- Method Profiling - BIGGEST impact -->
<event name="jdk.ExecutionSample">
  <setting name="period">10 ms</setting>  <!-- Lower = more samples = larger files -->
</event>

<!-- Object Allocations - HIGH impact -->
<event name="jdk.ObjectAllocationInNewTLAB">
  <setting name="enabled">true</setting>  <!-- Can disable to reduce size -->
</event>
```

#### Medium-Impact Events:

```xml
<!-- I/O Events -->
<event name="jdk.FileRead">
  <setting name="threshold">10 ms</setting>  <!-- Higher threshold = fewer events -->
</event>

<!-- Monitor/Lock Events -->
<event name="jdk.JavaMonitorEnter">
  <setting name="threshold">10 ms</setting>
</event>
```

#### Low-Impact Events:

```xml
<!-- Periodic Stats -->
<event name="jdk.CPULoad">
  <setting name="period">1 s</setting>  <!-- Already infrequent -->
</event>
```

## Reducing Snapshot Sizes

### Strategy 1: Increase Sampling Period (Minimal Impact on Insights)

```xml
<!-- Change from 10ms to 20ms -->
<event name="jdk.ExecutionSample">
  <setting name="period">20 ms</setting>
</event>
```
**Impact**: 50% fewer samples, still catches hot spots
**Size reduction**: ~30-40%

### Strategy 2: Increase Thresholds

```xml
<!-- Only capture slow operations -->
<event name="jdk.FileRead">
  <setting name="threshold">50 ms</setting>  <!-- Was 10ms -->
</event>
```
**Impact**: Only see slow I/O, miss fast operations
**Size reduction**: ~20-30% for I/O heavy apps

### Strategy 3: Disable Low-Value Events

```xml
<!-- Disable class loading after startup -->
<event name="jdk.ClassLoad">
  <setting name="enabled">false</setting>
</event>
```
**Impact**: No class loading data (usually not needed)
**Size reduction**: ~10-15% during startup, minimal after

### Strategy 4: Reduce Time Window

Change `maxage=15s` to `maxage=10s` or `maxage=5s`

**Impact**: Less historical data
**Size reduction**: Proportional (5s = 33% of 15s)

## Testing Methodology

1. **Start with baseline** (current config):
   ```bash
   ./start-with-jfr.sh
   # In another terminal:
   python3 jfr-snapshot-manager.py
   ```

2. **Generate consistent load**:
   ```bash
   ./load-generator.sh  # Choose option 2 (medium load)
   ```

3. **Let it run for 5 snapshots** (75 seconds)

4. **Analyze results**:
   ```bash
   python3 analyze-snapshots.py
   ```

5. **Modify `jfr-config.jfc`** with one change

6. **Repeat and compare**

## Typical Results

### Default Configuration (maxage=15s)

| Scenario | Size Range | Notes |
|----------|------------|-------|
| Idle | 200-500 KB | JVM housekeeping only |
| Light load (10 req/s) | 500 KB - 1 MB | Moderate activity |
| Heavy load (continuous) | 1-3 MB | High method sampling |

### Optimized Configuration (20ms sampling, 50ms thresholds)

| Scenario | Size Range | Notes |
|----------|------------|-------|
| Idle | 150-350 KB | ~30% smaller |
| Light load | 350-700 KB | ~30% smaller |
| Heavy load | 700 KB - 2 MB | ~30% smaller |

### Aggressive Configuration (50ms sampling, 100ms thresholds, no allocations)

| Scenario | Size Range | Notes |
|----------|------------|-------|
| Idle | 100-200 KB | ~60% smaller |
| Light load | 200-400 KB | ~60% smaller |
| Heavy load | 400 KB - 1 MB | ~65% smaller |

**Trade-off**: May miss short-lived hotspots

## What You Won't Lose

Even with aggressive reduction:
- **GC patterns**: Still visible
- **Major hotspots**: Still caught
- **Performance trends**: Still accurate
- **I/O bottlenecks**: Still detected (if thresholds reasonable)

## What You Might Miss

With too much reduction:
- **Short-lived methods**: May not sample enough
- **Fast I/O patterns**: Above threshold
- **Allocation hotspots**: If disabled
- **Detailed stack traces**: If depth reduced

## Recommended Starting Point

For production monitoring:

```xml
<!-- Good balance: detailed enough, not too large -->
<event name="jdk.ExecutionSample">
  <setting name="period">20 ms</setting>  <!-- Was 10ms -->
</event>

<!-- Focus on slow operations -->
<event name="jdk.FileRead">
  <setting name="threshold">20 ms</setting>  <!-- Was 10ms -->
</event>

<!-- Keep allocations but only in TLAB -->
<event name="jdk.ObjectAllocationInNewTLAB">
  <setting name="enabled">true</setting>
</event>
<event name="jdk.ObjectAllocationOutsideTLAB">
  <setting name="enabled">false</setting>  <!-- Rare, can disable -->
</event>
```

Expected size: **500 KB - 1.5 MB** under load

## Quick Commands

```bash
# Start everything
./menu.sh

# Build and run manually
mvn clean package
./start-with-jfr.sh

# Collect snapshots
python3 jfr-snapshot-manager.py

# Analyze
python3 analyze-snapshots.py

# View a snapshot
jfr print jfr-snapshots/snapshot-001-*.jfr | less
```
