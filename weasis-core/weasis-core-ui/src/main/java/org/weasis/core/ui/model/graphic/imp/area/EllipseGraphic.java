/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.area;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.util.MathUtil;

@XmlType(name = "ellipse")
@XmlRootElement(name = "ellipse")
public class EllipseGraphic extends ObliqueRectangleGraphic {

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_ELLIPSE);

  public static final Measurement AREA =
      new Measurement(Messages.getString("measure.area"), 1, true, true, true);
  public static final Measurement PERIMETER =
      new Measurement(Messages.getString("measure.perimeter"), 2, true, true, false);
  public static final Measurement CENTER_X =
      new Measurement(Messages.getString("measure.centerx"), 3, true, true, false);
  public static final Measurement CENTER_Y =
      new Measurement(Messages.getString("measure.centery"), 4, true, true, false);
  public static final Measurement WIDTH =
      new Measurement(Messages.getString("measure.width"), 5, true, true, false);
  public static final Measurement HEIGHT =
      new Measurement(Messages.getString("measure.height"), 6, true, true, false);
  public static final Measurement ORIENTATION =
      new Measurement(Messages.getString("measure.orientation"), 7, true, true, false);

  protected static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();

  static {
    MEASUREMENT_LIST.add(CENTER_X);
    MEASUREMENT_LIST.add(CENTER_Y);
    MEASUREMENT_LIST.add(AREA);
    MEASUREMENT_LIST.add(PERIMETER);
    MEASUREMENT_LIST.add(ORIENTATION);
    MEASUREMENT_LIST.add(WIDTH);
    MEASUREMENT_LIST.add(HEIGHT);
  }

  public EllipseGraphic() {
    super();
  }

  public EllipseGraphic(EllipseGraphic graphic) {
    super(graphic);
  }

  @Override
  public EllipseGraphic copy() {
    return new EllipseGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("MeasureToolBar.ellipse");
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    updateTool();

    Path2D polygonPath = new Path2D.Double(Path2D.WIND_NON_ZERO, pts.size());

    if (lineABvalid) {
      polygonPath.moveTo(ptA.getX(), ptA.getY());
      if (lineCDvalid) {
        double dist = ptC.distance(ptD);
        double a = ptA.distance(ptB) / 2.0;
        double b = dist / 2.0;

        Point2D ptx = GeomUtil.getPerpendicularPointFromLine(ptA, ptB, ptC, b);
        double widthTwoThirds = a * 4 / 3;
        double rotationAngle = Math.atan2(ptA.getY() - ptB.getY(), ptA.getX() - ptB.getX());
        double dx1 = Math.sin(rotationAngle) * b;
        double dy1 = Math.cos(rotationAngle) * b;
        double dx2 = Math.cos(rotationAngle) * widthTwoThirds;
        double dy2 = Math.sin(rotationAngle) * widthTwoThirds;

        double topCenterX = ptx.getX() - dx1;
        double topCenterY = ptx.getY() + dy1;
        double topRightX = topCenterX + dx2;
        double topRightY = topCenterY + dy2;
        double topLeftX = topCenterX - dx2;
        double topLeftY = topCenterY - dy2;

        double bottomCenterX = ptx.getX() + dx1;
        double bottomCenterY = ptx.getY() - dy1;
        double bottomRightX = bottomCenterX + dx2;
        double bottomRightY = bottomCenterY + dy2;
        double bottomLeftX = bottomCenterX - dx2;
        double bottomLeftY = bottomCenterY - dy2;

        polygonPath.moveTo(bottomCenterX, bottomCenterY);
        polygonPath.curveTo(
            bottomRightX, bottomRightY, topRightX, topRightY, topCenterX, topCenterY);
        polygonPath.curveTo(
            topLeftX, topLeftY, bottomLeftX, bottomLeftY, bottomCenterX, bottomCenterY);
      } else {
        polygonPath.lineTo(ptB.getX(), ptB.getY());
      }
      polygonPath.closePath();
    }
    setShape(polygonPath, mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
  }

  @Override
  public List<MeasureItem> computeMeasurements(
      MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {

    if (layer != null && layer.hasContent() && isShapeValid()) {
      MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

      if (adapter != null) {
        double ab = ptA.distance(ptB);
        double cd = ptC.distance(ptD);
        Point2D center = GeomUtil.getMidPoint(ptC, ptD);

        ArrayList<MeasureItem> measVal = new ArrayList<>();

        double ratio = adapter.getCalibRatio();
        String unitStr = adapter.getUnit();

        if (CENTER_X.getComputed()) {
          measVal.add(
              new MeasureItem(CENTER_X, adapter.getXCalibratedValue(center.getX()), unitStr));
        }
        if (CENTER_Y.getComputed()) {
          measVal.add(
              new MeasureItem(CENTER_Y, adapter.getYCalibratedValue(center.getY()), unitStr));
        }
        if (WIDTH.getComputed()) {
          measVal.add(new MeasureItem(WIDTH, ab * ratio, unitStr));
        }
        if (HEIGHT.getComputed()) {
          measVal.add(new MeasureItem(HEIGHT, cd * ratio, unitStr));
        }
        if (ORIENTATION.getComputed()) {
          measVal.add(
              new MeasureItem(
                  ORIENTATION,
                  MathUtil.getOrientation(ptA, ptB),
                  Messages.getString("measure.deg")));
        }
        if (AREA.getComputed()) {
          Double val = Math.PI * ab * ratio * cd * ratio / 4.0;
          String unit =
              "pix".equals(unitStr) // NON-NLS
                  ? unitStr
                  : unitStr + "2";
          measVal.add(new MeasureItem(AREA, val, unit));
        }
        if (PERIMETER.getComputed()) {
          double a = ratio * ab / 2.0;
          double b = ratio * cd / 2.0;
          Double val = 2.0 * Math.PI * Math.sqrt((a * a + b * b) / 2.0);
          measVal.add(new MeasureItem(PERIMETER, val, unitStr));
        }

        List<MeasureItem> stats = getImageStatistics(layer, releaseEvent);
        if (stats != null) {
          measVal.addAll(stats);
        }
        return measVal;
      }
    }
    return Collections.emptyList();
  }

  @Override
  public List<Measurement> getMeasurementList() {
    return MEASUREMENT_LIST;
  }

  @Override
  public List<Point2D> getRectanglePointList() {
    updateTool();
    List<Point2D> pts = new ArrayList<>();
    if (lineABvalid && lineCDvalid) {
      Point2D n = new Point2D.Double(ptC.getX(), ptC.getY());
      Point2D s = new Point2D.Double(ptD.getX(), ptD.getY());
      Line2D we = GeomUtil.getParallelLine(ptA, ptB, ptC.distance(ptD) / 2.0);
      if (ptA.getX() > ptB.getX()) {
        pts.add(n);
        pts.add(s);
        pts.add(we.getP1());
        pts.add(we.getP2());
      } else {
        pts.add(we.getP1());
        pts.add(we.getP2());
        pts.add(n);
        pts.add(s);
      }
    }
    return pts;
  }
}
