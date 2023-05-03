/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.geometry;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.weasis.dicom.codec.geometry.ImageOrientation.Plan;

public interface View {
  String title();

  Vector3d position();

  double zoom();

  Quaterniond rotation();

  default Quaterniond rotation(Plan plan) {
    if (plan == Plan.CORONAL) {
      return new Quaterniond(rotation()).rotateX(Math.toRadians(270));
    } else if (plan == Plan.SAGITTAL) {
      return new Quaterniond(rotation()).rotateX(Math.toRadians(270)).rotateY(Math.toRadians(270));
    }
    return rotation();
  }
}
