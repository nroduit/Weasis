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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SrgbInputProfileTest {

  private static final byte[] PROFILE = SrgbInputProfile.data();

  private static String signature(int offset) {
    return new String(PROFILE, offset, 4, StandardCharsets.US_ASCII);
  }

  @Test
  void header_satisfies_the_icc_profile_module() {
    ByteBuffer buffer = ByteBuffer.wrap(PROFILE);
    assertAll(
        () -> assertEquals(PROFILE.length, buffer.getInt(0), "declared size"),
        () -> assertEquals(0, PROFILE.length % 4, "profiles are padded to 4 bytes"),
        () -> assertEquals("scnr", signature(12), "Input Device class, PS3.3 C.11.15.1.1"),
        () -> assertEquals("RGB ", signature(16), "input color space"),
        () -> assertEquals("XYZ ", signature(20), "profile connection space"),
        () -> assertEquals("acsp", signature(36), "profile file signature"),
        () -> assertEquals(0, buffer.getInt(64), "perceptual rendering intent"),
        () -> assertEquals(4, PROFILE[8], "ICC version 4"));
  }

  @Test
  void profile_id_is_the_md5_of_the_profile() throws NoSuchAlgorithmException {
    byte[] zeroed = PROFILE.clone();
    Arrays.fill(zeroed, 84, 100, (byte) 0);
    assertArrayEquals(
        MessageDigest.getInstance("MD5").digest(zeroed),
        Arrays.copyOfRange(PROFILE, 84, 100),
        "flags and rendering intent are already zero");
  }

  @Test
  void the_three_transfer_curves_share_one_payload() {
    ByteBuffer buffer = ByteBuffer.wrap(PROFILE);
    int count = buffer.getInt(128);
    int red = 0;
    int green = 0;
    int blue = 0;
    for (int i = 0; i < count; i++) {
      int entry = 132 + i * 12;
      switch (new String(PROFILE, entry, 4, StandardCharsets.US_ASCII)) {
        case "rTRC" -> red = buffer.getInt(entry + 4);
        case "gTRC" -> green = buffer.getInt(entry + 4);
        case "bTRC" -> blue = buffer.getInt(entry + 4);
        default -> {
          // other tags carry their own payload
        }
      }
    }
    int offset = red;
    int greenOffset = green;
    int blueOffset = blue;
    assertAll(
        () -> assertTrue(offset > 0, "red curve present"),
        () -> assertEquals(offset, greenOffset),
        () -> assertEquals(offset, blueOffset),
        () -> assertEquals("para", new String(PROFILE, offset, 4, StandardCharsets.US_ASCII)));
  }

  @Test
  void profile_is_read_back_as_an_input_profile_stating_the_srgb_transform() {
    ICC_Profile profile = ICC_Profile.getInstance(PROFILE);
    ICC_ColorSpace space = new ICC_ColorSpace(profile);
    ColorSpace srgb = ColorSpace.getInstance(ColorSpace.CS_sRGB);

    float difference = 0f;
    for (float red = 0f; red <= 1f; red += 0.1f) {
      for (float green = 0f; green <= 1f; green += 0.1f) {
        for (float blue = 0f; blue <= 1f; blue += 0.1f) {
          float[] color = {red, green, blue};
          float[] mine = space.toCIEXYZ(color);
          float[] reference = srgb.toCIEXYZ(color);
          for (int i = 0; i < 3; i++) {
            difference = Math.max(difference, Math.abs(mine[i] - reference[i]));
          }
        }
      }
    }
    float maximum = difference;
    assertAll(
        () -> assertEquals(ICC_Profile.CLASS_INPUT, profile.getProfileClass()),
        () -> assertEquals(ColorSpace.TYPE_RGB, profile.getColorSpaceType()),
        () -> assertEquals(ColorSpace.TYPE_XYZ, profile.getPCSType()),
        () -> assertTrue(maximum < 0.001f, "XYZ difference with sRGB: " + maximum));
  }

  @Test
  void profile_is_compact_and_callers_get_their_own_copy() {
    assertAll(
        () -> assertTrue(PROFILE.length < 1024, "size: " + PROFILE.length),
        () -> assertEquals(0, PROFILE.length % 2, "OB values have an even length"),
        () -> assertNotSame(SrgbInputProfile.data(), SrgbInputProfile.data()),
        () -> assertArrayEquals(SrgbInputProfile.data(), SrgbInputProfile.data()));
  }
}
