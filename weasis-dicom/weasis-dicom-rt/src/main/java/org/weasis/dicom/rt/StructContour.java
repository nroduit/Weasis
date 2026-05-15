/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import java.util.List;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.seg.Segment;

/** RT-specific {@link SegContour} carrying its raw patient-coordinate points and slice Z. */
public class StructContour extends SegContour {

  private static final double UNCOMPUTED_AREA = -1.0;

  private Double positionZ;
  private double area = UNCOMPUTED_AREA;
  private double[] points;

  public StructContour(String id, List<Segment> segmentList) {
    super(id, segmentList);
  }

  public StructContour(String id, List<Segment> segmentList, int numberOfPixels) {
    super(id, segmentList, numberOfPixels);
  }

  /**
   * Creates a {@code StructContour} backed by a raster fractional mask (single-channel grayscale,
   * normalized to {@code [0, 255]}) instead of vector contours. Used for raster-based RT overlays
   * such as isodose regions, which are computed by thresholding the RT Dose grid.
   */
  public StructContour(String id, PlanarImage fractionalMask, double weightedPixelCount) {
    super(id, fractionalMask, weightedPixelCount);
  }

  public void setPositionZ(Double z) {
    this.positionZ = z;
  }

  public Double getPositionZ() {
    return positionZ;
  }

  @Override
  public double getArea() {
    if (area < 0) {
      area = super.getArea();
    }
    return area;
  }

  /**
   * Returns the raw RTSTRUCT contour points as a flat array of patient-coordinate triplets {@code
   * [x0, y0, z0, x1, y1, z1, ...]} in millimeters.
   *
   * @return the contour points, or {@code null} if not set
   */
  public double[] getPoints() {
    return points;
  }

  public void setPoints(double[] points) {
    this.points = points;
  }
}
