# JFR Snapshot Mechanism Explained

## Visual Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                    JFR RECORDING ARCHITECTURE                       │
└─────────────────────────────────────────────────────────────────────┘

                    Application Running
                           │
                           ▼
        ┌──────────────────────────────────────┐
        │    JVM with JFR Enabled              │
        │                                      │
        │  ┌────────────────────────────────┐ │
        │  │   Circular Event Buffer        │ │
        │  │   (maxage=15s)                 │ │
        │  │                                │ │
        │  │  [Events: 0s ──────► 15s]    │ │
        │  │                                │ │
        │  │  Oldest events automatically   │ │
        │  │  discarded when buffer full    │ │
        │  └────────────────────────────────┘ │
        │           │                          │
        │           │ Event Stream             │
        │           ▼                          │
        │  ┌────────────────────────────────┐ │
        │  │  JFR Configuration             │ │
        │  │  (apps/jfr-profiler-based-app/jfr-config.jfc)              │ │
        │  │                                │ │
        │  │  • Which events to capture     │ │
        │  │  • Sampling rates              │ │
        │  │  • Thresholds                  │ │
        │  │  • Stack trace depth           │ │
        │  └────────────────────────────────┘ │
        └──────────────────────────────────────┘
                           │
                           │ On demand or periodic
                           ▼
        ┌──────────────────────────────────────┐
        │      JFR.dump / JFR.check            │
        │   (jcmd or automated manager)        │
        └──────────────────────────────────────┘
                           │
                           │ Snapshot
                           ▼
        ┌──────────────────────────────────────┐
        │     .jfr File (Snapshot)             │
        │                                      │
        │  Contains last 15 seconds of events  │
        │  Size: ~500KB - 3MB typical          │
        └──────────────────────────────────────┘
```

## Timeline View: How Sizes Stabilize

```
Time:     0s        5s       10s       15s       20s       25s       30s
          │─────────│─────────│─────────│─────────│─────────│─────────│
Events    │░░░░░░░░░│░░░░░░░░░│░░░░░░░░░│▓▓▓▓▓▓▓▓▓│▓▓▓▓▓▓▓▓▓│▓▓▓▓▓▓▓▓▓│
Buffer:   │ Growing │ Growing │ Growing │  Full   │  Full   │  Full   │
          │─────────│─────────│─────────│─────────│─────────│─────────│

Snapshot
Size:     100KB     200KB     300KB     400KB     400KB     400KB
          ▲         ▲         ▲         ▲         ▲         ▲
          Growing   Growing   Growing   Stable    Stable    Stable

Legend:
  ░ = Events being accumulated (buffer filling)
  ▓ = Full buffer (oldest discarded, newest added)
```

## Event Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        EVENT LIFECYCLE                              │
└─────────────────────────────────────────────────────────────────────┘

1. Application Activity
   │
   ├─► Method Call ──────► jdk.ExecutionSample (every 10ms)
   ├─► Object Allocation ► jdk.ObjectAllocationInNewTLAB
   ├─► File I/O ─────────► jdk.FileRead (if > threshold)
   ├─► GC Run ───────────► jdk.GarbageCollection
   └─► Thread Event ─────► jdk.ThreadStart/End

2. Event Filtering (apps/jfr-profiler-based-app/jfr-config.jfc)
   │
   ├─► Enabled? ────────► YES → Continue
   │                      NO  → Discard
   │
   ├─► Above Threshold? ► YES → Continue
   │                      NO  → Discard
   │
   └─► Sample Period? ──► YES → Continue
                          NO  → Skip

3. Event Storage
   │
   └─► Add to Circular Buffer
       │
       ├─► Buffer has space? ──► YES → Add event
       │                         NO  → Discard oldest, add newest
       │
       └─► Result: Always last 15 seconds

4. Snapshot Creation
   │
   └─► jcmd <pid> JFR.dump
       │
       └─► Write buffer contents to .jfr file
```

## Size Control Factors

```
┌─────────────────────────────────────────────────────────────────────┐
│              FACTORS AFFECTING SNAPSHOT SIZE                        │
└─────────────────────────────────────────────────────────────────────┘

PRIMARY (90% of impact):
╔═══════════════════════════════════════════════════════════════════╗
║ maxage=15s                                                        ║
║ ───────────                                                       ║
║ Controls how many seconds of events to keep                       ║
║                                                                   ║
║ 5s  → ~150-800 KB    [Minimal history]                          ║
║ 15s → ~400 KB-3 MB   [Good balance] ⭐                           ║
║ 30s → ~1-6 MB        [More history]                              ║
║ 60s → ~2-12 MB       [Extended history]                          ║
╚═══════════════════════════════════════════════════════════════════╝

SECONDARY (10% of impact):
┌───────────────────────────────────────────────────────────────────┐
│ JFR Configuration (apps/jfr-profiler-based-app/jfr-config.jfc)                               │
├───────────────────────────────────────────────────────────────────┤
│                                                                   │
│ Execution Sampling Period:                                        │
│   10ms → More samples  → Larger files                            │
│   20ms → Fewer samples → Smaller files (-30%)                    │
│   50ms → Even fewer    → Much smaller (-60%)                     │
│                                                                   │
│ Event Thresholds:                                                 │
│   10ms → Catch most events → Larger files                        │
│   50ms → Only slow events  → Smaller files                       │
│                                                                   │
│ Enabled Events:                                                   │
│   All events       → Maximum detail → Larger files               │
│   Essential only   → Core profiling → Smaller files              │
│   No allocations   → Reduced detail → Much smaller               │
└───────────────────────────────────────────────────────────────────┘

APPLICATION ACTIVITY (Variable):
┌───────────────────────────────────────────────────────────────────┐
│ What the application is doing                                     │
├───────────────────────────────────────────────────────────────────┤
│ Idle        → Minimal events    → Small snapshots                │
│ Light load  → Some events       → Medium snapshots               │
│ Heavy load  → Maximum events    → Large snapshots                │
└───────────────────────────────────────────────────────────────────┘
```

## Comparison: With vs Without maxage

```
WITHOUT maxage parameter:
─────────────────────────

Time:   0s ────► 1min ────► 5min ────► 1hour ────► 8hours
Size:   500KB    30 MB      150 MB     1.8 GB      14.4 GB
        │        │          │          │           │
        └────────┴──────────┴──────────┴───────────┘
                    Unbounded Growth! 💥


WITH maxage=15s:
────────────────

Time:   0s ────► 15s ────► 1min ────► 1hour ────► 8hours
Size:   100KB    1.2 MB    1.2 MB     1.2 MB      1.2 MB
        │        │         │          │           │
        └────────┴─────────┴──────────┴───────────┘
              Stable!  (only last 15s) ✅
```

## Real-World Size Examples

```
┌─────────────────────────────────────────────────────────────────────┐
│                    TYPICAL SNAPSHOT SIZES                           │
│                      (maxage=15s)                                   │
└─────────────────────────────────────────────────────────────────────┘

┌──────────────────┬──────────────┬─────────────────────────────────┐
│  Scenario        │  Size Range  │  What's Being Captured          │
├──────────────────┼──────────────┼─────────────────────────────────┤
│  Idle App        │  200-500 KB  │  • JVM housekeeping             │
│                  │              │  • Periodic stats (CPU, memory) │
│                  │              │  • Minimal method samples       │
├──────────────────┼──────────────┼─────────────────────────────────┤
│  Light Load      │  500KB-1MB   │  • REST endpoint calls          │
│  (10 req/s)      │              │  • JSON serialization           │
│                  │              │  • Spring MVC processing        │
│                  │              │  • Moderate allocations         │
├──────────────────┼──────────────┼─────────────────────────────────┤
│  Heavy Load      │  1-3 MB      │  • Continuous computation       │
│  (continuous)    │              │  • High allocation rate         │
│                  │              │  • Many method samples          │
│                  │              │  • Frequent GC events           │
├──────────────────┼──────────────┼─────────────────────────────────┤
│  Spike Load      │  2-5 MB      │  • Burst of activity            │
│  (burst pattern) │              │  • Exception handling           │
│                  │              │  • Lock contention              │
│                  │              │  • I/O operations               │
└──────────────────┴──────────────┴─────────────────────────────────┘
```

## The 15-Second Window Explained

```
Current Time: 10:30:45

Circular Buffer Contents:
┌────────────────────────────────────────────────────────────────┐
│ Events from: 10:30:30 ──────────────────────► 10:30:45       │
│              (15 seconds ago)         (now)                   │
│                                                                │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │ Oldest Event          ←───── Events ─────►   Newest Event│ │
│ │ [10:30:30.123]                              [10:30:45.789]│ │
│ └──────────────────────────────────────────────────────────┘ │
│                                                                │
│ Events older than 10:30:30 have been automatically discarded  │
└────────────────────────────────────────────────────────────────┘

When you create a snapshot at 10:30:45:
  → Captures events from 10:30:30 to 10:30:45
  → Exactly 15 seconds of profiling data
  → Size depends on activity during those 15 seconds
```

## Decision Tree: Optimizing Snapshot Size

```
                  Start Here
                      │
                      ▼
        Are snapshots too large? (>5 MB)
                      │
          ┌───────────┴───────────┐
          │ YES                   │ NO
          ▼                       ▼
    Reduce maxage?          Current size OK!
       15s → 10s            Keep monitoring
          │
          ▼
    Still too large?
          │
          ├─► YES: Adjust JFR config
          │         • Sampling: 10ms→20ms
          │         • Thresholds: 10ms→50ms
          │         • Disable allocations
          │
          └─► NO: Done! Monitor stability

Missing important data?
          │
          ├─► YES: Reduce less aggressively
          │         • Keep allocations
          │         • Lower thresholds
          │         • Increase maxage
          │
          └─► NO: Configuration optimal!
```

This visual guide helps understand how JFR's circular buffer and maxage parameter work together to control snapshot sizes while maintaining profiling insights.
