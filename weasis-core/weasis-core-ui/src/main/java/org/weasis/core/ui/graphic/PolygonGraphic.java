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
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

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
    public String getDescription() {
        return null;
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
        // TODO Auto-generated method stub
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

    // // return area of polygon
    // public double getAreaValue() {
    // Rectangle2D.Float bounds = getBoundValue();
    // double x = bounds.x;
    // double y = bounds.y;
    // double sum = 0.0;
    // for (int m = 0; m < points.length - 2; m = m + 2) {
    // sum = sum + ((points[m] - x) * (points[m + 3] - y)) - ((points[m + 1] - y) * (points[m + 2] - x));
    // }
    // return Math.abs(0.5 * sum);
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
    //
    // // return bound of polygon
    // public Rectangle2D.Float getBoundValue() {
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
