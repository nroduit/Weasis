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
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;

/**
 * @author Benoit Jacquemoud
 */
public class ThreePointsCircleGraphic extends AbstractDragGraphicArea {

    public static final Icon ICON = new ImageIcon(
        ThreePointsCircleGraphic.class.getResource("/icon/22x22/draw-circle.png")); //$NON-NLS-1$

    public final static Measurement CenterX = new Measurement("Center X", true);
    public final static Measurement CenterY = new Measurement("Center Y", true);
    public final static Measurement Radius = new Measurement("Radius", true);
    public final static Measurement Diameter = new Measurement("Diameter", true);
    public final static Measurement Area = new Measurement("Area", true);
    public final static Measurement Perimeter = new Measurement("Perimeter", true);
    public final static Measurement ColorRGB = new Measurement("Color (RGB)", true);

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
    protected void updateShapeOnDrawing(MouseEvent mouseEvent) {
        Shape newShape = null;

        if (handlePointList.size() > 1) {
            Point2D centerPt = GeomUtil.getCircleCenter(handlePointList);
            if (centerPt != null) {
                double radius = centerPt.distance(handlePointList.get(0));
                if (radius < 5000) {
                    Rectangle2D rectangle = new Rectangle2D.Double();
                    rectangle.setFrameFromCenter(centerPt.getX(), centerPt.getY(), centerPt.getX() - radius,
                        centerPt.getY() - radius);

                    // GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, handlePointList.size());
                    // generalpath.append(new Ellipse2D.Double(rectangle.getX(), rectangle.getY(), rectangle.getWidth(),
                    // rectangle.getHeight()), false);

                    newShape =
                        new Ellipse2D.Double(rectangle.getX(), rectangle.getY(), rectangle.getWidth(),
                            rectangle.getHeight());
                }
            }
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent) {
        if (imageElement != null && handlePointList.size() > 1) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();
            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();

                Point2D centerPt = GeomUtil.getCircleCenter(handlePointList);
                double radius = centerPt.distance(handlePointList.get(0));
                double ratio = adapter.getCalibRatio();

                if (CenterX.isComputed() && (releaseEvent || CenterX.isGraphicLabel())) {
                    Double val =
                        releaseEvent || CenterX.isQuickComputing() ? adapter.getXCalibratedValue(centerPt.getX())
                            : null;
                    measVal.add(new MeasureItem(CenterX, val, adapter.getUnit()));
                }
                if (CenterY.isComputed() && (releaseEvent || CenterY.isGraphicLabel())) {
                    Double val =
                        releaseEvent || CenterY.isQuickComputing() ? adapter.getYCalibratedValue(centerPt.getY())
                            : null;
                    measVal.add(new MeasureItem(CenterY, val, adapter.getUnit()));
                }

                if (Radius.isComputed() && (releaseEvent || Radius.isGraphicLabel())) {
                    Double val = releaseEvent || Radius.isQuickComputing() ? ratio * radius : null;
                    measVal.add(new MeasureItem(Radius, val, adapter.getUnit()));
                }
                if (Diameter.isComputed() && (releaseEvent || Diameter.isGraphicLabel())) {
                    Double val = releaseEvent || Diameter.isQuickComputing() ? ratio * radius * 2.0 : null;
                    measVal.add(new MeasureItem(Diameter, val, adapter.getUnit()));
                }

                if (Area.isComputed() && (releaseEvent || Area.isGraphicLabel())) {
                    Double val =
                        releaseEvent || Area.isQuickComputing() ? Math.PI * radius * radius * ratio * ratio : null;
                    String unit = "pix".equals(adapter.getUnit()) ? adapter.getUnit() : adapter.getUnit() + "2";
                    measVal.add(new MeasureItem(Area, val, unit));
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

}