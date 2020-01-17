/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec.utils;

import java.awt.image.DataBuffer;
import java.lang.reflect.Array;
import java.util.Arrays;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.opencv.core.CvType;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

/**
 *
 * @author Benoit Jacquemoud
 * @author Nicolas Roduit
 */
public class DicomImageUtils {

    private DicomImageUtils() {
    }

    public static PlanarImage getRGBImageFromPaletteColorModel(PlanarImage source, Attributes ds) {
        // Convert images with PaletteColorModel to RGB model
        if (ds != null) {
            int[] rDesc = DicomImageUtils.lutDescriptor(ds, Tag.RedPaletteColorLookupTableDescriptor);
            int[] gDesc = DicomImageUtils.lutDescriptor(ds, Tag.GreenPaletteColorLookupTableDescriptor);
            int[] bDesc = DicomImageUtils.lutDescriptor(ds, Tag.BluePaletteColorLookupTableDescriptor);
            byte[] r = DicomImageUtils.lutData(ds, rDesc, Tag.RedPaletteColorLookupTableData,
                Tag.SegmentedRedPaletteColorLookupTableData);
            byte[] g = DicomImageUtils.lutData(ds, gDesc, Tag.GreenPaletteColorLookupTableData,
                Tag.SegmentedGreenPaletteColorLookupTableData);
            byte[] b = DicomImageUtils.lutData(ds, bDesc, Tag.BluePaletteColorLookupTableData,
                Tag.SegmentedBluePaletteColorLookupTableData);

            if (source.depth() <= CvType.CV_8S) {
                // Replace the original image with the RGB image.
                return ImageProcessor.applyLUT(source.toMat(), new byte[][] { b, g, r });
            } else {
                LookupTableCV lookup = new LookupTableCV( new byte[][] { b, g, r });
                return lookup.lookup(source.toMat());
            }
        }
        return source;
    }

    /**
     * Minimum output is given for input value below (level - window/2)<br>
     * Maximum output is given for input value above (level + window/2) <br>
     * <br>
     * These Minimum and Maximum values depends on bitsStored and signed given attributes. ie : <br>
     * - when bitsStored=8 bits unsigned => minOutValue=0 and maxOutValue=255 <br>
     * - when bitsStored=8 bits signed => minOutValue=-128 and maxOutValue=127 <br>
     * - when bitsStored=16 bits unsigned => minOutValue= 0 and maxOutValue= 65535 <br>
     * - when bitsStored=16 bits signed => minOutValue= -32768 and maxOutValue= 32767 <br>
     *
     * @param lutShape
     * @param window
     * @param level
     * @param minValue
     * @param maxValue
     * @param bitsStored
     * @param isSigned
     * @param inverse
     *
     * @return a LookupTableJAI for data between minValue and maxValue according to all given parameters <br>
     */

    public static LookupTableCV createWindowLevelLut(LutShape lutShape, double window, double level, int minValue,
        int maxValue, int bitsStored, boolean isSigned, boolean inverse) {

        if (lutShape == null) {
            return null;
        }

        int bStored = bitsStored > 16 ? 16 : (bitsStored < 1) ? 1 : bitsStored;
        double win = window < 1.0 ? 1.0 : window;

        int bitsAllocated = (bStored <= 8) ? 8 : 16;
        int outRangeSize = (1 << bitsAllocated) - 1;
        int maxOutValue = isSigned ? (1 << (bitsAllocated - 1)) - 1 : outRangeSize;
        int minOutValue = isSigned ? -(maxOutValue + 1) : 0;

        int minInValue = Math.min(maxValue, minValue);
        int maxInValue = Math.max(maxValue, minValue);

        int numEntries = maxInValue - minInValue + 1;
        Object outLut = bStored <= 8 ? new byte[numEntries] : new short[numEntries];

        if (lutShape.getFunctionType() != null) {
            switch (lutShape.getFunctionType()) {
                case LINEAR:
                    setWindowLevelLinearLut(win, level, minInValue, outLut, minOutValue, maxOutValue, inverse);
                    break;
                case SIGMOID:
                    setWindowLevelSigmoidLut(win, level, minInValue, outLut, minOutValue, maxOutValue, inverse);
                    break;
                case SIGMOID_NORM:
                    setWindowLevelSigmoidLut(win, level, minInValue, outLut, minOutValue, maxOutValue, inverse, true);
                    break;
                case LOG:
                    setWindowLevelLogarithmicLut(win, level, minInValue, outLut, minOutValue, maxOutValue, inverse);
                    break;
                case LOG_INV:
                    setWindowLevelExponentialLut(win, level, minInValue, outLut, minOutValue, maxOutValue, inverse);
                    break;
                default:
                    return null;
            }
        } else {
            setWindowLevelSequenceLut(win, level, lutShape.getLookup(), minInValue, maxInValue, outLut, minOutValue,
                maxOutValue, inverse);
        }

        return (outLut instanceof byte[]) ? new LookupTableCV((byte[]) outLut, minInValue) : //
            new LookupTableCV((short[]) outLut, minInValue, isSigned);
    }

    /**
     * @return LookupTable with full range of possible input entries according to bitStored.<br>
     *         Note that isSigned is relevant for both input and output values
     */

    public static LookupTableCV createRescaleRampLut(LutParameters params) {
        return createRescaleRampLut(params.getIntercept(), params.getSlope(), params.getBitsStored(), params.isSigned(),
            params.isOutputSigned(), params.getBitsOutput());
    }

    public static LookupTableCV createRescaleRampLut(double intercept, double slope, int bitsStored, boolean isSigned,
        boolean outputSigned, int bitsOutput) {

        return createRescaleRampLut(intercept, slope, Integer.MIN_VALUE, Integer.MAX_VALUE, bitsStored, isSigned, false,
            outputSigned, bitsOutput);
    }

    public static LookupTableCV createRescaleRampLut(double intercept, double slope, int minValue, int maxValue,
        int bitsStored, boolean isSigned, boolean inverse, boolean outputSigned, int bitsOutput) {

        int stored = (bitsStored > 16) ? 16 : ((bitsStored < 1) ? 1 : bitsStored);

        int bitsOutLut = bitsOutput <= 8 ? 8 : 16;
        int outRangeSize = (1 << bitsOutLut) - 1;
        int maxOutValue = outputSigned ? (1 << (bitsOutLut - 1)) - 1 : outRangeSize;
        int minOutValue = outputSigned ? -(maxOutValue + 1) : 0;

        int minInValue = isSigned ? -(1 << (stored - 1)) : 0;
        int maxInValue = isSigned ? (1 << (stored - 1)) - 1 : (1 << stored) - 1;

        minInValue = Math.max(minInValue, maxValue < minValue ? maxValue : minValue);
        maxInValue = Math.min(maxInValue, maxValue < minValue ? minValue : maxValue);

        int numEntries = maxInValue - minInValue + 1;
        Object outLut = (bitsOutLut == 8) ? new byte[numEntries] : new short[numEntries];

        for (int i = 0; i < numEntries; i++) {
            int value = (int) Math.round((i + minInValue) * slope + intercept);

            value = (value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value);
            value = inverse ? (maxOutValue + minOutValue - value) : value;

            if (outLut instanceof byte[]) {
                Array.set(outLut, i, (byte) value);
            } else if (outLut instanceof short[]) {
                Array.set(outLut, i, (short) value);
            }
        }

        return (outLut instanceof byte[]) ? new LookupTableCV((byte[]) outLut, minInValue) : //
            new LookupTableCV((short[]) outLut, minInValue, !outputSigned);
    }

    /**
     * Apply the pixel padding to the modality LUT
     *
     * @see DICOM standard PS 3.3
     *
     *      §C.7.5.1.1.2 Pixel Padding Value and Pixel Padding Range Limit If Photometric Interpretation
     *
     *      * If a Pixel Padding Value (0028,0120) only is present in the image then image contrast manipulations shall
     *      be not be applied to those pixels with the value specified in Pixel Padding Value (0028,0120). If both Pixel
     *      Padding Value (0028,0120) and Pixel Padding Range Limit (0028,0121) are present in the image then image
     *      contrast manipulations shall not be applied to those pixels with values in the range between the values of
     *      Pixel Padding Value (0028,0120) and Pixel Padding Range Limit (0028,0121), inclusive."
     *
     *
     *      (0028,0004) is MONOCHROME2, Pixel Padding Value (0028,0120) shall be less than (closer to or equal to the
     *      minimum possible pixel value) or equal to Pixel Padding Range Limit (0028,0121). If Photometric
     *      Interpretation (0028,0004) is MONOCHROME1, Pixel Padding Value (0028,0120) shall be greater than (closer to
     *      or equal to the maximum possible pixel value) or equal to Pixel Padding Range Limit (0028,0121).
     *
     *      When the relationship between pixel value and X-Ray Intensity is unknown, it is recommended that the
     *      following values be used to pad with black when the image is unsigned:
     *
     *      0 if Photometric Interpretation (0028,0004) is MONOCHROME2. 2BitsStored - 1 if Photometric Interpretation
     *      (0028,0004) is MONOCHROME1.
     *
     *      and when the image is signed: -2BitsStored-1 if Photometric Interpretation (0028,0004) is MONOCHROME2.
     *      2BitsStored-1 - 1 if Photometric Interpretation (0028,0004) is MONOCHROME1.
     *
     *
     */
    public static void applyPixelPaddingToModalityLUT(LookupTableCV modalityLookup, LutParameters lutparams) {
        if (modalityLookup != null && lutparams.isApplyPadding() && lutparams.getPaddingMinValue() != null
            && modalityLookup.getDataType() <= DataBuffer.TYPE_SHORT) {

            int paddingValue = lutparams.getPaddingMinValue();
            Integer paddingLimit = lutparams.getPaddingMaxValue();
            int paddingValueMin = (paddingLimit == null) ? paddingValue : Math.min(paddingValue, paddingLimit);
            int paddingValueMax = (paddingLimit == null) ? paddingValue : Math.max(paddingValue, paddingLimit);

            int numPaddingValues = paddingValueMax - paddingValueMin + 1;
            int paddingValuesStartIndex = paddingValueMin - modalityLookup.getOffset();

            if (paddingValuesStartIndex >= modalityLookup.getNumEntries()) {
                return;
            }

            if (paddingValuesStartIndex < 0) {
                numPaddingValues += paddingValuesStartIndex;
                if (numPaddingValues < 1) {
                    // No padding value in the LUT range
                    return;
                }
                paddingValuesStartIndex = 0;
            }

            Object inLut;
            // if FALSE DataBuffer Type is supposed to be either TYPE_SHORT or TYPE_USHORT
            final boolean isDataTypeByte = modalityLookup.getDataType() == DataBuffer.TYPE_BYTE;
            if (isDataTypeByte) {
                inLut = modalityLookup.getByteData(0);
            } else {
                inLut = modalityLookup.getShortData(0);
            }

            Object outLut = inLut;
            if (isDataTypeByte) {
                byte fillVal = lutparams.isInversePaddingMLUT() ? (byte) 255 : (byte) 0;
                byte[] data = (byte[]) outLut;
                Arrays.fill(data, paddingValuesStartIndex, paddingValuesStartIndex + numPaddingValues, fillVal);
            } else {
                short[] data = (short[]) outLut;
                short fillVal = lutparams.isInversePaddingMLUT() ? data[data.length - 1] : data[0];
                Arrays.fill(data, paddingValuesStartIndex, paddingValuesStartIndex + numPaddingValues, fillVal);
            }
        }
    }

    private static void setWindowLevelLinearLutLegacy(double window, double level, int minInValue, Object outLut,
        int minOutValue, int maxOutValue, boolean inverse) {

        /**
         * Pseudo code defined in Dicom Standard 2011 - PS 3.3 § C.11.2 VOI LUT Module
         */
        double lowLevel = (level - 0.5) - (window - 1.0) / 2.0;
        double highLevel = (level - 0.5) + (window - 1.0) / 2.0;

        for (int i = 0; i < Array.getLength(outLut); i++) {
            int value;

            if ((i + minInValue) <= lowLevel) {
                value = minOutValue;
            } else if ((i + minInValue) > highLevel) {
                value = maxOutValue;
            } else {
                value = (int) ((((i + minInValue) - (level - 0.5)) / (window - 1.0) + 0.5) * (maxOutValue - minOutValue)
                    + minOutValue);
            }

            value = (value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value);
            value = inverse ? (maxOutValue + minOutValue - value) : value;

            if (outLut instanceof byte[]) {
                Array.set(outLut, i, (byte) value);
            } else if (outLut instanceof short[]) {
                Array.set(outLut, i, (short) value);
            }
        }
    }

    private static void setWindowLevelLinearLut(double window, double level, int minInValue, Object outLut,
        int minOutValue, int maxOutValue, boolean inverse) {

        double slope = (maxOutValue - minOutValue) / window;
        double intercept = maxOutValue - slope * (level + (window / 2.0));

        for (int i = 0; i < Array.getLength(outLut); i++) {
            int value = (int) ((i + minInValue) * slope + intercept);

            value = (value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value);
            value = inverse ? (maxOutValue + minOutValue - value) : value;

            if (outLut instanceof byte[]) {
                Array.set(outLut, i, (byte) value);
            } else if (outLut instanceof short[]) {
                Array.set(outLut, i, (short) value);
            }
        }
    }

    private static void setWindowLevelSigmoidLut(double width, double center, int minInValue, Object outLut,
        int minOutValue, int maxOutValue, boolean inverse) {

        setWindowLevelSigmoidLut(width, center, minInValue, outLut, minOutValue, maxOutValue, inverse, false);
    }

    private static void setWindowLevelSigmoidLut(double width, double center, int minInValue, Object outLut,
        int minOutValue, int maxOutValue, boolean inverse, boolean normalize) {

        double nFactor = -20d; // factor defined by default in Dicom standard ( -20*2/10 = -4 )
        double outRange = maxOutValue - (double) minOutValue;

        double minValue = 0;
        double outRescaleRatio = 1;

        if (normalize) {
            double lowLevel = center - width / 2d;
            double highLevel = center + width / 2d;

            minValue = minOutValue + outRange / (1d + Math.exp((2d * nFactor / 10d) * (lowLevel - center) / width));
            double maxValue =
                minOutValue + outRange / (1d + Math.exp((2d * nFactor / 10d) * (highLevel - center) / width));
            outRescaleRatio = (maxOutValue - minOutValue) / Math.abs(maxValue - minValue);
        }

        for (int i = 0; i < Array.getLength(outLut); i++) {
            double value = outRange / (1d + Math.exp((2d * nFactor / 10d) * (i + minInValue - center) / width));

            if (normalize) {
                value = (value - minValue) * outRescaleRatio;
            }

            value = (int) Math.round(value + minOutValue);
            value = (int) ((value > maxOutValue) ? maxOutValue : ((value < minOutValue) ? minOutValue : value));
            value = (int) (inverse ? (maxOutValue + minOutValue - value) : value);

            if (outLut instanceof byte[]) {
                Array.set(outLut, i, (byte) value);
            } else if (outLut instanceof short[]) {
                Array.set(outLut, i, (short) value);
            }
        }
    }

    private static void setWindowLevelExponentialLut(double width, double center, int minInValue, Object outLut,
        int minOutValue, int maxOutValue, boolean inverse) {

        setWindowLevelExponentialLut(width, center, minInValue, outLut, minOutValue, maxOutValue, inverse, true);
    }

    private static void setWindowLevelExponentialLut(double width, double center, int minInValue, Object outLut,
        int minOutValue, int maxOutValue, boolean inverse, boolean normalize) {

        double nFactor = 20d;
        double outRange = maxOutValue - (double) minOutValue;

        double minValue = 0;
        double outRescaleRatio = 1;

        if (normalize) {
            double lowLevel = center - width / 2d;
            double highLevel = center + width / 2d;

            minValue = minOutValue + outRange * Math.exp((nFactor / 10d) * (lowLevel - center) / width);
            double maxValue = minOutValue + outRange * Math.exp((nFactor / 10d) * (highLevel - center) / width);
            outRescaleRatio = (maxOutValue - minOutValue) / Math.abs(maxValue - minValue);
        }

        for (int i = 0; i < Array.getLength(outLut); i++) {
            double value = outRange * Math.exp((nFactor / 10d) * (i + minInValue - center) / width);

            if (normalize) {
                value = (value - minValue) * outRescaleRatio;
            }

            value = (int) Math.round(value + minOutValue);
            value = (int) ((value > maxOutValue) ? maxOutValue : ((value < minOutValue) ? minOutValue : value));
            value = (int) (inverse ? (maxOutValue + minOutValue - value) : value);

            if (outLut instanceof byte[]) {
                Array.set(outLut, i, (byte) value);
            } else if (outLut instanceof short[]) {
                Array.set(outLut, i, (short) value);
            }
        }

    }

    private static void setWindowLevelLogarithmicLut(double width, double center, int minInValue, Object outLut,
        int minOutValue, int maxOutValue, boolean inverse) {

        setWindowLevelLogarithmicLut(width, center, minInValue, outLut, minOutValue, maxOutValue, inverse, true);
    }

    private static void setWindowLevelLogarithmicLut(double width, double center, int minInValue, Object outLut,
        int minOutValue, int maxOutValue, boolean inverse, boolean normalize) {

        double nFactor = 20d;
        double outRange = maxOutValue - (double) minOutValue;

        double minValue = 0;
        double outRescaleRatio = 1;

        if (normalize) {
            double lowLevel = center - width / 2d;
            double highLevel = center + width / 2d;

            minValue = minOutValue + outRange * Math.log((nFactor / 10d) * (1 + (lowLevel - center) / width));
            double maxValue = minOutValue + outRange * Math.log((nFactor / 10d) * (1 + (highLevel - center) / width));

            outRescaleRatio = (maxOutValue - minOutValue) / Math.abs(maxValue - minValue);
        }

        for (int i = 0; i < Array.getLength(outLut); i++) {
            double value = outRange * Math.log((nFactor / 10d) * (1 + (i + minInValue - center) / width));

            if (normalize) {
                value = (value - minValue) * outRescaleRatio;
            }

            value = (int) Math.round(value + minOutValue);
            value = (int) ((value > maxOutValue) ? maxOutValue : ((value < minOutValue) ? minOutValue : value));
            value = (int) (inverse ? (maxOutValue + minOutValue - value) : value);

            if (outLut instanceof byte[]) {
                Array.set(outLut, i, (byte) value);
            } else if (outLut instanceof short[]) {
                Array.set(outLut, i, (short) value);
            }
        }
    }

    private static Object getLutDataArray(LookupTableCV lookup) {
        Object lutDataArray = null;
        if (lookup != null) {
            if (lookup.getDataType() == DataBuffer.TYPE_BYTE) {
                lutDataArray = lookup.getByteData(0);
            } else if (lookup.getDataType() <= DataBuffer.TYPE_SHORT) {
                lutDataArray = lookup.getShortData(0);
            }
        }
        return lutDataArray;
    }

    /**
     * @param width
     * @param center
     * @param lookupSequence
     * @param minInValue
     * @param maxInValue
     * @param outLut
     * @param minOutValue
     * @param maxOutValue
     * @param inverse
     * @return a normalized LookupTableJAI based upon given lutSequence <br>
     */

    private static void setWindowLevelSequenceLut(double width, double center, LookupTableCV lookupSequence,
        int minInValue, int maxInValue, Object outLut, int minOutValue, int maxOutValue, boolean inverse) {

        final Object inLutDataArray = getLutDataArray(lookupSequence);

        if (inLutDataArray == null) {
            return;
        }

        // Use this mask to get positive value assuming inLutData value is always unsigned
        final int lutDataValueMask = inLutDataArray instanceof byte[] ? 0x000000FF
            : (inLutDataArray instanceof short[] ? 0x0000FFFF : 0xFFFFFFFF);

        double lowLevel = center - width / 2.0;
        double highLevel = center + width / 2.0;

        int maxInLutIndex = Array.getLength(inLutDataArray) - 1;
        int minLookupValue = Integer.MAX_VALUE;
        int maxLookupValue = Integer.MIN_VALUE;
        for (int i = 0; i < Array.getLength(inLutDataArray); i++) {
            int val = lutDataValueMask & Array.getInt(inLutDataArray, i);
            if (val < minLookupValue) {
                minLookupValue = val;
            }
            if (val > maxLookupValue) {
                maxLookupValue = val;
            }
        }
        int lookupValueRange = Math.abs(maxLookupValue - minLookupValue);

        double widthRescaleRatio = maxInLutIndex / width;
        double outRescaleRatio = (maxOutValue - minOutValue) / (double) lookupValueRange;

        for (int i = 0; i < Array.getLength(outLut); i++) {
            int value;
            double inValueRescaled;

            if ((i + minInValue) <= lowLevel) {
                inValueRescaled = 0;
            } else if ((i + minInValue) > highLevel) {
                inValueRescaled = maxInLutIndex;
            } else {
                inValueRescaled = (i + minInValue - lowLevel) * widthRescaleRatio;
            }

            int inValueRoundDown = Math.max(0, (int) Math.floor(inValueRescaled));
            int inValueRoundUp = Math.min(maxInLutIndex, (int) Math.ceil(inValueRescaled));

            int valueDown = lutDataValueMask & Array.getInt(inLutDataArray, inValueRoundDown);
            int valueUp = lutDataValueMask & Array.getInt(inLutDataArray, inValueRoundUp);

            // Linear Interpolation of the output value with respect to the rescaled ratio
            value = (int) ((inValueRoundUp == inValueRoundDown) ? valueDown : Math.round(valueDown
                + (inValueRescaled - inValueRoundDown) * (valueUp - valueDown) / (inValueRoundUp - inValueRoundDown)));

            value = (int) Math.round(value * outRescaleRatio);

            value = (value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value);
            value = inverse ? (maxOutValue + minOutValue - value) : value;

            if (outLut instanceof byte[]) {
                Array.set(outLut, i, (byte) value);
            } else if (outLut instanceof short[]) {
                Array.set(outLut, i, (short) value);
            }
        }
    }

    public static double pixel2rescale(TagReadable tagable, double pixelValue) {
        if (tagable != null) {
            LookupTableCV lookup = (LookupTableCV) tagable.getTagValue(TagW.ModalityLUTData);
            if (lookup != null) {
                if (pixelValue >= lookup.getOffset() && pixelValue <= lookup.getOffset() + lookup.getNumEntries() - 1) {
                    return lookup.lookup(0, (int) pixelValue);
                }
            } else {
                // value = pixelValue * rescale slope + intercept value
                Double slope = TagD.getTagValue(tagable, Tag.RescaleSlope, Double.class);
                Double intercept = TagD.getTagValue(tagable, Tag.RescaleIntercept, Double.class);
                if (slope != null || intercept != null) {
                    return pixelValue * (slope == null ? 1.0 : slope) + (intercept == null ? 0.0 : intercept);
                }
            }
        }
        return pixelValue;
    }

    // ////////////////////////////////////////////////////////////////////////////
    // Take from dcm4che3, should be public

    public static int[] lutDescriptor(Attributes ds, int descTag) {
        int[] desc = DicomMediaUtils.getIntAyrrayFromDicomElement(ds, descTag, null);
        if (desc == null) {
            throw new IllegalArgumentException("Missing LUT Descriptor!"); //$NON-NLS-1$
        }
        if (desc.length != 3) {
            throw new IllegalArgumentException("Illegal number of LUT Descriptor values: " + desc.length); //$NON-NLS-1$
        }
        if (desc[0] < 0) {
            throw new IllegalArgumentException("Illegal LUT Descriptor: len=" + desc[0]); //$NON-NLS-1$
        }
        int bits = desc[2];
        if (bits != 8 && bits != 16) {
            throw new IllegalArgumentException("Illegal LUT Descriptor: bits=" + bits); //$NON-NLS-1$
        }
        return desc;
    }

    public static byte[] lutData(Attributes ds, int[] desc, int dataTag, int segmTag) {
        int len = desc[0] == 0 ? 0x10000 : desc[0];
        int bits = desc[2];
        byte[] data = ds.getSafeBytes(dataTag);
        if (data == null) {
            int[] segm = DicomMediaUtils.getIntAyrrayFromDicomElement(ds, segmTag, null);
            if (segm == null) {
                throw new IllegalArgumentException("Missing LUT Data!"); //$NON-NLS-1$
            }
            if (bits == 8) {
                throw new IllegalArgumentException("Segmented LUT Data with LUT Descriptor: bits=8"); //$NON-NLS-1$
            }
            data = new byte[len];
            inflateSegmentedLut(segm, data);
        } else if (bits == 16 || data.length != len) {
            if (data.length != len << 1) {
                lutLengthMismatch(data.length, len);
            }
            int hilo = ds.bigEndian() ? 0 : 1;
            if (bits == 8) {
                hilo = 1 - hilo; // padded high bits -> use low bits
            }
            byte[] bs = new byte[data.length >> 1];
            for (int i = 0; i < bs.length; i++) {
                bs[i] = data[(i << 1) | hilo];
            }
            data = bs;
        }
        return data;
    }

    private static void inflateSegmentedLut(int[] in, byte[] out) {
        int x = 0;
        try {
            for (int i = 0; i < in.length;) {
                int op = in[i++];
                int n = in[i++];
                switch (op) {
                    case 0:
                        while (n-- > 0) {
                            out[x++] = (byte) in[i++];
                        }
                        break;
                    case 1:
                        x = linearSegment(in[i++], out, x, n);
                        break;
                    case 2: {
                        int i2 = (in[i++] & 0xffff) | (in[i++] << 16);
                        while (n-- > 0) {
                            int op2 = in[i2++];
                            int n2 = in[i2++] & 0xffff;
                            switch (op2) {
                                case 0:
                                    while (n2-- > 0) {
                                        out[x++] = (byte) in[i2++];
                                    }
                                    break;
                                case 1:
                                    x = linearSegment(in[i2++], out, x, n);
                                    break;
                                default:
                                    illegalOpcode(op, i2 - 2);
                            }
                        }
                    }
                        break;
                    default:
                        illegalOpcode(op, i - 2);
                }
            }
        } catch (IndexOutOfBoundsException e) {
            if (x > out.length) {
                exceedsLutLength(out.length);
            } else {
                endOfSegmentedLut();
            }
        }
        if (x < out.length) {
            lutLengthMismatch(x, out.length);
        }
    }

    private static void endOfSegmentedLut() {
        throw new IllegalArgumentException("Running out of data inflating segmented LUT"); //$NON-NLS-1$
    }

    private static int linearSegment(int y1, byte[] out, int x, int n) {
        if (x == 0) {
            throw new IllegalArgumentException("Linear segment cannot be the first segment"); //$NON-NLS-1$
        }

        try {
            int y0 = out[x - 1];
            int dy = y1 - y0;
            for (int j = 1; j <= n; j++) {
                out[x++] = (byte) ((y0 + dy * j / n) >> 8);
            }
        } catch (IndexOutOfBoundsException e) {
            exceedsLutLength(out.length);
        }
        return x;
    }

    private static void exceedsLutLength(int descLen) {
        throw new IllegalArgumentException("Number of entries in inflated segmented LUT exceeds specified value: " //$NON-NLS-1$
            + descLen + " in LUT Descriptor"); //$NON-NLS-1$
    }

    private static void lutLengthMismatch(int lutLen, int descLen) {
        throw new IllegalArgumentException("Number of actual LUT entries: " + lutLen + " mismatch specified value: " //$NON-NLS-1$ //$NON-NLS-2$
            + descLen + " in LUT Descriptor"); //$NON-NLS-1$
    }

    private static void illegalOpcode(int op, int i) {
        throw new IllegalArgumentException("illegal op code:" + op + ", index:" + i); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
