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
import java.awt.Paint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.List;

import javax.media.jai.OpImage;
import javax.media.jai.ROIShape;
import javax.media.jai.RenderedOp;

import org.weasis.core.api.image.op.ImageStatistics2Descriptor;
import org.weasis.core.api.image.op.ImageStatisticsDescriptor;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.media.data.TagW;

/**
 * @author Nicolas Roduit, Benoit Jacquemoud
 */
public abstract class AbstractDragGraphicArea extends AbstractDragGraphic implements ImageStatistics {

    public AbstractDragGraphicArea(int handlePointTotalNumber) {
        this(handlePointTotalNumber, Color.YELLOW, 1f, true);
    }

    public AbstractDragGraphicArea(int handlePointTotalNumber, Paint paintColor, float lineThickness,
        boolean labelVisible) {
        this(handlePointTotalNumber, paintColor, lineThickness, labelVisible, false);
    }

    public AbstractDragGraphicArea(int handlePointTotalNumber, Paint paintColor, float lineThickness,
        boolean labelVisible, boolean filled) {
        this(null, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled);

    }

    public AbstractDragGraphicArea(List<Point2D.Double> handlePointList, int handlePointTotalNumber, Paint paintColor,
        float lineThickness, boolean labelVisible, boolean filled) {
        super(handlePointList, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public Area getArea(AffineTransform transform) {
        if (shape == null) {
            return new Area();
        } else {
            Area area = super.getArea(transform);
            area.add(new Area(shape)); // Add inside area for closed shape
            return area;
        }
    }

    public List<MeasureItem> getImageStatistics(MeasurableLayer layer, boolean releaseEvent) {
        if (layer != null) {
            if (layer.hasContent() && isShapeValid()) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();

                if (IMAGE_MIN.isComputed() || IMAGE_MAX.isComputed() || IMAGE_MEAN.isComputed()) {

                    Double[] min = null;
                    Double[] max = null;
                    Double[] mean = null;
                    Double[] stdv = null;
                    Double[] skew = null;
                    Double[] kurtosis = null;

                    if (releaseEvent && shape != null) {
                        RenderedImage image = layer.getSourceRenderedImage();
                        if (image == null) {
                            return null;
                        }
                        // long startTime = System.currentTimeMillis();
                        AffineTransform transform = layer.getShapeTransform();
                        ROIShape roi;
                        if (transform != null) {
                            // Rescale ROI, if needed
                            roi = new ROIShape(transform.createTransformedShape(shape));
                        } else {
                            roi = new ROIShape(shape);
                        }
                        // Get padding values => exclude values
                        Double excludedMin = null;
                        Double excludedMax = null;
                        Integer paddingValue = (Integer) layer.getSourceTagValue(TagW.PixelPaddingValue);
                        Integer paddingLimit = (Integer) layer.getSourceTagValue(TagW.PixelPaddingRangeLimit);
                        if (paddingValue != null) {
                            if (paddingLimit == null) {
                                paddingLimit = paddingValue;
                            } else if (paddingLimit < paddingValue) {
                                int temp = paddingValue;
                                paddingValue = paddingLimit;
                                paddingLimit = temp;
                            }
                            excludedMin = paddingValue == null ? null : new Double(paddingValue);
                            excludedMax = paddingLimit == null ? null : new Double(paddingLimit);
                        }
                        RenderedOp dst =
                            ImageStatisticsDescriptor.create(image, roi, 1, 1, excludedMin, excludedMax, null);
                        // To ensure this image is not stored in tile cache
                        ((OpImage) dst.getRendering()).setTileCache(null);
                        // For basic statistics, rescale values can be computed afterwards
                        double[][] extrema = (double[][]) dst.getProperty("statistics"); //$NON-NLS-1$
                        if (extrema == null || extrema.length < 1 || extrema[0].length < 1) {
                            return null;
                        }
                        min = new Double[extrema[0].length];
                        max = new Double[extrema[0].length];
                        mean = new Double[extrema[0].length];

                        // LOGGER.error("Basic stats [ms]: {}", System.currentTimeMillis() - startTime);
                        // unit = pixelValue * rescale slope + rescale intercept
                        Float slopeVal = (Float) layer.getSourceTagValue(TagW.RescaleSlope);
                        Float interceptVal = (Float) layer.getSourceTagValue(TagW.RescaleIntercept);
                        double slope = slopeVal == null ? 1.0f : slopeVal.doubleValue();
                        double intercept = interceptVal == null ? 0.0f : interceptVal.doubleValue();
                        for (int i = 0; i < extrema[0].length; i++) {
                            min[i] = extrema[0][i] * slope + intercept;
                            max[i] = extrema[1][i] * slope + intercept;
                            mean[i] = extrema[2][i] * slope + intercept;
                        }

                        if (IMAGE_STD.isComputed() || IMAGE_SKEW.isComputed() || IMAGE_KURTOSIS.isComputed()) {
                            // startTime = System.currentTimeMillis();
                            // Required the mean value (not rescaled), slope and intercept to calculate correctly std,
                            // skew and kurtosis
                            dst =
                                ImageStatistics2Descriptor.create(image, roi, 1, 1, extrema[2][0], excludedMin,
                                    excludedMax, slope, intercept, null);
                            // To ensure this image is not stored in tile cache
                            ((OpImage) dst.getRendering()).setTileCache(null);
                            double[][] extrema2 = (double[][]) dst.getProperty("statistics"); //$NON-NLS-1$
                            if (extrema != null && extrema.length > 0 && extrema[0].length > 0) {
                                stdv = new Double[extrema2[0].length];
                                skew = new Double[extrema2[0].length];
                                kurtosis = new Double[extrema2[0].length];
                                // LOGGER.info("Adv. stats [ms]: {}", System.currentTimeMillis() - startTime);
                                for (int i = 0; i < extrema2[0].length; i++) {
                                    stdv[i] = extrema2[0][i];
                                    skew[i] = extrema2[1][i];
                                    kurtosis[i] = extrema2[2][i];
                                }
                            }
                        }
                    }

                    String unit = layer.getPixelValueUnit();
                    if (IMAGE_MIN.isComputed()) {
                        addMeasure(measVal, IMAGE_MIN, min, unit);
                    }
                    if (IMAGE_MAX.isComputed()) {
                        addMeasure(measVal, IMAGE_MAX, max, unit);
                    }
                    if (IMAGE_MEAN.isComputed()) {
                        addMeasure(measVal, IMAGE_MEAN, mean, unit);
                    }

                    if (IMAGE_STD.isComputed()) {
                        addMeasure(measVal, IMAGE_STD, stdv, unit);
                    }
                    if (IMAGE_SKEW.isComputed()) {
                        addMeasure(measVal, IMAGE_SKEW, skew, unit);
                    }
                    if (IMAGE_KURTOSIS.isComputed()) {
                        addMeasure(measVal, IMAGE_KURTOSIS, kurtosis, unit);
                    }

                    Double suv = (Double) layer.getSourceTagValue(TagW.SuvFactor);
                    if (suv != null) {
                        unit = "SUVbw"; //$NON-NLS-1$
                        if (IMAGE_MIN.isComputed()) {
                            measVal.add(new MeasureItem(IMAGE_MIN, min == null || min[0] == null ? null : min[0] * suv,
                                unit));
                        }
                        if (IMAGE_MAX.isComputed()) {
                            measVal.add(new MeasureItem(IMAGE_MAX, max == null || max[0] == null ? null : max[0] * suv,
                                unit));
                        }
                        if (IMAGE_MEAN.isComputed()) {
                            measVal.add(new MeasureItem(IMAGE_MEAN, mean == null || mean[0] == null ? null : mean[0]
                                * suv, unit));
                        }
                    }
                }
                return measVal;
            }
        }
        return null;
    }

    private static void addMeasure(ArrayList<MeasureItem> measVal, Measurement measure, Double[] val, String unit) {
        if (val == null) {
            measVal.add(new MeasureItem(measure, null, unit));
        } else if (val.length == 1) {
            measVal.add(new MeasureItem(measure, val[0], unit));
        } else {
            for (int i = 0; i < val.length; i++) {
                measVal.add(new MeasureItem(measure, " " + (i + 1), val[i], unit)); //$NON-NLS-1$
            }
        }
    }
}
