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
import org.weasis.opencv.seg.Region;
import org.weasis.opencv.seg.Segment;

public class SegContour extends Region {
  private double[] points;

  public SegContour(String id, List<Segment> segmentList) {
    super(id, segmentList);
  }

  public SegContour(String id, List<Segment> segmentList, int numberOfPixels) {
    super(id, segmentList, numberOfPixels);
  }

  public SegGraphic getSegGraphic() {
    if (segmentList.isEmpty() || !attributes.isVisible()) {
      return null;
    }

    Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO);
    for (Segment segment : segmentList) {
      addRecursivelySegment(path, segment);
    }

    if (path.getCurrentPoint() == null) {
      return null;
    }

    SegGraphic graphic = new SegGraphic(path);
    graphic.setFilled(attributes.isFilled());
    graphic.setLineThickness(attributes.getLineThickness());
    graphic.setFillOpacity(attributes.getInteriorOpacity());
    graphic.setPaint(attributes.getColor());
    graphic.setLayerType(LayerType.DICOM_SEG);
    return graphic;
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
    for (int i = 1; i < segment.size(); i++) {
      p = segment.get(i);
      path.lineTo(p.getX(), p.getY());
    }
    path.closePath(); // TODO check if it is necessary
  }

  public double[] getPoints() {
    return points;
  }

  public void setPoints(double[] points) {
    this.points = points;
  }
}
