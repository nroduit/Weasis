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

import org.joml.Quaternionf;
import org.joml.Vector3f;

public enum CameraView implements View {
  INITIAL(
      new Quaternionf().rotationXYZ((float) Math.toRadians(290), 0, (float) Math.toRadians(120))),
  FRONT(new Quaternionf(0, 0, 0, 1)),
  LEFT(new Quaternionf(0, -rad(), 0, rad())),
  RIGHT(new Quaternionf(0, -rad(), 0, -rad())),
  BACK(new Quaternionf(0, 1, 0, 0)),
  TOP(new Quaternionf(rad(), 0, 0, rad())),
  BOTTOM(new Quaternionf(rad(), 0, 0, -rad()));

  private Quaternionf rotation;

  CameraView(Quaternionf rotation) {
    this.rotation = rotation;
  }

  @Override
  public Vector3f position() {
    return Camera.POSITION_ZERO;
  }

  @Override
  public float zoom() {
    return Camera.DEFAULT_ZOOM;
  }

  @Override
  public Quaternionf rotation() {
    return rotation;
  }

  private static float rad() {
    return (float) (.5f / org.joml.Math.sin(.25f * Math.PI));
  }
}
