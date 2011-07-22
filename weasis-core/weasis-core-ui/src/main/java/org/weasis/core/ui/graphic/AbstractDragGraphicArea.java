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
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.media.jai.iterator.RectIter;
import javax.media.jai.iterator.RectIterFactory;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;

/**
 * @author Nicolas Roduit,Benoit Jacquemoud
 */
public abstract class AbstractDragGraphicArea extends AbstractDragGraphic {

    public final static Measurement ImageMean = new Measurement("Mean", false, true, true);
    public final static Measurement ImageMin = new Measurement("Min", false, true, false);
    public final static Measurement ImageMax = new Measurement("Max", false, true, false);
    public final static Measurement ImageSTD = new Measurement("StDev", false, true, false);

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public AbstractDragGraphicArea(int handlePointTotalNumber) {
        this(handlePointTotalNumber, Color.YELLOW, 1f, true);
    }

    public AbstractDragGraphicArea(int handlePointTotalNumber, Color paintColor, float lineThickness,
        boolean labelVisible) {
        this(handlePointTotalNumber, paintColor, lineThickness, labelVisible, false);
    }

    public AbstractDragGraphicArea(int handlePointTotalNumber, Color paintColor, float lineThickness,
        boolean labelVisible, boolean filled) {
        this(null, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled);

    }

    public AbstractDragGraphicArea(List<Point2D> handlePointList, int handlePointTotalNumber, Color paintColor,
        float lineThickness, boolean labelVisible, boolean filled) {
        super(handlePointList, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public Area getArea(AffineTransform transform) {
        if (shape == null)
            return new Area();
        else {
            Area area = super.getArea(transform);
            area.add(new Area(shape)); // Add inside area for closed shape
            return area;
        }
    }

    public List<MeasureItem> getImageStatistics(ImageElement imageElement, boolean releaseEvent) {
        if (imageElement != null && shape != null) {
            ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>(5);

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
                                if (slope != null || intercept != null) {
                                    slope = slope == null ? 1.0f : slope;
                                    intercept = intercept == null ? 0.0f : intercept;
                                }

                                min = Double.MAX_VALUE;
                                max = -Double.MAX_VALUE;
                                double sum = 0.0;
                                for (Integer val : pList) {
                                    double v = val.doubleValue();
                                    if (slope != null) {
                                        v = v * slope + intercept;
                                    }
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
                                    if (slope != null) {
                                        v = v * slope + intercept;
                                    }
                                    if (v < min) {
                                        min = v;
                                    }

                                    if (v > max) {
                                        max = v;
                                    }

                                    stdv += (v - mean) * (v - mean);
                                }

                                stdv = Math.sqrt(stdv / (pList.size() - 1.0));

                            } else {
                                // message.append("R=" + c[0] + " G=" + c[1] + " B=" + c[2]);
                            }
                        }
                    } catch (ArrayIndexOutOfBoundsException ex) {
                    }
                }
                String unit = imageElement.getPixelValueUnit() == null ? "" : imageElement.getPixelValueUnit(); //$NON-NLS-1$ 
                // if (ImageMin.isComputed() && (releaseEvent || ImageMin.isGraphicLabel())) {
                if (ImageMin.isComputed()) {
                    Double val = releaseEvent || ImageMin.isQuickComputing() ? min : null;
                    measVal.add(new MeasureItem(ImageMin, val, unit));
                }
                // if (ImageMax.isComputed() && (releaseEvent || ImageMax.isGraphicLabel())) {
                if (ImageMax.isComputed()) {
                    Double val = releaseEvent || ImageMax.isQuickComputing() ? max : null;
                    measVal.add(new MeasureItem(ImageMax, val, unit));
                }
                // if (ImageSTD.isComputed() && (releaseEvent || ImageSTD.isGraphicLabel())) {
                if (ImageSTD.isComputed()) {
                    Double val = releaseEvent || ImageSTD.isQuickComputing() ? stdv : null;
                    measVal.add(new MeasureItem(ImageSTD, val, unit));
                }
                // if (ImageMean.isComputed() && (releaseEvent || ImageMean.isGraphicLabel())) {
                if (ImageMean.isComputed()) {
                    Double val = releaseEvent || ImageMean.isQuickComputing() ? mean : null;
                    measVal.add(new MeasureItem(ImageMean, val, unit));
                }

                Double suv = (Double) imageElement.getTagValue(TagW.SuvFactor);
                if (suv != null) {
                    unit = "SUV (bw)";
                    if (ImageMin.isComputed()) {
                        Double val =
                            releaseEvent || ImageMin.isQuickComputing() ? min == null ? null : min * suv : null;
                        measVal.add(new MeasureItem(ImageMin, val, unit));
                    }
                    if (ImageMax.isComputed()) {
                        Double val =
                            releaseEvent || ImageMax.isQuickComputing() ? max == null ? null : max * suv : null;
                        measVal.add(new MeasureItem(ImageMax, val, unit));
                    }
                    if (ImageMean.isComputed()) {
                        Double val =
                            releaseEvent || ImageMean.isQuickComputing() ? mean == null ? null : mean * suv : null;
                        measVal.add(new MeasureItem(ImageMean, val, unit));
                    }
                }
            }
            return measVal;
        }

        return null;
    }

    protected ArrayList<Integer> getValueFromArea(PlanarImage imageData) {
        if (imageData == null || shape == null)
            return null;

        Area shapeArea = new Area(shape);
        Rectangle bound = shapeArea.getBounds();

        bound = imageData.getBounds().intersection(bound);

        if (bound.width == 0 || bound.height == 0)
            return null;

        RectIter it;
        try {
            it = RectIterFactory.create(imageData, bound);
        } catch (Exception ex) {
            it = null;
        }

        ArrayList<Integer> list = null;

        if (it != null) {
            int band = imageData.getSampleModel().getNumBands();
            list = new ArrayList<Integer>();

            int[] c = { 0, 0, 0 };
            it.startBands();
            it.startLines();
            int y = bound.y;

            while (!it.finishedLines()) {
                it.startPixels();
                int x = bound.x;

                while (!it.finishedPixels()) {
                    if (shapeArea.contains(x, y)) {
                        it.getPixel(c);
                        for (int i = 0; i < band; i++) {
                            list.add(c[i]);
                        }
                    }
                    it.nextPixel();
                    x++;
                }
                it.nextLine();
                y++;
            }
        }
        return list;
    }

}
