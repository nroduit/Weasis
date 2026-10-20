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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class IccProfileTest {

  @Test
  void platform_srgb_is_a_valid_rgb_display_profile() {
    IccProfile srgb = IccProfile.SRGB;
    assertTrue(srgb.isRgb());
    assertEquals("mntr", srgb.deviceClass());
    assertTrue(srgb.description().orElseThrow().contains("sRGB"), srgb.toString());
  }

  @Test
  void same_bytes_give_the_same_object() {
    byte[] bytes = IccProfile.SRGB.data();
    assertSame(IccProfile.SRGB, IccProfile.of(bytes).orElseThrow());
    assertNotSame(bytes, IccProfile.SRGB.data(), "defensive copy");
  }

  @Test
  void even_length_padding_is_dropped() {
    byte[] bytes = IccProfile.SRGB.data();
    byte[] padded = Arrays.copyOf(bytes, bytes.length + 2);
    assertEquals(IccProfile.SRGB, IccProfile.of(padded).orElseThrow());
  }

  @Test
  void corrupt_blobs_are_rejected() {
    byte[] bytes = IccProfile.SRGB.data();
    byte[] noSignature = bytes.clone();
    noSignature[36] = 'x';
    byte[] truncated = Arrays.copyOf(bytes, bytes.length - 4);
    assertTrue(IccProfile.of(noSignature).isEmpty());
    assertTrue(IccProfile.of(truncated).isEmpty());
    assertTrue(IccProfile.of(new byte[10]).isEmpty());
    assertTrue(IccProfile.of(null).isEmpty());
  }
}
