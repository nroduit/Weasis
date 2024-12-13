/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.line.LineWithGapGraphic;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlType(name = "CrossLine")
@XmlRootElement(name = "CrossLine")
public class CrossLineGraphic extends LineWithGapGraphic {

  private int extendLength = 0;

  public CrossLineGraphic() {
    super();
  }

  public CrossLineGraphic(CrossLineGraphic graphic) {
    super(graphic);
  }

  @Override
  public CrossLineGraphic copy() {
    return new CrossLineGraphic(this);
  }

  @Override
  protected void initCopy(Graphic graphic) {
    super.initCopy(graphic);
  }

  public int getExtendLength() {
    return extendLength;
  }

  public void setExtendLength(int extendLength) {
    this.extendLength = extendLength;
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    updateTool();
    AdvancedShape newShape = null;

    if (lineABvalid) {
      if (centerGap == null) {
        centerGap = GeomUtil.getColinearPointWithRatio(ptA, ptB, 0.5);
      }
      double dist = ptA.distance(ptB);
      double distCenterGap = ptA.distance(centerGap);
      double distCenterB = ptB.distance(centerGap);

      Point2D ptap = null;
      Point2D ptbp = null;
      if (distCenterGap < dist && distCenterB < dist) {
        double distGap = 0.5 * gapSize / dist;
        ptap = GeomUtil.getColinearPointWithRatio(ptA, ptB, distCenterGap / dist - distGap);
        ptbp = GeomUtil.getColinearPointWithRatio(ptA, ptB, distCenterGap / dist + distGap);
      }

      Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 4);
      path.moveTo(ptA.getX(), ptA.getY());
      if (ptap != null) {
        path.lineTo(ptap.getX(), ptap.getY());
        path.moveTo(ptbp.getX(), ptbp.getY());
      }
      path.lineTo(ptB.getX(), ptB.getY());

      newShape = new AdvancedShape(this, 2);
      newShape.addShape(path);

      Path2D arrow = new Path2D.Double();
      double arrowLength = 15;
      double angle = Math.atan2(ptA.getY() - ptB.getY(), ptA.getX() - ptB.getX());
      arrow.moveTo(ptA.getX(), ptA.getY());
      arrow.lineTo(
          ptA.getX() - arrowLength * Math.cos(angle - Math.PI / 6),
          ptA.getY() - arrowLength * Math.sin(angle - Math.PI / 6));
      arrow.moveTo(ptA.getX(), ptA.getY());
      arrow.lineTo(
          ptA.getX() - arrowLength * Math.cos(angle + Math.PI / 6),
          ptA.getY() - arrowLength * Math.sin(angle + Math.PI / 6));
      newShape.addAllInvShape(arrow, ptA, getStroke(lineThickness), false);

      if (extendLength > 0) {
        Line2D lineExt1 = GeomUtil.getParallelLine(ptA, ptB, extendLength);
        Line2D lineExt2 = GeomUtil.getParallelLine(ptB, ptA, extendLength);

        newShape.addShape(lineExt1, getDashStroke(1.0f), true);
        newShape.addShape(lineExt2, getDashStroke(1.0f), true);
      }
    }

    setShape(newShape, mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
  }
}
