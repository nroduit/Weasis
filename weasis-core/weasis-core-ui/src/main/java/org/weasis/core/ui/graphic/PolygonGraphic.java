/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.graphic;

import static java.lang.Double.NaN;

import java.awt.Color;
import java.awt.Paint;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.graphic.algo.MinimumEnclosingRectangle;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class PolygonGraphic.
 *
 *
 * @author Nicolas Roduit,Benoit Jacquemoud
 */
@Root(name = "polygon")
public class PolygonGraphic extends AbstractDragGraphicArea {

    public static final Icon ICON = new ImageIcon(PolygonGraphic.class.getResource("/icon/22x22/draw-polygon.png")); //$NON-NLS-1$

    public static final Measurement AREA = new Measurement(Messages.getString("measure.area"), 1, true, true, true); //$NON-NLS-1$
    public static final Measurement PERIMETER =
        new Measurement(Messages.getString("measure.perimeter"), 2, true, true, false); //$NON-NLS-1$
    public static final Measurement WIDTH = new Measurement(Messages.getString("measure.width"), 3, true, true, false); //$NON-NLS-1$
    public static final Measurement HEIGHT =
        new Measurement(Messages.getString("measure.height"), 4, true, true, false); //$NON-NLS-1$
    public static final Measurement TOP_LEFT_POINT_X =
        new Measurement(Messages.getString("measure.topx"), 5, true, true, false); //$NON-NLS-1$
    public static final Measurement TOP_LEFT_POINT_Y =
        new Measurement(Messages.getString("measure.topy"), 6, true, true, false); //$NON-NLS-1$
    public static final Measurement CENTROID_X =
        new Measurement(Messages.getString("measure.centerx"), 7, true, true, false); //$NON-NLS-1$
    public static final Measurement CENTROID_Y =
        new Measurement(Messages.getString("measure.centery"), 8, true, true, false); //$NON-NLS-1$
    public static final Measurement WIDTH_OMBB =
        new Measurement(Messages.getString("measure.width") + " (OMBB)", 9, false, true, false); //$NON-NLS-1$
    public static final Measurement LENGTH_OMBB =
        new Measurement(Messages.getString("measure.length") + " (OMBB)", 10, false, true, false); //$NON-NLS-1$
    public static final Measurement ORIENTATION_OMBB =
        new Measurement(Messages.getString("measure.orientation") + " (OMBB)", 10, false, true, false); //$NON-NLS-1$
    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public PolygonGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(BasicGraphic.UNDEFINED, paintColor, lineThickness, labelVisible);
    }

    public PolygonGraphic(List<Point2D.Double> handlePointList, Color paintColor, float lineThickness,
        boolean labelVisible, boolean filled) throws InvalidShapeException {
        this(handlePointList, handlePointList.size(), paintColor, lineThickness, labelVisible, filled);
    }

    protected PolygonGraphic(
        @ElementList(name = "pts", entry = "pt", type = Point2D.Double.class) List<Point2D.Double> handlePointList,
        @Attribute(name = "handle_pts_nb") int handlePointTotalNumber,
        @Element(name = "paint", required = false) Paint paintColor, @Attribute(name = "thickness") float lineThickness,
        @Attribute(name = "label_visible") boolean labelVisible, @Attribute(name = "fill") boolean filled)
            throws InvalidShapeException {
        super(handlePointList, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled);
        if (handlePointList == null || handlePointList.size() < 3) {
            throw new InvalidShapeException("Polygon must have at least 3 points!"); //$NON-NLS-1$
        }
        buildShape(null);
        // Do not draw points any more
        this.handlePointTotalNumber = handlePointList.size();
        if (!isShapeValid()) {
            int lastPointIndex = handlePointList.size() - 1;
            if (lastPointIndex > 1) {
                Point2D checkPoint = handlePointList.get(lastPointIndex);
                /*
                 * Must not have two or several points with the same position at the end of the list (two points is the
                 * convention to have a uncompleted shape when drawing)
                 */
                for (int i = lastPointIndex - 1; i >= 0; i--) {
                    if (checkPoint.equals(handlePointList.get(i))) {
                        handlePointList.remove(i);
                    } else {
                        break;
                    }
                }
                // Not useful to close the shape
                if (checkPoint.equals(handlePointList.get(0))) {
                    handlePointList.remove(0);
                }
                this.handlePointTotalNumber = handlePointList.size();
            }
            if (!isShapeValid() || handlePointList.size() < 3) {
                throw new InvalidShapeException("This Polygon cannot be drawn"); //$NON-NLS-1$
            }
            buildShape(null);
        }
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.polygon"); //$NON-NLS-1$
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
    protected void buildShape(MouseEventDouble mouseEvent) {

        Shape newShape = null;
        Point2D firstHandlePoint = (handlePointList.size() > 1) ? getHandlePoint(0) : null;

        PATH_AREA_ITERATION:

        if (firstHandlePoint != null) {

            Path2D polygonPath = new Path2D.Double(Path2D.WIND_NON_ZERO, handlePointList.size());
            polygonPath.moveTo(firstHandlePoint.getX(), firstHandlePoint.getY());

            for (int i = 1; i < handlePointList.size(); i++) {
                Point2D pt = getHandlePoint(i);
                if (pt == null) {
                    break PATH_AREA_ITERATION;
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

        int lastPointIndex = handlePointList.size() - 1;

        if (lastPointIndex > 0) {
            Point2D checkPoint = handlePointList.get(lastPointIndex);
            if (checkPoint.equals(handlePointList.get(--lastPointIndex))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<MeasureItem> computeMeasurements(ImageLayer layer, boolean releaseEvent, Unit displayUnit) {

        if (layer != null && layer.getSourceImage() != null && isShapeValid()) {
            MeasurementsAdapter adapter = layer.getSourceImage().getMeasurementAdapter(displayUnit);

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>(12);

                double ratio = adapter.getCalibRatio();
                String unitStr = adapter.getUnit();

                Area pathArea = null;
                List<Line2D> lineSegmentList = null;

                if (TOP_LEFT_POINT_X.isComputed()) {
                    Double val = null;
                    pathArea = (pathArea == null) ? getPathArea() : pathArea;
                    val = (pathArea != null) ? adapter.getXCalibratedValue(pathArea.getBounds2D().getX()) : null;

                    measVal.add(new MeasureItem(TOP_LEFT_POINT_X, val, unitStr));
                }
                if (TOP_LEFT_POINT_Y.isComputed()) {
                    Double val = null;
                    pathArea = (pathArea == null) ? getPathArea() : pathArea;
                    val = (pathArea != null) ? adapter.getXCalibratedValue(pathArea.getBounds2D().getY()) : null;
                    measVal.add(new MeasureItem(TOP_LEFT_POINT_Y, val, unitStr));
                }
                if (WIDTH.isComputed()) {
                    Double val = null;
                    pathArea = (pathArea == null) ? getPathArea() : pathArea;
                    val = (pathArea != null) ? ratio * pathArea.getBounds2D().getWidth() : null;
                    measVal.add(new MeasureItem(WIDTH, val, unitStr));
                }
                if (HEIGHT.isComputed()) {
                    Double val = null;
                    pathArea = (pathArea == null) ? getPathArea() : pathArea;
                    val = (pathArea != null) ? ratio * pathArea.getBounds2D().getHeight() : null;
                    measVal.add(new MeasureItem(HEIGHT, val, unitStr));
                }

                Point2D centroid = null;
                if (CENTROID_X.isComputed()) {
                    Double val = null;
                    if (lineSegmentList == null) {
                        pathArea = (pathArea == null) ? getPathArea() : pathArea;
                        lineSegmentList = getClosedPathSegments(pathArea);
                    }
                    centroid = (centroid == null) ? getCentroid(lineSegmentList) : centroid;
                    val = (centroid != null) ? centroid.getX() * ratio : null;
                    measVal.add(new MeasureItem(CENTROID_X, val, unitStr));
                }
                if (CENTROID_Y.isComputed()) {
                    Double val = null;
                    if (lineSegmentList == null) {
                        pathArea = (pathArea == null) ? getPathArea() : pathArea;
                        lineSegmentList = getClosedPathSegments(pathArea);
                    }
                    centroid = (centroid == null) ? getCentroid(lineSegmentList) : centroid;
                    val = (centroid != null) ? centroid.getY() * ratio : null;
                    measVal.add(new MeasureItem(CENTROID_Y, val, unitStr));
                }
                if (AREA.isComputed()) {
                    Double val = null;
                    if (lineSegmentList == null) {
                        pathArea = (pathArea == null) ? getPathArea() : pathArea;
                        lineSegmentList = getClosedPathSegments(pathArea);
                    }
                    val = (lineSegmentList != null) ? getAreaValue(lineSegmentList) * ratio * ratio : null;
                    String unit = "pix".equals(unitStr) ? unitStr : unitStr + "2"; //$NON-NLS-1$ //$NON-NLS-2$
                    measVal.add(new MeasureItem(AREA, val, unit));
                }
                if (PERIMETER.isComputed()) {
                    Double val = null;
                    if (lineSegmentList == null) {
                        pathArea = (pathArea == null) ? getPathArea() : pathArea;
                        lineSegmentList = getClosedPathSegments(pathArea);
                    }
                    val = (lineSegmentList != null) ? getPerimeter(lineSegmentList) * ratio : null;
                    measVal.add(new MeasureItem(PERIMETER, val, unitStr));
                }
                if (releaseEvent && (WIDTH_OMBB.isComputed() || LENGTH_OMBB.isComputed())) {
                    Double l = null;
                    Double w = null;
                    Double o = null;

                    MinimumEnclosingRectangle rect = new MinimumEnclosingRectangle(handlePointList, false);
                    List<java.awt.geom.Point2D.Double> minRect = rect.getMinimumRectangle();
                    if (minRect != null && minRect.size() == 4) {
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
        return null;
    }

    @Override
    public List<Measurement> getMeasurementList() {
        List<Measurement> list = new ArrayList<Measurement>();
        list.add(TOP_LEFT_POINT_X);
        list.add(TOP_LEFT_POINT_Y);
        list.add(WIDTH);
        list.add(HEIGHT);
        list.add(CENTROID_X);
        list.add(CENTROID_Y);
        list.add(AREA);
        list.add(PERIMETER);
        return list;
    }

    /**
     * Construct a polygon Area which represents a non-self-intersecting shape using a path Winding Rule : WIND_NON_ZERO
     *
     * @return area of the closed polygon, or null if shape is invalid
     */
    protected final Area getPathArea() {

        Point2D firstHandlePoint = (handlePointList.size() > 1) ? getHandlePoint(0) : null;

        if (firstHandlePoint != null) {
            Path2D polygonPath = new Path2D.Double(Path2D.WIND_NON_ZERO, handlePointList.size());
            polygonPath.moveTo(firstHandlePoint.getX(), firstHandlePoint.getY());

            for (int i = 1; i < handlePointList.size(); i++) {
                Point2D pt = getHandlePoint(i);
                if (pt == null) {
                    return null;
                }
                polygonPath.lineTo(pt.getX(), pt.getY());
            }
            // polygonPath.closePath(); // Useless since Area constructor decompose the shape into a closed path
            return new Area(polygonPath);
        }
        return null;
    }

    /**
     * Construct a list of line segments which defines the outside path of a given polygon Area with each vertices
     * ordered in the same direction<br>
     *
     * @return list of line segments around the closed polygon, or null if shape is invalid
     */

    public final List<Line2D> getClosedPathSegments() {
        return getClosedPathSegments(getPathArea());
    }

    protected final List<Line2D> getClosedPathSegments(Area pathArea) {

        List<Line2D> lineSegmentList = null;

        if (pathArea != null) {

            lineSegmentList = new ArrayList<Line2D>(handlePointList.size());
            PathIterator pathIt = pathArea.getPathIterator(null);

            double coords[] = new double[6];
            double startX = NaN, startY = NaN, curX = NaN, curY = NaN;

            Set<Point2D> ptSet = new HashSet<Point2D>(lineSegmentList.size() * 2);

            while (!pathIt.isDone()) {

                int segType = pathIt.currentSegment(coords);
                double lastX = coords[0], lastY = coords[1];

                switch (segType) {
                    case PathIterator.SEG_CLOSE:
                        // lineSegmentList.add(new Line2D.Double(curX, curY, startX, startY));
                        lastX = startX;
                        lastY = startY;
                    case PathIterator.SEG_LINETO:
                        Point2D ptP1 = new Point2D.Double(curX, curY);
                        Point2D ptP2 = new Point2D.Double(lastX, lastY);

                        BigDecimal dist = new BigDecimal(ptP1.distance(ptP2)).setScale(10, RoundingMode.DOWN);
                        if (dist.compareTo(BigDecimal.ZERO) != 0) {
                            for (Point2D pt : new Point2D[] { ptP1, ptP2 }) {
                                boolean newPt = true;
                                for (Point2D p : ptSet) {
                                    dist = new BigDecimal(p.distance(pt)).setScale(10, RoundingMode.DOWN);
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
                        startX = curX = lastX;
                        startY = curY = lastY;
                        break;
                }
                pathIt.next();
            }
        }

        return lineSegmentList;
    }

    /**
     * @return perimeter the closed polygon, or null if shape is invalid
     */
    public Double getPerimeter() {
        return getPerimeter(getClosedPathSegments());
    }

    protected Double getPerimeter(List<Line2D> lineSegmentList) {
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
     * The centroid (a.k.a. the center of mass, or center of gravity) of a polygon can be computed as the weighted sum
     * of the centroids of a partition of the polygon into triangles. <br>
     * This suggests first triangulating the polygon, then forming a sum of the centroids of each triangle, weighted by
     * the area of each triangle, the whole sum normalized by the total polygon area. <br>
     * <br>
     * Simpler method: the triangulation need not be a partition, but rather can use positively and negatively oriented
     * triangles (with positive and negative areas), as is used when computing the area of a polygon. Then, simple
     * algorithm for computing the centroid is based on a sum of triangle centroids weighted with their signed area. The
     * triangles can be taken to be those formed by one fixed vertex v0 of the polygon, and the two endpoints of
     * consecutive edges of the polygon: (v1,v2), (v2,v3), etc.<br>
     *
     * @return position of the centroid assuming the polygon is closed, or null if shape is not valid
     */
    public Point2D getCentroid() {
        return getCentroid(getClosedPathSegments());
    }

    protected Point2D getCentroid(List<Line2D> lineSegmentList) {
        if (lineSegmentList != null) {
            double area = 0.0, cx = 0.0, cy = 0.0;

            for (Line2D line : lineSegmentList) {
                Point2D P1 = line.getP1();
                Point2D P2 = line.getP2();

                double tmp = (P1.getX() * P2.getY()) - (P2.getX() * P1.getY());
                area += tmp;
                cx += (P1.getX() + P2.getX()) * tmp;
                cy += (P1.getY() + P2.getY()) * tmp;
            }
            area /= 2.0;
            cx /= (6.0 * area);
            cy /= (6.0 * area);

            return new Point2D.Double(cx, cy);
        }
        return null;
    }

    /**
     * <b>Algorithm</b><br>
     * <br>
     * -1- List the x and y coordinates of each vertex of the polygon in counterclockwise order about the normal. Repeat
     * the coordinates of the first point at the end of the list. <br>
     * -2- Multiply the x coordinate of each vertex by the y coordinate of the next vertex.<br>
     * -3- Multiply the y coordinate of each vertex by the x coordinate of the next vertex <br>
     * -4- Subtract the sum of the products computed in step 3 from the sum of the products from step 2 <br>
     * -5- Divide this difference by 2 to get the area of the polygon. <br>
     * <br>
     * <b> Warning </b><br>
     * <br>
     * This formula computes area with orientation. When listing the points in a clockwise order instead of
     * counterclockwise, result is the negative of the area. <br>
     * The method produces the wrong answer for crossed polygons, where one side crosses over another. <br>
     * For instance, when two lines of the drawing path cross like a figure eight, result is the area surrounded
     * counterclockwise minus the area surrounded clockwise.<br>
     * It works correctly however for triangles, regular, irregular, convex and concave polygons. <br>
     * <br>
     * Solution is to compute area only from the outside path of the polygon with each vertices ordered in the same
     * direction.<br>
     * This can be achieved trough Area() constructors which decompose the shape into non-self-intersecting shape.
     */

    public Double getAreaValue() {
        return getAreaValue(getClosedPathSegments());
    }

    protected Double getAreaValue(List<Line2D> lineSegmentList) {
        if (lineSegmentList != null) {
            double area = 0.0;

            for (Line2D line : lineSegmentList) {
                Point2D P1 = line.getP1();
                Point2D P2 = line.getP2();

                area += (P1.getX() * P2.getY()) - (P2.getX() * P1.getY());
            }

            return Math.abs(area) / 2.0;
        }
        return null;
    }
}
