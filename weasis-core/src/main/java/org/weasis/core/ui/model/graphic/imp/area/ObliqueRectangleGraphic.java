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
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
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
      new Measurement("area", Messages.getString("measure.area"), 1, true, true, true);
  public static final Measurement PERIMETER =
      new Measurement("perimeter", Messages.getString("measure.perimeter"), 2, true, true, false);
  public static final Measurement CENTER_X =
      new Measurement("center.x", Messages.getString("measure.centerx"), 5, true, true, false);
  public static final Measurement CENTER_Y =
      new Measurement("center.y", Messages.getString("measure.centery"), 6, true, true, false);
  public static final Measurement WIDTH =
      new Measurement("width", Messages.getString("measure.width"), 7, true, true, false);
  public static final Measurement HEIGHT =
      new Measurement("height", Messages.getString("measure.height"), 8, true, true, false);
  public static final Measurement ORIENTATION =
      new Measurement(
          "orientation", Messages.getString("measure.orientation"), 9, true, true, false);

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

  /** True while the shape is being created axis-aligned from its diagonal. */
  private boolean axisAlignedCreation;

  public ObliqueRectangleGraphic() {
    super(POINTS_NUMBER);
  }

  public ObliqueRectangleGraphic(ObliqueRectangleGraphic graphic) { // NOSONAR see initCopy()
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

    if (handlePointIndex == 1 && isAxisAlignedCreation(mouseEvent)) {
      axisAlignedCreation = true;
      alignToAxes(mouseEvent.getImageCoordinates());
      return handlePointIndex;
    }

    if (handlePointIndex >= 0 && handlePointIndex < getHandlePointListSize()) {
      updateTool();

      if (handlePointIndex == 0 || handlePointIndex == 1) { // drag point is A or B

        Point2D prevPtA = (!prevHandlePointList.isEmpty()) ? prevHandlePointList.get(0) : null;
        Point2D prevPtB = (prevHandlePointList.size() > 1) ? prevHandlePointList.get(1) : null;

        if (lineABvalid && GeomUtil.isLineValid(prevPtA, prevPtB) && ptC != null && ptD != null) {
          double height = GeomUtil.getSignedDistanceToLine(prevPtA, prevPtB, ptD);
          ptC = GeomUtil.getMidPoint(ptA, ptB);
          ptD = GeomUtil.getPerpendicularPointFromLine(ptA, ptB, ptC, height);
          setHandlePoint(2, ptC);
          setHandlePoint(3, ptD);
        }
      } else if (handlePointIndex == 2) { // drag point is C (collinear with ab)
        if (lineABvalid && ptC != null) {
          if (ptD == null) {
            // Second drawing step: the opposite side follows the cursor from the AB side
            ptC = GeomUtil.getMidPoint(ptA, ptB);
            setHandlePoint(2, ptC);
            setHandlePoint(3, oppositeSidePoint(mouseEvent, 0));
            return 3;
          }
          // Shifts the whole shape along its normal, keeping its height
          double height = GeomUtil.getSignedDistanceToLine(ptA, ptB, ptD);
          Line2D ab =
              GeomUtil.getParallelLine(ptA, ptB, GeomUtil.getSignedDistanceToLine(ptA, ptB, ptC));
          ptA = ab.getP1();
          ptB = ab.getP2();
          setHandlePoint(0, ptA);
          setHandlePoint(1, ptB);
          ptC = GeomUtil.getMidPoint(ptA, ptB);
          setHandlePoint(2, ptC);
          setHandlePoint(3, GeomUtil.getPerpendicularPointFromLine(ptA, ptB, ptC, height));
        }
      } else if (handlePointIndex == 3) { // drag point is D (perpendicular to ab through c)
        if (lineABvalid && ptC != null && ptD != null) {
          setHandlePoint(
              3, oppositeSidePoint(mouseEvent, GeomUtil.getSignedDistanceToLine(ptA, ptB, ptD)));
        }
      }
    }

    return handlePointIndex;
  }

  /**
   * The first drag is either the first side of an oblique shape or the diagonal of a classic,
   * axis-aligned one drawn in one go. Shift selects the mode that is not the preferred one.
   */
  private boolean isAxisAlignedCreation(MouseEventDouble mouseEvent) {
    return mouseEvent != null
        && mouseEvent.isShiftDown() != isUprightByDrag()
        && (getHandlePointListSize() == 2 || axisAlignedCreation);
  }

  protected boolean isUprightByDrag() {
    return MeasureTool.viewSetting.isUprightByDrag();
  }

  /** Places B, C and D so that A and the cursor are opposite corners of an upright rectangle. */
  private void alignToAxes(Point2D cursor) {
    Point2D a = getHandlePoint(0);
    if (a == null || cursor == null) {
      return;
    }
    double midX = (a.getX() + cursor.getX()) / 2.0;
    setHandlePoint(1, new Point2D.Double(cursor.getX(), a.getY()));
    setHandlePoint(2, new Point2D.Double(midX, a.getY()));
    setHandlePoint(3, new Point2D.Double(midX, cursor.getY()));
  }

  @Override
  public void setResizeOrMoving(Boolean value) {
    super.setResizeOrMoving(value);
    if (!Boolean.TRUE.equals(value)) {
      axisAlignedCreation = false;
    }
  }

  /**
   * Point of the perpendicular through C at the signed distance of the cursor from AB, so the
   * opposite side follows the cursor on whichever side it is.
   */
  private Point2D oppositeSidePoint(MouseEventDouble mouseEvent, double defaultHeight) {
    Point2D cursor = mouseEvent == null ? null : mouseEvent.getImageCoordinates();
    double height =
        cursor == null ? defaultHeight : GeomUtil.getSignedDistanceToLine(ptA, ptB, cursor);
    return GeomUtil.getPerpendicularPointFromLine(ptA, ptB, ptC, height);
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    updateTool();

    Path2D polygonPath = new Path2D.Double(Path2D.WIND_NON_ZERO, pts.size());

    if (lineABvalid) {
      polygonPath.moveTo(ptA.getX(), ptA.getY());
      polygonPath.lineTo(ptB.getX(), ptB.getY());
      if (lineCDvalid) {
        Line2D cd =
            GeomUtil.getParallelLine(ptA, ptB, GeomUtil.getSignedDistanceToLine(ptA, ptB, ptD));
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

        double ratio = adapter.calibrationRatio();
        String unitStr = adapter.unit();

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
