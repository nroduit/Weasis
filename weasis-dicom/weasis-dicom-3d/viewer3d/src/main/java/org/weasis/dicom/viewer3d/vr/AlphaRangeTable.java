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

/**
 * Highest opacity of a preset over any interval of its normalized coordinate, as a square table of
 * {@value #BINS} bins per side: the entry at column {@code lo} and row {@code hi} bounds the alpha
 * the LUT can return for a coordinate between the two bins. The path tracer reads it for each block
 * of the {@link MajorantGrid}, whose windowed value range is such an interval. Every bin is widened
 * by the texels its neighbours blend in, since the LUT is sampled linearly.
 */
final class AlphaRangeTable {

  static final int BINS = 256;

  private AlphaRangeTable() {}

  /**
   * The table of an RGBA8 LUT of {@code width} texels, row-major with the low bin along the row;
   * entries above the diagonal are zero.
   */
  static byte[] build(byte[] rgba, int width) {
    int[] binAlpha = new int[BINS];
    for (int bin = 0; bin < BINS; bin++) {
      int first = Math.max(0, Math.floorDiv(bin * width, BINS) - 1);
      int last = Math.min(width - 1, Math.ceilDiv((bin + 1) * width, BINS));
      int max = 0;
      for (int texel = first; texel <= last; texel++) {
        max = Math.max(max, rgba[texel * 4 + 3] & 0xFF);
      }
      binAlpha[bin] = max;
    }
    byte[] table = new byte[BINS * BINS];
    for (int hi = 0; hi < BINS; hi++) {
      int max = 0;
      for (int lo = hi; lo >= 0; lo--) {
        max = Math.max(max, binAlpha[lo]);
        table[hi * BINS + lo] = (byte) max;
      }
    }
    return table;
  }

  /** Bound stored for the interval of bins {@code [lo, hi]}, in {@code [0, 255]}. */
  static int alphaAt(byte[] table, int lo, int hi) {
    return table[hi * BINS + lo] & 0xFF;
  }
}
