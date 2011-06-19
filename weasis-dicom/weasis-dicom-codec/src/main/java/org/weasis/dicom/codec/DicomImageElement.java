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
package org.weasis.dicom.codec;

import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.JAI;
import javax.media.jai.OpImage;
import javax.media.jai.ROI;
import javax.media.jai.RenderedOp;

import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

public class DicomImageElement extends ImageElement {

    public DicomImageElement(MediaReader mediaIO, Object key) {
        super(mediaIO, key);
        String modality = (String) mediaIO.getTagValue(TagW.Modality);
        if (!"SC".equals(modality) && !"OT".equals(modality)) { //$NON-NLS-1$ //$NON-NLS-2$
            // Physical distance in mm between the center of each pixel (ratio in mm)
            double[] val = (double[]) mediaIO.getTagValue(TagW.PixelSpacing);
            if (val == null) {
                val = (double[]) mediaIO.getTagValue(TagW.ImagerPixelSpacing);
                // Follows D. Clunie recommendations
                pixelSizeCalibrationDescription = val == null ? null : Messages.getString("DicomImageElement.detector"); //$NON-NLS-1$

            } else {
                pixelSizeCalibrationDescription = (String) mediaIO.getTagValue(TagW.PixelSpacingCalibrationDescription);
            }
            if (val != null && val[0] > 0.0 && val[1] > 0.0) {
                // Pixel Spacing = Row Spacing \ Column Spacing
                // The first value is the row spacing in mm, that is the spacing between the centers of adjacent rows,
                // or vertical spacing.
                // Pixel Spacing must be always positive, but some DICOMs have negative values
                pixelSizeX = val[1];
                pixelSizeY = val[0];
                pixelSpacingUnit = Unit.MILLIMETER;
            }
            pixelValueUnit = (String) getTagValue(TagW.RescaleType);
            if (pixelValueUnit == null) {
                pixelValueUnit = (String) getTagValue(TagW.Units);
            }
            if (pixelValueUnit == null && "CT".equals(modality)) { //$NON-NLS-1$
                pixelValueUnit = "HU"; //$NON-NLS-1$
            }

        }
    }

    @Override
    public float getPixelWindow(float window) {
        Float slope = (Float) getTagValue(TagW.RescaleSlope);
        if (slope != null)
            return window /= slope;
        return window;
    }

    @Override
    public float getPixelLevel(float level) {
        return rescale2pixel(level);
    }

    @Override
    protected boolean isGrayImage(RenderedImage source) {
        Boolean val = (Boolean) getTagValue(TagW.MonoChrome);
        return val == null ? true : val;
    }

    @Override
    public void findMinMaxValues(RenderedImage img) {
        // This function can be called several times from the inner class Load.
        // Not necessary to find again min and max
        if (minValue == 0.0f && maxValue == 0.0f) {
            Integer min = (Integer) getTagValue(TagW.SmallestImagePixelValue);
            Integer max = (Integer) getTagValue(TagW.LargestImagePixelValue);
            minValue = min == null ? 0.0f : min.floatValue();
            maxValue = max == null ? 0.0f : max.floatValue();
            /*
             * If a Pixel Padding Value (0028,0120) only is present in the image then image contrast manipulations shall
             * be not be applied to those pixels with the value specified in Pixel Padding Value (0028,0120). If both
             * Pixel Padding Value (0028,0120) and Pixel Padding Range Limit (0028,0121) are present in the image then
             * image contrast manipulations shall not be applied to those pixels with values in the range between the
             * values of Pixel Padding Value (0028,0120) and Pixel Padding Range Limit (0028,0121), inclusive."
             */
            Integer paddingValue = (Integer) getTagValue(TagW.PixelPaddingValue);
            Integer paddingLimit = (Integer) getTagValue(TagW.PixelPaddingRangeLimit);
            if (paddingValue != null) {
                if (paddingLimit == null) {
                    paddingLimit = paddingValue;
                } else if (paddingLimit < paddingValue) {
                    int temp = paddingValue;
                    paddingValue = paddingLimit;
                    paddingLimit = temp;
                }
                findMinMaxValues(img, paddingValue, paddingLimit);
            } else if (minValue == 0.0f && maxValue == 0.0f) {
                super.findMinMaxValues(img);
            }

            minValue = pixel2rescale(minValue);
            maxValue = pixel2rescale(maxValue);
        }
    }

    public void findMinMaxValues(RenderedImage img, double paddingValueMin, double paddingValueMax) {
        if (img != null) {
            int datatype = img.getSampleModel().getDataType();
            if (datatype == DataBuffer.TYPE_BYTE) {
                this.minValue = 0;
                this.maxValue = 255;
            } else {
                ParameterBlock pb = new ParameterBlock();
                pb.addSource(img);
                pb.add((ROI) null);
                pb.add(1);
                pb.add(1);
                pb.add(paddingValueMin);
                pb.add(paddingValueMax);
                RenderedOp dst = JAI.create("ExtremaRangeLimit", pb, null); //$NON-NLS-1$
                // To ensure this image is not stored in tile cache
                ((OpImage) dst.getRendering()).setTileCache(null);
                double[][] extrema = (double[][]) dst.getProperty("extrema"); //$NON-NLS-1$
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                int numBands = dst.getSampleModel().getNumBands();
                for (int i = 0; i < numBands; i++) {
                    min = Math.min(min, extrema[0][i]);
                    max = Math.max(max, extrema[1][i]);
                }
                this.minValue = (int) min;
                this.maxValue = (int) max;
            }
        }
    }

    @Override
    public float getDefaultWindow() {
        Float val = (Float) getTagValue(TagW.WindowWidth);
        if (val == null)
            return super.getDefaultWindow();
        return val;
    }

    @Override
    public float getDefaultLevel() {
        Float val = (Float) getTagValue(TagW.WindowCenter);
        if (val == null)
            return super.getDefaultLevel();
        return val;
    }

    public float pixel2rescale(float pixelValue) {
        // Hounsfield units: hu
        // hu = pixelValue * rescale slope + intercept value
        Float slope = (Float) getTagValue(TagW.RescaleSlope);
        Float intercept = (Float) getTagValue(TagW.RescaleIntercept);
        if (slope != null || intercept != null)
            return (pixelValue * (slope == null ? 1.0f : slope) + (intercept == null ? 0.0f : intercept));
        return pixelValue;

    }

    public float rescale2pixel(float hounsfieldValue) {
        // Hounsfield units: hu
        // pixelValue = (hu - intercept value) / rescale slope
        Float slope = (Float) getTagValue(TagW.RescaleSlope);
        Float intercept = (Float) getTagValue(TagW.RescaleIntercept);
        if (slope != null || intercept != null)
            return (hounsfieldValue - (intercept == null ? 0.0f : intercept)) / (slope == null ? 1.0f : slope);
        return hounsfieldValue;

    }

    public GeometryOfSlice getSliceGeometry() {
        double[] imgOr = (double[]) getTagValue(TagW.ImageOrientationPatient);
        if (imgOr != null && imgOr.length == 6) {
            double[] pos = (double[]) getTagValue(TagW.ImagePositionPatient);
            if (pos != null && pos.length == 3) {
                double[] spacing = { getPixelSize(), getPixelSize(), 0.0 };
                Float sliceTickness = (Float) getTagValue(TagW.SliceThickness);
                Integer rows = (Integer) getTagValue(TagW.Rows);
                Integer columns = (Integer) getTagValue(TagW.Columns);
                if (rows != null && columns != null && rows > 0 && columns > 0)
                    // If no sliceTickness: set 0, sliceTickness is only use in IntersectVolume
                    // Multiply rows and columns by getZoomScale() to have square pixel image size
                    return new GeometryOfSlice(new double[] { imgOr[0], imgOr[1], imgOr[2] }, new double[] { imgOr[3],
                        imgOr[4], imgOr[5] }, pos, spacing, sliceTickness == null ? 0.0 : sliceTickness.doubleValue(),
                        new double[] { rows * getRescaleY(), columns * getRescaleX(), 1 });
            }
        }
        return null;
    }

}
