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
import org.joml.Vector3f;
import org.weasis.dicom.codec.geometry.PatientOrientation.Biped;

public enum CameraView implements View {
  INITIAL("Default", new Quaterniond().rotationXYZ(-Math.toRadians(90), 0, Math.toRadians(15))),
  FRONT(Biped.A.getFullName(), new Quaterniond().rotationXYZ(-Math.toRadians(90), 0, 0)),
  BACK(
      Biped.P.getFullName(),
      new Quaterniond().rotationXYZ(-Math.toRadians(90), 0, Math.toRadians(180))),
  TOP(Biped.H.getFullName(), new Quaterniond().rotationXYZ(0, 0, Math.toRadians(180))),
  BOTTOM(Biped.F.getFullName(), new Quaterniond().rotationXYZ(Math.toRadians(180), 0, 0)),
  LEFT(
      Biped.L.getFullName(),
      new Quaterniond().rotationXYZ(-Math.toRadians(90), 0, -Math.toRadians(90))),
  RIGHT(
      Biped.R.getFullName(),
      new Quaterniond().rotationXYZ(-Math.toRadians(90), 0, Math.toRadians(90)));

  private Quaterniond rotation;

  private String title;

  CameraView(String title, Quaterniond rotation) {
    this.title = title;
    this.rotation = rotation;
  }

  public String title() {
    return title;
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
  public Quaterniond rotation() {
    return rotation;
  }
}
