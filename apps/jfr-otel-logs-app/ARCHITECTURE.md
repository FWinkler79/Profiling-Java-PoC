# Architecture: JFR OpenTelemetry Logs Application

## Overview

This document describes the architecture of the JFR OpenTelemetry Logs application, which continuously records Java Flight Recorder (JFR) events and exports them to OpenTelemetry Protocol (OTLP) backends.

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                       │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              JFR Recording (Continuous)                   │  │
│  │  - ExecutionSample (10ms)                                 │  │
│  │  - CPULoad, ThreadStats                                   │  │
│  │  - GC Events                                              │  │
│  │  - Memory Allocation                                      │  │
│  │  - I/O Events, Exceptions                                 │  │
│  │  - 15-second rolling buffer                               │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│                   │ Every 15 seconds                             │
│                   ↓                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │          JfrOtelExportService (Scheduled)                 │  │
│  │                                                            │  │
│  │  1. recording.dump(tempFile)  → Snapshot to temp         │  │
│  │  2. RecordingFile.read()      → Parse JFR events         │  │
│  │  3. Convert to OTLP Logs      → Structured attributes    │  │
│  │  4. loggerProvider.emit()     → Send to exporter         │  │
│  │  5. Delete temp file          → Clean up                 │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│                   ↓                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         OpenTelemetry SDK Components                      │  │
│  │                                                            │  │
│  │  ┌────────────────────────────────────┐                  │  │
│  │  │    SdkLoggerProvider                │                  │  │
│  │  │  - Resource attributes              │                  │  │
│  │  │  - Instrumentation scope            │                  │  │
│  │  └──────────────┬─────────────────────┘                  │  │
│  │                 │                                          │  │
│  │                 ↓                                          │  │
│  │  ┌────────────────────────────────────┐                  │  │
│  │  │  BatchLogRecordProcessor            │                  │  │
│  │  │  - Batches events for efficiency    │                  │  │
│  │  │  - 5-second schedule delay          │                  │  │
│  │  └──────────────┬─────────────────────┘                  │  │
│  │                 │                                          │  │
│  │                 ↓                                          │  │
│  │  ┌────────────────────────────────────┐                  │  │
│  │  │  OtlpGrpcLogRecordExporter          │                  │  │
│  │  │  - gRPC protocol                    │                  │  │
│  │  │  - 30-second timeout                │                  │  │
│  │  │  - Configurable endpoint            │                  │  │
│  │  └──────────────┬─────────────────────┘                  │  │
│  └─────────────────┼─────────────────────────────────────────┘  │
└────────────────────┼────────────────────────────────────────────┘
                     │
                     │ gRPC (OTLP)
                     │ Port 4317
                     ↓
       ┌──────────────────────────────┐
       │    OTLP Backend               │
       │  - OpenTelemetry Collector    │
       │  - Elastic DevFiler           │
       │  - Jaeger                     │
       │  - Grafana Tempo              │
       │  - Custom OTLP receiver       │
       └──────────────────────────────┘
```

## Component Details

### 1. JFR Recording Layer

**Purpose:** Continuously capture JVM runtime events with minimal overhead.

**Configuration:**
- `maxAge`: 15 seconds - only keeps recent events
- `toDisk`: true - buffers to disk for reliability
- Event types: Aligned with `apps/jfr-profiler-based-app/jfr-config.jfc`

**Key Events Monitored:**
```java
jdk.ExecutionSample        // Method profiling (10ms sampling)
jdk.CPULoad                // CPU usage (1s period)
jdk.JavaThreadStatistics   // Thread metrics (1s period)
jdk.GarbageCollection      // All GC events
jdk.ObjectAllocation*      // Memory allocations
jdk.JavaExceptionThrow     // Exception tracking
jdk.FileRead/Write         // I/O operations
jdk.SocketRead/Write       // Network I/O
jdk.ClassLoad              // Class loading
jdk.Compilation            // JIT compilation
jdk.JavaMonitorEnter       // Lock contention
jdk.ThreadPark             // Thread parking
```

### 2. Export Service Layer

**Class:** `JfrOtelExportService`

**Scheduling:**
- Initial delay: 30 seconds (let JFR warm up)
- Fixed delay: 15 seconds between exports
- Spring `@Scheduled` annotation

**Export Process:**

```java
1. Create temporary file
2. recording.dump(tempFile)         // Non-blocking snapshot
3. Parse with RecordingFile
4. For each JFR event:
   a. Extract event type and metadata
   b. Build OTLP log attributes
   c. Add thread information
   d. Add stack trace (if present)
   e. Extract type-specific fields
   f. Emit as log record
5. Force flush to ensure delivery
6. Delete temporary file
```

**Event to OTLP Mapping:**

| JFR Field | OTLP Attribute | Type |
|-----------|---------------|------|
| Event type | `jfr.event.type` | String |
| Duration | `jfr.event.duration` | Long (nanos) |
| Thread name | `thread.name` | String |
| Thread ID | `thread.id` | Long |
| Top frame method | `code.function` | String |
| Top frame class | `code.namespace` | String |
| Stack depth | `stack.depth` | Integer |
| CPU metrics | `cpu.jvm.user`, `cpu.jvm.system` | Float |
| GC name | `gc.name` | String |
| Exception type | `exception.type` | String |
| Exception message | `exception.message` | String |

### 3. OpenTelemetry Layer

**Resource Attributes:**
```java
service.name              = jfr-otel-app (configurable)
service.version           = 1.0.0 (configurable)
telemetry.sdk.name        = jfr-otel-exporter
telemetry.sdk.language    = java
```

**Batching Strategy:**
- `BatchLogRecordProcessor` batches log records
- 5-second schedule delay
- Reduces network overhead
- Improves throughput

**Export Configuration:**
- Protocol: gRPC
- Endpoint: Configurable via `jfr.otlp.endpoint`
- Timeout: 30 seconds per export attempt
- Retry: Handled by OpenTelemetry SDK

### 4. REST API Layer

**Controller:** `JfrOtelController`

**Endpoints:**

```
GET /api/jfr/status
{
  "recording_state": "RUNNING",
  "otlp_endpoint": "http://localhost:4317",
  "total_exports": 42,
  "successful_exports": 40,
  "failed_exports": 2,
  "total_events_exported": 123456
}

GET /api/jfr/health
{
  "status": "UP",
  "service": "JFR OTLP Export Service"
}
```

## Configuration

All settings in `application.yaml`:

```yaml
server:
  port: 8080

jfr:
  otlp:
    endpoint: http://localhost:4317
  export:
    interval:
      seconds: 15
  recording:
    max-age:
      seconds: 15
  instrumentation:
    scope:
      name: jfr-otel-app
      version: 1.0.0
```

## Data Flow Sequence

```
Time: T
  ↓
[JFR Recording Active - Buffering last 15 seconds]
  ↓
Time: T+30 (first export)
  ↓
[Create /tmp/jfr-snapshot-xxx.jfr]
  ↓
[Dump current buffer to file]
  ↓
[Parse JFR events]
  ↓
[Convert to OTLP logs with attributes]
  ↓
[Batch processor queues records]
  ↓
Time: T+35 (batch delay)
  ↓
[gRPC export to OTLP backend]
  ↓
[Delete temp file]
  ↓
Time: T+45 (next export cycle)
  ↓
[Repeat...]
```

## Performance Characteristics

### Overhead

**JFR Recording:**
- CPU: <2% (JFR is production-grade)
- Memory: ~15 seconds of buffered events
- Disk: Temporary buffer files

**Export Process:**
- Duration: 200-500ms per export
- Network: 100KB-1MB per export (varies by activity)
- I/O: Temporary file creation/deletion

**Overall Impact:**
- Export occurs every 15 seconds
- Non-blocking: JFR continues recording during export
- Batched network I/O reduces overhead

### Scalability

**Event Volume:**
- Typical: 1,000-10,000 events per export
- High load: 50,000+ events per export
- Parser can handle 100K+ events/second

**Network:**
- gRPC with compression
- Batch processing reduces request count
- Configurable timeout handles slow backends

## Error Handling

### Export Failures

```java
try {
    // Export process
    successfulExports.increment();
} catch (Exception e) {
    failedExports.increment();
    logger.error("Export failed", e);
    // Continue recording - don't stop JFR
}
```

**Recovery:**
- Failed exports don't stop JFR recording
- Next export attempt continues from current buffer
- Lost events are not retried (real-time streaming model)

### Shutdown

```java
@PreDestroy
1. Stop JFR recording if running
2. Close recording resources
3. Flush pending OTLP batches
4. Shutdown logger provider
5. Close exporter
```

## Comparison with Alternatives

### vs. JFR File-Based Export

| Aspect | This App (OTLP) | File-Based |
|--------|----------------|------------|
| Storage | None (streaming) | Local files |
| Analysis | Real-time backend | Post-processing |
| Integration | Direct OTLP | Manual transfer |
| Overhead | Network + export | Disk I/O only |

### vs. Native Profile Signal (Future)

Current implementation uses **Logs API** because the Profile Signal extension
is not yet released to Maven Central.

**When Profile Signal is available:**
- Replace `OtlpGrpcLogRecordExporter` with `OtlpGrpcProfileExporter`
- Use `JfrExecutionSampleEventConverter` directly
- Export as `ProfileData` instead of `LogRecordData`
- Same network protocol (gRPC), better semantic fit

## Deployment Considerations

### Backend Requirements

**OTLP Receiver must support:**
- gRPC protocol
- OTLP logs format
- Port 4317 (default) or custom port

**Backend Options:**
- OpenTelemetry Collector (with OTLP receiver)
- Elastic DevFiler (standalone profile receiver)
- Jaeger with OTLP support
- Grafana Tempo
- Any OTLP-compatible APM

### Network

**Firewall Rules:**
- Outbound gRPC to OTLP endpoint
- Port 4317 (or configured port)

**Bandwidth:**
- ~100KB-1MB per 15 seconds
- ~4-16KB/second average
- Spikes during high activity

### Monitoring

**Metrics to Track:**
- `successful_exports` / `failed_exports` ratio
- `total_events_exported` growth rate
- Export duration (logged)
- Backend ingestion lag

## Security

### Data Sensitivity

JFR events may contain:
- Stack traces (method and class names)
- Thread names
- File paths
- Exception messages

**Considerations:**
- Review data before routing to external backends
- Consider filtering sensitive events
- Use secure transport (TLS) for OTLP

### Network Security

**Recommended:**
- mTLS for gRPC connection
- Backend authentication
- Network isolation

**Configuration:**
```java
// Add to OtlpGrpcLogRecordExporter.builder()
.setTrustedCertificates(cert)
.addHeader("Authorization", "Bearer " + token)
```

## Troubleshooting

### No Events Exported

1. Check recording state: `/api/jfr/status`
2. Verify OTLP endpoint connectivity
3. Review application logs for errors
4. Ensure 30-second initial delay has passed

### High Failed Exports

1. Check backend availability
2. Verify endpoint URL format
3. Review network connectivity
4. Check backend resource limits

### High Event Volume

1. Reduce `maxAge` to < 15 seconds
2. Disable non-critical event types
3. Increase export interval
4. Scale backend resources

## Future Enhancements

1. **Profile Signal Support**
   - Use native Profile API when available
   - Better semantic fit for profiling data

2. **Event Filtering**
   - Configuration to exclude event types
   - Sampling strategies for high-volume events

3. **Metrics Export**
   - Add OpenTelemetry metrics for export statistics
   - Histogram of event counts by type

4. **Health Checks**
   - Ping OTLP endpoint on startup
   - Report backend connectivity in health endpoint

5. **Adaptive Intervals**
   - Adjust export frequency based on activity
   - Faster exports during high load

## References

- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/otel/)
- [JFR Documentation](https://docs.oracle.com/en/java/javase/17/jfapi/)
- [OTLP Specification](https://github.com/open-telemetry/opentelemetry-proto)
