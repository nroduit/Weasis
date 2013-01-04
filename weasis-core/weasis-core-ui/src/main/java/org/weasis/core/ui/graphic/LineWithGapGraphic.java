/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Benoit Jacquemoud
 ******************************************************************************/
package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

import javax.swing.Icon;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.ui.util.MouseEventDouble;

public class LineWithGapGraphic extends AbstractDragGraphic {

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    protected Point2D ptA, ptB; // Let AB be a simple a line segment
    protected boolean lineABvalid; // estimate if line segment is valid or not
    protected Point2D centerGap;
    protected int gapSize;

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public LineWithGapGraphic(Point2D ptStart, Point2D ptEnd, float lineThickness, Color paintColor,
        boolean labelVisible, Point2D centerGap, int gapSize) throws InvalidShapeException {
        super(2, paintColor, lineThickness, labelVisible, false);
        if (ptStart == null || ptEnd == null) {
            throw new InvalidShapeException("Point2D is null!");
        }
        this.gapSize = gapSize;
        this.centerGap = centerGap;
        setHandlePointList(ptStart, ptEnd);

        if (!isShapeValid()) {
            throw new InvalidShapeException("This shape cannot be drawn");
        }
    }

    protected void setHandlePointList(Point2D ptStart, Point2D ptEnd) {
        setHandlePoint(0, ptStart == null ? null : (Point2D) ptStart.clone());
        setHandlePoint(1, ptEnd == null ? null : (Point2D) ptEnd.clone());
        buildShape(null);
    }

    @Override
    public Icon getIcon() {
        return LineGraphic.ICON;
    }

    @Override
    public String getUIName() {
        return "";
    }

    @Override
    protected void buildShape(MouseEventDouble mouseEvent) {
        updateTool();
        Shape newShape = null;

        if (lineABvalid) {
            if (centerGap == null) {
                centerGap = GeomUtil.getColinearPointWithRatio(ptA, ptB, 0.5);
            }
            double dist = ptA.distance(ptB);
            double distCenterGap = ptA.distance(centerGap);
            double distCenterB = ptB.distance(centerGap);

            Point2D ptap = null;
            Point2D ptbp = null;
            if (distCenterGap < dist && distCenterB < dist) {
                double distGap = 0.5 * gapSize / dist;
                ptap = GeomUtil.getColinearPointWithRatio(ptA, ptB, distCenterGap / dist - distGap);
                ptbp = GeomUtil.getColinearPointWithRatio(ptA, ptB, distCenterGap / dist + distGap);
            }

            Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 4);
            path.moveTo(ptA.getX(), ptA.getY());
            if (ptap != null) {
                path.lineTo(ptap.getX(), ptap.getY());
                path.moveTo(ptbp.getX(), ptbp.getY());
            }
            path.lineTo(ptB.getX(), ptB.getY());

            newShape = path;
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public List<MeasureItem> computeMeasurements(ImageLayer layer, boolean releaseEvent) {
        return null;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateTool() {
        ptA = getHandlePoint(0);
        ptB = getHandlePoint(1);

        lineABvalid = ptA != null && ptB != null && !ptB.equals(ptA);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public Point2D getStartPoint() {
        updateTool();
        return ptA == null ? null : (Point2D) ptA.clone();
    }

    public Point2D getEndPoint() {
        updateTool();
        return ptB == null ? null : (Point2D) ptB.clone();
    }

    @Override
    public List<Measurement> getMeasurementList() {
        return null;
    }
}
