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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
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
 * @author Nicolas Roduit
 */
public class PolygonGraphic extends AbstractDragGraphicArea {

    public static final Icon ICON = new ImageIcon(PolygonGraphic.class.getResource("/icon/22x22/draw-polyline.png")); //$NON-NLS-1$

    public final static Measurement Area = new Measurement("Area", false, true, true);
    public final static Measurement Perimeter = new Measurement("Perimeter", true, true, true);
    public final static Measurement Width = new Measurement("Width", true, true, false);
    public final static Measurement Height = new Measurement("Height", true, true, false);
    public final static Measurement TopLeftPointX = new Measurement("Top Left X", true, true, false);
    public final static Measurement TopLeftPointY = new Measurement("Top Left Y", true, true, false);
    public final static Measurement CentroidX = new Measurement("Centroid X", true, true, false);
    public final static Measurement CentroidY = new Measurement("Centroid X", true, true, false);

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
        return Messages.getString("PolygonGraphic.title"); //$NON-NLS-1$
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

        if (handlePointList.size() > 1) {
            GeneralPath generalpath = new GeneralPath();
            Point2D p = handlePointList.get(0);
            generalpath.moveTo(p.getX(), p.getY());
            for (int i = 1; i < handlePointList.size(); i++) {
                p = handlePointList.get(i);
                if (p != null)
                    generalpath.lineTo(p.getX(), p.getY());
            }
            generalpath.closePath();
            newShape = generalpath;
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

                // if (Area.isComputed() && (!drawOnLabel || Area.isGraphicLabel())) {
                // Double val = null;
                // if (releaseEvent || Area.isQuickComputing())
                // val = getAreaValue() * ratio * ratio;
                // String unit = "pix".equals(adapter.getUnit()) ? adapter.getUnit() : adapter.getUnit() + "2";
                // measVal.add(new MeasureItem(Area, val, unit));
                // }
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

                if (stats != null)
                    measVal.addAll(stats);

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

    // /**
    // * < ALGO ><br>
    // * <br>
    // * - List the x and y coordinates of each vertex of the polygon in counterclockwise order. Repeat the coordinates
    // of
    // * the first point at the bottom of the list. <br>
    // * - Multiply the x coordinate of each vertex by the y coordinate of the next vertex.<br>
    // * - Multiply the y coordinate of each vertex by the x coordinate of the next vertex <br>
    // * - Subtract the sum of the products computed in step 3 from the sum of the products from step 2 <br>
    // * - Divide this difference by 2 to get the area of the polygon. <br>
    // * <br>
    // * < WARNINGS > <br>
    // * - If you list the points in a clockwise order instead of counterclockwise, you will get the negative of the
    // area. <br>
    // * - This formula computes area with orientation. If you use it on a shape where two of the lines cross like a
    // * figure eight, you will get the area surrounded counterclockwise minus the area surrounded clockwise.<br>
    // */
    //
    // public double getAreaValue() {
    //
    // double area = 0.0;
    //
    // Path2D closedPath = new Path2D.Double();
    // Shape boundingShape = null;
    //
    // computeArea:
    //
    // if (handlePointList.size() > 1 && handlePointList.get(0) != null) {
    //
    // closedPath.moveTo(handlePointList.get(0).getX(), handlePointList.get(0).getY());
    //
    // for (Point2D point : handlePointList) {
    // if (point != null)
    // closedPath.lineTo(point.getX(), point.getY());
    // else
    // break computeArea;
    // }
    // closedPath.closePath();
    //
    // boundingShape = new BasicStroke().createStrokedShape(closedPath);
    // List<Line2D> lineSegmentsList = new ArrayList<Line2D>(handlePointList.size());
    //
    // double coords[] = new double[12];
    // double movx = 0, movy = 0, curx = 0, cury = 0, newx, newy;
    //
    // PathIterator pi = boundingShape.getPathIterator(null);
    //
    // while (!pi.isDone()) {
    // switch (pi.currentSegment(coords)) {
    // case PathIterator.SEG_MOVETO:
    // curx = movx = coords[0];
    // cury = movy = coords[1];
    // break;
    // case PathIterator.SEG_LINETO:
    // newx = coords[0];
    // newy = coords[1];
    // lineSegmentsList.add(new Line2D.Double(curx, cury, newx, newy));
    // curx = newx;
    // cury = newy;
    // break;
    // case PathIterator.SEG_CLOSE:
    // lineSegmentsList.add(new Line2D.Double(curx, cury, movx, movy));
    // curx = movx;
    // cury = movy;
    // break;
    // }
    // pi.next();
    // }
    //
    // Rectangle2D bounds = boundingShape.getBounds2D();
    // double Ox = bounds.getX();
    // double Oy = bounds.getY();
    // double area2 = 0.0;
    // for (Line2D line : lineSegmentsList) {
    // area += ((line.getX1() - Ox) * (line.getY2() - Oy) - (line.getY1() - Oy) * (line.getX2() - Ox));
    // area2 += ((line.getX1()) * (line.getY2()) - (line.getY1()) * (line.getX2()));
    // }
    // area = Math.abs(area / 2);
    // }
    //
    // return area;
    // }

    // @Override
    // public void paint(Graphics2D g2d, AffineTransform transform) {
    // super.paint(g2d, transform);
    //
    // Paint oldPaint = g2d.getPaint();
    // Stroke oldStroke = g2d.getStroke();
    //
    // // -----------------------------------------------------------------------------------//
    // double area = 0.0;
    //
    // Path2D closedPath = new Path2D.Double();
    // List<Line2D> lineSegmentsList = new ArrayList<Line2D>(handlePointList.size());
    // Shape boundingShape = null;
    //
    // computeArea:
    //
    // if (handlePointList.size() > 1 && handlePointList.get(0) != null) {
    //
    // closedPath.moveTo(handlePointList.get(0).getX(), handlePointList.get(0).getY());
    //
    // for (Point2D point : handlePointList) {
    // if (point != null)
    // closedPath.lineTo(point.getX(), point.getY());
    // else
    // break computeArea;
    // }
    // closedPath.closePath();
    //
    // Stroke stroke = new BasicStroke(5);
    // boundingShape = stroke.createStrokedShape(closedPath);
    //
    // Area closedArea = new Area(closedPath);
    //
    // double coords[] = new double[12];
    // double movx = 0, movy = 0, curx = 0, cury = 0, newx, newy;
    //
    // PathIterator pi = closedArea.getPathIterator(null);
    //
    // while (!pi.isDone()) {
    // switch (pi.currentSegment(coords)) {
    // case PathIterator.SEG_MOVETO:
    // curx = movx = coords[0];
    // cury = movy = coords[1];
    // break;
    // case PathIterator.SEG_LINETO:
    // newx = coords[0];
    // newy = coords[1];
    // lineSegmentsList.add(new Line2D.Double(curx, cury, newx, newy));
    // curx = newx;
    // cury = newy;
    // break;
    // case PathIterator.SEG_CLOSE:
    // lineSegmentsList.add(new Line2D.Double(curx, cury, movx, movy));
    // curx = movx;
    // cury = movy;
    // break;
    // }
    // pi.next();
    // }
    //
    // Rectangle2D bounds = boundingShape.getBounds2D();
    // double Ox = bounds.getX();
    // double Oy = bounds.getY();
    // double area2 = 0.0;
    //
    // for (Line2D line : lineSegmentsList) {
    // area += ((line.getX1() - Ox) * (line.getY2() - Oy) - (line.getY1() - Oy) * (line.getX2() - Ox));
    // area2 += ((line.getX1()) * (line.getY2()) - (line.getY1()) * (line.getX2()));
    // }
    //
    // }
    // // -----------------------------------------------------------------------------------//
    // // g2d.setPaint(Color.RED);
    // // if (transform != null)
    // // g2d.draw(transform.createTransformedShape(closedPath));
    //
    // // g2d.setPaint(Color.BLUE);
    // // if (transform != null)
    // // g2d.draw(transform.createTransformedShape(boundingShape));
    //
    // boolean switchDraw = false;
    // Stroke stroke1 = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
    // Stroke stroke2 = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
    //
    // if (transform != null) {
    // for (Line2D line : lineSegmentsList) {
    // switchDraw = !switchDraw;
    // g2d.setStroke(switchDraw ? stroke1 : stroke2);
    // g2d.setPaint(switchDraw ? Color.GREEN : Color.BLUE);
    // g2d.draw(transform.createTransformedShape(line));
    // }
    // }
    //
    // g2d.setPaint(oldPaint);
    // g2d.setStroke(oldStroke);
    // }
    //
    // // return the centroid of the polygon
    // public Point2D.Double getCentroid() {
    // Rectangle2D.Float bounds = getBoundValue();
    // double x = bounds.x;
    // double y = bounds.y;
    // double cx = 0.0, cy = 0.0;
    // for (int m = 0; m < points.length - 2; m = m + 2) {
    // cx =
    // cx + (points[m] + points[m + 2] - 2 * x)
    // * ((points[m + 1] - y) * (points[m + 2] - x) - (points[m] - x) * (points[m + 3] - y));
    // cy =
    // cy + (points[m + 1] + points[m + 3] - 2 * y)
    // * ((points[m + 1] - y) * (points[m + 2] - x) - (points[m] - x) * (points[m + 3] - y));
    // }
    // double area = getAreaValue();
    // cx /= (6 * area);
    // cy /= (6 * area);
    // return new Point2D.Double(x + cx, y + cy);
    // }

    // // return bound of polygon
    // public Rectangle2D.Float getBoundValue() {
    // Shape shape = new Path2D.Double();
    //
    // area.
    //
    // float[] rect = { Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
    // for (int m = 0; m < points.length; m = m + 2) {
    // if (points[m] < rect[0]) {
    // rect[0] = points[m];
    // }
    // if (points[m + 1] < rect[1]) {
    // rect[1] = points[m + 1];
    // }
    // if (points[m] > rect[2]) {
    // rect[2] = points[m];
    // }
    // if (points[m + 1] > rect[3]) {
    // rect[3] = points[m + 1];
    // }
    // }
    // return new Rectangle2D.Float(rect[0], rect[1], rect[2], rect[3]);
    // }
}
