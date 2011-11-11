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

import java.awt.image.ByteLookupTable;
import java.awt.image.DataBuffer;
import java.awt.image.LookupTable;
import java.awt.image.RenderedImage;
import java.awt.image.ShortLookupTable;
import java.util.Arrays;

import javax.media.jai.LookupTableJAI;
import javax.media.jai.OpImage;
import javax.media.jai.ROI;
import javax.media.jai.RenderedOp;

import org.weasis.core.api.image.op.ImageStatisticsDescriptor;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.utils.DicomImageUtils;

public class DicomImageElement extends ImageElement {

    private LookupTableJAI modalityLookup = null;

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
                // Pixel Spacing = Row Spacing \ Column Spacing => (Y,X)
                // The first value is the row spacing in mm, that is the spacing between the centers of adjacent rows,
                // or vertical spacing.
                // Pixel Spacing must be always positive, but some DICOMs have negative values
                setPixelSize(val[1], val[0]);
                pixelSpacingUnit = Unit.MILLIMETER;
            } else if (val == null) {
                int[] aspects = (int[]) mediaIO.getTagValue(TagW.PixelAspectRatio);
                if (aspects != null && aspects.length == 2 && aspects[0] != aspects[1]) {
                    // set the aspects to the pixel size of the image to stretch the image rendering (square pixel)
                    if (aspects[1] < aspects[0]) {
                        setPixelSize(1.0, (double) aspects[0] / (double) aspects[1]);
                    } else {
                        setPixelSize((double) aspects[1] / (double) aspects[0], 1.0);
                    }
                }
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
    public float getMinValue() {
        return pixel2rescale(minValue);
    }

    @Override
    public float getMaxValue() {
        return pixel2rescale(maxValue);
    }

    // cannot be used like this since modality LUT may be not linear
    @Deprecated
    @Override
    public float getPixelWindow(float window) {
        Float slope = (Float) getTagValue(TagW.RescaleSlope);
        if (slope != null)
            return window /= slope;
        return window;
    }

    // TODO - must implement a reverse lookup
    @Override
    public float getPixelLevel(float level) {
        return rescale2pixel(level);
    }

    @Override
    protected boolean isGrayImage(RenderedImage source) {
        Boolean val = (Boolean) getTagValue(TagW.MonoChrome);
        return val == null ? true : val;
    }

    /**
     * Data representation of the pixel samples. Each sample shall have the same pixel representation. Enumerated
     * Values: 0000H = unsigned integer. 0001H = 2's complement
     * 
     * @return true if Tag exist and if explicitly define a signed
     * @see DICOM standard PS 3.3 - §C.7.6.3 - Image Pixel Module
     */

    public boolean isPixelRepresentationSigned() {
        Integer pixelRepresentation = (Integer) getTagValue(TagW.PixelRepresentation);
        return (pixelRepresentation != null) && (pixelRepresentation != 0);
    }

    /**
     * The value of Photometric Interpretation specifies the intended interpretation of the image pixel data.
     * 
     * @return following values (MONOCHROME1 , MONOCHROME2 , PALETTE COLOR ....) Other values are permitted but the
     *         meaning is not defined by this Standard.
     */
    public String getPhotometricInterpretation() {
        return (String) getTagValue(TagW.PhotometricInterpretation);
    }

    /**
     * 
     * Pixel Padding Value is used to pad grayscale images (those with a Photometric Interpretation of MONOCHROME1 or
     * MONOCHROME2)<br>
     * Pixel Padding Value specifies either a single value of this padding value, or when combined with Pixel Padding
     * Range Limit, a range of values (inclusive) that are padding.<br>
     * <br>
     * <b>Note :</b> It is explicitly described in order to prevent display applications from taking it into account
     * when determining the dynamic range of an image, since the Pixel Padding Value will be outside the range between
     * the minimum and maximum values of the pixels in the native image
     * 
     * @see DICOM standard PS 3.3 - §C.7.5.1.1.2 - Pixel Padding Value and Pixel Padding Range Limit
     */

    public Integer getPaddingValue() {
        return (Integer) getTagValue(TagW.PixelPaddingValue);
    }

    /**
     * @see getPaddingValue()
     */
    public Integer getPaddingLimit() {
        return (Integer) getTagValue(TagW.PixelPaddingRangeLimit);

    }

    /**
     * If a Pixel Padding Value (0028,0120) only is present in the image then image contrast manipulations shall be not
     * be applied to those pixels with the value specified in Pixel Padding Value (0028,0120). If both Pixel Padding
     * Value (0028,0120) and Pixel Padding Range Limit (0028,0121) are present in the image then image contrast
     * manipulations shall not be applied to those pixels with values in the range between the values of Pixel Padding
     * Value (0028,0120) and Pixel Padding Range Limit (0028,0121), inclusive."
     * 
     * @return
     */
    public LookupTableJAI getModalityLookup() {
        if (modalityLookup != null)
            return modalityLookup;

        boolean signed = isPixelRepresentationSigned();
        Integer bitsStored = (Integer) getTagValue(TagW.BitsStored);

        LookupTable lookup = (LookupTable) getTagValue(TagW.ModalityLUTData);
        Float intercept = (Float) getTagValue(TagW.RescaleIntercept);
        Float slope = (Float) getTagValue(TagW.RescaleSlope);

        if (lookup == null) {
            int dataType = 0;
            if (bitsStored <= 8) {
                dataType = DataBuffer.TYPE_BYTE;
            } else if (bitsStored <= 16) {
                dataType = signed ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT;
            }

            lookup = DicomImageUtils.createRampLut(dataType, intercept, slope, (int) minValue, (int) maxValue);
        }

        if (lookup != null) {
            // String lutType = (String) getTagValue(TagW.ModalityLUTType);
            // String explanation = (String) getTagValue(TagW.ModalityLUTExplanation);

            int offset = lookup.getOffset();
            int numEntries = 0;

            if (lookup instanceof ByteLookupTable) {
                numEntries = ((ByteLookupTable) lookup).getTable()[0].length;
            } else if (lookup instanceof ShortLookupTable) {
                numEntries = ((ShortLookupTable) lookup).getTable()[0].length;
            } else
                return null;

            String photometricInterpretation = getPhotometricInterpretation();

            if (photometricInterpretation != null && //
                ("MONOCHROME1".equalsIgnoreCase(photometricInterpretation) || //
                "MONOCHROME2".equalsIgnoreCase(photometricInterpretation))) {

                Integer paddingValue = getPaddingValue();
                Integer paddingLimit = getPaddingLimit();

                if (paddingValue != null) {
                    int paddingValueMin = (paddingLimit == null) ? paddingValue : Math.min(paddingValue, paddingLimit);
                    int paddingValueMax = (paddingLimit == null) ? paddingValue : Math.max(paddingValue, paddingLimit);

                    // Test if modality lookupTable take pixel padding values into account

                    if (paddingValueMax < -offset) {
                        // add padding value range to the start of the lookup shifting right all existing elements
                        // by numPaddingValues

                        int numPaddingValues = paddingValueMin - offset;
                        numEntries += numPaddingValues;
                        offset += numPaddingValues;

                        if (lookup instanceof ByteLookupTable) {
                            byte[] newData = new byte[numEntries];
                            System.arraycopy(((ByteLookupTable) lookup).getTable()[0], 0, newData, numPaddingValues,
                                numEntries - numPaddingValues);
                            lookup = new ByteLookupTable(offset, newData);
                        } else if (lookup instanceof ShortLookupTable) {
                            short[] newData = new short[numEntries];
                            System.arraycopy(((ShortLookupTable) lookup).getTable()[0], 0, newData, numPaddingValues,
                                numEntries - numPaddingValues);
                            lookup = new ShortLookupTable(offset, newData);
                        }

                    } else if (paddingValueMin > (numEntries - offset)) {
                        // add padding value range to the end of the lookup

                        int numPaddingValues = paddingValueMax - (numEntries - offset);
                        numEntries += numPaddingValues;

                        if (lookup instanceof ByteLookupTable) {
                            byte[] newData = Arrays.copyOf(((ByteLookupTable) lookup).getTable()[0], numEntries);
                            lookup = new ByteLookupTable(offset, newData);
                        } else if (lookup instanceof ShortLookupTable) {
                            short[] newData = Arrays.copyOf(((ShortLookupTable) lookup).getTable()[0], numEntries);
                            lookup = new ShortLookupTable(offset, newData);
                        }
                    }

                    // TODO - Set padding values to 0 in case they are in the range of the current LUT values
                }
            }

            if (lookup instanceof ByteLookupTable) {
                modalityLookup = new LookupTableJAI(((ByteLookupTable) lookup).getTable(), offset);
            } else if (lookup instanceof ShortLookupTable) {
                modalityLookup = new LookupTableJAI(((ShortLookupTable) lookup).getTable(), offset, !signed);
            }
        }

        return modalityLookup;
    }

    @Override
    protected void findMinMaxValues(RenderedImage img) {
        // This function can be called several times from the inner class Load.
        // Not necessary to find again min and max

        if (img != null && minValue == 0.0f && maxValue == 0.0f) {

            Integer min = (Integer) getTagValue(TagW.SmallestImagePixelValue);
            Integer max = (Integer) getTagValue(TagW.LargestImagePixelValue);

            minValue = (min == null) ? 0.0f : min.floatValue();
            maxValue = (max == null) ? 0.0f : max.floatValue();

            String photometricInterpretation = getPhotometricInterpretation();

            if (photometricInterpretation != null && //
                ("MONOCHROME1".equalsIgnoreCase(photometricInterpretation) || //
                "MONOCHROME2".equalsIgnoreCase(photometricInterpretation))) {

                Integer paddingValue = getPaddingValue();
                Integer paddingLimit = getPaddingLimit();

                if (paddingValue != null) {

                    int paddingValueMin = (paddingLimit == null) ? paddingValue : Math.min(paddingValue, paddingLimit);
                    int paddingValueMax = (paddingLimit == null) ? paddingValue : Math.max(paddingValue, paddingLimit);

                    if (minValue != 0.0f || maxValue != 0.0f) {
                        if ((paddingValueMin <= minValue && minValue <= paddingValueMax)
                            || (paddingValueMin <= maxValue && maxValue <= paddingValueMax)) {
                            // possible confusing Min/Max image values regarding to padding Min/Max range in order to
                            // get full dynamic range of the image with real pixel only
                            findMinMaxValues(img, paddingValueMin, paddingValueMax);
                        }
                    } else {
                        findMinMaxValues(img, paddingValueMin, paddingValueMax);
                    }
                }
            }

            if (minValue == 0.0f && maxValue == 0.0f) {
                super.findMinMaxValues(img);
            }

            // Lazily compute image pixel transformation here since inner class Load is called from a separate and
            // dedicated worker Thread. Also, it will be computed only once
            getModalityLookup();
        }
    }

    /**
     * Computes Min/Max values from Image excluding range of values provided
     * 
     * @param img
     * @param paddingValueMin
     * @param paddingValueMax
     */
    private void findMinMaxValues(RenderedImage img, double paddingValueMin, double paddingValueMax) {
        if (img != null) {

            RenderedOp dst =
                ImageStatisticsDescriptor.create(img, (ROI) null, 1, 1, new Double(paddingValueMin), new Double(
                    paddingValueMax), null);
            // To ensure this image won't be stored in tile cache
            ((OpImage) dst.getRendering()).setTileCache(null);

            double[][] extrema = (double[][]) dst.getProperty("statistics"); //$NON-NLS-1$
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            int numBands = dst.getSampleModel().getNumBands();

            for (int i = 0; i < numBands; i++) {
                min = Math.min(min, extrema[0][i]);
                max = Math.max(max, extrema[1][i]);
            }

            this.minValue = Math.round(min);
            this.maxValue = Math.round(max);
        }
    }

    @Override
    public float getDefaultWindow() {
        // Float val = (Float) getTagValue(TagW.WindowWidth);
        Float[] val = (Float[]) getTagValue(TagW.WindowWidth);
        if (val == null || val.length == 0)
            return super.getDefaultWindow();
        return val[0];
    }

    @Override
    public float getDefaultLevel() {
        // Float val = (Float) getTagValue(TagW.WindowCenter);
        Float[] val = (Float[]) getTagValue(TagW.WindowCenter);
        if (val == null || val.length == 0)
            return super.getDefaultLevel();
        return val[0];
    }

    // TODO - change name because rescale term is already used, must use modality table to do pixel transform
    @Deprecated
    public float pixel2rescale(float pixelValue) {
        // // Hounsfield units: hu
        // // hu = pixelValue * rescale slope + intercept value
        // Float slope = (Float) getTagValue(TagW.RescaleSlope);
        // Float intercept = (Float) getTagValue(TagW.RescaleIntercept);
        // if (slope != null || intercept != null)
        // return (pixelValue * (slope == null ? 1.0f : slope) + (intercept == null ? 0.0f : intercept));
        // return pixelValue;

        LookupTableJAI lookup = getModalityLookup();
        return (lookup != null) ? lookup.lookupFloat(0, (int) pixelValue) : 0f;
    }

    // TODO - change name because rescale term is already used
    @Deprecated
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
