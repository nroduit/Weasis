/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.seg;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.seg.Region;
import org.weasis.opencv.seg.Segment;

public class SegContour extends Region {
  private PlanarImage fractionalMask;

  public SegContour(String id, List<Segment> segmentList) {
    super(id, segmentList);
  }

  public SegContour(String id, List<Segment> segmentList, int numberOfPixels) {
    super(id, segmentList, numberOfPixels);
  }

  /**
   * Creates a SegContour carrying a fractional (probability/occupancy) mask instead of vector
   * contours. The mask is a single-channel image (CV_8UC1, CV_16UC1, or CV_32FC1) with values
   * normalized to {@code [0, maxFractionalValue]}. A LUT is applied at render time to colorize the
   * overlay.
   *
   * @param id the contour identifier
   * @param fractionalMask a single-channel image with fractional values
   * @param weightedPixelCount the weighted sum of fractional pixel values (Σ pixel/maxFrac)
   */
  public SegContour(String id, PlanarImage fractionalMask, double weightedPixelCount) {
    super(id, List.of(), 0);
    this.fractionalMask = fractionalMask;
    this.numberOfPixels = Math.round(weightedPixelCount);
  }

  /**
   * Returns the fractional mask image (single-channel grayscale), or {@code null} for binary
   * segmentations.
   */
  public PlanarImage getFractionalMask() {
    return fractionalMask;
  }

  public void setFractionalMask(PlanarImage fractionalMask) {
    this.fractionalMask = fractionalMask;
  }

  /** Returns true if this contour carries a raster fractional mask rather than vector contours. */
  public boolean isFractional() {
    return fractionalMask != null;
  }

  public SegGraphic getSegGraphic() {
    if (segmentList.isEmpty() || !attributes.isVisible()) {
      return null;
    }
    Path2D path = buildPath();
    if (path == null) {
      return null;
    }
    var graphic = new SegGraphic(path);
    graphic.setFilled(attributes.isFilled());
    graphic.setLineThickness(attributes.getLineThickness());
    graphic.setFillOpacity(attributes.getInteriorOpacity());
    graphic.setPaint(attributes.getColor());
    graphic.setLayerType(LayerType.DICOM_SEG);
    return graphic;
  }

  private Path2D buildPath() {
    var path = new Path2D.Double(Path2D.WIND_NON_ZERO);
    for (Segment segment : segmentList) {
      addRecursivelySegment(path, segment);
    }
    return path.getCurrentPoint() == null ? null : path;
  }

  private void addRecursivelySegment(Path2D path, Segment segment) {
    addPointsToPath(path, segment);
    for (Segment hole : segment.getChildren()) {
      addRecursivelySegment(path, hole);
    }
  }

  private void addPointsToPath(Path2D path, Segment segment) {
    if (segment.isEmpty()) {
      return;
    }
    Point2D p = segment.getFirst();
    path.moveTo(p.getX(), p.getY());
    for (int i = 1, n = segment.size(); i < n; i++) {
      p = segment.get(i);
      path.lineTo(p.getX(), p.getY());
    }
    path.closePath();
  }
}
