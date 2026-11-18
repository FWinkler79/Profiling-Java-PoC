package com.example.jfrotelprofiles.jfr;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.profiles.data.ProfileData;
import io.opentelemetry.sdk.profiles.data.ProfilesDictionaryData;
import io.opentelemetry.sdk.profiles.data.SampleData;
import io.opentelemetry.sdk.profiles.data.ValueTypeData;
import io.opentelemetry.sdk.resources.Resource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProfileAssembler}.
 *
 * <p>Uses a real JFR recording of {@code jdk.ExecutionSample} events to populate the converter, then
 * snapshots the materialized samples/dictionary/value-type from it (single-threaded here) to feed
 * the assembler, mirroring how the service drains the converter under its concurrency guard.
 */
class ProfileAssemblerTest {

  private JfrExecutionSampleEventConverter converter;
  private byte[] rawJfrBytes;

  /**
   * Generates a short JFR recording of {@code jdk.ExecutionSample} events, feeds all events into a
   * fresh converter, and stores the raw bytes of the recording for payload assertions.
   */
  @BeforeEach
  void setUp() throws Exception {
    Path jfrFile = Files.createTempFile("test-profile-assembler", ".jfr");
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

      rawJfrBytes = Files.readAllBytes(jfrFile);

      List<RecordedEvent> events = new ArrayList<>();
      try (RecordingFile recordingFile = new RecordingFile(jfrFile)) {
        while (recordingFile.hasMoreEvents()) {
          RecordedEvent event = recordingFile.readEvent();
          if (event != null) {
            events.add(event);
          }
        }
      }

      converter = JfrExecutionSampleEventConverter.create();
      events.forEach(converter::accept);
    } finally {
      Files.deleteIfExists(jfrFile);
    }
  }

  /** Resolves the (samples, count) value type from the converter's dictionary. */
  private ValueTypeData valueType() {
    int samplesIdx = converter.getProfilesDictionaryCompositor().putIfAbsent("samples");
    int countIdx = converter.getProfilesDictionaryCompositor().putIfAbsent("count");
    return ValueTypeData.create(samplesIdx, countIdx);
  }

  /** Snapshots the converter's dictionary data (call after {@link #valueType()}). */
  private ProfilesDictionaryData dictionary() {
    return converter.getProfilesDictionaryCompositor().getProfileDictionaryData();
  }

  private List<SampleData> samples() {
    return List.copyOf(converter.getSamples());
  }

  @Test
  void test_assembleProducesProfileWithGeneratedId() {
    Resource resource = Resource.getDefault();
    InstrumentationScopeInfo scope = InstrumentationScopeInfo.create("test-scope");
    long startNanos = System.nanoTime();
    long durationNanos = Duration.ofSeconds(2).toNanos();
    long periodNanos = Duration.ofMillis(10).toNanos();

    ValueTypeData valueType = valueType();
    ProfilesDictionaryData dictionary = dictionary();
    List<SampleData> samples = samples();

    ProfileData profile1 =
        ProfileAssembler.assemble(
            dictionary, valueType, samples, resource, scope, startNanos, durationNanos, periodNanos,
            rawJfrBytes);
    ProfileData profile2 =
        ProfileAssembler.assemble(
            dictionary, valueType, samples, resource, scope, startNanos, durationNanos, periodNanos,
            rawJfrBytes);

    String id1 = profile1.getProfileId();
    String id2 = profile2.getProfileId();

    assertThat(id1).as("profileId must be 32 characters").hasSize(32);
    assertThat(id1).as("profileId must be lowercase hex").matches("[0-9a-f]{32}");
    assertThat(id2).as("second profileId must be 32 characters").hasSize(32);
    assertThat(id2).as("second profileId must be lowercase hex").matches("[0-9a-f]{32}");
    assertThat(id1).as("two consecutive calls must produce different profileIds").isNotEqualTo(id2);
  }

  @Test
  void test_assembleCarriesTimingAndPayload() {
    Resource resource = Resource.getDefault();
    InstrumentationScopeInfo scope = InstrumentationScopeInfo.create("test-scope");
    long startNanos = 1_700_000_000_000_000_000L;
    long durationNanos = Duration.ofSeconds(5).toNanos();
    long periodNanos = Duration.ofMillis(20).toNanos();

    ValueTypeData valueType = valueType();
    ProfilesDictionaryData dictionary = dictionary();
    List<SampleData> samples = samples();

    ProfileData profile =
        ProfileAssembler.assemble(
            dictionary, valueType, samples, resource, scope, startNanos, durationNanos, periodNanos,
            rawJfrBytes);

    assertThat(profile.getTimeNanos())
        .as("timeNanos must equal startEpochNanos")
        .isEqualTo(startNanos);
    assertThat(profile.getDurationNanos())
        .as("durationNanos must equal the argument passed in")
        .isEqualTo(durationNanos);
    assertThat(profile.getPeriod())
        .as("period must equal periodNanos")
        .isEqualTo(periodNanos);

    assertThat(profile.getOriginalPayloadFormat())
        .as("originalPayloadFormat must be \"jfr\"")
        .isEqualTo("jfr");
    assertThat(profile.getOriginalPayload().remaining())
        .as("originalPayload remaining bytes must equal input byte-array length")
        .isEqualTo(rawJfrBytes.length);
  }

  @Test
  void test_assembleUsesResourceAndScope() {
    Resource resource =
        Resource.builder()
            .put("service.name", "test-service")
            .put("host.name", "test-host")
            .build();
    InstrumentationScopeInfo scope =
        InstrumentationScopeInfo.builder("test-instrumentation").setVersion("1.0.0").build();

    long startNanos = System.nanoTime();
    long durationNanos = Duration.ofSeconds(1).toNanos();
    long periodNanos = Duration.ofMillis(10).toNanos();

    ValueTypeData valueType = valueType();
    ProfilesDictionaryData dictionary = dictionary();
    List<SampleData> samples = samples();

    ProfileData profile =
        ProfileAssembler.assemble(
            dictionary, valueType, samples, resource, scope, startNanos, durationNanos, periodNanos,
            rawJfrBytes);

    assertThat(profile.getResource())
        .as("resource must equal the one passed in")
        .isEqualTo(resource);
    assertThat(profile.getInstrumentationScopeInfo())
        .as("instrumentationScopeInfo must equal the scope passed in")
        .isEqualTo(scope);
  }
}
