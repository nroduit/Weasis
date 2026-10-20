/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.color;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The bytes of an ICC profile, validated and identified by their fingerprint. Two profiles with the
 * same bytes are equal and, once {@link #of(byte[]) interned}, the same object, so a series whose
 * every instance carries the profile holds it once.
 *
 * @param id hexadecimal MD5 fingerprint of the bytes
 */
public record IccProfile(String id, byte[] data) {

  private static final int HEADER_SIZE = 128;
  private static final int CLASS_OFFSET = 12;
  private static final int COLOR_SPACE_OFFSET = 16;
  private static final int SIGNATURE_OFFSET = 36;
  private static final int MAX_INTERNED = 32;

  private static final Map<String, IccProfile> INTERNED =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, IccProfile> eldest) {
          return size() > MAX_INTERNED;
        }
      };

  /** The sRGB profile of the platform, what an untagged color image means. */
  public static final IccProfile SRGB =
      of(ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData()).orElseThrow();

  public IccProfile {
    data = data.clone();
  }

  /**
   * The profile held by {@code bytes}, or empty when they are not an ICC profile: the size field of
   * the header must not exceed the length and the {@code acsp} signature must be in place. The
   * fork's JPEG reader appends every APP2 payload, so a corrupt blob is possible and is rejected
   * here.
   */
  public static Optional<IccProfile> of(byte[] bytes) {
    if (bytes == null || bytes.length < HEADER_SIZE + 4) {
      return Optional.empty();
    }
    long size = Integer.toUnsignedLong(ByteBuffer.wrap(bytes).getInt(0));
    // Lenient: a DICOM OB value is padded to an even length (PS3.5 7.1.1), so trailing bytes past
    // the declared size are accepted and dropped.
    if (size < HEADER_SIZE + 4
        || size > bytes.length
        || !"acsp".equals(signature(bytes, SIGNATURE_OFFSET))) {
      return Optional.empty();
    }
    byte[] profile = size == bytes.length ? bytes : Arrays.copyOf(bytes, (int) size);
    String id = fingerprint(profile);
    synchronized (INTERNED) {
      return Optional.of(INTERNED.computeIfAbsent(id, k -> new IccProfile(k, profile)));
    }
  }

  /** A copy of the profile bytes. */
  @Override
  public byte[] data() {
    return data.clone();
  }

  public int size() {
    return data.length;
  }

  /** Data color space signature, {@code RGB } for the profiles a color image can carry. */
  public String colorSpace() {
    return signature(data, COLOR_SPACE_OFFSET);
  }

  /** Profile/device class signature: {@code mntr}, {@code scnr}, {@code prtr}… */
  public String deviceClass() {
    return signature(data, CLASS_OFFSET);
  }

  public boolean isRgb() {
    return "RGB ".equals(colorSpace()); // NON-NLS
  }

  /** The profile description ({@code desc} tag, ICC v2 text or v4 localized), if readable. */
  public Optional<String> description() {
    ByteBuffer buf = ByteBuffer.wrap(data);
    int count = buf.getInt(HEADER_SIZE);
    for (int i = 0; i < count; i++) {
      int entry = HEADER_SIZE + 4 + i * 12;
      if (entry + 12 > data.length) {
        break;
      }
      if ("desc".equals(signature(data, entry))) { // NON-NLS
        return readText(buf, buf.getInt(entry + 4), buf.getInt(entry + 8));
      }
    }
    return Optional.empty();
  }

  /** Parses the profile with the platform color management module. */
  public ICC_Profile toIccProfile() {
    return ICC_Profile.getInstance(data);
  }

  private static Optional<String> readText(ByteBuffer buf, int offset, int length) {
    if (offset < 0 || length < 12 || offset + length > buf.capacity()) {
      return Optional.empty();
    }
    String type = signature(buf.array(), offset);
    String text = null;
    if ("desc".equals(type)) { // NON-NLS
      int count = buf.getInt(offset + 8);
      if (count > 0 && offset + 12 + count <= buf.capacity()) {
        text = new String(buf.array(), offset + 12, count - 1, StandardCharsets.US_ASCII);
      }
    } else if ("mluc".equals(type) && buf.getInt(offset + 8) > 0) { // NON-NLS
      int textLength = buf.getInt(offset + 20);
      int textOffset = offset + buf.getInt(offset + 24);
      if (textLength > 0 && textOffset + textLength <= buf.capacity()) {
        text = new String(buf.array(), textOffset, textLength, StandardCharsets.UTF_16BE);
      }
    }
    return Optional.ofNullable(text).map(String::strip).filter(s -> !s.isEmpty());
  }

  private static String signature(byte[] bytes, int offset) {
    return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
  }

  private static String fingerprint(byte[] bytes) {
    try {
      // NOSONAR not a security use: a content key, as the ICC profile id is
      return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 is required to identify an ICC profile", e);
    }
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof IccProfile other && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return description().orElse("ICC profile") + " (" + data.length + " bytes)"; // NON-NLS
  }
}
