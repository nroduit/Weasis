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
    return v1.cross(v2, new Vector3d()).normalize();
  }

  public static Vector3d computeNormalOfSurface(Vector3d origin, Vector3d v1, Vector3d v2) {
    Vector3d u = v1.sub(origin, new Vector3d());
    Vector3d w = v2.sub(origin, new Vector3d());
    return u.cross(w).normalize();
  }
}
