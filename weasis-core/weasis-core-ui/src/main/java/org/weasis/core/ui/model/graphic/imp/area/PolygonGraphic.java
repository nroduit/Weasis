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

import static java.lang.Double.NaN;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.swing.Icon;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.graphic.AbstractDragGraphicArea;
import org.weasis.core.ui.model.utils.algo.MinimumEnclosingRectangle;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.util.MathUtil;

@XmlType(name = "polygon")
@XmlRootElement(name = "polygon")
public class PolygonGraphic extends AbstractDragGraphicArea {

  public static final Integer POINTS_NUMBER = UNDEFINED;

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_POLYGON);

  public static final Measurement AREA =
      new Measurement(Messages.getString("measure.area"), 1, true, true, true);
  public static final Measurement PERIMETER =
      new Measurement(Messages.getString("measure.perimeter"), 2, true, true, false);
  public static final Measurement WIDTH =
      new Measurement(Messages.getString("measure.width"), 3, true, true, false);
  public static final Measurement HEIGHT =
      new Measurement(Messages.getString("measure.height"), 4, true, true, false);
  public static final Measurement TOP_LEFT_POINT_X =
      new Measurement(Messages.getString("measure.topx"), 5, true, true, false);
  public static final Measurement TOP_LEFT_POINT_Y =
      new Measurement(Messages.getString("measure.topy"), 6, true, true, false);
  public static final Measurement CENTROID_X =
      new Measurement(Messages.getString("measure.centerx"), 7, true, true, false);
  public static final Measurement CENTROID_Y =
      new Measurement(Messages.getString("measure.centery"), 8, true, true, false);
  public static final Measurement WIDTH_OMBB =
      new Measurement(
          Messages.getString("measure.width") + " (OMBB)", 9, false, true, false); // NON-NLS
  public static final Measurement LENGTH_OMBB =
      new Measurement(
          Messages.getString("measure.length") + " (OMBB)", 10, false, true, false); // NON-NLS
  public static final Measurement ORIENTATION_OMBB =
      new Measurement(
          Messages.getString("measure.orientation") + " (OMBB)", 10, false, true, false); // NON-NLS

  protected static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();

  static {
    MEASUREMENT_LIST.add(TOP_LEFT_POINT_X);
    MEASUREMENT_LIST.add(TOP_LEFT_POINT_Y);
    MEASUREMENT_LIST.add(WIDTH);
    MEASUREMENT_LIST.add(HEIGHT);
    MEASUREMENT_LIST.add(CENTROID_X);
    MEASUREMENT_LIST.add(CENTROID_Y);
    MEASUREMENT_LIST.add(AREA);
    MEASUREMENT_LIST.add(PERIMETER);
  }

  public PolygonGraphic() {
    super(POINTS_NUMBER);
  }

  public PolygonGraphic(PolygonGraphic graphic) {
    super(graphic);
  }

  @Override
  public PolygonGraphic copy() {
    return new PolygonGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("MeasureToolBar.polygon");
  }

  @Override
  public int getKeyCode() {
    return KeyEvent.VK_Y;
  }

  @Override
  public int getModifier() {
    return 0;
  }

  @Override
  protected void prepareShape() throws InvalidShapeException {
    // Do not draw points anymore
    setPointNumber(pts.size());
    buildShape(null);

    if (!isShapeValid()) {
      int lastPointIndex = pts.size() - 1;
      if (lastPointIndex > 1) {
        Point2D checkPoint = pts.get(lastPointIndex);
        /*
         * Must not have two or several points with the same position at the end of the list (two points is the
         * convention to have an uncompleted shape when drawing)
         */
        for (int i = lastPointIndex - 1; i >= 0; i--) {
          if (Objects.equals(checkPoint, pts.get(i))) {
            pts.remove(i);
          } else {
            break;
          }
        }
        // Not useful to close the shape
        if (Objects.equals(checkPoint, pts.get(0))) {
          pts.remove(0);
        }
        setPointNumber(pts.size());
      }

      if (!isShapeValid() || pts.size() < 3) {
        throw new InvalidShapeException("This Polygon cannot be drawn");
      }
      buildShape(null);
    }
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    Shape newShape = null;
    Optional<Point2D> firstHandlePoint = pts.stream().findFirst();

    if (firstHandlePoint.isPresent()) {
      Point2D p = firstHandlePoint.get();

      Path2D polygonPath = new Path2D.Double(Path2D.WIND_NON_ZERO, pts.size());
      polygonPath.moveTo(p.getX(), p.getY());

      for (Point2D pt : pts) {
        if (pt == null) {
          break;
        }
        polygonPath.lineTo(pt.getX(), pt.getY());
      }
      polygonPath.closePath();
      newShape = polygonPath;
    }

    setShape(newShape, mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
  }

  @Override
  public boolean isShapeValid() {
    if (!isGraphicComplete()) {
      return false;
    }

    int lastPointIndex = pts.size() - 1;

    if (lastPointIndex > 0) {
      Point2D checkPoint = pts.get(lastPointIndex);
      return !Objects.equals(checkPoint, pts.get(--lastPointIndex));
    }
    return true;
  }

  @Override
  public List<MeasureItem> computeMeasurements(
      MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {

    if (layer != null && layer.hasContent() && isShapeValid()) {
      MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

      if (adapter != null) {
        ArrayList<MeasureItem> measVal = new ArrayList<>(12);

        double ratio = adapter.getCalibRatio();
        String unitStr = adapter.getUnit();

        Area pathArea = getPathArea();
        List<Line2D.Double> lineSegmentList = null;

        if (TOP_LEFT_POINT_X.getComputed()) {
          Double val =
              Optional.ofNullable(pathArea)
                  .map(pa -> adapter.getXCalibratedValue(pa.getBounds2D().getX()))
                  .orElse(null);
          measVal.add(new MeasureItem(TOP_LEFT_POINT_X, val, unitStr));
        }
        if (TOP_LEFT_POINT_Y.getComputed()) {
          Double val =
              Optional.ofNullable(pathArea)
                  .map(pa -> adapter.getYCalibratedValue(pa.getBounds2D().getY()))
                  .orElse(null);
          measVal.add(new MeasureItem(TOP_LEFT_POINT_Y, val, unitStr));
        }
        if (WIDTH.getComputed()) {
          Double val =
              Optional.ofNullable(pathArea)
                  .map(pa -> ratio * pa.getBounds2D().getWidth())
                  .orElse(null);
          measVal.add(new MeasureItem(WIDTH, val, unitStr));
        }
        if (HEIGHT.getComputed()) {
          Double val =
              Optional.ofNullable(pathArea)
                  .map(pa -> ratio * pa.getBounds2D().getHeight())
                  .orElse(null);
          measVal.add(new MeasureItem(HEIGHT, val, unitStr));
        }

        Point2D centroid = null;
        if (CENTROID_X.getComputed()) {
          if (lineSegmentList == null) {
            lineSegmentList = getClosedPathSegments(pathArea);
          }
          centroid = (centroid == null) ? getCentroid(lineSegmentList) : centroid;
          Double val = (centroid != null) ? adapter.getXCalibratedValue(centroid.getX()) : null;
          measVal.add(new MeasureItem(CENTROID_X, val, unitStr));
        }
        if (CENTROID_Y.getComputed()) {
          if (lineSegmentList == null) {
            lineSegmentList = getClosedPathSegments(pathArea);
          }
          centroid = (centroid == null) ? getCentroid(lineSegmentList) : centroid;
          Double val = (centroid != null) ? adapter.getYCalibratedValue(centroid.getY()) : null;
          measVal.add(new MeasureItem(CENTROID_Y, val, unitStr));
        }
        if (AREA.getComputed()) {
          if (lineSegmentList == null) {
            lineSegmentList = getClosedPathSegments(pathArea);
          }
          Double val =
              (lineSegmentList != null) ? getAreaValue(lineSegmentList) * ratio * ratio : null;
          String unit = "pix".equals(unitStr) ? unitStr : unitStr + "2"; // NON-NLS
          measVal.add(new MeasureItem(AREA, val, unit));
        }
        if (PERIMETER.getComputed()) {
          if (lineSegmentList == null) {
            lineSegmentList = getClosedPathSegments(pathArea);
          }
          Double val = (lineSegmentList != null) ? getPerimeter(lineSegmentList) * ratio : null;
          measVal.add(new MeasureItem(PERIMETER, val, unitStr));
        }
        if (releaseEvent && (WIDTH_OMBB.getComputed() || LENGTH_OMBB.getComputed())) {
          Double l = null;
          Double w = null;
          Double o = null;

          MinimumEnclosingRectangle rect = new MinimumEnclosingRectangle(pts, false);
          List<java.awt.geom.Point2D> minRect = rect.getMinimumRectangle();
          if (minRect.size() == 4) {
            l = ratio * minRect.get(0).distance(minRect.get(1));
            w = ratio * minRect.get(1).distance(minRect.get(2));
            o = MathUtil.getOrientation(minRect.get(0), minRect.get(1));
            if (l < w) {
              double tmp = l;
              l = w;
              w = tmp;
              o = MathUtil.getOrientation(minRect.get(1), minRect.get(2));
            }
          }
          measVal.add(new MeasureItem(LENGTH_OMBB, l, unitStr));
          measVal.add(new MeasureItem(WIDTH_OMBB, w, unitStr));
          measVal.add(new MeasureItem(ORIENTATION_OMBB, o, Messages.getString("measure.deg")));
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

  protected final Area getPathArea() {
    return getPathArea(pts);
  }
  /**
   * Construct a polygon Area which represents a non-self-intersecting shape using a path Winding
   * Rule : WIND_NON_ZERO
   *
   * @return area of the closed polygon, or null if shape is invalid
   */
  public static Area getPathArea(List<Point2D> pts) {

    Optional<Point2D> firstHandlePoint = pts.stream().findFirst();

    if (firstHandlePoint.isPresent()) {
      Point2D p = firstHandlePoint.get();
      Path2D polygonPath = new Path2D.Double(Path2D.WIND_NON_ZERO, pts.size());
      polygonPath.moveTo(p.getX(), p.getY());

      for (Point2D pt : pts) {
        if (pt == null) {
          return null;
        }
        polygonPath.lineTo(pt.getX(), pt.getY());
      }
      return new Area(polygonPath);
    }
    return null;
  }

  /**
   * Construct a list of line segments which defines the outside path of a given polygon Area with
   * each vertex ordered in the same direction<br>
   *
   * @return list of line segments around the closed polygon, or null if shape is invalid
   */
  public final List<Line2D.Double> getClosedPathSegments() {
    return getClosedPathSegments(getPathArea());
  }

  protected final List<Line2D.Double> getClosedPathSegments(Area pathArea) {

    List<Line2D.Double> lineSegmentList = null;

    if (pathArea != null) {

      lineSegmentList = new ArrayList<>(pts.size());
      PathIterator pathIt = pathArea.getPathIterator(null);

      double[] coords = new double[6];
      Double curX = NaN;
      Double curY = NaN;

      Set<Point2D> ptSet = new HashSet<>(lineSegmentList.size() * 2);

      while (!pathIt.isDone()) {

        int segType = pathIt.currentSegment(coords);
        Double lastX = coords[0];
        Double lastY = coords[1];

        switch (segType) {
          case PathIterator.SEG_CLOSE:
            break;
          case PathIterator.SEG_LINETO:
            Point2D ptP1 = new Point2D.Double(curX, curY);
            Point2D ptP2 = new Point2D.Double(lastX, lastY);

            BigDecimal dist =
                BigDecimal.valueOf(ptP1.distance(ptP2)).setScale(10, RoundingMode.DOWN);
            if (dist.compareTo(BigDecimal.ZERO) != 0) {
              for (Point2D pt : new Point2D[] {ptP1, ptP2}) {
                boolean newPt = true;
                for (Point2D p : ptSet) {
                  dist = BigDecimal.valueOf(p.distance(pt)).setScale(10, RoundingMode.DOWN);
                  if (dist.compareTo(BigDecimal.ZERO) == 0) {
                    pt.setLocation(p);
                    newPt = false;
                    break;
                  }
                }
                if (newPt) {
                  ptSet.add(pt);
                }
              }
              lineSegmentList.add(new Line2D.Double(ptP1, ptP2));
            }

            curX = lastX;
            curY = lastY;
            break;
          case PathIterator.SEG_MOVETO:
            curX = lastX;
            curY = lastY;
            break;

          default:
            break;
        }
        pathIt.next();
      }
    }

    return lineSegmentList;
  }

  /** @return perimeter the closed polygon, or null if shape is invalid */
  public Double getPerimeter() {
    return getPerimeter(getClosedPathSegments());
  }

  protected Double getPerimeter(List<Line2D.Double> lineSegmentList) {
    if (lineSegmentList != null) {
      double perimeter = 0.0;

      for (Line2D line : lineSegmentList) {
        perimeter += line.getP1().distance(line.getP2());
      }

      return perimeter;
    }
    return null;
  }

  /**
   * The centroid (a.k.a. the center of mass, or center of gravity) of a polygon can be computed as
   * the weighted sum of the centroids of a partition of the polygon into triangles. <br>
   * This suggests first triangulating the polygon, then forming a sum of the centroids of each
   * triangle, weighted by the area of each triangle, the whole sum normalized by the total polygon
   * area. <br>
   * <br>
   * Simpler method: the triangulation need not be a partition, but rather can use positively and
   * negatively oriented triangles (with positive and negative areas), as is used when computing the
   * area of a polygon. Then, simple algorithm for computing the centroid is based on a sum of
   * triangle centroids weighted with their signed area. The triangles can be taken to be those
   * formed by one fixed vertex v0 of the polygon, and the two endpoints of consecutive edges of the
   * polygon: (v1,v2), (v2,v3), etc.<br>
   *
   * @return position of the centroid assuming the polygon is closed, or null if shape is not valid
   */
  public Point2D getCentroid() {
    return getCentroid(getClosedPathSegments());
  }

  protected Point2D getCentroid(List<Line2D.Double> lineSegmentList) {
    if (lineSegmentList != null) {
      double area = 0d;
      double cx = 0d;
      double cy = 0d;

      for (Line2D.Double line : lineSegmentList) {
        Point2D p1 = line.getP1();
        Point2D p2 = line.getP2();

        double tmp = (p1.getX() * p2.getY()) - (p2.getX() * p1.getY());
        area += tmp;
        cx += (p1.getX() + p2.getX()) * tmp;
        cy += (p1.getY() + p2.getY()) * tmp;
      }
      area /= 2.0;
      if (area == 0.0 || MathUtil.isEqualToZero(area)) {
        return null;
      }
      cx /= (6.0 * area);
      cy /= (6.0 * area);

      return new Point2D.Double(cx, cy);
    }
    return null;
  }

  /**
   * <b>Algorithm</b><br>
   * <br>
   * -1- List the x and y coordinates of each vertex of the polygon in counterclockwise order about
   * the normal. Repeat the coordinates of the first point at the end of the list. <br>
   * -2- Multiply the x coordinate of each vertex by the y coordinate of the next vertex.<br>
   * -3- Multiply the y coordinate of each vertex by the x coordinate of the next vertex <br>
   * -4- Subtract the sum of the products computed in step 3 from the sum of the products from step
   * 2 <br>
   * -5- Divide this difference by 2 to get the area of the polygon. <br>
   * <br>
   * <b> Warning </b><br>
   * <br>
   * This formula computes area with orientation. When listing the points in a clockwise order
   * instead of counterclockwise, result is the negative of the area. <br>
   * The method produces the wrong answer for crossed polygons, where one side crosses over another.
   * <br>
   * For instance, when two lines of the drawing path cross like a figure eight, result is the area
   * surrounded counterclockwise minus the area surrounded clockwise.<br>
   * It works correctly however for triangles, regular, irregular, convex and concave polygons. <br>
   * <br>
   * Solution is to compute area only from the outside path of the polygon with each vertices
   * ordered in the same direction.<br>
   * This can be achieved trough Area() constructors which decompose the shape into
   * non-self-intersecting shape.
   */
  public Double getAreaValue() {
    return getAreaValue(getClosedPathSegments());
  }

  protected Double getAreaValue(List<Line2D.Double> lineSegmentList) {
    if (lineSegmentList != null) {
      double area = 0d;

      for (Line2D.Double line : lineSegmentList) {
        Point2D p1 = line.getP1();
        Point2D p2 = line.getP2();

        area += (p1.getX() * p2.getY()) - (p2.getX() * p1.getY());
      }

      return Math.abs(area) / 2.0;
    }
    return null;
  }
}
