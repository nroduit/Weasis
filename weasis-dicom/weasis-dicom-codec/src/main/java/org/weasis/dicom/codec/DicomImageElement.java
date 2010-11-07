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
import org.weasis.core.api.media.data.TagElement;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

public class DicomImageElement extends ImageElement {

    public DicomImageElement(DicomMediaIO mediaIO, Object key) {
        super(mediaIO, key);
        String modality = (String) mediaIO.getTagValue(TagElement.Modality);
        if (!"SC".equals(modality) && !"OT".equals(modality)) { //$NON-NLS-1$ //$NON-NLS-2$
            // Physical distance in mm between the center of each pixel (ratio in mm)
            double[] val = (double[]) mediaIO.getTagValue(TagElement.PixelSpacing);
            if (val == null) {
                val = (double[]) mediaIO.getTagValue(TagElement.ImagerPixelSpacing);
                // Follows D. Clunies recommendations
                pixelSizeCalibrationDescription = val == null ? null : Messages.getString("DicomImageElement.detector"); //$NON-NLS-1$

            } else {
                pixelSizeCalibrationDescription =
                    (String) mediaIO.getTagValue(TagElement.PixelSpacingCalibrationDescription);
            }
            if (val != null) {
                pixelSizeX = val[0];
                pixelSizeY = val[1];
                pixelSpacingUnit = Unit.MILLIMETER;
            }
            pixelValueUnit = (String) getTagValue(TagElement.RescaleType);
            if (pixelValueUnit == null) {
                pixelValueUnit = (String) getTagValue(TagElement.Units);
            }
            if (pixelValueUnit == null && "CT".equals(modality)) { //$NON-NLS-1$
                pixelValueUnit = "HU"; //$NON-NLS-1$
            }

        }
    }

    @Override
    public float getPixelWindow(float window) {
        return window /= (Float) getTagValue(TagElement.RescaleSlope);
    }

    @Override
    public float getPixelLevel(float level) {
        return rescale2pixel(level);
    }

    @Override
    protected boolean isGrayImage(RenderedImage source) {
        return (Boolean) getTagValue(TagElement.MonoChrome);
    }

    @Override
    public void findMinMaxValues(RenderedImage img) {
        // This function can be called several times from the inner class Load.
        // Not necessary to find again min and max
        if (minValue == 0.0f && maxValue == 0.0f) {
            minValue = (Float) getTagValue(TagElement.SmallestImagePixelValue);
            maxValue = (Float) getTagValue(TagElement.LargestImagePixelValue);
            /*
             * If a Pixel Padding Value (0028,0120) only is present in the image then image contrast manipulations shall
             * be not be applied to those pixels with the value specified in Pixel Padding Value (0028,0120). If both
             * Pixel Padding Value (0028,0120) and Pixel Padding Range Limit (0028,0121) are present in the image then
             * image contrast manipulations shall not be applied to those pixels with values in the range between the
             * values of Pixel Padding Value (0028,0120) and Pixel Padding Range Limit (0028,0121), inclusive."
             */
            int paddingValue = (Integer) getTagValue(TagElement.PixelPaddingValue);
            int paddingLimit = (Integer) getTagValue(TagElement.PixelPaddingRangeLimit);
            if (paddingValue > 0) {
                if (paddingLimit == 0) {
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
        Float val = (Float) getTagValue(TagElement.WindowWidth);
        if (val.isNaN()) {
            return super.getDefaultWindow();
        }
        return val;
    }

    @Override
    public float getDefaultLevel() {
        Float val = (Float) getTagValue(TagElement.WindowCenter);
        if (val.isNaN()) {
            return super.getDefaultLevel();
        }
        return val;
    }

    public float pixel2rescale(float pixelValue) {
        // Hounsfield units: hu
        // hu = pixelValue * rescale slope + intercept value
        return (pixelValue * (Float) getTagValue(TagElement.RescaleSlope) + (Float) getTagValue(TagElement.RescaleIntercept));

    }

    public float rescale2pixel(float hounsfieldValue) {
        // Hounsfield units: hu
        // pixelValue = (hu - intercept value) / rescale slope
        return (hounsfieldValue - (Float) getTagValue(TagElement.RescaleIntercept))
            / (Float) getTagValue(TagElement.RescaleSlope);

    }

    public GeometryOfSlice getSliceGeometry() {
        double[] imgOr = (double[]) getTagValue(TagElement.ImageOrientationPatient);
        if (imgOr != null && imgOr.length == 6) {
            double[] pos = (double[]) getTagValue(TagElement.ImagePositionPatient);
            if (pos != null && pos.length == 3) {
                double[] spacing = { getPixelSizeX(), getPixelSizeY(), 0.0 };
                Float sliceTickness = (Float) getTagValue(TagElement.SliceThickness);
                if (sliceTickness != null) {
                    int rows = (Integer) getTagValue(TagElement.Rows);
                    int columns = (Integer) getTagValue(TagElement.Columns);
                    if (rows > 0 && columns > 0) {
                        return new GeometryOfSlice(new double[] { imgOr[0], imgOr[1], imgOr[2] }, new double[] {
                            imgOr[3], imgOr[4], imgOr[5] }, pos, spacing, sliceTickness.doubleValue(), new double[] {
                            rows, columns, 1 });
                    }
                }
            }
        }
        return null;
    }

}
