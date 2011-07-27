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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Benoit Jacquemoud
 */
public class ThreePointsCircleGraphic extends AbstractDragGraphicArea {

    public static final Icon ICON = new ImageIcon(
        ThreePointsCircleGraphic.class.getResource("/icon/22x22/draw-circle.png")); //$NON-NLS-1$

    public static final Measurement AREA = new Measurement("Area", true, true, true);
    public static final Measurement DIAMETER = new Measurement("Diameter", true, true, false);
    public static final Measurement PERIMETER = new Measurement("Perimeter", true, true, false);
    public static final Measurement CENTER_X = new Measurement("Center X", true, true, false);
    public static final Measurement CENTER_Y = new Measurement("Center Y", true, true, false);
    public static final Measurement RADIUS = new Measurement("Radius", true, true, false);

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    protected Point2D centerPt; // Let O be the center of the three point interpolated circle
    protected double radius; // circle radius

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public ThreePointsCircleGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(3, paintColor, lineThickness, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return "Three Points Circle";
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {
        updateTool();
        Shape newShape = null;

        if (centerPt != null && radius != 0) {
            newShape = new Ellipse2D.Double(centerPt.getX() - radius, centerPt.getY() - radius, 2 * radius, 2 * radius);
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    /**
     * Force to display handles even during resizing or moving sequences
     */
    @Override
    protected boolean isResizingOrMoving() {
        return false;
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {

        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>(10);

                double ratio = adapter.getCalibRatio();

                if (CENTER_X.isComputed() && (!drawOnLabel || CENTER_X.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || CENTER_X.isQuickComputing()) {
                        val = adapter.getXCalibratedValue(centerPt.getX());
                    }
                    measVal.add(new MeasureItem(CENTER_X, val, adapter.getUnit()));
                }
                if (CENTER_Y.isComputed() && (!drawOnLabel || CENTER_Y.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || CENTER_Y.isQuickComputing()) {
                        val = adapter.getYCalibratedValue(centerPt.getY());
                    }
                    measVal.add(new MeasureItem(CENTER_Y, val, adapter.getUnit()));
                }
                if (RADIUS.isComputed() && (!drawOnLabel || RADIUS.isGraphicLabel())) {
                    Double val = releaseEvent || RADIUS.isQuickComputing() ? ratio * radius : null;
                    measVal.add(new MeasureItem(RADIUS, val, adapter.getUnit()));
                }
                if (DIAMETER.isComputed() && (!drawOnLabel || DIAMETER.isGraphicLabel())) {
                    Double val = releaseEvent || DIAMETER.isQuickComputing() ? ratio * radius * 2.0 : null;
                    measVal.add(new MeasureItem(DIAMETER, val, adapter.getUnit()));
                }
                if (AREA.isComputed() && (!drawOnLabel || AREA.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || AREA.isQuickComputing()) {
                        val = Math.PI * radius * radius * ratio * ratio;
                    }
                    String unit = "pix".equals(adapter.getUnit()) ? adapter.getUnit() : adapter.getUnit() + "2";
                    measVal.add(new MeasureItem(AREA, val, unit));
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
    public boolean isShapeValid() {
        updateTool();
        return (super.isShapeValid() && centerPt != null && radius < 50000);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateTool() {
        Point2D ptA = getHandlePoint(0);

        centerPt = GeomUtil.getCircleCenter(handlePointList);
        radius = (centerPt != null && ptA != null) ? centerPt.distance(ptA) : 0;
    }

}