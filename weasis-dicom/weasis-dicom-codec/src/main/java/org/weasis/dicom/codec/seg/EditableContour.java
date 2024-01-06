/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;
import org.dcm4che3.img.data.Region;
import org.dcm4che3.img.data.Segment;
import org.weasis.core.ui.model.graphic.imp.NonEditableGraphic;
import org.weasis.core.ui.model.layer.LayerType;

public class EditableContour extends Region {

  public EditableContour(String id, List<Segment> segmentList) {
    super(id, segmentList);
  }

  public NonEditableGraphic getNonEditableGraphic() {
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

    NonEditableGraphic graphic = new NonEditableGraphic(path);
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
}
