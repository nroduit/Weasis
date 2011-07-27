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

    public static final Measurement FIRST_POINT_X = new Measurement("First point X", true, true, false);
    public static final Measurement FIRST_POINT_Y = new Measurement("First point Y", true, true, false);
    public static final Measurement LAST_POINT_X = new Measurement("Last point X", true, true, false);
    public static final Measurement LAST_POINT_Y = new Measurement("Last point Y", true, true, false);
    public static final Measurement LINE_LENGTH = new Measurement("Line length", true, true, true);
    public static final Measurement ORIENTATION = new Measurement("Orientation", true, true, false);
    public static final Measurement AZIMUTH = new Measurement("Azimuth", true, true, false);

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
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {
        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();

                if (FIRST_POINT_X.isComputed() && (!drawOnLabel || FIRST_POINT_X.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || FIRST_POINT_X.isQuickComputing()) {
                        val = adapter.getXCalibratedValue(ptA.getX());
                    }
                    measVal.add(new MeasureItem(FIRST_POINT_X, val, adapter.getUnit()));
                }
                if (FIRST_POINT_Y.isComputed() && (!drawOnLabel || FIRST_POINT_Y.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || FIRST_POINT_Y.isQuickComputing()) {
                        val = adapter.getXCalibratedValue(ptA.getY());
                    }
                    measVal.add(new MeasureItem(FIRST_POINT_Y, val, adapter.getUnit()));
                }
                if (LAST_POINT_X.isComputed() && (!drawOnLabel || LAST_POINT_X.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || LAST_POINT_X.isQuickComputing()) {
                        val = adapter.getXCalibratedValue(ptB.getX());
                    }
                    measVal.add(new MeasureItem(LAST_POINT_X, val, adapter.getUnit()));
                }
                if (LAST_POINT_Y.isComputed() && (!drawOnLabel || LAST_POINT_Y.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || LAST_POINT_Y.isQuickComputing()) {
                        val = adapter.getXCalibratedValue(ptB.getY());
                    }
                    measVal.add(new MeasureItem(LAST_POINT_Y, val, adapter.getUnit()));
                }
                if (LINE_LENGTH.isComputed() && (!drawOnLabel || LINE_LENGTH.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || LINE_LENGTH.isQuickComputing()) {
                        val = ptA.distance(ptB) * adapter.getCalibRatio();
                    }
                    measVal.add(new MeasureItem(LINE_LENGTH, val, adapter.getUnit()));
                }
                if (ORIENTATION.isComputed() && (!drawOnLabel || ORIENTATION.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || ORIENTATION.isQuickComputing()) {
                        val = MathUtil.getOrientation(ptA, ptB);
                    }
                    measVal.add(new MeasureItem(ORIENTATION, val, "deg"));

                }
                if (AZIMUTH.isComputed() && (!drawOnLabel || AZIMUTH.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || AZIMUTH.isQuickComputing()) {
                        val = MathUtil.getAzimuth(ptA, ptB);
                    }
                    measVal.add(new MeasureItem(AZIMUTH, val, "deg"));

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

}
