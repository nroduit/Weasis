/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import org.joml.Matrix4d;
import org.joml.Vector3d;

/**
 * Where the pixel (x, y) of one source slice lands in the volume, in voxel units. The terms that do
 * not depend on (x, y) are computed once per slice; the rest is the arithmetic of {@code
 * Vector4d.mulGeneric} with w = 1, through {@code org.joml.Math.fma} as JOML does (a plain multiply
 * and add unless JOML is told to fuse), so a position is the one JOML gives, to the last bit.
 */
final class SlicePlacement {
  private final double m00;
  private final double m10;
  private final double m01;
  private final double m11;
  private final double m02;
  private final double m12;
  private final double cx;
  private final double cy;
  private final double cz;
  private final Vector3d divisor;
  private final boolean axial;
  private final int sizeZ;

  /**
   * @param z the z of the source pixel: the slice index for a basic volume, 0 otherwise
   * @param pixelRatio mm per voxel, or null when the transform already gives voxel units (basic)
   * @param axial whether z is mirrored as for an axial stack, else counted from {@code sizeZ}
   */
  SlicePlacement(Matrix4d t, double z, Vector3d pixelRatio, boolean axial, int sizeZ) {
    m00 = t.m00();
    m10 = t.m10();
    m01 = t.m01();
    m11 = t.m11();
    m02 = t.m02();
    m12 = t.m12();
    // The innermost term of mulGeneric, fma(m2i, z, m3i * w), is constant over the slice
    cx = org.joml.Math.fma(t.m20(), z, t.m30());
    cy = org.joml.Math.fma(t.m21(), z, t.m31());
    cz = org.joml.Math.fma(t.m22(), z, t.m32());
    divisor = pixelRatio == null ? null : new Vector3d(pixelRatio);
    this.axial = axial;
    this.sizeZ = sizeZ;
  }

  double x(int x, int y) {
    double v = org.joml.Math.fma(m00, x, org.joml.Math.fma(m10, y, cx));
    return divisor == null ? v : v / divisor.x;
  }

  double y(int x, int y) {
    double v = org.joml.Math.fma(m01, x, org.joml.Math.fma(m11, y, cy));
    return divisor == null ? v : v / divisor.y;
  }

  double z(int x, int y) {
    double v = org.joml.Math.fma(m02, x, org.joml.Math.fma(m12, y, cz));
    if (divisor == null) {
      return v;
    }
    v = v / divisor.z;
    return axial ? -v : sizeZ - v;
  }

  /** {@code (int) Math.floor(v)} without the call, for any double. */
  static int floor(double v) {
    int i = (int) v;
    return v < i && i != Integer.MIN_VALUE ? i - 1 : i;
  }
}
