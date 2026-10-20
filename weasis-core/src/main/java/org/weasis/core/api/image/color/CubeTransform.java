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
 * The conversion sampled on a regular RGB grid and interpolated trilinearly per pixel. It expresses
 * any source profile, LUT-based ones included.
 *
 * <p>When the destination is a matrix/TRC profile, the grid holds the unclipped XYZ of the source
 * and the destination matrix and encode tables are applied per pixel: interpolating colors already
 * clipped to the destination gamut errs by tens of levels next to the gamut boundary. Otherwise the
 * grid holds the 8-bit destination values.
 */
final class CubeTransform implements ColorTransform {
  /** Nodes per axis: 33 keeps the error within one 8-bit level, 17 triples the mean error. */
  static final int GRID = 33;

  private static final int[] BASE = new int[256];
  private static final float[] FRACTION = new float[256];

  static {
    for (int v = 0; v < 256; v++) {
      float position = v * (GRID - 1) / 255f;
      int base = Math.min((int) position, GRID - 2);
      BASE[v] = base;
      FRACTION[v] = position - base;
    }
  }

  private final float[] nodes;
  private final DestinationStage destination;

  /**
   * @param nodes {@code GRID³} triplets, red the slowest axis and blue the fastest: XYZ when {@code
   *     destination} is given, otherwise 8-bit RGB values
   * @param destination XYZ to destination conversion, or null
   */
  CubeTransform(float[] nodes, DestinationStage destination) {
    if (nodes.length != GRID * GRID * GRID * 3) {
      throw new IllegalArgumentException("Expected " + GRID + "³ nodes");
    }
    this.nodes = nodes.clone();
    this.destination = destination;
  }

  @Override
  public Kind kind() {
    return Kind.CUBE;
  }

  @Override
  public void convertRow(byte[] in, byte[] out, int length) {
    float[] n = nodes;
    float[] value = new float[3];
    int strideG = GRID * 3;
    int strideR = GRID * GRID * 3;
    for (int i = 0; i < length; i += 3) {
      int b = in[i] & 0xFF;
      int g = in[i + 1] & 0xFF;
      int r = in[i + 2] & 0xFF;
      float fr = FRACTION[r];
      float fg = FRACTION[g];
      float fb = FRACTION[b];
      int c000 = BASE[r] * strideR + BASE[g] * strideG + BASE[b] * 3;
      int c001 = c000 + 3;
      int c010 = c000 + strideG;
      int c011 = c010 + 3;
      int c100 = c000 + strideR;
      int c101 = c100 + 3;
      int c110 = c100 + strideG;
      int c111 = c110 + 3;
      for (int c = 0; c < 3; c++) {
        float x00 = n[c000 + c] + (n[c001 + c] - n[c000 + c]) * fb;
        float x01 = n[c010 + c] + (n[c011 + c] - n[c010 + c]) * fb;
        float x10 = n[c100 + c] + (n[c101 + c] - n[c100 + c]) * fb;
        float x11 = n[c110 + c] + (n[c111 + c] - n[c110 + c]) * fb;
        float y0 = x00 + (x01 - x00) * fg;
        float y1 = x10 + (x11 - x10) * fg;
        value[c] = y0 + (y1 - y0) * fr;
      }
      if (destination == null) {
        // RGB nodes into BGR pixels
        out[i] = (byte) (int) (value[2] + 0.5f);
        out[i + 1] = (byte) (int) (value[1] + 0.5f);
        out[i + 2] = (byte) (int) (value[0] + 0.5f);
      } else {
        destination.encode(value[0], value[1], value[2], out, i);
      }
    }
  }
}
