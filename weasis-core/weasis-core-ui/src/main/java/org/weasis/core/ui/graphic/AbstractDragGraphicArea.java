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

import javax.media.jai.OpImage;
import javax.media.jai.PlanarImage;
import javax.media.jai.ROIShape;
import javax.media.jai.RenderedOp;
import javax.media.jai.iterator.RectIter;
import javax.media.jai.iterator.RectIterFactory;

import org.weasis.core.api.image.op.ImageStatistics2Descriptor;
import org.weasis.core.api.image.op.ImageStatisticsDescriptor;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;

/**
 * @author Nicolas Roduit, Benoit Jacquemoud
 */
public abstract class AbstractDragGraphicArea extends AbstractDragGraphic {

    public static final Measurement IMAGE_MEAN = new Measurement("Mean", false, true, true);
    public static final Measurement IMAGE_MIN = new Measurement("Min", false, true, false);
    public static final Measurement IMAGE_MAX = new Measurement("Max", false, true, false);
    public static final Measurement IMAGE_STD = new Measurement("StDev", false, false, false);
    public static final Measurement IMAGE_SKEW = new Measurement("Skewness", false, false, false);
    public static final Measurement IMAGE_KURTOSIS = new Measurement("Kurtosis", false, false, false);

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

                    // try {
                    // ArrayList<Integer> pList = getValueFromArea(image);
                    // if (pList != null && pList.size() > 0) {
                    // int band = image.getSampleModel().getNumBands();
                    // if (band == 1) {
                    // // unit = pixelValue * rescale slope + rescale intercept
                    // Float slope = (Float) imageElement.getTagValue(TagW.RescaleSlope);
                    // Float intercept = (Float) imageElement.getTagValue(TagW.RescaleIntercept);
                    // if (slope != null || intercept != null) {
                    // slope = slope == null ? 1.0f : slope;
                    // intercept = intercept == null ? 0.0f : intercept;
                    // }
                    //
                    // min = Double.MAX_VALUE;
                    // max = -Double.MAX_VALUE;
                    // double sum = 0.0;
                    // for (Integer val : pList) {
                    // double v = val.doubleValue();
                    // if (slope != null) {
                    // v = v * slope + intercept;
                    // }
                    // if (v < min) {
                    // min = v;
                    // }
                    //
                    // if (v > max) {
                    // max = v;
                    // }
                    //
                    // sum += v;
                    // }
                    //
                    // mean = sum / pList.size();
                    //
                    // stdv = 0.0D;
                    // for (Integer val : pList) {
                    // double v = val.doubleValue();
                    // if (slope != null) {
                    // v = v * slope + intercept;
                    // }
                    // if (v < min) {
                    // min = v;
                    // }
                    //
                    // if (v > max) {
                    // max = v;
                    // }
                    //
                    // stdv += (v - mean) * (v - mean);
                    // }
                    //
                    // stdv = Math.sqrt(stdv / (pList.size() - 1.0));
                    //
                    // } else {
                    // // message.append("R=" + c[0] + " G=" + c[1] + " B=" + c[2]);
                    // }
                    // }
                    // } catch (ArrayIndexOutOfBoundsException ex) {
                    // }
                    // logger.info("1 method [ms]: {}", System.currentTimeMillis() - startTime);
                    // startTime = System.currentTimeMillis();

                    // Second method
                    ROIShape roi = new ROIShape(shape);
                    RenderedOp dst = ImageStatisticsDescriptor.create(image, roi, 1, 1, null, null, null);
                    // To ensure this image is not stored in tile cache
                    ((OpImage) dst.getRendering()).setTileCache(null);
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
                        dst =
                            ImageStatistics2Descriptor.create(image, roi, 1, 1, extrema[2][0], null, null, slope,
                                intercept, null);
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

    protected ArrayList<Integer> getValueFromArea(PlanarImage imageData) {
        if (imageData == null || shape == null) {
            return null;
        }

        Area shapeArea = new Area(shape);
        Rectangle bound = shapeArea.getBounds();

        bound = imageData.getBounds().intersection(bound);

        if (bound.width == 0 || bound.height == 0) {
            return null;
        }

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
