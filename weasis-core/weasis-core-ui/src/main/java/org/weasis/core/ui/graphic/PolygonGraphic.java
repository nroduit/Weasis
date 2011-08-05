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
import java.awt.Shape;
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

import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class PolygonGraphic.
 * 
 * 
 * @author Nicolas Roduit,Benoit Jacquemoud
 */
public class PolygonGraphic extends AbstractDragGraphicArea {

    public static final Icon ICON = new ImageIcon(PolygonGraphic.class.getResource("/icon/22x22/draw-polyline.png")); //$NON-NLS-1$

    public static final Measurement AREA = new Measurement("Area", 1, true, true, true); //$NON-NLS-1$
    public static final Measurement PERIMETER = new Measurement("Perimeter", 2, true, true, false); //$NON-NLS-1$
    public static final Measurement WIDTH = new Measurement("Width", 3, true, true, false); //$NON-NLS-1$
    public static final Measurement HEIGHT = new Measurement("Height", 4, true, true, false); //$NON-NLS-1$
    public static final Measurement TOP_LEFT_POINT_X = new Measurement("Top Left X", 5, true, true, false); //$NON-NLS-1$
    public static final Measurement TOP_LEFT_POINT_Y = new Measurement("Top Left Y", 6, true, true, false); //$NON-NLS-1$
    public static final Measurement CENTROID_X = new Measurement("Centroid X", 7, true, true, false); //$NON-NLS-1$
    public static final Measurement CENTROID_Y = new Measurement("Centroid X", 8, true, true, false); //$NON-NLS-1$

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public PolygonGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(AbstractDragGraphic.UNDEFINED, paintColor, lineThickness, labelVisible);
    }

    public PolygonGraphic(List<Point2D> handlePointList, Color paintColor, float lineThickness, boolean labelVisible,
        boolean filled) {
        super(handlePointList, AbstractDragGraphic.UNDEFINED, paintColor, lineThickness, labelVisible, filled);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.polygon"); //$NON-NLS-1$
    }

    /**
     * Force to display handles even during resizing or moving sequences
     */
    @Override
    protected boolean isResizingOrMoving() {
        return false;
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {

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
    public List<MeasureItem> computeMeasurements(ImageElement imageElement, boolean releaseEvent) {

        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

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
                    String unit = "pix".equals(unitStr) ? unitStr : unitStr + "2";
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

                List<MeasureItem> stats = getImageStatistics(imageElement, releaseEvent);

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
                Point2D P1 = line.getP1();
                Point2D P2 = line.getP2();

                double dx = P1.getX() - P2.getX();
                double dy = P1.getY() - P2.getY();

                perimeter += Math.sqrt(Math.abs((dx * dx) - (dy * dy)));
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

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    // DEBUG only - overridden method defined for graphic debug

    // @Override
    // public void paint(Graphics2D g2d, AffineTransform transform) {
    // super.paint(g2d, transform);
    //
    // Paint oldPaint = g2d.getPaint();
    // Stroke oldStroke = g2d.getStroke();
    //
    // List<Line2D> lineSegmentList = getClosedPathSegments(getPathArea());
    //
    // if (lineSegmentList != null) {
    // double area = 0.0, cx = 0.0, cy = 0.0;
    // for (Line2D line : lineSegmentList) {
    // Point2D P1 = line.getP1();
    // Point2D P2 = line.getP2();
    //
    // double tmp = (P1.getX() * P2.getY()) - (P2.getX() * P1.getY());
    // area += tmp;
    // cx += (P1.getX() + P2.getX()) * tmp;
    // cy += (P1.getY() + P2.getY()) * tmp;
    // }
    //
    // area = 0.5 * area;
    // cx /= (6.0 * area);
    // cy /= (6.0 * area);
    //
    // Point2D cT =
    // (transform != null) ? transform.transform(new Point2D.Double(cx, cy), null)
    // : new Point2D.Double(cx, cy);
    //
    // Path2D centroid = new Path2D.Double();
    // centroid.append(new Line2D.Double(cT.getX() - 8, cT.getY(), cT.getX() + 8, cT.getY()), false);
    // centroid.append(new Line2D.Double(cT.getX(), cT.getY() - 8, cT.getX(), cT.getY() + 8), false);
    // centroid.append(new Ellipse2D.Double(cT.getX() - 5, cT.getY() - 5, 10, 10), false);
    //
    // g2d.draw(centroid);
    //
    // Map<Point2D, StringBuilder> ptMap = new HashMap<Point2D, StringBuilder>(lineSegmentList.size() * 2);
    // int ptIndex = 0;
    //
    // for (Line2D line : lineSegmentList) {
    // for (Point2D pt : new Point2D[] { line.getP1(), line.getP2() }) {
    // StringBuilder sb = ptMap.get(pt);
    // if (sb == null) {
    // ptMap.put(pt, new StringBuilder(Integer.toString(ptIndex++)));
    // } else {
    // sb.append(" , ").append(Integer.toString(ptIndex++));
    // }
    // }
    // }
    //
    // for (Entry<Point2D, StringBuilder> entry : ptMap.entrySet()) {
    // Point2D pt = entry.getKey();
    // String str = entry.getValue().toString();
    // if (transform != null) {
    // pt = transform.transform(new Point2D.Double(pt.getX() + 5, pt.getY() + 5), null);
    // }
    // g2d.drawString(str, (float) pt.getX(), (float) pt.getY());
    // }
    //
    // g2d.setPaint(oldPaint);
    // g2d.setStroke(oldStroke);
    // }
    // }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    // DEBUG only - previous version but still useful to understand full process

    // @Override
    // public void paint(Graphics2D g2d, AffineTransform transform) {
    // super.paint(g2d, transform);
    //
    // Paint oldPaint = g2d.getPaint();
    // Stroke oldStroke = g2d.getStroke();
    //
    // PATH_AREA_ITERATION:
    //
    // if (handlePointList.size() > 1 && handlePointList.get(0) != null) {
    //
    // Path2D polygonPath = new Path2D.Double(Path2D.WIND_NON_ZERO, handlePointList.size());
    // polygonPath.moveTo(handlePointList.get(0).getX(), handlePointList.get(0).getY());
    //
    // for (int i = 1; i < handlePointList.size(); i++) {
    // Point2D pt = handlePointList.get(i);
    // if (pt == null) {
    // break PATH_AREA_ITERATION;
    // }
    // polygonPath.lineTo(pt.getX(), pt.getY());
    // }
    //
    // List<Line2D> lineSegmentList = new ArrayList<Line2D>(handlePointList.size());
    // PathIterator pathIt = new Area(polygonPath).getPathIterator(null);
    //
    // double coords[] = new double[6];
    // double startX = NaN, startY = NaN, curX = NaN, curY = NaN;
    // double area = 0.0, cx = 0.0, cy = 0.0;
    //
    // while (!pathIt.isDone()) {
    //
    // int segType = pathIt.currentSegment(coords);
    // double lastX = coords[0], lastY = coords[1];
    //
    // switch (segType) {
    // case PathIterator.SEG_CLOSE:
    // // lineSegmentList.add(new Line2D.Double(curX, curY, startX, startY));
    // lastX = startX;
    // lastY = startY;
    // case PathIterator.SEG_LINETO:
    // double tmp = (curX * lastY) - (lastX * curY);
    // area += tmp;
    // cx += (curX + lastX) * tmp;
    // cy += (curY + lastY) * tmp;
    //
    // BigDecimal dX = new BigDecimal(Math.abs(curX - lastX)).setScale(10, RoundingMode.DOWN);
    // BigDecimal dY = new BigDecimal(Math.abs(curY - lastY)).setScale(10, RoundingMode.DOWN);
    // if (dX.compareTo(BigDecimal.ZERO) != 0 || dY.compareTo(BigDecimal.ZERO) != 0) {
    // lineSegmentList.add(new Line2D.Double(curX, curY, lastX, lastY));
    // }
    // curX = lastX;
    // curY = lastY;
    // break;
    // case PathIterator.SEG_MOVETO:
    // startX = curX = lastX;
    // startY = curY = lastY;
    // break;
    // default:
    // break PATH_AREA_ITERATION;
    // }
    // pathIt.next();
    // }
    //
    // if (java.lang.Double.isNaN(area)) {
    // logger.warn("pathIterator contains an open path");
    // break PATH_AREA_ITERATION;
    // }
    //
    // area = 0.5 * area;
    // cx /= (6.0 * area);
    // cy /= (6.0 * area);
    //
    // Point2D cT =
    // (transform != null) ? transform.transform(new Point2D.Double(cx, cy), null)
    // : new Point2D.Double(cx, cy);
    //
    // Path2D centroid = new Path2D.Double();
    // centroid.append(new Line2D.Double(cT.getX() - 8, cT.getY(), cT.getX() + 8, cT.getY()), false);
    // centroid.append(new Line2D.Double(cT.getX(), cT.getY() - 8, cT.getX(), cT.getY() + 8), false);
    // centroid.append(new Ellipse2D.Double(cT.getX() - 5, cT.getY() - 5, 10, 10), false);
    //
    // g2d.draw(centroid);
    //
    // Map<Point2D, StringBuilder> ptMap = new HashMap<Point2D, StringBuilder>(lineSegmentList.size() * 2);
    // Set<Point2D> ptSet = ptMap.keySet();
    // int ptIndex = 0;
    //
    // for (Line2D line : lineSegmentList) {
    // for (Point2D pt : new Point2D[] { line.getP1(), line.getP2() }) {
    //
    // for (Point2D p : ptSet) {
    // BigDecimal dist = new BigDecimal(p.distance(pt)).setScale(10, RoundingMode.DOWN);
    // if (dist.compareTo(BigDecimal.ZERO) == 0) {
    // pt = p;
    // break;
    // }
    // }
    //
    // StringBuilder sb = ptMap.get(pt);
    // if (sb == null) {
    // ptMap.put(pt, new StringBuilder(Integer.toString(ptIndex++)));
    // } else {
    // sb.append(" , ").append(Integer.toString(ptIndex++));
    // }
    // }
    // }
    //
    // for (Entry<Point2D, StringBuilder> entry : ptMap.entrySet()) {
    // Point2D pt = entry.getKey();
    // String str = entry.getValue().toString();
    // if (transform != null) {
    // pt = transform.transform(new Point2D.Double(pt.getX() + 5, pt.getY() + 5), null);
    // }
    // g2d.drawString(str, (float) pt.getX(), (float) pt.getY());
    // }
    // }
    //
    // g2d.setPaint(oldPaint);
    // g2d.setStroke(oldStroke);
    // }
}
