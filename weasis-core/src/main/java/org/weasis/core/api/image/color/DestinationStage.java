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

/**
 * The end of a conversion into a matrix/TRC profile: a 3×3 matrix to the linear light of the
 * destination, then one encode table per channel indexed over 16 bits of linear light, so no
 * transcendental is computed per pixel. Out-of-gamut values are clipped.
 */
final class DestinationStage {
  static final int ENCODE_MAX = 65535;

  private final float m0;
  private final float m1;
  private final float m2;
  private final float m3;
  private final float m4;
  private final float m5;
  private final float m6;
  private final float m7;
  private final float m8;
  private final byte[] encodeR;
  private final byte[] encodeG;
  private final byte[] encodeB;

  /**
   * @param matrix row-major 3×3 matrix into destination linear RGB
   * @param encode three tables of {@code ENCODE_MAX + 1} 8-bit values (R, G, B)
   */
  DestinationStage(float[] matrix, byte[][] encode) {
    this.m0 = matrix[0];
    this.m1 = matrix[1];
    this.m2 = matrix[2];
    this.m3 = matrix[3];
    this.m4 = matrix[4];
    this.m5 = matrix[5];
    this.m6 = matrix[6];
    this.m7 = matrix[7];
    this.m8 = matrix[8];
    this.encodeR = encode[0];
    this.encodeG = encode[1];
    this.encodeB = encode[2];
  }

  /** Writes the pixel of the three input values, in BGR order, at {@code offset}. */
  void encode(float a, float b, float c, byte[] out, int offset) {
    out[offset] = encodeB[index(m6 * a + m7 * b + m8 * c)];
    out[offset + 1] = encodeG[index(m3 * a + m4 * b + m5 * c)];
    out[offset + 2] = encodeR[index(m0 * a + m1 * b + m2 * c)];
  }

  private static int index(float linear) {
    if (linear <= 0f) {
      return 0;
    }
    return linear >= 1f ? ENCODE_MAX : (int) (linear * ENCODE_MAX + 0.5f);
  }
}
