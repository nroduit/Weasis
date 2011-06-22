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

    public final static Measurement Area = new Measurement("Area", true, true, true);
    public final static Measurement Diameter = new Measurement("Diameter", true, true, true);
    public final static Measurement Perimeter = new Measurement("Perimeter", true, true, false);
    public final static Measurement CenterX = new Measurement("Center X", true, true, false);
    public final static Measurement CenterY = new Measurement("Center Y", true, true, false);
    public final static Measurement Radius = new Measurement("Radius", true, true, false);

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

        if (centerPt != null && radius != 0)
            newShape = new Ellipse2D.Double(centerPt.getX() - radius, centerPt.getY() - radius, 2 * radius, 2 * radius);

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public void paintHandles(Graphics2D g2d, AffineTransform transform) {
        if (resizingOrMoving) {
            resizingOrMoving = false; // Force to display handles even on resizing or moving
            super.paintHandles(g2d, transform);
            resizingOrMoving = true;
        } else
            super.paintHandles(g2d, transform);
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {

        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>(10);

                double ratio = adapter.getCalibRatio();

                if (CenterX.isComputed() && (!drawOnLabel || CenterX.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || CenterX.isQuickComputing())
                        val = adapter.getXCalibratedValue(centerPt.getX());
                    measVal.add(new MeasureItem(CenterX, val, adapter.getUnit()));
                }
                if (CenterY.isComputed() && (!drawOnLabel || CenterY.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || CenterY.isQuickComputing())
                        val = adapter.getYCalibratedValue(centerPt.getY());
                    measVal.add(new MeasureItem(CenterY, val, adapter.getUnit()));
                }
                if (Radius.isComputed() && (!drawOnLabel || Radius.isGraphicLabel())) {
                    Double val = releaseEvent || Radius.isQuickComputing() ? ratio * radius : null;
                    measVal.add(new MeasureItem(Radius, val, adapter.getUnit()));
                }
                if (Diameter.isComputed() && (!drawOnLabel || Diameter.isGraphicLabel())) {
                    Double val = releaseEvent || Diameter.isQuickComputing() ? ratio * radius * 2.0 : null;
                    measVal.add(new MeasureItem(Diameter, val, adapter.getUnit()));
                }
                if (Area.isComputed() && (!drawOnLabel || Area.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || Area.isQuickComputing())
                        val = Math.PI * radius * radius * ratio * ratio;
                    String unit = "pix".equals(adapter.getUnit()) ? adapter.getUnit() : adapter.getUnit() + "2";
                    measVal.add(new MeasureItem(Area, val, unit));
                }

                List<MeasureItem> stats = getImageStatistics(imageElement, releaseEvent);
                if (stats != null)
                    measVal.addAll(stats);

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
        Point2D C1 = handlePointList.size() >= 1 ? handlePointList.get(0) : null;

        centerPt = GeomUtil.getCircleCenter(handlePointList);
        radius = (centerPt != null && C1 != null) ? centerPt.distance(C1) : 0;
    }

}