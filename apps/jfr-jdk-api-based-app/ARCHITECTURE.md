# JFR Stream Application Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   JFR Stream Application                         │
│                        (Port 8081)                               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      Application Startup                         │
│                                                                  │
│  @SpringBootApplication                                          │
│  @EnableScheduling                                               │
│           │                                                      │
│           ├─► JfrStreamService (@PostConstruct)                 │
│           │                                                      │
│           └─► JfrController (REST endpoints)                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    JfrStreamService                              │
│                                                                  │
│  ┌──────────────────────────────────────────────────┐           │
│  │  Continuous Recording                             │           │
│  │  ┌────────────────────────────────────────────┐  │           │
│  │  │  Recording (jdk.jfr.Recording)             │  │           │
│  │  │  • State: RUNNING                          │  │           │
│  │  │  • MaxAge: 15 seconds (rolling buffer)    │  │           │
│  │  │  • ToDisk: true                            │  │           │
│  │  └────────────────────────────────────────────┘  │           │
│  │                                                   │           │
│  │  Enabled Events:                                 │           │
│  │  • jdk.ExecutionSample (every 10ms)             │           │
│  │  • jdk.CPULoad (every 1s)                       │           │
│  │  • jdk.JavaThreadStatistics (every 1s)          │           │
│  │  • jdk.Garbage/Young/OldGarbageCollection (0ms) │           │
│  │  • jdk.ObjectAllocationIn/OutsideTLAB (stack)   │           │
│  │  • jdk.ThreadStart (stack) / ThreadEnd          │           │
│  │  • jdk.JavaException/JavaErrorThrow (stack)     │           │
│  │  • jdk.File/SocketRead/Write (>10ms, stack)     │           │
│  │  • jdk.ClassLoad (stack)                        │           │
│  │  • jdk.Compilation (>100ms)                     │           │
│  │  • jdk.JavaMonitorEnter (>10ms, stack)          │           │
│  │  • jdk.ThreadPark (>10ms, stack)                │           │
│  │  • jdk.InitialEnvironmentVariable               │           │
│  └──────────────────────────────────────────────────┘           │
│                                                                  │
│  ┌──────────────────────────────────────────────────┐           │
│  │  Scheduled Export Task                            │           │
│  │  (@Scheduled - every 15 seconds)                 │           │
│  │                                                   │           │
│  │  1. recording.copy(exportFile)                   │           │
│  │     ↓                                             │           │
│  │  2. Create snapshot (non-blocking)               │           │
│  │     ↓                                             │           │
│  │  3. Count events in snapshot                     │           │
│  │     ↓                                             │           │
│  │  4. Log statistics                               │           │
│  │     ↓                                             │           │
│  │  5. Recording continues capturing new events     │           │
│  └──────────────────────────────────────────────────┘           │
│                                                                  │
│  Statistics:                                                     │
│  • exportCounter (AtomicLong)                                   │
│  • eventCounter (AtomicLong)                                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      File System                                 │
│                                                                  │
│  ./jfr-exports/                                                  │
│    ├── jfr-export-20261116-143025-0001.jfr (3,214 events)      │
│    ├── jfr-export-20261116-143040-0002.jfr (3,189 events)      │
│    ├── jfr-export-20261116-143055-0003.jfr (3,201 events)      │
│    └── ...                                                       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      REST API                                    │
│                                                                  │
│  GET /api/jfr/status                                            │
│    Returns:                                                      │
│    {                                                             │
│      "recordingState": "RUNNING",                               │
│      "totalExports": 5,                                         │
│      "totalEventsProcessed": 15234,                             │
│      "message": "JFR continuous streaming is active"            │
│    }                                                             │
│                                                                  │
│  GET /api/jfr/health                                            │
│    Returns:                                                      │
│    {                                                             │
│      "status": "UP",                                            │
│      "service": "JFR Stream Service"                            │
│    }                                                             │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                   Timeline Example                               │
│                                                                  │
│  T=0s    Application starts                                     │
│          └─► JFR recording starts                               │
│                                                                  │
│  T=0-30s Events buffered in memory (rolling 15s window)         │
│                                                                  │
│  T=30s   First export triggered                                 │
│          └─► jfr-export-...-0001.jfr created                   │
│          └─► Recording continues (non-blocking)                 │
│                                                                  │
│  T=30-45s More events captured                                  │
│                                                                  │
│  T=45s   Second export triggered                                │
│          └─► jfr-export-...-0002.jfr created                   │
│          └─► Old events (>15s) dropped from buffer              │
│          └─► Recording continues                                │
│                                                                  │
│  T=60s   Third export triggered                                 │
│          └─► jfr-export-...-0003.jfr created                   │
│          └─► Recording continues                                │
│                                                                  │
│  ...     Process repeats every 15 seconds                       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                Key Implementation Points                         │
│                                                                  │
│  1. Non-blocking snapshots:                                     │
│     recording.copy() creates snapshot while recording continues │
│                                                                  │
│  2. Rolling buffer:                                             │
│     maxAge=15s keeps only recent events in memory               │
│                                                                  │
│  3. Scheduled exports:                                          │
│     Spring @Scheduled runs export task every 15s                │
│                                                                  │
│  4. Event counting:                                             │
│     RecordingFile.readEvent() processes each event              │
│                                                                  │
│  5. Graceful shutdown:                                          │
│     @PreDestroy stops recording and logs final stats            │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow

```
┌──────────────┐
│  JVM Events  │ (CPU, GC, Threads, Monitors, etc.)
└──────┬───────┘
       │
       ▼
┌──────────────────────────────┐
│  JFR Recording Buffer        │
│  (Rolling 15-second window)  │
└──────┬───────────────────────┘
       │
       │  Every 15 seconds
       │
       ▼
┌──────────────────────────────┐
│  recording.copy()            │
│  (Create snapshot)           │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│  .jfr File on Disk           │
│  (Timestamped snapshot)      │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│  Analysis Tools              │
│  • JDK Mission Control       │
│  • jfr command-line          │
│  • Custom processing         │
└──────────────────────────────┘
```
