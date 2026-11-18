# Design: `jfr-otel-profiles-app` — reproduce the OTel JFR profiles shim

Date: 2026-11-18
Status: Proposed (awaiting approval)

## Intent and why

OpenTelemetry's `opentelemetry-java` repo contains `opentelemetry-jfr-profiles-shim`, a reference example that
converts JFR events into the **OTLP profiles signal** (the pprof-style Function/Location/Stack/Sample model with
dictionary encoding) and exports them with `OtlpGrpcProfileExporter`. The user wants that mapping reproduced in this
repository.

The existing `jfr-stream-based-otel-app` maps JFR events to **OTLP logs** (one log record per event). That is a
different signal and is explicitly a stopgap — its own source comments say to switch to the profiles signal once the
JFR profiles support is released. It now is (alpha, on Maven Central). Rather than convert the working logs app, we
build a **new** app so the logs app stays intact for comparison.

## Goals

- A new Spring Boot app `apps/jfr-otel-profiles-app`, copied from `jfr-stream-based-otel-app`.
- Faithfully reproduce the shim's JFR→profiles mapping for `jdk.ExecutionSample`.
- Export the resulting `ProfileData` over OTLP (gRPC) to a collector's `profiles` pipeline.
- Provide a zero-setup way to verify the mapping end-to-end (collector `debug` exporter → stdout).
- Keep the existing REST status/health endpoints working, reporting profiles-export stats.

## Non-goals

- Mapping any JFR event type other than `jdk.ExecutionSample` (decided with the user).
- Exporting profiles to SAP Cloud Logging (left out unless the user later confirms it ingests profiles).
- Production hardening of the alpha profiles signal.
- Touching `jfr-stream-based-otel-app` or the shared root config in any functional way.

## Anti-goals (would make this a failure even if it "works")

- Silently forcing non-sample events (GC, CPULoad, exceptions) into the profile model — that would misrepresent the
  data and defeat a faithful reproduction.
- Diverging from the shim's mapping semantics (dictionary encoding, frame→location, `profile.frame.type=jvm`,
  `thread.name` sample attribute) without reason.

## Constraints

- **Alpha dependency.** `opentelemetry-sdk-profiles` / `opentelemetry-exporter-otlp-profiles` are `1.68.0-alpha`; the
  whole OTel stack moves `1.33.0` → `1.68.0`. APIs may shift in future bumps.
- **Collector gating.** The profiles signal needs a dedicated `profiles` pipeline AND the Collector started with
  `--feature-gates=+service.profilesSupport`. Confirmed against the pinned image: `collector-contrib:0.165.0`
  registers the `service.profilesSupport` gate (Alpha, default off). Without the gate, a `profiles` pipeline is
  rejected.
- **Java 17** (unchanged). `RecordingStream`/`jdk.jfr.consumer` are available.
- The shim classes were written against repo `main`; all classes they use are confirmed present in the published
  `1.68.0-alpha` jar, but exact method signatures must be validated at build time (addressed by TDD in the plan).

## Approaches considered

**A. Faithful file-based port.** Keep the logs app's periodic `recording.dump()` → read `RecordingFile` cycle, but
swap logs emission for the shim's profiles conversion. Smallest change, literal to the shim (which reads a file).

**B. True streaming conversion (CHOSEN).** Use `RecordingStream` to feed `jdk.ExecutionSample` events into the
converter live via `onEvent(...)`, and flush one `ProfileData` per interval. No temp-file round-trip. Fits the app
family's "stream-based" identity and is cleaner at runtime. Costs a bit more design than A (interval flushing +
thread-safety around the converter), which the shim doesn't itself demonstrate.

**C. Standalone converter library + example `main`.** Mirror the shim's own repo layout (reusable package plus a
`JfrExportExample`-style entry point) and have Spring call in. Most structurally faithful to OTel's module, but adds a
layer and a second entry point this single demo app does not need.

**Chosen: B**, at the user's direction. A was the safe literal port; C suits a published library, not one demo app.
B honors the "stream-based" lineage and removes the dump/read cycle.

## How it works (the decided design)

### New app skeleton
- Copy `apps/jfr-stream-based-otel-app` → `apps/jfr-otel-profiles-app`. New Maven `artifactId: jfr-otel-profiles-app`.
- Rename the base package `com.example.jfrotel` → `com.example.jfrotelprofiles` (keeps the two apps' classes distinct
  if ever on one classpath). Controllers/endpoints keep their paths (`/api/jfr/status`, `/health`).

### Dependencies (pom.xml)
- `opentelemetry.version` → `1.68.0`.
- Replace `opentelemetry-sdk-logs` with:
  - `opentelemetry-sdk-profiles:1.68.0-alpha`
  - `opentelemetry-exporter-otlp-profiles:1.68.0-alpha`
- Keep `opentelemetry-exporter-otlp` (pulls OTLP gRPC machinery), api, sdk. Drop the custom `protobuf-java` dep unless
  a compile need reappears.

### Ported mapping classes (into `com.example.jfrotelprofiles.jfr`)
Reproduced from the shim, semantics preserved:
- `JfrExecutionSampleEventConverter` — holds a `ProfilesDictionaryCompositor`, a `JfrLocationDataCompositor`, and a
  `SampleCompositionBuilder`; `accept(RecordedEvent)` filters to `jdk.ExecutionSample`, requires `sampledThread`,
  records a `thread.name` attribute, encodes the stack, and adds a sample keyed by (stack, attr) at the event's
  epoch-nanos timestamp.
- `JfrLocationDataCompositor` — frame → `LocationData`(`LineData`→`FunctionData`→name string) with the
  `profile.frame.type = jvm` attribute; stack → `StackData` of location indices. Dictionary-encoded.
- Profile assembly (adapted from `JfrExportExample.convertJfrFile`) — builds the `ProfileData` with sample/count
  value type, resource, scope info, dictionary, and samples. Adapted for streaming: samples come from the live
  stream-fed converter (not a file read), while identity/timing/payload follow the resolved decisions below.

### Streaming + flush (the B-specific part)
- A `JfrProfilesExportService` replaces `JfrOtelExportService`.
- On startup: build an `OtlpGrpcProfileExporter` (endpoint from `jfr.otlp.endpoint`), start a `RecordingStream` with
  `enable("jdk.ExecutionSample").withStackTrace().withPeriod(...)` and `onEvent("jdk.ExecutionSample", converter::accept)`,
  then `startAsync()`.
- A scheduled task every `jfr.export.interval.seconds` **atomically swaps in a fresh converter**, builds a
  `ProfileData` from the drained one, and calls `exporter.export(List.of(profileData))`. Swapping avoids mutating the
  converter while `onEvent` callbacks (a JFR thread) are still writing to it — the one concurrency concern B introduces.
- `@PreDestroy` closes the stream and exporter; a final flush exports any remaining samples.
- Status endpoint reports: stream state, endpoint, export counts (success/fail), total samples exported.
- **`jfr.recording.max-age.seconds` is dropped.** It belonged to the logs app's `Recording`/snapshot model;
  `RecordingStream` has no `setMaxAge`/snapshot — events are drained per interval instead. The property is removed
  from `application.yaml` rather than carried over as a dead setting.

### Profile assembly decisions (resolved with the user)

These three points are where the shim's one-shot-file-dump demo values meet a streaming app. Decided:

- **`original_payload` = raw JFR bytes via a per-interval dump.** Each flush also calls `recording.dump()` to a temp
  `.jfr` and embeds its bytes as the profile's original payload, matching the shim's output faithfully. Implication,
  accepted: a temp file touches disk each interval, so B does not fully eliminate the file — but event→sample
  *conversion* still happens live on the stream (the dump is only for the payload blob). The temp file is deleted
  after each export.
- **Real per-export identity.** A fresh random `profileId` is generated per interval export (backends key on it, so a
  constant would collide across the many profiles a streaming app emits). `Resource` and `InstrumentationScopeInfo`
  are built from the app's existing config (`service.name`/`version`, `jfr.instrumentation.scope.name`/`version`) —
  not the shim's empty `Resource` / `testLib` constants. Keeps the app's config and status endpoint meaningful.
- **Real profile timing.** The profile's start time, duration, and sampling period are populated from the actual
  interval window and the configured sample period, rather than the shim's zeros — so devfiler/Pyroscope render the
  profiles correctly. A deliberate, reasoned divergence from the shim (the zeros were acceptable only for a one-shot
  file dump).


- Copy the logs app's `docker/` into the new app, then:
  - Add a `profiles` pipeline: `receivers: [otlp]`, `exporters: [debug]` (plus optional backend).
  - Start the collector with `--feature-gates=+service.profilesSupport` (via the compose `command:`).
  - Keep the `debug` exporter for stdout verification — the primary acceptance check.
- **SAP Cloud Logging exporter is NOT added to the profiles pipeline.** (The logs-oriented mTLS exporter config is
  carried over only if trivially compatible; otherwise the new app's collector is profiles+debug only. Decided: keep
  the new app's collector focused on profiles verification, no SAP path.)
- Optional, documented but off by default: a devfiler (`:11000`) or Grafana Pyroscope target to view flamegraphs.

### Docs
- New `README.md` / `DOCKER-SETUP-SUMMARY.md` in the new app's `docker/` describing the profiles pipeline, the feature
  gate, and how to inspect output (debug / devfiler / Pyroscope). Markdown linted per the repo rule.

## What's left to the builder

- Exact `1.68.0-alpha` method signatures for the profiles data classes (validated via a compile-first/TDD loop in the
  plan; the shim is the reference but may differ slightly from the published alpha).
- The precise thread-safety mechanism for the converter swap (e.g. `synchronized` handoff vs. `AtomicReference`),
  chosen to keep the JFR callback thread and the scheduler from racing.
- Whether a minimal unit test exercises the converter with synthetic/`RecordingFile` fixtures (preferred) given JFR
  events are awkward to fabricate; at minimum an end-to-end "profiles appear in collector debug output" check.
- Final Java package name if `com.example.jfrotelprofiles` conflicts with any existing convention in the repo.
- Mechanics of the per-interval dump used only for `original_payload` (reuse a short-lived `Recording`, or a
  `dump()` off the stream's buffer) — an implementation detail, as long as conversion stays stream-driven and the
  temp file is cleaned up.
