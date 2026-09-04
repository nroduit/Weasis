/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The sRGB profile written in the ICC Profile module (PS3.3 C.11.15). The module requires an Input
 * Device profile (class {@code scnr}), which the profile of {@link java.awt.color.ColorSpace} is
 * not: it is a display profile ({@code mntr}). This one is built as an ICC v4 three-component
 * matrix/TRC profile with parametric curves, so it states the same transform in about 530 bytes
 * instead of 6876.
 */
final class SrgbInputProfile {

  private static final int HEADER_SIZE = 128;
  private static final int VERSION_4_3 = 0x04300000;
  private static final int PROFILE_ID_OFFSET = 84;
  private static final int TAG_ENTRY_SIZE = 12;

  /** sRGB primaries chromatically adapted to the D50 illuminant of the PCS. */
  private static final double[] RED = {0.436065674, 0.222381592, 0.013916016};

  private static final double[] GREEN = {0.385330200, 0.717041016, 0.097137451};
  private static final double[] BLUE = {0.143020630, 0.060592651, 0.713836670};
  private static final double[] D50 = {0.964202880, 1.0, 0.824905396};

  /** Bradford adaptation from the sRGB white point (D65) to D50. */
  private static final double[] CHROMATIC_ADAPTATION = {
    1.047882, 0.022919, -0.050201, 0.029587, 0.990479, -0.017059, -0.009232, 0.015076, 0.751678
  };

  /** sRGB transfer function as an ICC parametric curve of type 3: g, a, b, c, d. */
  private static final double[] TRANSFER_FUNCTION = {
    2.4, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045
  };

  private static final class Holder {
    static final byte[] DATA = build();
  }

  private SrgbInputProfile() {}

  /** The profile bytes of ICC Profile (0028,2000), a fresh copy on each call. */
  static byte[] data() {
    return Holder.DATA.clone();
  }

  private record Tag(String signature, byte[] data) {}

  private static byte[] build() {
    byte[] curve = parametricCurve();
    Tag[] tags = {
      new Tag("desc", text("sRGB IEC61966-2.1")), // NON-NLS
      new Tag("cprt", text("No restrictions")), // NON-NLS
      new Tag("wtpt", xyz(D50)), // NON-NLS
      new Tag("chad", chromaticAdaptation()), // NON-NLS
      new Tag("rXYZ", xyz(RED)), // NON-NLS
      new Tag("gXYZ", xyz(GREEN)), // NON-NLS
      new Tag("bXYZ", xyz(BLUE)), // NON-NLS
      // The three curves are equal, so the tags share one payload, as ICC allows.
      new Tag("rTRC", curve), // NON-NLS
      new Tag("gTRC", null), // NON-NLS
      new Tag("bTRC", null) // NON-NLS
    };

    int tableSize = 4 + tags.length * TAG_ENTRY_SIZE;
    int[] offsets = new int[tags.length];
    int position = HEADER_SIZE + tableSize;
    int sharedCurveOffset = 0;
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    for (int i = 0; i < tags.length; i++) {
      byte[] data = tags[i].data();
      if (data == null) {
        offsets[i] = sharedCurveOffset;
        continue;
      }
      offsets[i] = position;
      if (data == curve) {
        sharedCurveOffset = position;
      }
      body.writeBytes(data);
      int padding = (4 - data.length % 4) % 4;
      body.writeBytes(new byte[padding]);
      position += data.length + padding;
    }

    byte[] profile = new byte[position];
    ByteBuffer buffer = ByteBuffer.wrap(profile);
    buffer.putInt(0, profile.length);
    buffer.putInt(8, VERSION_4_3);
    signature(profile, 12, "scnr"); // NON-NLS Input Device class, PS3.3 C.11.15.1.1
    signature(profile, 16, "RGB "); // NON-NLS
    signature(profile, 20, "XYZ "); // NON-NLS
    signature(profile, 36, "acsp"); // NON-NLS
    buffer.putInt(68, fixed(D50[0]));
    buffer.putInt(72, fixed(D50[1]));
    buffer.putInt(76, fixed(D50[2]));

    buffer.putInt(HEADER_SIZE, tags.length);
    for (int i = 0; i < tags.length; i++) {
      int entry = HEADER_SIZE + 4 + i * TAG_ENTRY_SIZE;
      signature(profile, entry, tags[i].signature());
      buffer.putInt(entry + 4, offsets[i]);
      buffer.putInt(entry + 8, tags[i].data() == null ? curve.length : tags[i].data().length);
    }
    System.arraycopy(body.toByteArray(), 0, profile, HEADER_SIZE + tableSize, body.size());

    // Profile ID: MD5 of the profile, the flags, rendering intent and id fields being zero here.
    System.arraycopy(md5(profile), 0, profile, PROFILE_ID_OFFSET, 16);
    return profile;
  }

  private static byte[] xyz(double[] values) {
    ByteBuffer buffer = ByteBuffer.allocate(20);
    buffer.put(ascii("XYZ ")).putInt(0); // NON-NLS
    for (double value : values) {
      buffer.putInt(fixed(value));
    }
    return buffer.array();
  }

  private static byte[] chromaticAdaptation() {
    ByteBuffer buffer = ByteBuffer.allocate(44);
    buffer.put(ascii("sf32")).putInt(0); // NON-NLS
    for (double value : CHROMATIC_ADAPTATION) {
      buffer.putInt(fixed(value));
    }
    return buffer.array();
  }

  private static byte[] parametricCurve() {
    ByteBuffer buffer = ByteBuffer.allocate(32);
    buffer.put(ascii("para")).putInt(0).putShort((short) 3).putShort((short) 0); // NON-NLS
    for (double value : TRANSFER_FUNCTION) {
      buffer.putInt(fixed(value));
    }
    return buffer.array();
  }

  /** Unicode text of an ICC v4 tag: a single en-US record. */
  private static byte[] text(String value) {
    byte[] utf16 = value.getBytes(StandardCharsets.UTF_16BE);
    ByteBuffer buffer = ByteBuffer.allocate(28 + utf16.length);
    buffer.put(ascii("mluc")).putInt(0).putInt(1).putInt(12); // NON-NLS
    buffer.put(ascii("enUS")).putInt(utf16.length).putInt(28).put(utf16); // NON-NLS
    return buffer.array();
  }

  private static int fixed(double value) {
    return (int) Math.round(value * 65536.0);
  }

  private static byte[] ascii(String value) {
    return value.getBytes(StandardCharsets.US_ASCII);
  }

  private static void signature(byte[] profile, int offset, String value) {
    System.arraycopy(ascii(value), 0, profile, offset, 4);
  }

  private static byte[] md5(byte[] data) {
    try {
      return MessageDigest.getInstance("MD5").digest(data); // NOSONAR ICC profile fingerprint
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 is required by the ICC profile id", e);
    }
  }
}
