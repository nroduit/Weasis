/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AlphaRangeTableTest {

  private static byte[] lut(int... alphas) {
    byte[] rgba = new byte[alphas.length * 4];
    for (int i = 0; i < alphas.length; i++) {
      rgba[i * 4 + 3] = (byte) alphas[i];
    }
    return rgba;
  }

  @Test
  void intervalBoundIsTheHighestAlphaWithin() {
    byte[] table = AlphaRangeTable.build(lut(0, 100, 0, 200), 4);
    int last = AlphaRangeTable.BINS - 1;
    assertAll(
        () -> assertEquals(100, AlphaRangeTable.alphaAt(table, 0, 0)),
        () -> assertEquals(100, AlphaRangeTable.alphaAt(table, 64, 64)),
        () -> assertEquals(200, AlphaRangeTable.alphaAt(table, 0, last)),
        () -> assertEquals(200, AlphaRangeTable.alphaAt(table, 128, last)),
        () -> assertEquals(200, AlphaRangeTable.alphaAt(table, 200, last)));
  }

  @Test
  void neighbouringTexelsWidenABin() {
    // Bin 64 starts at texel 1 of a 4-texel LUT and blends with texel 0.
    byte[] table = AlphaRangeTable.build(lut(50, 0, 0, 0), 4);
    assertAll(
        () -> assertEquals(50, AlphaRangeTable.alphaAt(table, 64, 64)),
        () -> assertEquals(0, AlphaRangeTable.alphaAt(table, 200, 200)));
  }

  @Test
  void transparentLutBoundsNothing() {
    byte[] table = AlphaRangeTable.build(lut(0, 0, 0), 3);
    int last = AlphaRangeTable.BINS - 1;
    assertEquals(0, AlphaRangeTable.alphaAt(table, 0, last));
  }

  @Test
  void wideLutIsBinnedConservatively() {
    int width = 4096;
    int[] alphas = new int[width];
    alphas[1000] = 77;
    byte[] table = AlphaRangeTable.build(lut(alphas), width);
    int bin = 1000 * AlphaRangeTable.BINS / width;
    assertAll(
        () -> assertEquals(77, AlphaRangeTable.alphaAt(table, bin, bin)),
        () -> assertEquals(0, AlphaRangeTable.alphaAt(table, bin + 2, bin + 2)),
        () -> assertEquals(0, AlphaRangeTable.alphaAt(table, 0, bin - 2)));
  }
}
