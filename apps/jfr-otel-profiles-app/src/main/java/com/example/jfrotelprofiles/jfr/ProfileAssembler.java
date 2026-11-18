/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.jfrotelprofiles.jfr;

import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.profiles.data.ProfileData;
import io.opentelemetry.sdk.profiles.data.ProfilesDictionaryData;
import io.opentelemetry.sdk.profiles.data.SampleData;
import io.opentelemetry.sdk.profiles.data.ValueTypeData;
import io.opentelemetry.sdk.resources.Resource;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Assembles a complete {@link ProfileData} from already-materialized profile data and associated
 * metadata.
 *
 * <p>This class is deliberately decoupled from {@link JfrExecutionSampleEventConverter}: it never
 * reads or mutates a live converter. The caller is responsible for snapshotting the samples,
 * dictionary, and value type from the converter (e.g. once, under a concurrency guard) and passing
 * them in. This removes any possibility of a concurrent JFR callback mutating the converter's
 * non-concurrent backing maps while the profile is being assembled.
 *
 * <p>Each call generates a fresh, random 32-character lowercase-hex {@code profileId}.
 */
public final class ProfileAssembler {

  private static final SecureRandom RANDOM = new SecureRandom();

  private ProfileAssembler() {}

  /**
   * Builds a {@link ProfileData} from pre-materialized samples and dictionary data, plus the
   * supplied timing and payload metadata.
   *
   * @param dictionary the profiles dictionary snapshot (already includes the sample/count strings)
   * @param valueType the resolved (samples, count) value type, reused for both sample and period type
   * @param samples the materialized, defensively-copied list of samples
   * @param resource the OTel {@link Resource} to attach to the profile
   * @param scope the {@link InstrumentationScopeInfo} to attach to the profile
   * @param startEpochNanos the profile's start timestamp in epoch nanoseconds
   * @param durationNanos the wall-clock duration of the profiling window in nanoseconds
   * @param periodNanos the sampling period in nanoseconds
   * @param rawJfrBytes the raw JFR recording bytes to embed as the original payload
   * @return a fully assembled {@link ProfileData}
   */
  public static ProfileData assemble(
      ProfilesDictionaryData dictionary,
      ValueTypeData valueType,
      List<SampleData> samples,
      Resource resource,
      InstrumentationScopeInfo scope,
      long startEpochNanos,
      long durationNanos,
      long periodNanos,
      byte[] rawJfrBytes) {

    // Generate a fresh random 32-char lowercase-hex profileId (16 random bytes).
    byte[] idBytes = new byte[16];
    RANDOM.nextBytes(idBytes);
    String profileId = HexFormat.of().formatHex(idBytes);

    return ProfileData.create(
        resource,
        scope,
        dictionary,
        valueType,
        samples,
        startEpochNanos,
        durationNanos,
        /* periodType= */ valueType,
        periodNanos,
        profileId,
        /* droppedAttributesCount= */ 0,
        /* originalPayloadFormat= */ "jfr",
        /* originalPayload= */ ByteBuffer.wrap(rawJfrBytes),
        /* attributeIndices= */ Collections.emptyList());
  }
}
