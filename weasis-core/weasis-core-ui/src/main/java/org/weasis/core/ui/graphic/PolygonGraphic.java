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
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

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
 * @author Nicolas Roduit
 */
public class PolygonGraphic extends AbstractDragGraphicArea {

    public static final Icon ICON = new ImageIcon(PolygonGraphic.class.getResource("/icon/22x22/draw-polyline.png")); //$NON-NLS-1$

    public final static Measurement Area = new Measurement("Area", true, true, true); //$NON-NLS-1$
    public final static Measurement Perimeter = new Measurement("Perimeter", true, true, true); //$NON-NLS-1$
    public final static Measurement Width = new Measurement("Width", true, true, false); //$NON-NLS-1$
    public final static Measurement Height = new Measurement("Height", true, true, false); //$NON-NLS-1$
    public final static Measurement TopLeftPointX = new Measurement("Top Left X", true, true, false); //$NON-NLS-1$
    public final static Measurement TopLeftPointY = new Measurement("Top Left Y", true, true, false); //$NON-NLS-1$
    public final static Measurement CentroidX = new Measurement("Centroid X", true, true, false); //$NON-NLS-1$
    public final static Measurement CentroidY = new Measurement("Centroid X", true, true, false); //$NON-NLS-1$

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

    @Override
    public void paintHandles(Graphics2D g2d, AffineTransform transform) {
        if (resizingOrMoving) {
            // Force to display handles even on resizing or moving
            resizingOrMoving = false;
            super.paintHandles(g2d, transform);
            resizingOrMoving = true;
        } else {
            super.paintHandles(g2d, transform);
        }
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {
        Shape newShape = null;

        PATH_AREA_ITERATION:

        if (handlePointList.size() > 1 && handlePointList.get(0) != null) {

            Path2D polygonPath = new Path2D.Double(Path2D.WIND_NON_ZERO, handlePointList.size());
            polygonPath.moveTo(handlePointList.get(0).getX(), handlePointList.get(0).getY());

            for (int i = 1; i < handlePointList.size(); i++) {
                Point2D pt = handlePointList.get(i);
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
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {

        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>(10);

                double ratio = adapter.getCalibRatio();
                double ratioSQ = ratio * ratio;

                if (Area.isComputed() && (!drawOnLabel || Area.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || Area.isQuickComputing()) {
                        val = getAreaValue() * ratioSQ;
                    }

                    String unit = "pix".equals(adapter.getUnit()) ? adapter.getUnit() : adapter.getUnit() + "2";
                    measVal.add(new MeasureItem(Area, val, unit));
                }
                // if (Perimeter.isComputed() && (!drawOnLabel || Perimeter.isGraphicLabel())) {
                // Double val = releaseEvent || Perimeter.isQuickComputing() ? ratio * radius * 2.0 : null;
                // measVal.add(new MeasureItem(Perimeter, val, adapter.getUnit()));
                // }
                // if (Width.isComputed() && (!drawOnLabel || Width.isGraphicLabel())) {
                // Double val = releaseEvent || Width.isQuickComputing() ? ratio * Width : null;
                // measVal.add(new MeasureItem(Width, val, adapter.getUnit()));
                // }
                // if (Height.isComputed() && (!drawOnLabel || Height.isGraphicLabel())) {
                // Double val = null;
                // if (releaseEvent || Height.isQuickComputing())
                // val = adapter.getYCalibratedValue(Height.getY());
                // measVal.add(new MeasureItem(Height, val, adapter.getUnit()));
                // }
                //
                // if (CentroidX.isComputed() && (!drawOnLabel || CentroidX.isGraphicLabel())) {
                // Double val = null;
                // if (releaseEvent || CentroidX.isQuickComputing())
                // val = adapter.getXCalibratedValue(CentroidX.getX());
                // measVal.add(new MeasureItem(CentroidX, val, adapter.getUnit()));
                // }
                // if (CentroidY.isComputed() && (!drawOnLabel || CentroidY.isGraphicLabel())) {
                // Double val = null;
                // if (releaseEvent || CentroidY.isQuickComputing())
                // val = adapter.getXCalibratedValue(CentroidY.getX());
                // measVal.add(new MeasureItem(CentroidY, val, adapter.getUnit()));
                // }
                // if (TopLeftPointX.isComputed() && (!drawOnLabel || TopLeftPointX.isGraphicLabel())) {
                // Double val = null;
                // if (releaseEvent || TopLeftPointX.isQuickComputing())
                // val = adapter.getXCalibratedValue(TopLeftPointX.getX());
                // measVal.add(new MeasureItem(TopLeftPointX, val, adapter.getUnit()));
                // }
                // if (TopLeftPointY.isComputed() && (!drawOnLabel || TopLeftPointY.isGraphicLabel())) {
                // Double val = null;
                // if (releaseEvent || TopLeftPointY.isQuickComputing())
                // val = adapter.getXCalibratedValue(TopLeftPointY.getX());
                // measVal.add(new MeasureItem(TopLeftPointY, val, adapter.getUnit()));
                // }

                List<MeasureItem> stats = getImageStatistics(imageElement, releaseEvent);

                if (stats != null) {
                    measVal.addAll(stats);
                }

                return measVal;
            }
        }
        return null;
    }

    // public static double getPolygonPerimeter(PolygonGraphic graph) {
    // float[] coord = graph.getPoints();
    // double perimeter = 0.0;
    // if (coord != null && coord.length > 2) {
    // float x1 = coord[0];
    // float y1 = coord[1];
    // for (int m = 2; m < coord.length; m = m + 2) {
    // float x2 = coord[m];
    // float y2 = coord[m + 1];
    // perimeter += (float) Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    // x1 = x2;
    // y1 = y2;
    // }
    // }
    // return perimeter;
    // }

    /**
     * The centroid (a.k.a. the center of mass, or center of gravity) of a polygon can be computed as the weighted sum
     * of the centroids of a partition of the polygon into triangles. <br>
     * This suggests first triangulating the polygon, then forming a sum of the centroids of each triangle, weighted by
     * the area of each triangle, the whole sum normalized by the total polygon area. <br>
     * <br>
     * This indeed works, but there is a simpler method: the triangulation need not be a partition, but rather can use
     * positively and negatively oriented triangles (with positive and negative areas), as is used when computing the
     * area of a polygon. This leads to a very simple algorithm for computing the centroid, based on a sum of triangle
     * centroids weighted with their signed area. The triangles can be taken to be those formed by one fixed vertex v0
     * of the polygon, and the two endpoints of consecutive edges of the polygon: (v1,v2), (v2,v3), etc. The area of a
     * triangle with vertices a, b, c is half of this expression:
     * 
     * (b[X] - a[X]) * (c[Y] - a[Y]) - (c[X] - a[X]) * (b[Y] - a[Y]);
     * 
     * 
     * @return the centroid of the polygon or null if shape is not valid
     */
    public Point2D.Double getCentroid() {
        PATH_AREA_ITERATION:

        if (handlePointList.size() > 1 && handlePointList.get(0) != null) {

            Path2D polygonPath = new Path2D.Double(Path2D.WIND_NON_ZERO, handlePointList.size());
            polygonPath.moveTo(handlePointList.get(0).getX(), handlePointList.get(0).getY());

            for (int i = 1; i < handlePointList.size(); i++) {
                Point2D pt = handlePointList.get(i);
                if (pt == null) {
                    break PATH_AREA_ITERATION;
                }
                polygonPath.lineTo(pt.getX(), pt.getY());
            }

            // polygonPath.closePath(); // Useless since Area constructor will decompose the shape into a closed path

            PathIterator pathIt = new Area(polygonPath).getPathIterator(null);

            double coords[] = new double[6];
            double startX = NaN, startY = NaN, curX = NaN, curY = NaN;
            double area = 0.0, cx = 0.0, cy = 0.0;

            while (!pathIt.isDone()) {

                int segType = pathIt.currentSegment(coords);
                double lastX = coords[0], lastY = coords[1];

                switch (segType) {
                    case PathIterator.SEG_CLOSE:
                        lastX = startX;
                        lastY = startY;
                    case PathIterator.SEG_LINETO:
                        double tmp = (curX * lastY) - (lastX * curY);
                        area += tmp;
                        cx += (curX + lastX) * tmp;
                        cy += (curY + lastY) * tmp;

                        curX = lastX;
                        curY = lastY;
                        break;
                    case PathIterator.SEG_MOVETO:
                        startX = curX = lastX;
                        startY = curY = lastY;
                        break;
                    default:
                        logger.warn("pathIterator contains curved segments");
                        // Should never happen with a FlatteningPathIterator
                        break PATH_AREA_ITERATION;

                }
                pathIt.next();
            }
            if (Double.isNaN(area)) {
                logger.warn("pathIterator contains an open path");
                break PATH_AREA_ITERATION;
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

    public double getAreaValue() {

        PATH_AREA_ITERATION:

        if (handlePointList.size() > 1 && handlePointList.get(0) != null) {

            Path2D polygonPath = new Path2D.Double(Path2D.WIND_NON_ZERO, handlePointList.size());
            polygonPath.moveTo(handlePointList.get(0).getX(), handlePointList.get(0).getY());

            for (int i = 1; i < handlePointList.size(); i++) {
                Point2D pt = handlePointList.get(i);
                if (pt == null) {
                    break PATH_AREA_ITERATION;
                }
                polygonPath.lineTo(pt.getX(), pt.getY());
            }
            // polygonPath.closePath(); // Useless since Area constructor will decompose the shape into a closed path

            PathIterator pathIt = new Area(polygonPath).getPathIterator(null);

            double coords[] = new double[6];
            double startX = NaN, startY = NaN, curX = NaN, curY = NaN;
            double area = 0.0;

            while (!pathIt.isDone()) {

                int segType = pathIt.currentSegment(coords);
                double lastX = coords[0], lastY = coords[1];

                switch (segType) {
                    case PathIterator.SEG_CLOSE:
                        lastX = startX;
                        lastY = startY;
                    case PathIterator.SEG_LINETO:
                        area += (curX * lastY) - (lastX * curY);
                        curX = lastX;
                        curY = lastY;
                        break;
                    case PathIterator.SEG_MOVETO:
                        startX = curX = lastX;
                        startY = curY = lastY;
                        break;
                    default:
                        logger.warn("pathIterator contains curved segments");
                        // Should never happen with a FlatteningPathIterator
                        break PATH_AREA_ITERATION;

                }
                pathIt.next();
            }
            if (Double.isNaN(area)) {
                logger.warn("pathIterator contains an open path");
                break PATH_AREA_ITERATION;
            }

            return 0.5 * Math.abs(area);
        }

        return 0.0;
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    // For graphic DEBUG only
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
    // int ptIndex = 0;
    // Map<Point2D, StringBuilder> ptMap = new HashMap<Point2D, StringBuilder>(lineSegmentList.size() * 2);
    // Set<Point2D> ptSet = ptMap.keySet();
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
