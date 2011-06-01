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

import javax.media.jai.PlanarImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;

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

    public final static Measurement ImageMin = new Measurement("Min", false);
    public final static Measurement ImageMax = new Measurement("Max", false);
    public final static Measurement ImageSTD = new Measurement("StDev", false);
    public final static Measurement ImageMean = new Measurement("Mean", false);

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

                if (ImageMin.isComputed() || ImageMax.isComputed() || ImageMean.isComputed() || ImageSTD.isComputed()) {
                    Double min = null;
                    Double max = null;
                    Double stdv = null;
                    Double mean = null;
                    if (releaseEvent) {
                        PlanarImage image = imageElement.getImage();
                        try {
                            ArrayList<Integer> pList = getValueFromArea(image);
                            if (pList != null && pList.size() > 0) {
                                int band = image.getSampleModel().getNumBands();
                                if (band == 1) {
                                    // Hounsfield = pixelValue * rescale slope + intercept value
                                    Float slope = (Float) imageElement.getTagValue(TagW.RescaleSlope);
                                    Float intercept = (Float) imageElement.getTagValue(TagW.RescaleIntercept);
                                    min = Double.MAX_VALUE;
                                    max = -Double.MAX_VALUE;
                                    double sum = 0.0;
                                    for (Integer val : pList) {
                                        double v = val.doubleValue();
                                        if (v < min) {
                                            min = v;
                                        }
                                        if (v > max) {
                                            max = v;
                                        }
                                        sum += v;
                                    }

                                    mean = sum / pList.size();

                                    stdv = 0.0D;
                                    for (Integer val : pList) {
                                        double v = val.doubleValue();
                                        if (v < min) {
                                            min = v;
                                        }
                                        if (v > max) {
                                            max = v;
                                        }
                                        stdv += (v - mean) * (v - mean);
                                    }

                                    stdv = Math.sqrt(stdv / (pList.size() - 1.0));

                                    if (slope != null || intercept != null) {
                                        slope = slope == null ? 1.0f : slope;
                                        intercept = intercept == null ? 0.0f : intercept;
                                        mean = mean * slope + intercept;
                                        stdv = stdv * slope + intercept;
                                        min = min * slope + intercept;
                                        max = max * slope + intercept;
                                    }

                                } else {
                                    // message.append("R=" + c[0] + " G=" + c[1] + " B=" + c[2]);
                                }
                            }
                        } catch (ArrayIndexOutOfBoundsException ex) {
                        }
                    }
                    String unit = imageElement.getPixelValueUnit() == null ? "" : imageElement.getPixelValueUnit(); //$NON-NLS-1$ 
                    if (ImageMin.isComputed() && (releaseEvent || ImageMin.isGraphicLabel())) {
                        Double val = releaseEvent || ImageMin.isQuickComputing() ? min : null;
                        measVal.add(new MeasureItem(ImageMin, val, unit));
                    }
                    if (ImageMax.isComputed() && (releaseEvent || ImageMax.isGraphicLabel())) {
                        Double val = releaseEvent || ImageMax.isQuickComputing() ? max : null;
                        measVal.add(new MeasureItem(ImageMax, val, unit));
                    }
                    if (ImageMean.isComputed() && (releaseEvent || ImageMean.isGraphicLabel())) {
                        Double val = releaseEvent || ImageMean.isQuickComputing() ? mean : null;
                        measVal.add(new MeasureItem(ImageMean, val, unit));
                    }
                    if (ImageSTD.isComputed() && (releaseEvent || ImageSTD.isGraphicLabel())) {
                        Double val = releaseEvent || ImageSTD.isQuickComputing() ? stdv : null;
                        measVal.add(new MeasureItem(ImageSTD, val, unit));
                    }
                }
                return measVal;
            }
        }
        return null;
    }

}