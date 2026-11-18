# Design: Export raw JFR recording bytes as a single OTel log body

Date: 2026-11-18
App: `apps/jfr-file-upload-stream-app`

## Intent & why

Today the app records JFR continuously, dumps a 15-second slice every 15
seconds, then **iterates every event** in that slice and emits one OTel log
record per `RecordedEvent` (body = event-type name, event fields → attributes).

We want to stop decomposing the slice into per-event logs. Instead, send the
**raw JFR recording** — the exact bytes that would land in a `.jfr` file — as
the **body of a single OTel log record** per slice. The consumer receives an
opaque `.jfr` blob it can write to disk and open in JDK Mission Control,
rather than hundreds of flattened event logs.

## Goals

- Keep the 15-second slicing exactly as-is (same continuous `Recording`, same
  `maxAge`, same `recording.enable(...)` set, same scheduled `dump()`); each
  slice stays ~150 KB of JFR data.
- Emit **one** OTel log record per slice whose body is the raw JFR bytes.
- Body must be **real bytes** on the wire (OTLP `bytes_value`), not base64.
- Upgrade the OpenTelemetry Java SDK to **1.68.0**, done properly via the
  OTel BOM.

## Non-goals

- No change to which JFR event types are recorded or to the recording cadence.
- No change to the transport (still OTLP/gRPC logs to the same endpoint, same
  mTLS collector setup).
- Not adopting the OTel Profiles signal — still using the Logs signal as the
  carrier.

## Anti-goals (would make this a failure)

- Base64-encoding the recording into a string body.
- Inflating or re-encoding the JFR bytes in any way — the body must be the
  byte-for-byte content of the dumped `.jfr` file.
- Breaking the compile of the controller / `/status` endpoint.

## Constraints / verified facts

- OTel 1.68.0 `io.opentelemetry.api.common.Value` provides `Value.of(byte[])`
  (`ValueType.BYTES`), and `LogRecordBuilder.setBody(Value)` exists alongside
  `setBody(String)`. The javadoc explicitly notes `Value` is currently used
  only as an argument to `setBody`.
- The 1.68.0 OTLP common marshaler maps `ValueType.BYTES` →
  `BytesAnyValueMarshaler` → protobuf `AnyValue.bytes_value`. Confirmed in
  `AnyValueMarshaler` / `BytesAnyValueMarshaler` at tag `v1.68.0`. So
  `setBody(Value.of(bytes))` yields real bytes end-to-end.
- `opentelemetry-bom:1.68.0`, `opentelemetry-sdk-logs:1.68.0`, and
  `opentelemetry-exporter-otlp:1.68.0` all exist on Maven Central.

## Approaches considered

- **A — `setBody(Value.of(byte[]))` on the stock Logs SDK (chosen).** Upgrade to
  1.68.0, read the dumped slice with `Files.readAllBytes`, emit one log whose
  body is `Value.of(bytes)`. Minimal, uses the exact API designed for this.
- **B — base64 string body.** Rejected: not real bytes; ~33% inflation.
- **C — hand-rolled lower-level OTLP encoding of a bytes body.** Rejected:
  unnecessary now that `setBody(Value)` exists; would keep the `protobuf-java`
  dependency and custom encoding we can otherwise delete.

## The how (decided)

### pom.xml
- Remove the `opentelemetry.version` property.
- Add `<dependencyManagement>` importing `io.opentelemetry:opentelemetry-bom:1.68.0`
  (`type=pom`, `scope=import`).
- Keep `opentelemetry-api`, `opentelemetry-sdk`, `opentelemetry-exporter-otlp`,
  `opentelemetry-sdk-logs` but **without** explicit `<version>` (managed by BOM).
- Remove the `com.google.protobuf:protobuf-java` dependency (no longer used —
  nothing is hand-encoded).

### JfrUploadStreamExportService.java
- **Unchanged:** `@PostConstruct` recording setup, exporter/`SdkLoggerProvider`
  wiring, all `recording.enable(...)` calls, `maxAge`, the `@Scheduled`
  `exportToOtlp()` dump-to-temp-file flow, temp-file cleanup, `@PreDestroy`.
- **Replace** `exportJfrEventsAsLogs(Path)` with
  `exportRecordingAsLog(Path)`:
  - `byte[] jfrBytes = Files.readAllBytes(jfrFilePath);`
  - Emit one record:
    `logger.logRecordBuilder()
        .setTimestamp(Instant.now())
        .setSeverity(Severity.INFO)
        .setBody(Value.of(jfrBytes))
        .setAllAttributes(attrs).emit();`
  - Attributes: `jfr.recording.format` = `"jfr"`,
    `jfr.recording.size_bytes` = `jfrBytes.length`,
    `jfr.slice.duration_seconds` = `maxAgeSeconds`.
  - Return `jfrBytes.length` (bytes exported for this slice).
- Remove imports/uses of `RecordingFile`, `RecordedEvent`, the per-event
  `switch`, and `HashMap`/`Map` event-type tally.
- **Counters:** rename the `totalEventsExported` counter to
  `totalBytesExported`; `exportToOtlp()` adds the returned byte count to it.
  Keep `exportCounter` (slices), `successfulExports`, `failedExports`.
- Replace `getTotalEventsExported()` with `getTotalBytesExported()`.
- Import `io.opentelemetry.api.common.Value`.

### JfrUploadStreamController.java
- `/status`: replace the `total_events_exported` key with
  `total_bytes_exported`, backed by `getTotalBytesExported()`.
  (Decision (a): rename the JSON key.)

## Left to the builder

- Exact attribute key spelling may follow existing conventions in the file.
- Log-line wording in `exportToOtlp()` ("exported N bytes (slice #k)").

## Verification

- `mvn clean package` builds (resolves 1.68.0 via BOM, compiles the rewritten
  service + controller).
- Optional runtime check against the local collector: one log record per
  ~15s with a `bytes_value` body of ~150 KB and no per-event logs.
