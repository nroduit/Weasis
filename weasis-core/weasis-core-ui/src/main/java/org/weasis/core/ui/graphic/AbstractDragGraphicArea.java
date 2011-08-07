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
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.media.jai.OpImage;
import javax.media.jai.PlanarImage;
import javax.media.jai.ROIShape;
import javax.media.jai.RenderedOp;

import org.weasis.core.api.image.op.ImageStatistics2Descriptor;
import org.weasis.core.api.image.op.ImageStatisticsDescriptor;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;

/**
 * @author Nicolas Roduit, Benoit Jacquemoud
 */
public abstract class AbstractDragGraphicArea extends AbstractDragGraphic implements ImageStatistics {

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
        if (shape == null) {
            return new Area();
        } else {
            Area area = super.getArea(transform);
            area.add(new Area(shape)); // Add inside area for closed shape
            return area;
        }
    }

    public List<MeasureItem> getImageStatistics(ImageElement imageElement, boolean releaseEvent) {
        if (imageElement != null && isShapeValid()) {
            ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>(4);

            if (IMAGE_MIN.isComputed() || IMAGE_MAX.isComputed() || IMAGE_MEAN.isComputed()) {

                Double min = null;
                Double max = null;
                Double mean = null;
                Double stdv = null;
                Double skew = null;
                Double kurtosis = null;

                if (releaseEvent) {
                    PlanarImage image = imageElement.getImage();
                    // long startTime = System.currentTimeMillis();
                    ROIShape roi = new ROIShape(shape);
                    // Get padding values => exclude values
                    Double excludedMin = null;
                    Double excludedMax = null;
                    Integer paddingValue = (Integer) imageElement.getTagValue(TagW.PixelPaddingValue);
                    Integer paddingLimit = (Integer) imageElement.getTagValue(TagW.PixelPaddingRangeLimit);
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
                    RenderedOp dst = ImageStatisticsDescriptor.create(image, roi, 1, 1, excludedMin, excludedMax, null);
                    // To ensure this image is not stored in tile cache
                    ((OpImage) dst.getRendering()).setTileCache(null);
                    // For basic statistics, rescale values can be computed afterwards
                    double[][] extrema = (double[][]) dst.getProperty("statistics"); //$NON-NLS-1$
                    // LOGGER.error("Basic stats [ms]: {}", System.currentTimeMillis() - startTime);
                    // unit = pixelValue * rescale slope + rescale intercept
                    Float slopeVal = (Float) imageElement.getTagValue(TagW.RescaleSlope);
                    Float interceptVal = (Float) imageElement.getTagValue(TagW.RescaleIntercept);
                    double slope = slopeVal == null ? 1.0f : slopeVal.doubleValue();
                    double intercept = interceptVal == null ? 0.0f : interceptVal.doubleValue();
                    min = extrema[0][0] * slope + intercept;
                    max = extrema[1][0] * slope + intercept;
                    mean = extrema[2][0] * slope + intercept;

                    if (IMAGE_STD.isComputed() || IMAGE_SKEW.isComputed() || IMAGE_KURTOSIS.isComputed()) {
                        // startTime = System.currentTimeMillis();
                        // Required the mean value (not rescaled), slope and intercept to calculate correctly std, skew
                        // and kurtosis
                        dst =
                            ImageStatistics2Descriptor.create(image, roi, 1, 1, extrema[2][0], excludedMin,
                                excludedMax, slope, intercept, null);
                        // To ensure this image is not stored in tile cache
                        ((OpImage) dst.getRendering()).setTileCache(null);
                        double[][] extrema2 = (double[][]) dst.getProperty("statistics"); //$NON-NLS-1$
                        // LOGGER.info("Adv. stats [ms]: {}", System.currentTimeMillis() - startTime);
                        stdv = extrema2[0][0];
                        skew = extrema2[1][0];
                        kurtosis = extrema2[2][0];
                    }
                }

                String unit = imageElement.getPixelValueUnit();
                if (IMAGE_MIN.isComputed()) {
                    measVal.add(new MeasureItem(IMAGE_MIN, min, unit));
                }
                if (IMAGE_MAX.isComputed()) {
                    measVal.add(new MeasureItem(IMAGE_MAX, max, unit));
                }
                if (IMAGE_MEAN.isComputed()) {
                    measVal.add(new MeasureItem(IMAGE_MEAN, mean, unit));
                }

                if (IMAGE_STD.isComputed()) {
                    measVal.add(new MeasureItem(IMAGE_STD, stdv, unit));
                }
                if (IMAGE_SKEW.isComputed()) {
                    measVal.add(new MeasureItem(IMAGE_SKEW, skew, unit));
                }
                if (IMAGE_KURTOSIS.isComputed()) {
                    measVal.add(new MeasureItem(IMAGE_KURTOSIS, kurtosis, unit));
                }

                Double suv = (Double) imageElement.getTagValue(TagW.SuvFactor);
                if (suv != null) {
                    unit = "SUVbw"; //$NON-NLS-1$
                    if (IMAGE_MIN.isComputed()) {
                        measVal.add(new MeasureItem(IMAGE_MIN, min == null ? null : min * suv, unit));
                    }
                    if (IMAGE_MAX.isComputed()) {
                        measVal.add(new MeasureItem(IMAGE_MAX, max == null ? null : max * suv, unit));
                    }
                    if (IMAGE_MEAN.isComputed()) {
                        measVal.add(new MeasureItem(IMAGE_MEAN, mean == null ? null : mean * suv, unit));
                    }
                }
            }
            return measVal;
        }

        return null;
    }

}
