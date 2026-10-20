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
 * The conversion between two matrix/TRC profiles: one decode table per channel to the linear light
 * of the source, then the {@link DestinationStage} whose matrix goes from source to destination
 * linear light.
 */
final class MatrixShaperTransform implements ColorTransform {
  private final float[] decodeR;
  private final float[] decodeG;
  private final float[] decodeB;
  private final DestinationStage destination;

  /**
   * @param decode three tables of 256 linear values (R, G, B) of the source
   */
  MatrixShaperTransform(float[][] decode, DestinationStage destination) {
    this.decodeR = decode[0];
    this.decodeG = decode[1];
    this.decodeB = decode[2];
    this.destination = destination;
  }

  @Override
  public Kind kind() {
    return Kind.MATRIX;
  }

  @Override
  public void convertRow(byte[] in, byte[] out, int length) {
    for (int i = 0; i < length; i += 3) {
      destination.encode(
          decodeR[in[i + 2] & 0xFF], decodeG[in[i + 1] & 0xFF], decodeB[in[i] & 0xFF], out, i);
    }
  }
}
