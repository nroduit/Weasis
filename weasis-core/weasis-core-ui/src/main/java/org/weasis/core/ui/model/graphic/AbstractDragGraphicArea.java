/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.model.graphic;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.media.jai.OpImage;
import javax.media.jai.ROIShape;
import javax.media.jai.RenderedOp;

import org.weasis.core.api.image.op.ImageStatistics2Descriptor;
import org.weasis.core.api.image.op.ImageStatisticsDescriptor;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;

public abstract class AbstractDragGraphicArea extends AbstractDragGraphic implements GraphicArea {
    private static final long serialVersionUID = -3042328664891626708L;

    public AbstractDragGraphicArea(Integer pointNumber) {
        super(pointNumber);
    }

    public AbstractDragGraphicArea(AbstractDragGraphicArea graphic) {
        super(graphic);
    }

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

    @Override
    public List<MeasureItem> getImageStatistics(MeasurableLayer layer, Boolean releaseEvent) {
        if (layer != null) {
            if (layer.hasContent() && isShapeValid()) {
                ArrayList<MeasureItem> measVal = new ArrayList<>();

                if (IMAGE_MIN.getComputed() || IMAGE_MAX.getComputed() || IMAGE_MEAN.getComputed()) {

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
                        AffineTransform transform = layer.getShapeTransform();
                        Point offset = layer.getOffset();
                        if (offset != null) {
                            if (transform == null) {
                                transform = AffineTransform.getTranslateInstance(-offset.getX(), -offset.getY());
                            } else {
                                transform.translate(-offset.getX(), -offset.getY());
                            }
                        }
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
                        Integer paddingValue = (Integer) layer.getSourceTagValue(TagW.get("PixelPaddingValue")); //$NON-NLS-1$
                        Integer paddingLimit = (Integer) layer.getSourceTagValue(TagW.get("PixelPaddingRangeLimit")); //$NON-NLS-1$
                        if (paddingValue != null) {
                            if (paddingLimit == null) {
                                paddingLimit = paddingValue;
                            } else if (paddingLimit < paddingValue) {
                                int temp = paddingValue;
                                paddingValue = paddingLimit;
                                paddingLimit = temp;
                            }
                            excludedMin = paddingValue == null ? null : (double) paddingValue;
                            excludedMax = paddingLimit == null ? null : (double) paddingLimit;
                        }
                        RenderedOp dst =
                            ImageStatisticsDescriptor.create(image, roi, 1, 1, excludedMin, excludedMax, null);
                        // To ensure this image is not stored in tile cache
                        ((OpImage) dst.getRendering()).setTileCache(null);
                        // For basic statistics, rescale values can be computed afterwards
                        double[][] extrema = (double[][]) dst.getProperty("statistics"); //$NON-NLS-1$
                        if (extrema == null || extrema.length < 1 || extrema[0].length < 1) {
                            return Collections.emptyList();
                        }
                        min = new Double[extrema[0].length];
                        max = new Double[extrema[0].length];
                        mean = new Double[extrema[0].length];

                        // LOGGER.error("Basic stats [ms]: {}", System.currentTimeMillis() - startTime);
                        // unit = pixelValue * rescale slope + rescale intercept
                        Double slopeVal = (Double) layer.getSourceTagValue(TagW.get("RescaleSlope")); //$NON-NLS-1$
                        Double interceptVal = (Double) layer.getSourceTagValue(TagW.get("RescaleIntercept")); //$NON-NLS-1$
                        double slope = slopeVal == null ? 1.0f : slopeVal.doubleValue();
                        double intercept = interceptVal == null ? 0.0f : interceptVal.doubleValue();
                        for (int i = 0; i < extrema[0].length; i++) {
                            min[i] = extrema[0][i] * slope + intercept;
                            max[i] = extrema[1][i] * slope + intercept;
                            mean[i] = extrema[2][i] * slope + intercept;
                        }

                        if (IMAGE_STD.getComputed() || IMAGE_SKEW.getComputed() || IMAGE_KURTOSIS.getComputed()) {
                            // startTime = System.currentTimeMillis();
                            // Required the mean value (not rescaled), slope and intercept to calculate correctly std,
                            // skew and kurtosis
                            dst = ImageStatistics2Descriptor.create(image, roi, 1, 1, extrema[2][0], excludedMin,
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
                    if (IMAGE_MIN.getComputed()) {
                        addMeasure(measVal, IMAGE_MIN, min, unit);
                    }
                    if (IMAGE_MAX.getComputed()) {
                        addMeasure(measVal, IMAGE_MAX, max, unit);
                    }
                    if (IMAGE_MEAN.getComputed()) {
                        addMeasure(measVal, IMAGE_MEAN, mean, unit);
                    }
                    if (IMAGE_STD.getComputed()) {
                        addMeasure(measVal, IMAGE_STD, stdv, unit);
                    }
                    if (IMAGE_SKEW.getComputed()) {
                        addMeasure(measVal, IMAGE_SKEW, skew, unit);
                    }
                    if (IMAGE_KURTOSIS.getComputed()) {
                        addMeasure(measVal, IMAGE_KURTOSIS, kurtosis, unit);
                    }

                    Double suv = (Double) layer.getSourceTagValue(TagW.SuvFactor);
                    if (Objects.nonNull(suv)) {
                        unit = "SUVbw"; //$NON-NLS-1$
                        if (IMAGE_MIN.getComputed()) {
                            measVal.add(
                                new MeasureItem(IMAGE_MIN, min == null || min[0] == null ? null : min[0] * suv, unit));
                        }
                        if (IMAGE_MAX.getComputed()) {
                            measVal.add(
                                new MeasureItem(IMAGE_MAX, max == null || max[0] == null ? null : max[0] * suv, unit));
                        }
                        if (IMAGE_MEAN.getComputed()) {
                            measVal.add(new MeasureItem(IMAGE_MEAN,
                                mean == null || mean[0] == null ? null : mean[0] * suv, unit));
                        }
                    }
                }
                return measVal;
            }
        }
        return Collections.emptyList();
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
