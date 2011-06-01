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
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.Messages;

/**
 * @author Nicolas Roduit
 */
public class EllipseGraphic extends RectangleGraphic {

    public static final Icon ICON = new ImageIcon(EllipseGraphic.class.getResource("/icon/22x22/draw-eclipse.png")); //$NON-NLS-1$

    public final static Measurement CenterX = new Measurement("Center X", true);
    public final static Measurement CenterY = new Measurement("Center Y", true);
    public final static Measurement Width = new Measurement("Width", true);
    public final static Measurement Height = new Measurement("Height", true);
    public final static Measurement Area = new Measurement("Area", true);
    public final static Measurement Perimeter = new Measurement("Perimeter", true);
    public final static Measurement ColorRGB = new Measurement("Color (RGB)", true);

    public final static Measurement ImageMin = new Measurement("Min", false);
    public final static Measurement ImageMax = new Measurement("Max", false);
    public final static Measurement ImageSTD = new Measurement("StDev", false);
    public final static Measurement ImageMean = new Measurement("Mean", false);

    public EllipseGraphic(float lineThickness, Color paint, boolean labelVisible) {
        super(lineThickness, paint, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.ellipse"); //$NON-NLS-1$
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseevent) {
        Rectangle2D rectangle = new Rectangle2D.Double();
        rectangle.setFrameFromDiagonal(handlePointList.get(eHandlePoint.NW.index),
            handlePointList.get(eHandlePoint.SE.index));
        setShape(new Ellipse2D.Double(rectangle.getX(), rectangle.getY(), rectangle.getWidth(), rectangle.getHeight()),
            mouseevent);
        updateLabel(mouseevent, getDefaultView2d(mouseevent));
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent) {
        if (imageElement != null && handlePointList.size() > 1) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();
            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();
                Rectangle2D rect = new Rectangle2D.Double();
                rect.setFrameFromDiagonal(handlePointList.get(eHandlePoint.NW.index),
                    handlePointList.get(eHandlePoint.SE.index));
                double ratio = adapter.getCalibRatio();
                if (CenterX.isComputed() && (releaseEvent || CenterX.isGraphicLabel())) {
                    Double val =
                        releaseEvent || CenterX.isQuickComputing() ? adapter.getXCalibratedValue(rect.getCenterX())
                            : null;
                    measVal.add(new MeasureItem(CenterX, val, adapter.getUnit()));
                }
                if (CenterY.isComputed() && (releaseEvent || CenterY.isGraphicLabel())) {
                    Double val =
                        releaseEvent || CenterY.isQuickComputing() ? adapter.getYCalibratedValue(rect.getCenterY())
                            : null;
                    measVal.add(new MeasureItem(CenterY, val, adapter.getUnit()));
                }

                if (Width.isComputed() && (releaseEvent || Width.isGraphicLabel())) {
                    Double val = releaseEvent || Width.isQuickComputing() ? ratio * rect.getWidth() : null;
                    measVal.add(new MeasureItem(Width, val, adapter.getUnit()));
                }
                if (Height.isComputed() && (releaseEvent || Height.isGraphicLabel())) {
                    Double val = releaseEvent || Height.isQuickComputing() ? ratio * rect.getHeight() : null;
                    measVal.add(new MeasureItem(Height, val, adapter.getUnit()));
                }

                if (Area.isComputed() && (releaseEvent || Area.isGraphicLabel())) {
                    Double val =
                        releaseEvent || Area.isQuickComputing() ? Math.PI * rect.getWidth() * ratio * rect.getHeight()
                            * ratio / 4.0 : null;
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
