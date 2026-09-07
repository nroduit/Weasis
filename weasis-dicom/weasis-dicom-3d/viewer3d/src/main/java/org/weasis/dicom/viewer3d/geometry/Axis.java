/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.geometry;

import org.joml.Vector3d;
import org.joml.Vector3dc;

public enum Axis {
  X(1, 0, 0),
  Y(0, 1, 0),
  Z(0, 0, 1);

  private final Vector3dc direction;

  Axis(double x, double y, double z) {
    this.direction = new Vector3d(x, y, z);
  }

  /** Unit vector of this axis in the volume frame. */
  public Vector3dc direction() {
    return direction;
  }
}
