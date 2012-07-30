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
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class PolygonGraphic.
 * 
 * 
 * @author Nicolas Roduit
 */
public class PolylineGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(PolylineGraphic.class.getResource("/icon/22x22/draw-polyline.png")); //$NON-NLS-1$

    public static final Measurement FIRST_POINT_X = new Measurement(
        Messages.getString("measure.firstx"), 1, true, true, false); //$NON-NLS-1$
    public static final Measurement FIRST_POINT_Y = new Measurement(
        Messages.getString("measure.firsty"), 2, true, true, false); //$NON-NLS-1$
    public static final Measurement LAST_POINT_X = new Measurement(
        Messages.getString("measure.lastx"), 3, true, true, false); //$NON-NLS-1$
    public static final Measurement LAST_POINT_Y = new Measurement(
        Messages.getString("measure.lasty"), 4, true, true, false); //$NON-NLS-1$
    public static final Measurement LINE_LENGTH = new Measurement(
        Messages.getString("measure.length"), 5, true, true, true); //$NON-NLS-1$

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public PolylineGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(AbstractDragGraphic.UNDEFINED, paintColor, lineThickness, labelVisible, false);
    }

    public PolylineGraphic(List<Point2D> handlePointList, Color paintColor, float lineThickness, boolean labelVisible) {
        super(handlePointList, AbstractDragGraphic.UNDEFINED, paintColor, lineThickness, labelVisible, false);
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
            newShape = polygonPath;
        }
        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public List<MeasureItem> computeMeasurements(ImageLayer layer, boolean releaseEvent) {

        if (layer != null && layer.getSourceImage() != null && isShapeValid()) {
            MeasurementsAdapter adapter = layer.getSourceImage().getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>(5);

                double ratio = adapter.getCalibRatio();
                String unitStr = adapter.getUnit();
                // Get copy to be sure point value are not modified any more and filter point egal to null.
                List<Point2D> handlePointListcopy = new ArrayList<Point2D>(handlePointList.size());
                for (Point2D handlePt : handlePointList) {
                    if (handlePt != null) {
                        handlePointListcopy.add((Point2D) handlePt.clone());
                    }
                }
                Point2D ptA = null;
                Point2D ptB = null;

                if (FIRST_POINT_X.isComputed()) {
                    if (handlePointListcopy.size() > 0) {
                        ptA = handlePointListcopy.get(0);
                    }
                    measVal.add(new MeasureItem(FIRST_POINT_X, adapter.getXCalibratedValue(ptA.getX()), adapter
                        .getUnit()));
                }
                if (FIRST_POINT_Y.isComputed()) {
                    if (handlePointListcopy.size() > 0) {
                        ptA = handlePointListcopy.get(0);
                    }
                    measVal.add(new MeasureItem(FIRST_POINT_Y, adapter.getXCalibratedValue(ptA.getY()), adapter
                        .getUnit()));
                }
                if (LAST_POINT_X.isComputed()) {
                    if (handlePointListcopy.size() > 1) {
                        ptB = handlePointListcopy.get(handlePointList.size() - 1);
                    }
                    measVal.add(new MeasureItem(LAST_POINT_X, adapter.getXCalibratedValue(ptB.getX()), adapter
                        .getUnit()));
                }
                if (LAST_POINT_Y.isComputed()) {
                    if (handlePointListcopy.size() > 1) {
                        ptB = handlePointListcopy.get(handlePointList.size() - 1);
                    }
                    measVal.add(new MeasureItem(LAST_POINT_Y, adapter.getXCalibratedValue(ptB.getY()), adapter
                        .getUnit()));
                }
                if (LINE_LENGTH.isComputed()) {
                    Double val = (handlePointListcopy.size() > 1) ? getPerimeter(handlePointListcopy) * ratio : null;
                    measVal.add(new MeasureItem(LINE_LENGTH, val, unitStr));
                }
                return measVal;
            }
        }
        return null;
    }

    @Override
    public List<Measurement> getMeasurementList() {
        List<Measurement> list = new ArrayList<Measurement>();
        list.add(FIRST_POINT_X);
        list.add(FIRST_POINT_Y);
        list.add(LAST_POINT_X);
        list.add(LAST_POINT_Y);
        list.add(LINE_LENGTH);
        return list;
    }

    protected Double getPerimeter(List<Point2D> handlePointList) {
        if (handlePointList.size() > 1) {
            double perimeter = 0.0;
            Point2D pLast = handlePointList.get(0);
            for (int i = 1; i < handlePointList.size(); i++) {
                Point2D P2 = handlePointList.get(i);
                perimeter += pLast.distance(P2);
                pLast = P2;
            }
            return perimeter;
        }
        return null;
    }

    @Override
    public void forceToAddPoints(int fromPtIndex) {
        if (isVariablePointsNumber() && fromPtIndex >= 0 && fromPtIndex < handlePointList.size()) {
            if (fromPtIndex < handlePointList.size() - 1) {
                // Add only one point
                handlePointList.add(fromPtIndex, getHandlePoint(fromPtIndex));
                handlePointTotalNumber++;
            } else {
                // Continue to draw when it is the last point
                handlePointTotalNumber = UNDEFINED;
            }
        }
    }
}
