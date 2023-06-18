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

import static org.weasis.dicom.viewer3d.geometry.Camera.getQuaternion;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.weasis.dicom.codec.geometry.PatientOrientation.Biped;
import org.weasis.dicom.viewer3d.Messages;

public enum CameraView implements View {
  INITIAL(Messages.getString("default"), getQuaternion(-75, 0, 15)),
  FRONT(Biped.A.getFullName(), getQuaternion(-90, 0, 0)),
  BACK(Biped.P.getFullName(), getQuaternion(-90, 0, 180)),
  TOP(Biped.H.getFullName(), getQuaternion(0, 0, 180)),
  BOTTOM(Biped.F.getFullName(), getQuaternion(180, 0, 0)),
  LEFT(Biped.L.getFullName(), getQuaternion(-90, 0, -90)),
  RIGHT(Biped.R.getFullName(), getQuaternion(-90, 0, 90));

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
  public Vector3d position() {
    return Camera.POSITION_ZERO;
  }

  @Override
  public double zoom() {
    return Camera.DEFAULT_ZOOM;
  }

  @Override
  public String toString() {
    return title;
  }

  @Override
  public Quaterniond rotation() {
    return rotation;
  }

  public static CameraView getCameraView(String view) {
    try {
      return CameraView.valueOf(view);
    } catch (Exception ignore) {
      // Do nothing
    }
    return INITIAL;
  }
}
