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
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.MouseEventDouble;

public class LineGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(LineGraphic.class.getResource("/icon/22x22/draw-line.png")); //$NON-NLS-1$

    public static final Measurement FIRST_POINT_X = new Measurement(Messages.getString("measure.firstx"), 1, true, true, false); //$NON-NLS-1$
    public static final Measurement FIRST_POINT_Y = new Measurement(Messages.getString("measure.firsty"), 2, true, true, false); //$NON-NLS-1$
    public static final Measurement LAST_POINT_X = new Measurement(Messages.getString("measure.lastx"), 3, true, true, false); //$NON-NLS-1$
    public static final Measurement LAST_POINT_Y = new Measurement(Messages.getString("measure.lasty"), 4, true, true, false); //$NON-NLS-1$
    public static final Measurement LINE_LENGTH = new Measurement(Messages.getString("measure.line_length"), 5, true, true, true); //$NON-NLS-1$
    public static final Measurement ORIENTATION = new Measurement(Messages.getString("measure.orientation"), 6, true, true, false); //$NON-NLS-1$
    public static final Measurement AZIMUTH = new Measurement(Messages.getString("measure.azimuth"), 7, true, true, false); //$NON-NLS-1$

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    protected Point2D ptA, ptB; // Let AB be a simple a line segment
    protected boolean lineABvalid; // estimate if line segment is valid or not

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public LineGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(2, paintColor, lineThickness, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.line"); //$NON-NLS-1$
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {

        updateTool();
        Shape newShape = null;

        if (lineABvalid) {
            newShape = new Line2D.Double(ptA, ptB);
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public List<MeasureItem> computeMeasurements(ImageElement imageElement, boolean releaseEvent) {
        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();

                if (FIRST_POINT_X.isComputed()) {
                    measVal.add(new MeasureItem(FIRST_POINT_X, adapter.getXCalibratedValue(ptA.getX()), adapter
                        .getUnit()));
                }
                if (FIRST_POINT_Y.isComputed()) {
                    measVal.add(new MeasureItem(FIRST_POINT_Y, adapter.getXCalibratedValue(ptA.getY()), adapter
                        .getUnit()));
                }
                if (LAST_POINT_X.isComputed()) {
                    measVal.add(new MeasureItem(LAST_POINT_X, adapter.getXCalibratedValue(ptB.getX()), adapter
                        .getUnit()));
                }
                if (LAST_POINT_Y.isComputed()) {
                    measVal.add(new MeasureItem(LAST_POINT_Y, adapter.getXCalibratedValue(ptB.getY()), adapter
                        .getUnit()));
                }
                if (LINE_LENGTH.isComputed()) {
                    measVal.add(new MeasureItem(LINE_LENGTH, ptA.distance(ptB) * adapter.getCalibRatio(), adapter
                        .getUnit()));
                }
                if (ORIENTATION.isComputed()) {
                    measVal.add(new MeasureItem(ORIENTATION, MathUtil.getOrientation(ptA, ptB), Messages.getString("measure.deg"))); //$NON-NLS-1$
                }
                if (AZIMUTH.isComputed()) {
                    measVal.add(new MeasureItem(AZIMUTH, MathUtil.getAzimuth(ptA, ptB), Messages.getString("measure.deg"))); //$NON-NLS-1$
                }
                return measVal;
            }
        }
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
        return ptA;
    }

    public Point2D getEndPoint() {
        updateTool();
        return ptB;
    }

    @Override
    public List<Measurement> getMeasurementList() {
        List<Measurement> list = new ArrayList<Measurement>();
        list.add(FIRST_POINT_X);
        list.add(FIRST_POINT_Y);
        list.add(LAST_POINT_X);
        list.add(LAST_POINT_Y);
        list.add(LINE_LENGTH);
        list.add(ORIENTATION);
        list.add(AZIMUTH);
        return list;
    }
}
