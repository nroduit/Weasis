/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.seg;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ByteLutAlphaTest {

  private static byte[][] rampBgrLut() {
    // B = i, G = 2i (mod 256), R = 255 - i
    byte[][] lut = new byte[3][ByteLutAlpha.CHANNEL_SIZE];
    for (int i = 0; i < ByteLutAlpha.CHANNEL_SIZE; i++) {
      lut[0][i] = (byte) i;
      lut[1][i] = (byte) (2 * i);
      lut[2][i] = (byte) (255 - i);
    }
    return lut;
  }

  @Test
  void fromColorLutCopiesColorBandsAndRampsAlpha() {
    byte[][] bgr = rampBgrLut();
    ByteLutAlpha lut = ByteLutAlpha.fromColorLut("fusion", bgr, 1.0f);

    byte[][] table = lut.lutTable();
    assertAll(
        () -> assertEquals(ByteLutAlpha.CHANNEL_COUNT, table.length),
        // Entry 0 is fully transparent regardless of opacity.
        () -> assertEquals(0, table[ByteLutAlpha.A][0]),
        // Alpha ramps to full at the top of the range when opacity = 1.
        () -> assertEquals((byte) 255, table[ByteLutAlpha.A][255]),
        // Color bands are copied verbatim from the BGR LUT.
        () -> assertEquals((byte) 200, table[ByteLutAlpha.B][200]),
        () -> assertEquals((byte) (2 * 100), table[ByteLutAlpha.G][100]),
        () -> assertEquals((byte) (255 - 40), table[ByteLutAlpha.R][40]));
  }

  @Test
  void fromColorLutScalesAlphaByOpacity() {
    ByteLutAlpha lut = ByteLutAlpha.fromColorLut("fusion", rampBgrLut(), 0.5f);
    // At full intensity, alpha is halved by the opacity multiplier.
    assertEquals(128, Byte.toUnsignedInt(lut.lutTable()[ByteLutAlpha.A][255]));
    assertEquals(0, lut.lutTable()[ByteLutAlpha.A][0]);
  }

  @Test
  void fromColorLutDoesNotAliasSourceArrays() {
    byte[][] bgr = rampBgrLut();
    ByteLutAlpha lut = ByteLutAlpha.fromColorLut("fusion", bgr, 1.0f);
    bgr[0][10] = (byte) 0xAB; // mutate source after construction
    assertEquals((byte) 10, lut.lutTable()[ByteLutAlpha.B][10]);
  }

  @Test
  void fromColorLutRejectsMalformedTables() {
    assertAll(
        () ->
            assertThrows(
                NullPointerException.class, () -> ByteLutAlpha.fromColorLut("x", null, 1f)),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> ByteLutAlpha.fromColorLut("x", new byte[2][256], 1f)),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> ByteLutAlpha.fromColorLut("x", new byte[3][10], 1f)));
  }
}
