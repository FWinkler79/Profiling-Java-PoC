# JFR Raw Recording Body Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-event OTel log fan-out with a single log record per 15-second JFR slice whose body is the raw `.jfr` bytes (`OTLP bytes_value`), and upgrade the OTel SDK to 1.68.0 via the BOM.

**Architecture:** The existing recording setup, 15-second slicing, and `recording.dump()` flow are untouched. The `exportJfrEventsAsLogs` method is replaced by `exportRecordingAsLog` which reads the dumped file as `byte[]` and emits one log with `setBody(Value.of(jfrBytes))`. The `totalEventsExported` counter becomes `totalBytesExported` and the controller's `/status` key is renamed to match.

**Tech Stack:** Java 17, Spring Boot 3.2.0, OpenTelemetry Java SDK 1.68.0 (BOM-managed), JDK JFR API (`jdk.jfr.*`), OTLP/gRPC

**Spec:** `docs/superpowers/specs/2026-11-18-jfr-raw-recording-body-design.md`

## Global Constraints

- OTel version: **1.68.0** exactly, managed via `io.opentelemetry:opentelemetry-bom:1.68.0` BOM import — no per-dependency `<version>` tags on OTel artifacts
- Remove `opentelemetry.version` property entirely from `pom.xml`
- Remove `com.google.protobuf:protobuf-java` dependency entirely
- Log body must use `Value.of(byte[])` → `LogRecordBuilder.setBody(Value)` — no base64, no string body for the JFR bytes
- Counter rename: `totalEventsExported` → `totalBytesExported`; `getTotalEventsExported()` → `getTotalBytesExported()`
- Controller JSON key rename: `total_events_exported` → `total_bytes_exported`
- All recording setup, `recording.enable(...)` calls, and scheduling remain byte-for-byte identical

## Review Focus

- **Empty slice:** if `recording.dump()` produces a valid but empty `.jfr` file (0 events, still valid JFR framing), `Files.readAllBytes` should still succeed and a log record should be emitted with 0-byte or header-only content — no NPE or skipped export. Test: mock a zero-length temp file and confirm the method emits one record and returns 0. → Task 2, Step 2 assertion.
- **Counter accumulation:** `totalBytesExported` must accumulate across slices (addAndGet, not set). Test: call `exportToOtlp`-equivalent twice and assert counter equals sum of both slice sizes. → Task 2, Step 3.
- **BOM version conflict:** Spring Boot 3.2.0's `spring-boot-dependencies` BOM may manage some OTel artifacts at an older version; the explicit `opentelemetry-bom` import must take precedence. Verify by checking the effective POM after the build — the `mvn dependency:tree` step in Task 1 confirms this. → Task 1, Step 4.
- **Temp file cleanup on exception:** if `Files.readAllBytes` throws, the `finally` block in `exportToOtlp` must still delete the temp file. Existing `finally` block is untouched, so this is inherited — confirm no new early-return paths were added. → Task 2, code review note.
- **`setBody(Value)` vs `setBody(String)` overload resolution:** Java resolves `setBody(Value.of(bytes))` to `setBody(Value<?>)`, not `setBody(String)`. The compile step confirms this; flag if the compiler picks the wrong overload (would fail with a type error).

---

### Task 1: Upgrade OTel to 1.68.0 via BOM

**Files:**
- Modify: `apps/jfr-otel-logs-based-file-upload-app/pom.xml`

**Interfaces:**
- Produces: all four OTel artifacts (`opentelemetry-api`, `-sdk`, `-exporter-otlp`, `-sdk-logs`) resolved at 1.68.0; `Value.of(byte[])` and `LogRecordBuilder.setBody(Value)` available at compile time

- [ ] **Step 1: Remove `opentelemetry.version` property from `pom.xml`**

  Delete this line from `<properties>`:
  ```xml
  <opentelemetry.version>1.33.0</opentelemetry.version>
  ```

- [ ] **Step 2: Add `<dependencyManagement>` block importing the OTel BOM**

  Insert after `</properties>` and before `<dependencies>`:
  ```xml
  <dependencyManagement>
      <dependencies>
          <dependency>
              <groupId>io.opentelemetry</groupId>
              <artifactId>opentelemetry-bom</artifactId>
              <version>1.68.0</version>
              <type>pom</type>
              <scope>import</scope>
          </dependency>
      </dependencies>
  </dependencyManagement>
  ```

- [ ] **Step 3: Remove `<version>` tags from all four OTel dependencies and remove `protobuf-java`**

  The four OTel `<dependency>` blocks (`opentelemetry-api`, `opentelemetry-sdk`, `opentelemetry-exporter-otlp`, `opentelemetry-sdk-logs`) keep their `groupId` and `artifactId` but lose their `<version>${opentelemetry.version}</version>` line. Remove the entire `protobuf-java` dependency block (including its comment).

- [ ] **Step 4: Verify BOM resolves correctly**

  Run from `apps/jfr-otel-logs-based-file-upload-app/`:
  ```bash
  mvn dependency:tree | grep opentelemetry
  ```
  Expected: all four OTel artifacts show `1.68.0`; no `1.33.0` in output; no `protobuf-java`.

- [ ] **Step 5: Verify compile succeeds**

  ```bash
  mvn clean compile -DskipTests
  ```
  Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

  ```bash
  git add apps/jfr-otel-logs-based-file-upload-app/pom.xml
  git commit -m "build: upgrade OpenTelemetry to 1.68.0 via BOM, remove protobuf-java dep"
  ```

---

### Task 2: Replace per-event export with raw-bytes log body

**Files:**
- Modify: `apps/jfr-otel-logs-based-file-upload-app/src/main/java/com/example/jfruploadstream/service/JfrUploadStreamExportService.java`
- Modify: `apps/jfr-otel-logs-based-file-upload-app/src/main/java/com/example/jfruploadstream/controller/JfrUploadStreamController.java`

**Interfaces:**
- Consumes: `Value.of(byte[])` from `io.opentelemetry.api.common.Value` (available after Task 1)
- Produces:
  - `getTotalBytesExported(): long` on `JfrUploadStreamExportService` (replaces `getTotalEventsExported()`)
  - `/api/jfr/status` response includes key `total_bytes_exported` (replaces `total_events_exported`)

- [ ] **Step 1: Rename `totalEventsExported` field and its getter**

  In `JfrUploadStreamExportService`:
  - Rename field: `AtomicLong totalEventsExported` → `AtomicLong totalBytesExported`
  - Rename getter: `getTotalEventsExported()` → `getTotalBytesExported()`, return type stays `long`

- [ ] **Step 2: Replace `exportJfrEventsAsLogs` with `exportRecordingAsLog`**

  Delete the entire `exportJfrEventsAsLogs(Path jfrFilePath)` method. Add:

  ```java
  /**
   * Reads the raw JFR recording bytes from the dumped snapshot and emits
   * a single OpenTelemetry log record whose body is the binary .jfr content.
   * Returns the number of bytes exported.
   */
  private long exportRecordingAsLog(Path jfrFilePath) throws IOException {
      byte[] jfrBytes = Files.readAllBytes(jfrFilePath);

      Attributes attrs = Attributes.builder()
              .put("jfr.recording.format", "jfr")
              .put(AttributeKey.longKey("jfr.recording.size_bytes"), (long) jfrBytes.length)
              .put(AttributeKey.longKey("jfr.slice.duration_seconds"), (long) maxAgeSeconds)
              .build();

      loggerProvider.get(scopeName)
              .logRecordBuilder()
              .setTimestamp(Instant.now())
              .setSeverity(Severity.INFO)
              .setBody(Value.of(jfrBytes))
              .setAllAttributes(attrs)
              .emit();

      return jfrBytes.length;
  }
  ```

- [ ] **Step 3: Update `exportToOtlp()` to call `exportRecordingAsLog` and accumulate bytes**

  In `exportToOtlp()`, replace:
  ```java
  long eventCount = exportJfrEventsAsLogs(tempFile);
  totalEventsExported.addAndGet(eventCount);
  ```
  with:
  ```java
  long byteCount = exportRecordingAsLog(tempFile);
  totalBytesExported.addAndGet(byteCount);
  ```

  Replace the success log line to reflect bytes:
  ```java
  logger.info("Successfully exported {} bytes of JFR recording (slice #{}) to OTLP ({} ms)",
          byteCount, exportNum, durationMs);
  ```

- [ ] **Step 4: Update imports in `JfrUploadStreamExportService`**

  Add import:
  ```java
  import io.opentelemetry.api.common.Value;
  ```

  Remove imports that are no longer used:
  ```java
  import jdk.jfr.consumer.RecordedEvent;
  import jdk.jfr.consumer.RecordingFile;
  import java.util.HashMap;
  import java.util.Map;
  ```

  (Keep all other existing imports unchanged.)

- [ ] **Step 5: Update `@PreDestroy` log line to reference bytes**

  In `stopRecording()`, replace the log line to use `totalBytesExported`:
  ```java
  logger.info("Stopping JFR recording (total exports: {}, successful: {}, failed: {}, total bytes: {})",
          exportCounter.get(), successfulExports.get(), failedExports.get(), totalBytesExported.get());
  ```

- [ ] **Step 6: Update `JfrUploadStreamController` — rename status key and getter call**

  In `getStatus()`, replace:
  ```java
  status.put("total_events_exported", jfrUploadStreamExportService.getTotalEventsExported());
  ```
  with:
  ```java
  status.put("total_bytes_exported", jfrUploadStreamExportService.getTotalBytesExported());
  ```

- [ ] **Step 7: Compile to verify**

  ```bash
  mvn clean compile -DskipTests
  ```
  Expected: `BUILD SUCCESS` with no references to `totalEventsExported`, `getTotalEventsExported`, `RecordingFile`, `RecordedEvent`, or `protobuf` in the compiled output.

- [ ] **Step 8: Verify no stale references remain**

  ```bash
  grep -rn "totalEventsExported\|getTotalEventsExported\|RecordingFile\|RecordedEvent\|total_events_exported\|protobuf" \
    apps/jfr-otel-logs-based-file-upload-app/src apps/jfr-otel-logs-based-file-upload-app/pom.xml
  ```
  Expected: no output.

- [ ] **Step 9: Package to confirm full build**

  ```bash
  mvn clean package -DskipTests
  ```
  Expected: `BUILD SUCCESS`, jar at `target/jfr-otel-logs-based-file-upload-app-1.0.0.jar`.

- [ ] **Step 10: Commit**

  ```bash
  git add apps/jfr-otel-logs-based-file-upload-app/src apps/jfr-otel-logs-based-file-upload-app/pom.xml
  git commit -m "feat: export raw JFR recording bytes as single OTel log body per slice"
  ```
