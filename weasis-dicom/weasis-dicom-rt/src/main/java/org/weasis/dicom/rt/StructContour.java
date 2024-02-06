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
import org.weasis.opencv.seg.Segment;

public class StructContour extends SegContour {

  private Double positionZ;
  private double area;

  public StructContour(String id, List<Segment> segmentList) {
    super(id, segmentList);
    this.area = -1.0;
  }

  public StructContour(String id, List<Segment> segmentList, int numberOfPixels) {
    super(id, segmentList, numberOfPixels);
    this.area = -1.0;
  }

  public void setPositionZ(Double z) {
    this.positionZ = z;
  }

  public Double getPositionZ() {
    return this.positionZ;
  }

  public double getArea() {
    if (this.area < 0) {
      this.area = super.getArea();
    }
    return area;
  }
}
