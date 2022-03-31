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
import java.awt.Point;
import java.awt.Robot;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.AbstractDragGraphicArea;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphic.eHandlePoint;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.util.MathUtil;

@XmlType(name = "rectangle")
@XmlRootElement(name = "rectangle")
public class ObliqueRectangleGraphic extends AbstractDragGraphicArea {

  public static final Integer POINTS_NUMBER = 4;

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_RECTANGLE);

  public static final Measurement AREA =
      new Measurement(Messages.getString("measure.area"), 1, true, true, true);
  public static final Measurement PERIMETER =
      new Measurement(Messages.getString("measure.perimeter"), 2, true, true, false);
  public static final Measurement CENTER_X =
      new Measurement(Messages.getString("measure.centerx"), 5, true, true, false);
  public static final Measurement CENTER_Y =
      new Measurement(Messages.getString("measure.centery"), 6, true, true, false);
  public static final Measurement WIDTH =
      new Measurement(Messages.getString("measure.width"), 7, true, true, false);
  public static final Measurement HEIGHT =
      new Measurement(Messages.getString("measure.height"), 8, true, true, false);
  public static final Measurement ORIENTATION =
      new Measurement(Messages.getString("measure.orientation"), 9, true, true, false);

  protected static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();

  static {
    MEASUREMENT_LIST.add(CENTER_X);
    MEASUREMENT_LIST.add(CENTER_Y);
    MEASUREMENT_LIST.add(WIDTH);
    MEASUREMENT_LIST.add(HEIGHT);
    MEASUREMENT_LIST.add(ORIENTATION);
    MEASUREMENT_LIST.add(AREA);
    MEASUREMENT_LIST.add(PERIMETER);
  }

  // Let AB & CD two perpendicular line segments with D being the projected point C on AB
  protected Point2D ptA;
  protected Point2D ptB;
  protected Point2D ptC;
  protected Point2D ptD;

  // estimate if line segments are valid or not
  protected boolean lineABvalid;
  protected boolean lineCDvalid;

  public ObliqueRectangleGraphic() {
    super(POINTS_NUMBER);
  }

  public ObliqueRectangleGraphic(ObliqueRectangleGraphic graphic) {
    super(graphic);
  }

  @Override
  public ObliqueRectangleGraphic copy() {
    return new ObliqueRectangleGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("MeasureToolBar.rect");
  }

  public ObliqueRectangleGraphic buildGraphic(Rectangle2D rectangle) throws InvalidShapeException {
    Rectangle2D r =
        Optional.ofNullable(rectangle)
            .orElseThrow(() -> new InvalidShapeException("Rectangle2D is null!"));
    setHandlePointList(r);
    prepareShape();
    return this;
  }

  @Override
  protected void prepareShape() throws InvalidShapeException {
    if (!isShapeValid()) {
      throw new InvalidShapeException("This shape cannot be drawn");
    }
    buildShape(null);
  }

  @Override
  public Integer moveAndResizeOnDrawing(
      Integer handlePointIndex, Double deltaX, Double deltaY, MouseEventDouble mouseEvent) {

    List<Point2D> prevHandlePointList = getHandlePointList();

    handlePointIndex = super.moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, mouseEvent);

    if (handlePointIndex >= 0 && handlePointIndex < getHandlePointListSize()) {
      updateTool();

      if (handlePointIndex == 0 || handlePointIndex == 1) { // drag point is A or B

        Point2D prevPtA = (!prevHandlePointList.isEmpty()) ? prevHandlePointList.get(0) : null;
        Point2D prevPtB = (prevHandlePointList.size() > 1) ? prevHandlePointList.get(1) : null;

        if (lineABvalid && GeomUtil.isLineValid(prevPtA, prevPtB) && ptC != null && ptD != null) {
          double dist = ptC.distance(ptD);
          ptC = GeomUtil.getMidPoint(ptA, ptB);
          ptD = GeomUtil.getPerpendicularPointFromLine(ptA, ptB, ptC, dist);
          setHandlePoint(2, ptC);
          setHandlePoint(3, ptD);
        }
      } else if (handlePointIndex == 2) { // drag point is C (collinear with ab)
        if (lineABvalid && ptC != null) {
          double abDist = ptA.distance(ptB);
          if (ptD == null) {
            if (this instanceof EllipseGraphic) {
              Point2D newPtA = GeomUtil.getPerpendicularPointFromLine(ptA, ptB, ptA, -abDist / 6);
              Point2D newPtB = GeomUtil.getPerpendicularPointFromLine(ptA, ptB, ptB, -abDist / 6);
              ptA = newPtA;
              ptB = newPtB;
              setHandlePoint(0, ptA);
              setHandlePoint(1, ptB);
            }
            ptC = GeomUtil.getMidPoint(ptA, ptB);
            ptD = GeomUtil.getPerpendicularPointFromLine(ptA, ptB, ptC, abDist / 3);
            setHandlePoint(2, ptC);
            setHandlePoint(3, ptD);

            ViewCanvas<?> graphPane = getDefaultView2d(mouseEvent);
            if (graphPane != null) {
              Point mousePt = graphPane.getMouseCoordinatesFromImage(ptD.getX(), ptD.getY());
              try {
                mouseEvent.translatePoint(
                    mousePt.x - mouseEvent.getX(), mousePt.y - mouseEvent.getY());
                mouseEvent.setImageCoordinates(ptD);
                SwingUtilities.convertPointToScreen(mousePt, graphPane.getJComponent());
                new Robot().mouseMove(mousePt.x, mousePt.y);
                return 3;
              } catch (Exception e) {
                // Do nothing
              }
            }
          } else {
            Point2D ptNext = GeomUtil.getPerpendicularPointToLine(ptA, ptB, ptC);
            double dist = ptC.distance(ptNext);
            if (ptA.getX() > ptB.getX()) {
              dist *= -1;
            }
            if (ptNext.getY() > ptC.getY()) {
              dist *= -1;
            }

            Point2D prevPtC = (prevHandlePointList.size() > 2) ? prevHandlePointList.get(2) : null;
            double dist2 = prevPtC == null ? abDist / 3 : prevPtC.distance(ptD);
            Line2D ab = GeomUtil.getParallelLine(ptA, ptB, dist);
            ptA = ab.getP1();
            ptB = ab.getP2();
            setHandlePoint(0, ptA);
            setHandlePoint(1, ptB);
            ptC = GeomUtil.getMidPoint(ptA, ptB);
            setHandlePoint(2, ptC);
            ptD = GeomUtil.getPerpendicularPointFromLine(ptA, ptB, ptC, dist2);
            setHandlePoint(3, ptD);
          }
        }
      } else if (handlePointIndex == 3) { // drag point is D (collinear with cd)
        if (lineABvalid && ptC != null && ptD != null) {
          Point2D ptNext = GeomUtil.getPerpendicularPointToLine(ptA, ptB, ptD);
          double dist = ptD.distance(ptNext);
          ptD = GeomUtil.getPerpendicularPointFromLine(ptA, ptB, ptC, dist);
          setHandlePoint(3, ptD);
        }
      }
    }

    return handlePointIndex;
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    updateTool();

    Path2D polygonPath = new Path2D.Double(Path2D.WIND_NON_ZERO, pts.size());

    if (lineABvalid) {
      polygonPath.moveTo(ptA.getX(), ptA.getY());
      polygonPath.lineTo(ptB.getX(), ptB.getY());
      if (lineCDvalid) {
        double dist = ptC.distance(ptD);
        Line2D cd = GeomUtil.getParallelLine(ptA, ptB, dist);
        polygonPath.lineTo(cd.getP2().getX(), cd.getP2().getY());
        polygonPath.lineTo(cd.getP1().getX(), cd.getP1().getY());
      }
      polygonPath.closePath();
    }
    setShape(polygonPath, mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
  }

  @Override
  public List<Measurement> getMeasurementList() {
    return MEASUREMENT_LIST;
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
          Double val = ab * cd * ratio * ratio;
          String unit =
              "pix".equals(unitStr) // NON-NLS
                  ? unitStr
                  : unitStr + "2";
          measVal.add(new MeasureItem(AREA, val, unit));
        }
        if (PERIMETER.getComputed()) {
          Double val = (ab + cd) * 2 * ratio;
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
  public boolean isShapeValid() {
    updateTool();
    return lineABvalid && lineCDvalid;
  }

  protected void updateTool() {
    // Handle old non oblique Rectangle
    if (pts.size() >= 8) {
      if (!getHandlePoint(eHandlePoint.NW.index).equals(getHandlePoint(eHandlePoint.SE.index))) {
        Rectangle2D rectangle = new Rectangle2D.Double();
        rectangle.setFrameFromDiagonal(
            getHandlePoint(eHandlePoint.NW.index), getHandlePoint(eHandlePoint.SE.index));
        setHandlePointList(rectangle);
      }
    }

    ptA = getHandlePoint(0);
    ptB = getHandlePoint(1);
    ptC = getHandlePoint(2);
    ptD = getHandlePoint(3);

    lineABvalid = ptA != null && ptB != null && !ptB.equals(ptA);
    lineCDvalid = ptC != null && ptD != null && !ptC.equals(ptD);
  }

  protected void setHandlePointList(Rectangle2D rectangle) {
    double x = rectangle.getX();
    double y = rectangle.getY();
    double w = rectangle.getWidth();
    double h = rectangle.getHeight();

    while (pts.size() < pointNumber) {
      pts.add(new Point2D.Double());
    }
    // Remove old 8 Rectangle points
    while (pts.size() > pointNumber) {
      pts.remove((int) pointNumber);
    }

    setHandlePoint(0, new Point2D.Double(x, y));
    setHandlePoint(1, new Point2D.Double(x + w, y));
    setHandlePoint(2, new Point2D.Double(x + w / 2, y));
    setHandlePoint(3, new Point2D.Double(x + w / 2, y + h));
  }

  public List<Point2D> getRectanglePointList() {
    updateTool();
    List<Point2D> pts = new ArrayList<>();
    if (lineABvalid && lineCDvalid) {
      Point2D a = new Point2D.Double(ptA.getX(), ptA.getY());
      Point2D b = new Point2D.Double(ptB.getX(), ptB.getY());
      Line2D cd = GeomUtil.getParallelLine(a, b, ptC.distance(ptD));
      if (a.getX() > b.getX()) {
        pts.add(cd.getP2());
        pts.add(cd.getP1());
        pts.add(a);
        pts.add(b);
      } else {
        pts.add(a);
        pts.add(b);
        pts.add(cd.getP2());
        pts.add(cd.getP1());
      }
    }
    return pts;
  }
}
