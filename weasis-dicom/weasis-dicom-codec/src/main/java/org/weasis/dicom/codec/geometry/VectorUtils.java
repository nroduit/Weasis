/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.geometry;

import org.joml.Vector3d;

public class VectorUtils {

  public static Vector3d computeNormalOfSurface(Vector3d v1, Vector3d v2) {
    Vector3d normal = v1.cross(v2, new Vector3d());
    return normal.lengthSquared() > 0.0 ? normal.normalize() : normal;
  }

  public static Vector3d computeNormalOfSurface(Vector3d origin, Vector3d v1, Vector3d v2) {
    Vector3d u = v1.sub(origin, new Vector3d());
    Vector3d w = v2.sub(origin, new Vector3d());
    Vector3d normal = u.cross(w);
    return normal.lengthSquared() > 0.0 ? normal.normalize() : normal;
  }

  /**
   * Flips the given normal in place so that its dominant component is positive in the DICOM LPS+
   * coordinate system (Left +X, Posterior +Y, Superior +Z).
   *
   * <p>The cross product (row × column) of the Image Orientation (Patient) vectors can point in
   * either direction for the same anatomical plane (e.g., +X or -X for sagittal). Forcing the
   * dominant axis to be positive yields a deterministic sign convention so that scalar projections
   * {@code dot(normal, IPP)} can be compared across slices and across series sharing the same Frame
   * of Reference.
   *
   * @param normal the normal vector to orient (modified in place)
   * @return the same vector, oriented so that its dominant axis component is non-negative
   */
  public static Vector3d orientNormalToDominantPositiveAxis(Vector3d normal) {
    if (normal == null) {
      return null;
    }
    double ax = Math.abs(normal.x);
    double ay = Math.abs(normal.y);
    double az = Math.abs(normal.z);
    double dominantComponent = ax >= ay && ax >= az ? normal.x : ay >= az ? normal.y : normal.z;
    if (dominantComponent < 0) {
      normal.negate();
    }
    return normal;
  }
}
