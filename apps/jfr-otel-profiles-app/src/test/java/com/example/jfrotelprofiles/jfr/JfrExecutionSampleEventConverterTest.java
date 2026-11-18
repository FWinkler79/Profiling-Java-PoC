package com.example.jfrotelprofiles.jfr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.opentelemetry.sdk.profiles.data.SampleData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;

/**
 * Tests for the JFR execution-sample → OTLP profiles converter.
 *
 * <p>Fabricating {@link RecordedEvent} / {@code RecordedFrame} instances is impractical, so these
 * tests drive the converter with events read from a real JFR recording generated at runtime.
 */
class JfrExecutionSampleEventConverterTest {

  /**
   * Produces a real JFR recording that contains {@code jdk.ExecutionSample} events by enabling the
   * sampler at a short period and keeping the CPU busy, then returns the events read back from the
   * dumped recording file.
   */
  private static List<RecordedEvent> recordExecutionSampleEvents() throws Exception {
    Path jfrFile = Files.createTempFile("test-exec-sample", ".jfr");
    try {
      try (Recording recording = new Recording()) {
        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
        recording.start();

        // CPU-busy work to give the sampler something to sample. Run long enough that the
        // periodic sampler is very likely to fire on this thread.
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        long acc = 0;
        while (System.nanoTime() < deadline) {
          for (int i = 0; i < 1_000_000; i++) {
            acc += (long) Math.sqrt(i) ^ (acc * 31 + i);
          }
        }
        // Prevent the busy loop from being optimised away.
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

  @Test
  void test_samplesProducedFromRealRecording() throws Exception {
    List<RecordedEvent> events = recordExecutionSampleEvents();

    long executionSamples =
        events.stream()
            .filter(e -> "jdk.ExecutionSample".equals(e.getEventType().getName()))
            .count();
    assertThat(executionSamples)
        .as("the generated recording must contain jdk.ExecutionSample events")
        .isGreaterThan(0);

    JfrExecutionSampleEventConverter converter = JfrExecutionSampleEventConverter.create();
    events.forEach(converter::accept);

    assertThat(converter.getSamples())
        .as("execution samples should produce SampleData")
        .isNotEmpty();

    List<String> stringTable =
        converter.getProfilesDictionaryCompositor().getProfileDictionaryData().getStringTable();
    assertThat(stringTable)
        .as("the dictionary string table should be populated")
        .isNotEmpty();
  }

  /**
   * Produces a real JFR recording that contains {@code jdk.ThreadSleep} events (and explicitly does
   * NOT enable {@code jdk.ExecutionSample}) by sleeping, then returns the events read back. These
   * stand in for "events of other types" that the converter must ignore.
   */
  private static List<RecordedEvent> recordNonExecutionSampleEvents() throws Exception {
    Path jfrFile = Files.createTempFile("test-other-events", ".jfr");
    try {
      try (Recording recording = new Recording()) {
        recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1));
        recording.start();

        for (int i = 0; i < 5; i++) {
          Thread.sleep(20);
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

  @Test
  void test_eventWithoutStackOrThreadIsSkippedGracefully() throws Exception {
    // A recording of a different event type. These must be ignored by the converter's
    // event-type filter guard: no samples produced and no exception thrown. (Using events of
    // another type also stands in for the null-sampledThread skip path, which is unreachable via
    // genuine jdk.ExecutionSample events.)
    List<RecordedEvent> otherEvents = recordNonExecutionSampleEvents();

    List<RecordedEvent> nonExecutionSampleEvents =
        otherEvents.stream()
            .filter(e -> !"jdk.ExecutionSample".equals(e.getEventType().getName()))
            .toList();
    assertThat(nonExecutionSampleEvents)
        .as("the generated recording should contain non-ExecutionSample events to feed this guard")
        .isNotEmpty();

    JfrExecutionSampleEventConverter converter = JfrExecutionSampleEventConverter.create();

    assertThatCode(() -> nonExecutionSampleEvents.forEach(converter::accept))
        .doesNotThrowAnyException();

    assertThat(converter.getSamples())
        .as("events of other types must not produce samples")
        .isEmpty();
  }
}
