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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.line.LineWithGapGraphic;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.AdvancedShape.InvariantShape;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.dicom.viewer2d.mpr.MprController.ControlPoints;

@XmlType(name = "CrossLine")
@XmlRootElement(name = "CrossLine")
public class CrossLineGraphic extends LineWithGapGraphic {

  private int extendLength = 0;
  private MprView mprView;

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
        centerGap = GeomUtil.getCollinearPointWithRatio(ptA, ptB, 0.5);
      }
      double dist = ptA.distance(ptB);
      double distCenterGap = ptA.distance(centerGap);
      double distCenterB = ptB.distance(centerGap);

      Point2D ptap = null;
      Point2D ptbp = null;
      if (distCenterGap < dist && distCenterB < dist) {
        double distGap = 0.5 * gapSize / dist;
        ptap = GeomUtil.getCollinearPointWithRatio(ptA, ptB, distCenterGap / dist - distGap);
        ptbp = GeomUtil.getCollinearPointWithRatio(ptA, ptB, distCenterGap / dist + distGap);
      }

      Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 4);
      path.moveTo(ptA.getX(), ptA.getY());
      if (ptap != null) {
        path.lineTo(ptap.getX(), ptap.getY());
        path.moveTo(ptbp.getX(), ptbp.getY());
      }
      path.lineTo(ptB.getX(), ptB.getY());

      newShape = new AdvancedShape(this, 3);
      newShape.addShape(path);

      if (mprView != null) {
        Line2D line = new Line2D.Double(ptA, ptB);
        ControlPoints ctrlPts = mprView.getControlPoints(line, centerGap);
        double size = lineThickness == 3.f ? 15 : 9;
        for (Point2D pt : ctrlPts.getPointList()) {
          Ellipse2D ellipse =
              new Ellipse2D.Double(pt.getX() - size / 2.0f, pt.getY() - size / 2.0f, size, size);

          InvariantShape ctrlShape = newShape.addAllInvShape(ellipse, pt, getStroke(1.0f), true);
          ctrlShape.setFilled(true);
        }

        Path2D arrow = new Path2D.Double();
        double arrowLength = 20;
        Line2D line1 = ctrlPts.getLine();
        Point2D p1 = line1.getP1();
        Point2D p2 = line1.getP2();
        double angle = Math.atan2(p1.getY() - p2.getY(), p1.getX() - p2.getX());
        arrow.moveTo(p1.getX(), p1.getY());
        arrow.lineTo(
            p1.getX() - arrowLength * Math.cos(angle - Math.PI / 6),
            p1.getY() - arrowLength * Math.sin(angle - Math.PI / 6));
        arrow.moveTo(p1.getX(), p1.getY());
        arrow.lineTo(
            p1.getX() - arrowLength * Math.cos(angle + Math.PI / 6),
            p1.getY() - arrowLength * Math.sin(angle + Math.PI / 6));
        newShape.addScaleInvShape(arrow, p1, getStroke(lineThickness), false);

        if (extendLength > 0) {
          // Show the thickness of the slice
          Line2D lineExt1 = GeomUtil.getParallelLine(p1, p2, extendLength);
          Line2D lineExt2 = GeomUtil.getParallelLine(p2, p1, extendLength);

          newShape.addShape(lineExt1, getDashStroke(1.0f), true);
          newShape.addShape(lineExt2, getDashStroke(1.0f), true);
        }
      }
    }

    setShape(newShape, mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
  }

  public void setMprView(MprView mprView) {
    this.mprView = mprView;
  }
}
