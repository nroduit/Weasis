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

import org.joml.Vector3d;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

/**
 * The slices of a multiplanar reformat: an isotropic grid in one of the standard planes covering a
 * box of patient space, each slice a DICOM geometry (orientation of PS3.3 C.7.6.2: rows left to
 * right or anterior to posterior, columns anterior to posterior or superior to inferior).
 *
 * @param plane the plane of the slices
 * @param min the corner of the box with the smallest coordinates (left-posterior-superior frame)
 * @param max the opposite corner
 * @param spacing the isotropic pixel and slice spacing in mm
 */
public record ReformatGrid(Plane plane, Vector3d min, Vector3d max, double spacing) {

  public ReformatGrid {
    if (spacing <= 0 || !Double.isFinite(spacing)) {
      throw new IllegalArgumentException("The spacing is a positive length");
    }
    min = new Vector3d(min);
    max = new Vector3d(max);
  }

  /** The direction along the rows of a slice. */
  public Vector3d row() {
    return switch (plane) {
      case AXIAL, CORONAL -> new Vector3d(1, 0, 0);
      case SAGITTAL -> new Vector3d(0, 1, 0);
    };
  }

  /** The direction along the columns of a slice. */
  public Vector3d column() {
    return switch (plane) {
      case AXIAL -> new Vector3d(0, 1, 0);
      case CORONAL, SAGITTAL -> new Vector3d(0, 0, -1);
    };
  }

  /** The direction the slices follow, the normal of the plane. */
  public Vector3d normal() {
    return plane.getDirection();
  }

  public int width() {
    return count(row());
  }

  public int height() {
    return count(column());
  }

  /** The number of slices across the box. */
  public int slices() {
    return count(normal());
  }

  private int count(Vector3d direction) {
    double extent = Math.abs(direction.dot(max) - direction.dot(min));
    return Math.max(1, (int) Math.round(extent / spacing) + 1);
  }

  /** The position of the top-left pixel of slice {@code index}, from 0. */
  public Vector3d tlhc(int index) {
    Vector3d row = row();
    Vector3d column = column();
    Vector3d normal = normal();
    Vector3d tlhc = new Vector3d();
    for (int axis = 0; axis < 3; axis++) {
      double start;
      if (normal.get(axis) != 0) {
        start = min.get(axis) + index * spacing;
      } else if (row.get(axis) < 0 || column.get(axis) < 0) {
        start = max.get(axis);
      } else {
        start = min.get(axis);
      }
      tlhc.setComponent(axis, start);
    }
    return tlhc;
  }

  /** The geometry of slice {@code index}, from 0. */
  public GeometryOfSlice geometry(int index) {
    return new GeometryOfSlice(
        row(),
        column(),
        tlhc(index),
        new Vector3d(spacing, spacing, spacing),
        spacing,
        new Vector3d(height(), width(), 1)); // rows, then columns
  }

  /** Image Orientation (Patient) of every slice. */
  public double[] orientation() {
    Vector3d row = row();
    Vector3d column = column();
    return new double[] {row.x, row.y, row.z, column.x, column.y, column.z};
  }

  /** The grid covering the box of a volume at that spacing, in that plane. */
  public static ReformatGrid of(Vector3d[] corners, double spacing, Plane plane) {
    Vector3d min = new Vector3d(Double.MAX_VALUE);
    Vector3d max = new Vector3d(-Double.MAX_VALUE);
    for (Vector3d corner : corners) {
      min.min(corner);
      max.max(corner);
    }
    return new ReformatGrid(plane, min, max, spacing);
  }
}
