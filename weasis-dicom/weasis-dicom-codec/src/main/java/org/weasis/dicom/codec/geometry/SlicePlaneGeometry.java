/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.geometry;

import java.awt.geom.Point2D;
import java.util.Objects;
import org.joml.Vector3d;
import org.opencv.core.Point3;
import org.weasis.core.api.image.measure.PlaneGeometry;

/** {@link PlaneGeometry} of a DICOM slice, backed by its {@link GeometryOfSlice}. */
public record SlicePlaneGeometry(GeometryOfSlice geometry, String frameOfReferenceUID)
    implements PlaneGeometry {

  public SlicePlaneGeometry {
    Objects.requireNonNull(geometry);
  }

  @Override
  public String getFrameOfReferenceUID() {
    return frameOfReferenceUID;
  }

  @Override
  public Point3 getNormal() {
    return toPoint3(geometry.getNormal());
  }

  @Override
  public Point3 toPatient(Point2D imagePoint) {
    return toPoint3(geometry.getPosition(imagePoint));
  }

  @Override
  public Point2D toImage(Point3 patientPoint) {
    return geometry.getImagePosition(new Vector3d(patientPoint.x, patientPoint.y, patientPoint.z));
  }

  private static Point3 toPoint3(Vector3d v) {
    return new Point3(v.x, v.y, v.z);
  }
}
