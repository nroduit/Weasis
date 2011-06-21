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

    public final static Measurement FirstPointX = new Measurement("First point X", true, true, false);
    public final static Measurement FirstPointY = new Measurement("First point Y", true, true, false);
    public final static Measurement LastPointX = new Measurement("Last point X", true, true, false);
    public final static Measurement LastPointY = new Measurement("Last point Y", true, true, false);
    public final static Measurement LineLength = new Measurement("Line length", true, true, true);
    public final static Measurement Orientation = new Measurement("Orientation", true, true, false);
    public final static Measurement Azimuth = new Measurement("Azimuth", true, true, false);
    public final static Measurement ColorRGB = new Measurement("Color (RGB)", true, true, false);

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    protected Point2D A, B; // Let AB be a simple a line segment
    protected boolean ABvalid; // estimate if line segment is valid or not

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

        if (ABvalid)
            newShape = new Line2D.Double(A, B);

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {
        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();

                if (FirstPointX.isComputed() && (!drawOnLabel || FirstPointX.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || FirstPointX.isQuickComputing())
                        val = adapter.getXCalibratedValue(A.getX());
                    measVal.add(new MeasureItem(FirstPointX, val, adapter.getUnit()));
                }
                if (FirstPointY.isComputed() && (!drawOnLabel || FirstPointY.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || FirstPointY.isQuickComputing())
                        val = adapter.getXCalibratedValue(A.getY());
                    measVal.add(new MeasureItem(FirstPointY, val, adapter.getUnit()));
                }
                if (LastPointX.isComputed() && (!drawOnLabel || LastPointX.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || LastPointX.isQuickComputing())
                        adapter.getXCalibratedValue(B.getX());
                    measVal.add(new MeasureItem(LastPointX, val, adapter.getUnit()));
                }
                if (LastPointY.isComputed() && (!drawOnLabel || LastPointY.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || LastPointY.isQuickComputing())
                        val = adapter.getXCalibratedValue(B.getY());
                    measVal.add(new MeasureItem(LastPointY, val, adapter.getUnit()));
                }
                if (LineLength.isComputed() && (!drawOnLabel || LineLength.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || LineLength.isQuickComputing())
                        val = A.distance(B) * adapter.getCalibRatio();
                    measVal.add(new MeasureItem(LineLength, val, adapter.getUnit()));
                }
                if (Orientation.isComputed() && (!drawOnLabel || Orientation.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || Orientation.isQuickComputing())
                        val = MathUtil.getOrientation(A, B);
                    measVal.add(new MeasureItem(Orientation, val, "deg"));

                }
                if (Azimuth.isComputed() && (!drawOnLabel || Azimuth.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || Azimuth.isQuickComputing())
                        val = MathUtil.getAzimuth(A, B);
                    measVal.add(new MeasureItem(Azimuth, val, "deg"));

                }
                return measVal;
            }
        }
        return null;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateTool() {
        A = handlePointList.size() >= 1 ? handlePointList.get(0) : null;
        B = handlePointList.size() >= 2 ? handlePointList.get(1) : null;

        ABvalid = A != null && B != null && !B.equals(A);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public Point2D getStartPoint() {
        updateTool();
        return A;
    }

    public Point2D getEndPoint() {
        updateTool();
        return B;
    }

}
