/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.gl;

/**
 * The phantom the reference frames are rendered from: a purely arithmetic 16-bit volume, so every
 * machine and every binding starts from the same voxels without a DICOM file.
 *
 * <p>It is built to exercise what the ray-caster actually does: a soft sphere with a radial ramp
 * (composite opacity and the colour map), a dense shell around it (iso-surface and MIP), three
 * orthogonal rods of distinct values (window/level and the MIP flavours), and a smooth
 * three-dimensional ripple that keeps gradients everywhere non-zero, so shading, ambient occlusion
 * and the path tracer have something to integrate. Values sit in the stored range of a CT volume
 * ({@code HU + 1024}), which is what puts them on the colourful part of a CT colour map.
 *
 * <p>The companion label volume gives the segmentation overlay two touching regions.
 */
final class SyntheticVolume {

  /** Cubic so the normalized texel size is (1, 1, 1) and the camera needs no anisotropy. */
  static final int SIZE = 128;

  static final int VALUE_MIN = 0;

  /** Highest value written, and the {@code inputLevelMax} the reference frames use. */
  static final int VALUE_MAX = 4095;

  private SyntheticVolume() {}

  /** Voxels in x-fastest order, the layout {@code glTexSubImage3D} expects. */
  static short[] values() {
    short[] voxels = new short[SIZE * SIZE * SIZE];
    for (int z = 0; z < SIZE; z++) {
      double nz = normalized(z);
      for (int y = 0; y < SIZE; y++) {
        double ny = normalized(y);
        for (int x = 0; x < SIZE; x++) {
          voxels[(z * SIZE + y) * SIZE + x] = (short) value(normalized(x), ny, nz);
        }
      }
    }
    return voxels;
  }

  /** Storage ids of two touching segments: 1 inside the ellipsoid, 2 in the shell. */
  static byte[] labels() {
    byte[] labels = new byte[SIZE * SIZE * SIZE];
    for (int z = 0; z < SIZE; z++) {
      double nz = normalized(z);
      for (int y = 0; y < SIZE; y++) {
        double ny = normalized(y);
        for (int x = 0; x < SIZE; x++) {
          double nx = normalized(x);
          double r = Math.sqrt(nx * nx + ny * ny + nz * nz);
          byte id = 0;
          if (r < 0.30) {
            id = 1;
          } else if (r > 0.40 && r < 0.46 && nz > 0) {
            id = 2;
          }
          labels[(z * SIZE + y) * SIZE + x] = id;
        }
      }
    }
    return labels;
  }

  // Normalized coordinate in [-0.5, 0.5].
  private static double normalized(int i) {
    return i / (double) (SIZE - 1) - 0.5;
  }

  private static int value(double x, double y, double z) {
    double r = Math.sqrt(x * x + y * y + z * z);
    int value = 0;
    if (r < 0.42) {
      // Radial ramp across the whole transfer function, so one ray integrates every material the
      // colour map defines, from the transparent low values to the opaque high ones.
      double ripple =
          Math.sin(8 * Math.PI * x) * Math.sin(8 * Math.PI * y) * Math.sin(8 * Math.PI * z);
      value = (int) (400 + 2200 * (1.0 - r / 0.42) + 60 * ripple);
    }
    if (r > 0.44 && r < 0.47 && z < 0) {
      // Half a dense shell: it gives the iso-surface a closed surface to follow and the composite
      // a solid back behind the ramp, while leaving the upper half open to see into.
      value = 1000;
    }
    // Three orthogonal rods, each at its own material, reaching past the sphere.
    if (Math.abs(y) < 0.04 && Math.abs(z) < 0.04) {
      value = Math.max(value, 1000);
    }
    if (Math.abs(x) < 0.04 && Math.abs(z) < 0.04) {
      value = Math.max(value, 1850);
    }
    if (Math.abs(x) < 0.04 && Math.abs(y) < 0.04) {
      value = Math.max(value, 2600);
    }
    return Math.clamp(value, VALUE_MIN, VALUE_MAX);
  }
}
