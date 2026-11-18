# JFR OTel Profiles App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `apps/jfr-otel-profiles-app`, a Spring Boot app that streams `jdk.ExecutionSample` JFR events and
exports them as the OTLP profiles signal, reproducing OpenTelemetry's `opentelemetry-jfr-profiles-shim`.

**Architecture:** A `RecordingStream` feeds `jdk.ExecutionSample` events live into a ported shim converter
(`ProfilesDictionaryCompositor` + `JfrLocationDataCompositor` + `SampleCompositionBuilder`). A scheduled task swaps in
a fresh converter each interval, assembles a `ProfileData` (with real per-export profileId/resource/scope/timing and a
raw-JFR-bytes payload from a per-interval `recording.dump()`), and exports it via `OtlpGrpcProfileExporter` to a
collector `profiles` pipeline (feature-gated), verified through the `debug` exporter.

**Tech Stack:** Java 17, Spring Boot 3.2.0, OpenTelemetry 1.68.0 + profiles 1.68.0-alpha, `jdk.jfr` / `jdk.jfr.consumer`,
Maven, Docker Compose, otel/opentelemetry-collector-contrib:0.165.0.

**Spec:** `docs/superpowers/specs/2026-11-18-jfr-otel-profiles-app-design.md`

## Global Constraints

- New app lives at `apps/jfr-otel-profiles-app`; **do not modify** `apps/jfr-stream-based-otel-app` or shared root config.
- Maven `artifactId: jfr-otel-profiles-app`, `version: 1.0.0`, `groupId: com.example`.
- Base Java package: `com.example.jfrotelprofiles` (distinct from the logs app's `com.example.jfrotel`).
- OTel core version: **1.68.0**. Profiles artifacts: **1.68.0-alpha** (`opentelemetry-sdk-profiles`,
  `opentelemetry-exporter-otlp-profiles`).
- Event scope: **`jdk.ExecutionSample` only**. No other JFR event type is recorded or mapped.
- Mapping semantics preserved from the shim: dictionary encoding; frame → Location(Line→Function→name) with
  `profile.frame.type = jvm`; stack → StackData; sample keyed by `(stack, [thread.name attr], 0)` with epoch-nanos
  timestamp; value type `(samples, count)`.
- Collector: profiles need a `profiles` pipeline AND `--feature-gates=+service.profilesSupport` (gate confirmed present
  in `collector-contrib:0.165.0`). **No SAP Cloud Logging exporter** in the profiles pipeline.
- Markdown docs must pass `markdownlint -c ~/.claude/markdownlint/.markdownlint.json` (MD013 line length 120).
- Verified `1.68.0-alpha` signatures (use verbatim):
  - `ProfilesDictionaryCompositor()`; `int putIfAbsent(String|KeyValueAndUnitData|FunctionData|LocationData|StackData)`;
    `ProfilesDictionaryData getProfileDictionaryData()`.
  - `JfrLocationDataCompositor` is ported (not in the jar).
  - `FunctionData.create(int,int,int,long)`, `LineData.create(int,long,long)`,
    `LocationData.create(int,long,List<LineData>,List<Integer>)`, `StackData.create(List<Integer>)`,
    `KeyValueAndUnitData.create(int,Value<?>,int)`, `ValueTypeData.create(int,int)`,
    `SampleData.create(int,List<Integer>,int,List<Long>,List<Long>)`.
  - `SampleCompositionKey(int,List<Integer>,int)`; `SampleCompositionBuilder.add(SampleCompositionKey,Long,Long)`;
    `List<SampleData> build()`.
  - `ProfileData.create(Resource, InstrumentationScopeInfo, ProfilesDictionaryData, ValueTypeData,
    List<SampleData> samples, long timeNanos, long durationNanos, ValueTypeData periodType, long period,
    String profileId, int droppedAttributesCount, String originalPayloadFormat, ByteBuffer originalPayload,
    List<Integer> attributeIndices)`.
  - `OtlpGrpcProfileExporter.builder()` → `OtlpGrpcProfilesExporterBuilder` with `setEndpoint(String)`,
    `setTimeout(Duration)`, `build()`; exporter has `CompletableResultCode export(Collection<ProfileData>)`,
    `flush()`, `shutdown()`.

## Review Focus

- **No `sampledThread` on an event** → the shim skips it; the converter must drop such events, not NPE. (Task 2)
- **Empty or null stack trace** on an ExecutionSample → must not crash; produce an empty/degenerate stack rather than
  throwing. (Task 2)
- **Flush interval with zero samples** → export must be skipped or send a well-formed empty profile, never throw or
  send a half-built one. (Task 4)
- **Concurrency: JFR callback thread writing while the scheduler swaps the converter** → the swap must be atomic so no
  sample is lost or read mid-mutation. (Task 4)
- **Frame with null method/type or line number -1** → name building and line handling must match the shim's
  null-guards and the `-1 → 0` line rule. (Task 2)

---

## Task 1: Scaffold the new app (copy, rename, repackage, build)

**Files:**
- Create (copy): entire `apps/jfr-stream-based-otel-app/` → `apps/jfr-otel-profiles-app/`
- Modify: `apps/jfr-otel-profiles-app/pom.xml` (artifactId, name, description, OTel version, profiles deps)
- Modify: move `src/main/java/com/example/jfrotel/**` → `src/main/java/com/example/jfrotelprofiles/**`, update
  `package`/imports, rename `JfrOtelApplication` → `JfrProfilesApplication`
- Modify: `apps/jfr-otel-profiles-app/src/main/resources/application.yaml`

**Interfaces:**
- Produces: a compiling Spring Boot app with the profiles dependencies on the classpath and the renamed package, but
  still carrying the old logs-based service (removed in Task 3).

- [ ] **Step 1: Copy the app directory** — `cp -R apps/jfr-stream-based-otel-app apps/jfr-otel-profiles-app`, then
  remove any copied `target/` and `docker/certs/`, `docker/.env`.
- [ ] **Step 2: Edit `pom.xml`** — set `artifactId` to `jfr-otel-profiles-app`, update `<name>`/`<description>` to the
  profiles app, set `opentelemetry.version` to `1.68.0`, add `opentelemetry-sdk-profiles` and
  `opentelemetry-exporter-otlp-profiles` at `1.68.0-alpha`, drop the standalone `protobuf-java` dependency. **Keep
  `opentelemetry-sdk-logs` for now** (the copied logs service still needs it; Task 4 removes both).
- [ ] **Step 3: Repackage** — move the three Java files from `com/example/jfrotel/` to `com/example/jfrotelprofiles/`,
  update every `package` and `import` line, rename the application class to `JfrProfilesApplication`.
- [ ] **Step 4: Trim `application.yaml`** — remove the `jfr.recording.max-age.seconds` property; leave
  `jfr.otlp.endpoint`, `jfr.export.interval.seconds`, `jfr.instrumentation.scope.*`, server port.
- [ ] **Step 5: Verify it compiles** — Run: `cd apps/jfr-otel-profiles-app && mvn -q -DskipTests compile`.
  Expected: BUILD SUCCESS. The copied logs service imports `opentelemetry-sdk-logs`, which was removed in Step 2, so
  it will not compile. Resolve by **keeping the `opentelemetry-sdk-logs` dependency temporarily** so the scaffold
  compiles as a faithful copy; Task 4 deletes the logs service, and its final step removes the now-unused logs
  dependency. (Do not partially gut the logs service here — that is Task 4's job.)
- [ ] **Step 6: Commit** — `git add apps/jfr-otel-profiles-app && git commit -m "scaffold jfr-otel-profiles-app from logs app"`.

---

## Task 2: Port the JFR → profiles converter classes

**Files:**
- Create: `src/main/java/com/example/jfrotelprofiles/jfr/JfrLocationDataCompositor.java`
- Create: `src/main/java/com/example/jfrotelprofiles/jfr/JfrExecutionSampleEventConverter.java`
- Test: `src/test/java/com/example/jfrotelprofiles/jfr/JfrExecutionSampleEventConverterTest.java`

**Interfaces:**
- Consumes: profiles SDK classes per Global Constraints.
- Produces:
  - `JfrLocationDataCompositor.create(ProfilesDictionaryCompositor) -> JfrLocationDataCompositor`;
    `int putIfAbsent(List<RecordedFrame>)`; `protected int frameToLocation(RecordedFrame)`;
    `protected String nameFrom(RecordedFrame)`.
  - `JfrExecutionSampleEventConverter.create() -> JfrExecutionSampleEventConverter`;
    `void accept(RecordedEvent)`; `ProfilesDictionaryCompositor getProfilesDictionaryCompositor()`;
    `List<SampleData> getSamples()`.

- [ ] **Step 1: Write failing tests** for the converter's documented skip/guard behavior. Because fabricating
  `RecordedEvent`/`RecordedFrame` is impractical, generate a real JFR file in the test: start a `Recording` with
  `jdk.ExecutionSample`, do CPU-busy work, dump to a temp file, read with `RecordingFile`, feed each event to
  `converter.accept(...)`. Assertions:
  - `test_samplesProducedFromRealRecording`: after feeding a recording with execution samples,
    `converter.getSamples()` is non-empty and `getProfilesDictionaryCompositor().getProfileDictionaryData()` has a
    non-empty string table.
  - `test_eventWithoutStackOrThreadIsSkippedGracefully`: feeding events of other types (or an event whose
    `sampledThread` is null, simulated by filtering) adds no samples and does not throw.
- [ ] **Step 2: Run tests, verify they fail** — Run: `mvn -q -Dtest=JfrExecutionSampleEventConverterTest test`.
  Expected: FAIL (classes/methods not defined).
- [ ] **Step 3: Implement `JfrLocationDataCompositor`** — port the shim class verbatim into the new package. Preserve
  the `-1 → 0` line rule, the `profile.frame.type = jvm` attribute, and the null-guarded `nameFrom`. Use the verified
  `create(...)` signatures (int literals auto-widen to the `long` params).
- [ ] **Step 4: Implement `JfrExecutionSampleEventConverter`** — port the shim class verbatim: filter to
  `jdk.ExecutionSample`, read `sampledThread`, skip on null thread, put `thread.name` KeyValueAndUnitData, encode the
  stack via the location compositor, build `SampleCompositionKey(stackIndex, List.of(attribIndex), 0)`, compute
  epoch-nanos, `sampleCompositionBuilder.add(key, null, epochNanos)`.
- [ ] **Step 5: Run tests, verify they pass** — Run: `mvn -q -Dtest=JfrExecutionSampleEventConverterTest test`.
  Expected: PASS.
- [ ] **Step 6: Commit** — `git add` the two classes + test; `commit -m "port JFR execution-sample profiles converter"`.

---

## Task 3: Profile assembly (identity, timing, payload)

**Files:**
- Create: `src/main/java/com/example/jfrotelprofiles/jfr/ProfileAssembler.java`
- Test: `src/test/java/com/example/jfrotelprofiles/jfr/ProfileAssemblerTest.java`

**Interfaces:**
- Consumes: `JfrExecutionSampleEventConverter` (Task 2).
- Produces: `ProfileAssembler.assemble(JfrExecutionSampleEventConverter converter, Resource resource,
  InstrumentationScopeInfo scope, long startEpochNanos, long durationNanos, long periodNanos, byte[] rawJfrBytes)
  -> ProfileData`. Generates a fresh random 32-hex-char `profileId` per call; sets value type `(samples, count)`;
  sets `timeNanos`/`durationNanos`/`period` from args; wraps `rawJfrBytes` as the `originalPayload` with format
  `"jfr"`; empty top-level attribute indices.

- [ ] **Step 1: Write failing tests:**
  - `test_assembleProducesProfileWithGeneratedId`: two calls yield two different, 32-char-hex profileIds.
  - `test_assembleCarriesTimingAndPayload`: returned `ProfileData` reports the start/duration/period passed in and an
    `originalPayload` with remaining bytes equal to the input length and format `"jfr"`.
  - `test_assembleUsesResourceAndScope`: the profile's resource/scope equal the ones passed in.
- [ ] **Step 2: Run tests, verify they fail** — Run: `mvn -q -Dtest=ProfileAssemblerTest test`. Expected: FAIL.
- [ ] **Step 3: Implement `ProfileAssembler.assemble(...)`** — adapt the shim's `convertJfrFile` tail: resolve
  `"samples"`/`"count"` string indices from the converter's dictionary, build `ValueTypeData.create(...)`, generate
  the random profileId (e.g. 16 random bytes hex-encoded), call the 14-arg `ProfileData.create(...)` with the verified
  argument order from Global Constraints.
- [ ] **Step 4: Run tests, verify they pass** — Run: `mvn -q -Dtest=ProfileAssemblerTest test`. Expected: PASS.
- [ ] **Step 5: Commit** — `git add` class + test; `commit -m "add profile assembler with real id/timing/payload"`.

---

## Task 4: Streaming export service

**Files:**
- Delete: `src/main/java/com/example/jfrotelprofiles/service/JfrOtelExportService.java` (the logs service)
- Create: `src/main/java/com/example/jfrotelprofiles/service/JfrProfilesExportService.java`
- Modify: `src/main/java/com/example/jfrotelprofiles/controller/JfrOtelController.java` → rename to
  `JfrProfilesController`, point at the new service, adjust stat names
- Test: `src/test/java/com/example/jfrotelprofiles/service/JfrProfilesExportServiceTest.java`

**Interfaces:**
- Consumes: `JfrExecutionSampleEventConverter`, `ProfileAssembler`, `OtlpGrpcProfileExporter`.
- Produces: a `@Service` with `@PostConstruct startStreaming()`, `@Scheduled exportInterval()`, `@PreDestroy stop()`,
  and getters used by the controller: `getStreamState()`, `getOtlpEndpoint()`, `getExportCount()`,
  `getSuccessfulExports()`, `getFailedExports()`, `getTotalSamplesExported()`.

- [ ] **Step 1: Write failing tests** (unit-level, no live collector):
  - `test_atomicSwapReturnsConverterAndResetsCounter`: a package-visible `swapConverter()` returns the current
    converter and installs a fresh one atomically; samples added before the swap belong to the returned instance,
    samples added after belong to the new one.
  - `test_exportSkippedWhenNoSamples`: an interval tick with an empty converter does not call the exporter and does
    not throw (use an injected fake `ProfileExporter` capturing `export` calls).
- [ ] **Step 2: Run tests, verify they fail** — Run: `mvn -q -Dtest=JfrProfilesExportServiceTest test`. Expected: FAIL.
- [ ] **Step 3: Implement `JfrProfilesExportService`:**
  - Delete the logs service; create the streaming service.
  - Fields from config: `jfr.otlp.endpoint`, `jfr.export.interval.seconds`, `jfr.instrumentation.scope.name/version`,
    sample period (new `jfr.sample.period.millis`, default 10).
  - `@PostConstruct`: build `OtlpGrpcProfileExporter` (endpoint, timeout); build a `Resource` from service.name/version;
    hold an `AtomicReference<JfrExecutionSampleEventConverter>`; start a `RecordingStream`:
    `enable("jdk.ExecutionSample").withStackTrace().withPeriod(Duration.ofMillis(period))`,
    `onEvent("jdk.ExecutionSample", e -> converterRef.get().accept(e))`, `startAsync()`. Also start a parallel
    `Recording` (`jdk.ExecutionSample`, `setToDisk(true)`) used only to `dump()` the raw payload each interval.
  - `swapConverter()`: `converterRef.getAndSet(JfrExecutionSampleEventConverter.create())`.
  - `@Scheduled(fixedDelayString=..., initialDelay=...)`: swap the converter; if it has no samples, skip; else
    `recording.dump(tmp)`, read bytes, `ProfileAssembler.assemble(...)` with the interval window
    (start = now − interval, duration = interval, period = sample period), `exporter.export(List.of(profile))`,
    join with timeout, update counters, delete tmp.
  - `@PreDestroy`: close the stream; final swap+export of remaining samples; `exporter.shutdown()`.
- [ ] **Step 4: Rename/point the controller** — rename to `JfrProfilesController`, inject
  `JfrProfilesExportService`, expose the same `/api/jfr/status` and `/api/jfr/health` with the profiles stat names.
- [ ] **Step 5: Run tests, verify they pass** — Run: `mvn -q -Dtest=JfrProfilesExportServiceTest test`. Expected: PASS.
- [ ] **Step 6: Full build** — Run: `mvn -q -DskipTests=false package`. Expected: BUILD SUCCESS.
- [ ] **Step 7: Remove the now-unused logs dependency** — delete `opentelemetry-sdk-logs` from `pom.xml` (the logs
  service is gone); re-run `mvn -q -DskipTests compile` to confirm nothing else referenced it. Expected: BUILD SUCCESS.
- [ ] **Step 8: Commit** — `git add -A apps/jfr-otel-profiles-app && commit -m "add streaming profiles export service"`.

---

## Task 5: Collector profiles pipeline

**Files:**
- Modify: `apps/jfr-otel-profiles-app/docker/otel-collector-config.yaml`
- Modify: `apps/jfr-otel-profiles-app/docker/docker-compose.yaml`
- Modify: `apps/jfr-otel-profiles-app/docker/.gitignore` (drop `certs/` / `.env` lines if present; no secrets here)

**Interfaces:**
- Produces: a collector that accepts the profiles signal on OTLP and prints it via `debug`.

- [ ] **Step 1: Rewrite `otel-collector-config.yaml`** — receivers: `otlp` (grpc 4317, http 4318); exporters:
  `debug` (verbosity detailed); `service.pipelines.profiles: { receivers: [otlp], exporters: [debug] }`. Remove the
  logs pipeline, the SAP `otlp/sap-cloud-logging` exporter, and the basicauth/TLS/cert config.
- [ ] **Step 2: Edit `docker-compose.yaml`** — keep image `otel/opentelemetry-collector-contrib:0.165.0`; set
  `command: ["--config=/etc/otel-collector-config.yaml", "--feature-gates=+service.profilesSupport"]`; remove the
  `./certs` mount. Keep ports 4317/4318/8888/13133.
- [ ] **Step 3: Start and verify the collector loads the profiles pipeline** — Run:
  `cd apps/jfr-otel-profiles-app/docker && docker compose up -d && sleep 6 && docker logs jfr-otel-collector 2>&1 | grep -iE "Everything is ready|profiles|error"`.
  Expected: "Everything is ready" and a profiles pipeline started, no fatal error about the `profiles` pipeline or the
  feature gate.
- [ ] **Step 4: Stop the collector** — Run: `docker compose down`.
- [ ] **Step 5: Commit** — `git add` the three docker files; `commit -m "collector: profiles pipeline behind feature gate"`.

---

## Task 6: End-to-end verification

**Files:**
- Test (manual/scripted): no new source; exercises app → collector → debug.

- [ ] **Step 1: Start the collector** — `cd apps/jfr-otel-profiles-app/docker && docker compose up -d`.
- [ ] **Step 2: Run the app** — `cd apps/jfr-otel-profiles-app && mvn -q spring-boot:run` (background), wait past the
  scheduled `initialDelay` + one interval.
- [ ] **Step 3: Confirm profiles reach the collector** — Run:
  `docker logs jfr-otel-collector 2>&1 | grep -iE "Profile|Sample|ResourceProfiles|profiles"`.
  Expected: profile records printed by the `debug` exporter (resource profiles / sample records visible), with no
  export error in the app log. Also `curl localhost:8082/api/jfr/status` shows `successful_exports` > 0.
- [ ] **Step 4: Tear down** — stop the app; `docker compose down`.
- [ ] **Step 5: Commit** — if any fixes were needed, commit them; otherwise no-op.

---

## Task 7: Documentation

**Files:**
- Modify: `apps/jfr-otel-profiles-app/docker/README.md`
- Modify: `apps/jfr-otel-profiles-app/docker/DOCKER-SETUP-SUMMARY.md`
- Modify: `apps/jfr-otel-profiles-app/README.md` and any top-level app docs copied from the logs app

**Interfaces:**
- Produces: docs that describe the profiles signal, the feature gate, inspection via `debug`/devfiler/Pyroscope, and
  the mapping's `jdk.ExecutionSample`-only scope. No stale logs/SAP/mTLS references.

- [ ] **Step 1: Rewrite the docker docs** — profiles pipeline, `--feature-gates=+service.profilesSupport`, how to read
  `debug` output, optional devfiler (`:11000`, what the OTel reference targets) and Grafana Pyroscope backends. Remove
  all SAP Cloud Logging / mTLS / logs-exporter content.
- [ ] **Step 2: Update the app-level README** — describe the app as a profiles-signal reproduction of the OTel JFR
  shim, ExecutionSample-only, streaming, OTel 1.68.0 + 1.68.0-alpha.
- [ ] **Step 3: Lint** — Run: `markdownlint -c ~/.claude/markdownlint/.markdownlint.json apps/jfr-otel-profiles-app/**/*.md`.
  Expected: no issues.
- [ ] **Step 4: Commit** — `git add` docs; `commit -m "docs: document jfr-otel-profiles-app profiles setup"`.
