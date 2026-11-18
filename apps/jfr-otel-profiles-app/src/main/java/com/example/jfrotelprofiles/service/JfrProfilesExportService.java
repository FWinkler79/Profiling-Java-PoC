package com.example.jfrotelprofiles.service;

import com.example.jfrotelprofiles.jfr.JfrExecutionSampleEventConverter;
import com.example.jfrotelprofiles.jfr.ProfileAssembler;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.profiles.OtlpGrpcProfileExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.profiles.ProfileExporter;
import io.opentelemetry.sdk.profiles.data.ProfileData;
import io.opentelemetry.sdk.profiles.data.ProfilesDictionaryData;
import io.opentelemetry.sdk.profiles.data.SampleData;
import io.opentelemetry.sdk.profiles.data.ValueTypeData;
import io.opentelemetry.sdk.resources.Resource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.RecordingStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service that continuously records {@code jdk.ExecutionSample} JFR events and periodically exports
 * them to an OTLP backend as OpenTelemetry Profile signals.
 *
 * <p>Two JFR facilities run in parallel:
 *
 * <ul>
 *   <li>a {@link RecordingStream} whose {@code onEvent} callback feeds each ExecutionSample into the
 *       <em>current</em> {@link JfrExecutionSampleEventConverter} (held in an {@link
 *       AtomicReference}); this converter accumulates the dictionary-encoded samples that become the
 *       exported {@link ProfileData};
 *   <li>a disk-backed {@link Recording} used solely to {@link Recording#dump(Path) dump} the raw JFR
 *       payload once per interval so it can be embedded in the profile.
 * </ul>
 *
 * <p><strong>Concurrency boundary.</strong> The JFR stream callback thread calls {@code
 * converterRef.get().accept(e)} while the scheduler thread periodically calls {@link
 * #swapConverter()}. The hand-off is a single {@link AtomicReference#getAndSet(Object)}: the
 * scheduler atomically takes the accumulated converter and installs a fresh one. No lock guards
 * {@code accept()}.
 *
 * <p>The swap itself is race-free, but the <em>drain</em> that follows is not: a callback that read
 * the old converter reference just before the swap may still be executing {@code accept()} on the
 * drained instance while the scheduler reads its samples. {@code SampleCompositionBuilder} is backed
 * by a plain non-concurrent {@link java.util.HashMap}, so that overlap could throw {@link
 * java.util.ConcurrentModificationException} or corrupt reader state. {@link #exportInterval()}
 * closes this window by (1) sleeping {@value #DRAIN_SETTLE_MILLIS} ms after the swap so in-flight
 * callbacks finish, and (2) reading the drained samples defensively with a retry so a transient CME
 * is contained rather than thrown out of the scheduled method.
 */
@Service
public class JfrProfilesExportService {

  private static final Logger logger = LoggerFactory.getLogger(JfrProfilesExportService.class);

  private static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";

  /** Pause after an atomic converter swap to let any in-flight JFR callback finish {@code accept()}. */
  private static final long DRAIN_SETTLE_MILLIS = 50;

  @Value("${jfr.otlp.endpoint:http://localhost:4317}")
  private String otlpEndpoint;

  @Value("${jfr.export.interval.seconds:15}")
  private int exportIntervalSeconds;

  @Value("${jfr.instrumentation.scope.name:jfr-otel-app}")
  private String scopeName;

  @Value("${jfr.instrumentation.scope.version:1.0.0}")
  private String scopeVersion;

  /** Sampling period for ExecutionSample events, in milliseconds. */
  @Value("${jfr.sample.period.millis:10}")
  private int samplePeriodMillis;

  /**
   * The converter the JFR stream callback is currently feeding. Swapped atomically each interval by
   * {@link #swapConverter()}.
   */
  private final AtomicReference<JfrExecutionSampleEventConverter> converterRef =
      new AtomicReference<>(JfrExecutionSampleEventConverter.create());

  private ProfileExporter exporter;
  private Resource resource;
  private InstrumentationScopeInfo scope;
  private RecordingStream recordingStream;
  private Recording rawRecording;

  private final AtomicLong exportCounter = new AtomicLong(0);
  private final AtomicLong successfulExports = new AtomicLong(0);
  private final AtomicLong failedExports = new AtomicLong(0);
  private final AtomicLong totalSamplesExported = new AtomicLong(0);

  /** Spring constructor. The exporter and stream are initialized in {@link #startStreaming()}. */
  public JfrProfilesExportService() {}

  /**
   * Test constructor: injects a fake {@link ProfileExporter} and a resolved resource/scope, and does
   * NOT start the JFR stream. This keeps the atomic-swap and interval logic exercisable without a
   * Spring context or a live collector.
   *
   * @param exporter the exporter to use (e.g. a capturing fake)
   */
  JfrProfilesExportService(ProfileExporter exporter) {
    this.exporter = exporter;
    this.exportIntervalSeconds = 15;
    this.samplePeriodMillis = 10;
    this.otlpEndpoint = "http://localhost:4317";
    this.scopeName = "jfr-otel-app";
    this.scopeVersion = "1.0.0";
    this.resource = buildResource();
    this.scope = buildScope();
  }

  @PostConstruct
  public void startStreaming() {
    try {
      exporter =
          OtlpGrpcProfileExporter.builder()
              .setEndpoint(otlpEndpoint)
              .setTimeout(Duration.ofSeconds(30))
              .build();
      logger.info("OTLP Profile Exporter configured for endpoint: {}", otlpEndpoint);

      resource = buildResource();
      scope = buildScope();

      Duration period = Duration.ofMillis(samplePeriodMillis);

      // Live stream: each ExecutionSample is fed into the current converter.
      recordingStream = new RecordingStream();
      recordingStream.enable(EXECUTION_SAMPLE).withStackTrace().withPeriod(period);
      recordingStream.onEvent(EXECUTION_SAMPLE, e -> converterRef.get().accept(e));
      recordingStream.startAsync();

      // Parallel disk-backed recording, dumped each interval to capture the raw JFR payload.
      rawRecording = new Recording();
      rawRecording.setName("ProfilesRawPayloadRecording");
      rawRecording.setToDisk(true);
      rawRecording.enable(EXECUTION_SAMPLE).withStackTrace().withPeriod(period);
      rawRecording.start();

      logger.info(
          "JFR profile streaming started - exporting to OTLP every {} seconds (sample period {} ms)",
          exportIntervalSeconds,
          samplePeriodMillis);
    } catch (Exception e) {
      logger.error("Failed to start JFR profile streaming", e);
    }
  }

  /**
   * Atomically takes the accumulated converter and installs a fresh one. This is the single
   * concurrency boundary between the JFR callback thread and the scheduler.
   *
   * @return the converter that was current before the swap (holding the window's samples)
   */
  JfrExecutionSampleEventConverter swapConverter() {
    return converterRef.getAndSet(JfrExecutionSampleEventConverter.create());
  }

  /** Returns the converter currently being fed by the stream (test/diagnostic accessor). */
  JfrExecutionSampleEventConverter currentConverter() {
    return converterRef.get();
  }

  /**
   * Swaps out the current window's converter and, if it has samples, dumps the raw payload, assembles
   * a {@link ProfileData}, and exports it. If there are no samples the export is skipped entirely —
   * the exporter is never touched.
   *
   * <p><strong>Drain race.</strong> The atomic swap installs a fresh converter, but a JFR callback
   * thread that fetched the <em>old</em> reference before the swap may still be inside {@code
   * drained.accept(e)} after we begin reading {@code drained} here. The converter's sample builder
   * and dictionary are backed by plain non-concurrent {@link java.util.HashMap}s, so a concurrent
   * writer while we read/mutate them could throw {@link java.util.ConcurrentModificationException} or
   * silently corrupt the exported profile. We close the race by construction: after the swap we (1)
   * settle-sleep to let in-flight callbacks finish, then (2) snapshot EVERYTHING we need from the
   * drained converter exactly once, inside one guarded block ({@link #snapshotDrained}) — the samples
   * copy, the resolved (samples,count) value type, and the dictionary data. After that snapshot the
   * live drained converter is never touched again; {@link ProfileAssembler#assemble} consumes only
   * the materialized snapshot. A persistent CME causes the interval to be skipped, not thrown.
   */
  @Scheduled(fixedDelayString = "${jfr.export.interval.seconds:15}000", initialDelay = 30000)
  public void exportInterval() {
    JfrExecutionSampleEventConverter drained = swapConverter();

    // Let any JFR callback still inside drained.accept(e) (it read the old ref before the swap)
    // finish before we read the drained converter's non-concurrent maps.
    settleAfterSwap();

    DrainedSnapshot snapshot = snapshotDrained(drained);
    if (snapshot == null) {
      // Persistent concurrent-modification failure; count it and skip this window rather than throw.
      failedExports.incrementAndGet();
      logger.warn("Skipping interval: could not safely drain converter samples");
      return;
    }

    int sampleCount = snapshot.samples().size();
    if (sampleCount == 0) {
      logger.debug("No ExecutionSamples in this interval; skipping export");
      return;
    }

    long exportNum = exportCounter.incrementAndGet();
    Path tempFile = null;
    try {
      long intervalNanos = Duration.ofSeconds(exportIntervalSeconds).toNanos();
      long nowNanos = System.currentTimeMillis() * 1_000_000L;
      long startEpochNanos = nowNanos - intervalNanos;
      long periodNanos = Duration.ofMillis(samplePeriodMillis).toNanos();

      byte[] rawJfrBytes = new byte[0];
      if (rawRecording != null) {
        tempFile = Files.createTempFile("jfr-profile", ".jfr");
        rawRecording.dump(tempFile);
        rawJfrBytes = Files.readAllBytes(tempFile);
      }

      ProfileData profile =
          ProfileAssembler.assemble(
              snapshot.dictionary(),
              snapshot.valueType(),
              snapshot.samples(),
              resource,
              scope,
              startEpochNanos,
              intervalNanos,
              periodNanos,
              rawJfrBytes);

      CompletableResultCode result = exporter.export(List.of(profile));
      result.join(30, TimeUnit.SECONDS);

      if (result.isSuccess()) {
        successfulExports.incrementAndGet();
        totalSamplesExported.addAndGet(sampleCount);
        logger.info(
            "Exported profile with {} samples (export #{}) to OTLP", sampleCount, exportNum);
      } else {
        failedExports.incrementAndGet();
        logger.warn("OTLP profile export #{} did not complete successfully", exportNum);
      }
    } catch (Exception e) {
      failedExports.incrementAndGet();
      logger.error("Error during OTLP profile export #{}", exportNum, e);
    } finally {
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException e) {
          logger.warn("Failed to delete temporary file: {}", tempFile, e);
        }
      }
    }
  }

  /** All data materialized from the drained converter in a single guarded pass. */
  private record DrainedSnapshot(
      List<SampleData> samples, ValueTypeData valueType, ProfilesDictionaryData dictionary) {}

  /**
   * Materializes everything {@link ProfileAssembler} needs from the drained converter in ONE pass,
   * so the live converter is touched exactly once. Order matters: the "samples"/"count" dictionary
   * strings are resolved (which mutates the dictionary) <em>before</em> the dictionary data is
   * captured, so they are present in the snapshot. Tolerates a transient {@link
   * ConcurrentModificationException} from a JFR callback still finishing {@code accept()} by retrying
   * once after a brief pause; returns {@code null} only on a persistent failure so the caller can
   * skip the interval instead of letting the exception (or a corrupt snapshot) escape.
   */
  private DrainedSnapshot snapshotDrained(JfrExecutionSampleEventConverter drained) {
    try {
      return materialize(drained);
    } catch (ConcurrentModificationException first) {
      logger.debug("Converter drain hit ConcurrentModificationException; retrying once", first);
      settleAfterSwap();
      try {
        return materialize(drained);
      } catch (ConcurrentModificationException second) {
        logger.warn("Converter drain still failing after retry; skipping interval", second);
        return null;
      }
    }
  }

  /** Single-pass read of the drained converter. Any concurrent write surfaces as a CME here. */
  private static DrainedSnapshot materialize(JfrExecutionSampleEventConverter drained) {
    int samplesIdx = drained.getProfilesDictionaryCompositor().putIfAbsent("samples");
    int countIdx = drained.getProfilesDictionaryCompositor().putIfAbsent("count");
    ValueTypeData valueType = ValueTypeData.create(samplesIdx, countIdx);
    ProfilesDictionaryData dictionary =
        drained.getProfilesDictionaryCompositor().getProfileDictionaryData();
    List<SampleData> samples = List.copyOf(drained.getSamples());
    return new DrainedSnapshot(samples, valueType, dictionary);
  }

  /** Sleeps briefly to let in-flight JFR callbacks drain; swallows interruption. */
  private static void settleAfterSwap() {
    try {
      Thread.sleep(DRAIN_SETTLE_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }


  @PreDestroy
  public void stop() {
    logger.info(
        "Stopping JFR profile streaming (exports: {}, successful: {}, failed: {}, samples: {})",
        exportCounter.get(),
        successfulExports.get(),
        failedExports.get(),
        totalSamplesExported.get());

    if (recordingStream != null) {
      try {
        recordingStream.close();
      } catch (Exception e) {
        logger.error("Error closing JFR recording stream", e);
      }
    }

    // Final swap + export of any samples accumulated since the last interval.
    try {
      if (exporter != null) {
        exportInterval();
      }
    } catch (Exception e) {
      logger.error("Error during final profile export", e);
    }

    if (rawRecording != null) {
      try {
        if (rawRecording.getState() == RecordingState.RUNNING) {
          rawRecording.stop();
        }
      } catch (Exception e) {
        logger.error("Error stopping raw JFR recording", e);
      } finally {
        rawRecording.close();
      }
    }

    if (exporter != null) {
      try {
        exporter.shutdown().join(10, TimeUnit.SECONDS);
        logger.info("OTLP profile exporter shut down");
      } catch (Exception e) {
        logger.error("Error shutting down OTLP profile exporter", e);
      }
    }
  }

  private Resource buildResource() {
    return Resource.create(
        Attributes.of(
            AttributeKey.stringKey("service.name"), scopeName,
            AttributeKey.stringKey("service.version"), scopeVersion));
  }

  private InstrumentationScopeInfo buildScope() {
    return InstrumentationScopeInfo.builder(scopeName).setVersion(scopeVersion).build();
  }

  /**
   * Returns the JFR state of the parallel disk-backed recording (the one dumped each interval for
   * the raw payload). The live {@link RecordingStream} exposes no queryable state, so the raw
   * recording's state is the observable proxy for whether profiling is active. Returns {@code
   * NOT_STARTED} before {@link #startStreaming()} has run.
   */
  public String getRecordingState() {
    if (rawRecording == null) {
      return "NOT_STARTED";
    }
    return rawRecording.getState().name();
  }

  public String getOtlpEndpoint() {
    return otlpEndpoint;
  }

  public long getExportCount() {
    return exportCounter.get();
  }

  public long getSuccessfulExports() {
    return successfulExports.get();
  }

  public long getFailedExports() {
    return failedExports.get();
  }

  public long getTotalSamplesExported() {
    return totalSamplesExported.get();
  }
}
