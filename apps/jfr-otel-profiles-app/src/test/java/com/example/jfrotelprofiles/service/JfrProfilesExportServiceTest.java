package com.example.jfrotelprofiles.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.jfrotelprofiles.jfr.JfrExecutionSampleEventConverter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.profiles.ProfileExporter;
import io.opentelemetry.sdk.profiles.data.ProfileData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;

/**
 * Unit-level tests for {@link JfrProfilesExportService}.
 *
 * <p>These tests deliberately avoid the Spring context, a live JFR stream, and a live OTLP
 * collector. The service is constructed via its package-visible test constructor which lets a fake
 * {@link ProfileExporter} be injected and the JFR stream startup be skipped.
 */
class JfrProfilesExportServiceTest {

  /** A fake exporter that records every call to {@link #export(Collection)}. */
  private static final class CapturingExporter implements ProfileExporter {
    final AtomicInteger exportCalls = new AtomicInteger(0);
    final List<Collection<ProfileData>> exported = new ArrayList<>();

    @Override
    public CompletableResultCode export(Collection<ProfileData> profiles) {
      exportCalls.incrementAndGet();
      exported.add(profiles);
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      return CompletableResultCode.ofSuccess();
    }
  }

  @Test
  void test_atomicSwapReturnsConverterAndResetsCounter() throws Exception {
    CapturingExporter exporter = new CapturingExporter();
    JfrProfilesExportService service = new JfrProfilesExportService(exporter);

    JfrExecutionSampleEventConverter before = service.currentConverter();

    // Feed real jdk.ExecutionSample events into the current converter before swapping.
    List<RecordedEvent> events = recordExecutionSamples();
    events.forEach(before::accept);
    int samplesBeforeSwap = before.getSamples().size();
    assertThat(samplesBeforeSwap)
        .as("precondition: the recording should produce at least one ExecutionSample")
        .isGreaterThan(0);

    JfrExecutionSampleEventConverter returned = service.swapConverter();

    // The swap returns the exact instance that was current.
    assertThat(returned).as("swapConverter must return the previously current instance").isSameAs(before);

    // A different, fresh converter is installed.
    JfrExecutionSampleEventConverter after = service.currentConverter();
    assertThat(after).as("swapConverter must install a different instance").isNotSameAs(before);

    // Samples accumulated before the swap belong to the returned instance.
    assertThat(returned.getSamples().size())
        .as("samples added before the swap belong to the returned converter")
        .isEqualTo(samplesBeforeSwap);

    // The freshly installed converter starts empty.
    assertThat(after.getSamples())
        .as("a post-swap converter starts empty")
        .isEmpty();
  }

  @Test
  void test_exportSkippedWhenNoSamples() {
    CapturingExporter exporter = new CapturingExporter();
    JfrProfilesExportService service = new JfrProfilesExportService(exporter);

    // The current converter is empty (no events fed). One interval tick must skip export.
    service.exportInterval();

    assertThat(exporter.exportCalls.get())
        .as("export must not be called when the drained converter has no samples")
        .isZero();
    assertThat(service.getExportCount())
        .as("no export attempt should have been counted")
        .isZero();
  }

  /** Produces a short real JFR recording and returns all recorded jdk.ExecutionSample events. */
  private static List<RecordedEvent> recordExecutionSamples() throws Exception {
    Path jfrFile = Files.createTempFile("test-swap", ".jfr");
    try {
      try (Recording recording = new Recording()) {
        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
        recording.start();

        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        long acc = 0;
        while (System.nanoTime() < deadline) {
          for (int i = 0; i < 1_000_000; i++) {
            acc += (long) Math.sqrt(i) ^ (acc * 31 + i);
          }
        }
        if (acc == Long.MIN_VALUE) {
          throw new IllegalStateException("unreachable " + acc);
        }

        recording.stop();
        recording.dump(jfrFile);
      }

      List<RecordedEvent> events = new ArrayList<>();
      try (RecordingFile recordingFile = new RecordingFile(jfrFile)) {
        while (recordingFile.hasMoreEvents()) {
          RecordedEvent event = recordingFile.readEvent();
          if (event != null) {
            events.add(event);
          }
        }
      }
      return events;
    } finally {
      Files.deleteIfExists(jfrFile);
    }
  }
}
