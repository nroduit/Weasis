/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.measure;

import java.awt.geom.Point2D;
import org.opencv.core.Point3;

/**
 * Position of an image plane in patient space: converts image pixels to patient coordinates (DICOM
 * LPS, millimetres) and back. Provided by images that know their orientation and position.
 */
public interface PlaneGeometry {

  /** Frame of reference the coordinates belong to, or {@code null} when unknown. */
  String getFrameOfReferenceUID();

  /** Unit normal of the plane in patient space. */
  Point3 getNormal();

  Point3 toPatient(Point2D imagePoint);

  /** Projection of a patient point onto the plane, in image pixels. */
  Point2D toImage(Point3 patientPoint);
}
