/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.jfrotelprofiles.jfr;

import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.profiles.data.ProfileData;
import io.opentelemetry.sdk.profiles.data.ProfilesDictionaryData;
import io.opentelemetry.sdk.profiles.data.ValueTypeData;
import io.opentelemetry.sdk.resources.Resource;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HexFormat;

/**
 * Assembles a complete {@link ProfileData} from a populated {@link JfrExecutionSampleEventConverter}
 * and associated metadata.
 *
 * <p>Each call generates a fresh, random 32-character lowercase-hex {@code profileId}.
 */
public final class ProfileAssembler {

  private static final SecureRandom RANDOM = new SecureRandom();

  private ProfileAssembler() {}

  /**
   * Builds a {@link ProfileData} from the converter's accumulated samples and dictionary, plus the
   * supplied timing and payload metadata.
   *
   * @param converter the populated converter whose samples and dictionary are used
   * @param resource the OTel {@link Resource} to attach to the profile
   * @param scope the {@link InstrumentationScopeInfo} to attach to the profile
   * @param startEpochNanos the profile's start timestamp in epoch nanoseconds
   * @param durationNanos the wall-clock duration of the profiling window in nanoseconds
   * @param periodNanos the sampling period in nanoseconds
   * @param rawJfrBytes the raw JFR recording bytes to embed as the original payload
   * @return a fully assembled {@link ProfileData}
   */
  public static ProfileData assemble(
      JfrExecutionSampleEventConverter converter,
      Resource resource,
      InstrumentationScopeInfo scope,
      long startEpochNanos,
      long durationNanos,
      long periodNanos,
      byte[] rawJfrBytes) {

    // Resolve "samples" and "count" string indices in the shared dictionary.
    int samplesIdx = converter.getProfilesDictionaryCompositor().putIfAbsent("samples");
    int countIdx = converter.getProfilesDictionaryCompositor().putIfAbsent("count");

    ValueTypeData sampleType = ValueTypeData.create(samplesIdx, countIdx);

    // For periodType reuse the same (samples, count) value type as the shim does.
    ValueTypeData periodType = ValueTypeData.create(samplesIdx, countIdx);

    ProfilesDictionaryData dictionary =
        converter.getProfilesDictionaryCompositor().getProfileDictionaryData();

    // Generate a fresh random 32-char lowercase-hex profileId (16 random bytes).
    byte[] idBytes = new byte[16];
    RANDOM.nextBytes(idBytes);
    String profileId = HexFormat.of().formatHex(idBytes);

    return ProfileData.create(
        resource,
        scope,
        dictionary,
        sampleType,
        converter.getSamples(),
        startEpochNanos,
        durationNanos,
        periodType,
        periodNanos,
        profileId,
        /* droppedAttributesCount= */ 0,
        /* originalPayloadFormat= */ "jfr",
        /* originalPayload= */ ByteBuffer.wrap(rawJfrBytes),
        /* attributeIndices= */ Collections.emptyList());
  }
}
