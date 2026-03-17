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
import org.joml.Vector3i;

/**
 * Helper record to hold computed volume bounds from DICOM geometry. Includes shear factors for
 * volume rectification.
 */
public record VolumeBounds(
    Vector3i size,
    Vector3d spacing,
    Vector3d origin,
    Vector3d rowDir,
    Vector3d colDir,
    Vector3d normalDir) {

  public static final double EPSILON = 1e-2;
}
