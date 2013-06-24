package org.weasis.dicom.codec.utils;

import java.awt.image.DataBuffer;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;

import javax.imageio.ImageIO;
import javax.media.jai.LookupTableJAI;

import org.dcm4che.data.Attributes;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.media.data.TagW;

/**
 * 
 * @author Benoit Jacquemoud
 * 
 * @version $Rev$ $Date$
 */
public class DicomImageUtils {

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

    public static LookupTableJAI createWindowLevelLut(LutShape lutShape, float window, float level, int minValue,
        int maxValue, int bitsStored, boolean isSigned, boolean inverse) {

        if (lutShape == null) {
            return null;
        }

        bitsStored = (bitsStored > 16) ? bitsStored = 16 : ((bitsStored < 1) ? 1 : bitsStored);
        window = (window < 1f) ? 1f : window;

        // TODO - use bitsAllocated as a parameter instead of extrapolated one
        int bitsAllocated = (bitsStored <= 8) ? 8 : 16;
        int outRangeSize = (1 << bitsAllocated) - 1;
        float maxOutValue = isSigned ? (1 << (bitsAllocated - 1)) - 1 : outRangeSize;
        float minOutValue = isSigned ? -(maxOutValue + 1) : 0;

        float minInValue = Math.min(maxValue, minValue);
        float maxInValue = Math.max(maxValue, minValue);

        int numEntries = (int) (maxInValue - minInValue + 1);
        Object outLut = (bitsStored <= 8) ? new byte[numEntries] : new short[numEntries];

        if (lutShape.getFunctionType() != null) {

            switch (lutShape.getFunctionType()) {
                case LINEAR:
                    setWindowLevelLinearLut(window, level, minInValue, outLut, minOutValue, maxOutValue, inverse);

                    // TODO - do this test below
                    // setWindowLevelLinearLutLegacy(window, level, minInValue, outLut, minOutValue, maxOutValue,
                    // inverse);
                    // Object outLut2 = (bitsStored <= 8) ? new byte[numEntries] : new short[numEntries];
                    // setWindowLevelLinearLut(window, level, minInValue, outLut2, minOutValue, maxOutValue, inverse);
                    // compareDataLuts(outLut, outLut2);
                    break;
                case SIGMOID:
                    setWindowLevelSigmoidLut(window, level, minInValue, outLut, minOutValue, maxOutValue, inverse);
                    break;
                case SIGMOID_NORM:
                    setWindowLevelSigmoidLut(window, level, minInValue, outLut, minOutValue, maxOutValue, inverse, true);
                    break;
                case LOG:
                    setWindowLevelLogarithmicLut(window, level, minInValue, outLut, minOutValue, maxOutValue, inverse);
                    break;
                case LOG_INV:
                    setWindowLevelExponentialLut(window, level, minInValue, outLut, minOutValue, maxOutValue, inverse);
                    break;

                default:
                    return null;
            }
        } else {
            setWindowLevelSequenceLut(window, level, lutShape.getLookup(), minInValue, maxInValue, outLut, minOutValue,
                maxOutValue, inverse);
        }

        return (outLut instanceof byte[]) ? new LookupTableJAI((byte[]) outLut, (int) minInValue) : //
            new LookupTableJAI((short[]) outLut, (int) minInValue, isSigned);
    }

    /**
     * @return LookupTable with full range of possible input entries according to bitStored.<br>
     *         Note that isSigned is relevant for both input and output values
     */

    public static LookupTableJAI createRescaleRampLut(LutParameters params) {
        return createRescaleRampLut(params.getIntercept(), params.getSlope(), params.getBitsStored(),
            params.isSigned(), params.isOutputSigned());
    }

    public static LookupTableJAI createRescaleRampLut(float intercept, float slope, int bitsStored, boolean isSigned,
        boolean outputSigned) {

        return createRescaleRampLut(intercept, slope, Integer.MIN_VALUE, Integer.MAX_VALUE, bitsStored, isSigned,
            false, outputSigned);
    }

    public static LookupTableJAI createRescaleRampLut(float intercept, float slope, int minValue, int maxValue,
        int bitsStored, boolean isSigned, boolean inverse, boolean outputSigned) {

        bitsStored = (bitsStored > 16) ? bitsStored = 16 : ((bitsStored < 1) ? 1 : bitsStored);

        int bitsAllocated = (bitsStored <= 8) ? 8 : 16;
        int outRangeSize = (1 << bitsAllocated) - 1;
        int maxOutValue = outputSigned ? (1 << (bitsAllocated - 1)) - 1 : outRangeSize;
        int minOutValue = outputSigned ? -(maxOutValue + 1) : 0;

        int minInValue = isSigned ? -(1 << (bitsStored - 1)) : 0;
        int maxInValue = isSigned ? (1 << (bitsStored - 1)) - 1 : (1 << bitsStored) - 1;

        if (maxValue < minValue) {
            int tmpMaxValue = minValue;
            minValue = maxValue;
            maxValue = tmpMaxValue;
        }

        minInValue = Math.max(minInValue, minValue);
        maxInValue = Math.min(maxInValue, maxValue);

        int numEntries = maxInValue - minInValue + 1;
        Object outLut = (bitsAllocated == 8) ? new byte[numEntries] : new short[numEntries];

        for (int i = 0; i < numEntries; i++) {
            int value = Math.round((i + minInValue) * slope + intercept);

            value = ((value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value));
            value = (inverse ? (maxOutValue + minOutValue - value) : value);

            if (outLut instanceof byte[]) {
                Array.set(outLut, i, (byte) value);
            } else if (outLut instanceof short[]) {
                Array.set(outLut, i, (short) value);
            }
        }

        return (outLut instanceof byte[]) ? new LookupTableJAI((byte[]) outLut, minInValue) : //
            new LookupTableJAI((short[]) outLut, minInValue, !outputSigned);
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
    public static void applyPixelPaddingToModalityLUT(LookupTableJAI modalityLookup, LutParameters lutparams) {
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

            Object inLut = null;
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

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static void setWindowLevelLinearLutLegacy(float window, float level, float minInValue, Object outLut,
        float minOutValue, float maxOutValue, boolean inverse) {

        /**
         * Pseudo code defined in Dicom Standard 2011 - PS 3.3 § C.11.2 VOI LUT Module
         */
        float lowLevel = (level - 0.5f) - (window - 1f) / 2f;
        float highLevel = (level - 0.5f) + (window - 1f) / 2f;

        for (int i = 0; i < Array.getLength(outLut); i++) {
            int value;

            if ((i + minInValue) <= lowLevel) {
                value = (int) minOutValue;
            } else if ((i + minInValue) > highLevel) {
                value = (int) maxOutValue;
            } else {
                value =
                    (int) ((((i + minInValue) - (level - 0.5f)) / (window - 1f) + 0.5f) * (maxOutValue - minOutValue) + minOutValue);
            }

            value = (int) ((value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value));
            value = (int) (inverse ? (maxOutValue + minOutValue - value) : value);

            if (outLut instanceof byte[]) {
                Array.set(outLut, i, (byte) value);
            } else if (outLut instanceof short[]) {
                Array.set(outLut, i, (short) value);
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static void setWindowLevelLinearLut(float window, float level, float minInValue, Object outLut,
        float minOutValue, float maxOutValue, boolean inverse) {

        float slope = (maxOutValue - minOutValue) / window;
        float intercept = maxOutValue - slope * (level + (window / 2f));

        for (int i = 0; i < Array.getLength(outLut); i++) {
            int value = (int) ((i + minInValue) * slope + intercept);

            value = (int) ((value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value));
            value = (int) (inverse ? (maxOutValue + minOutValue - value) : value);

            if (outLut instanceof byte[]) {
                Array.set(outLut, i, (byte) value);
            } else if (outLut instanceof short[]) {
                Array.set(outLut, i, (short) value);
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static void setWindowLevelSigmoidLut(float width, float center, float minInValue, Object outLut,
        float minOutValue, float maxOutValue, boolean inverse) {

        setWindowLevelSigmoidLut(width, center, minInValue, outLut, minOutValue, maxOutValue, inverse, false);
    }

    private static void setWindowLevelSigmoidLut(float width, float center, float minInValue, Object outLut,
        float minOutValue, float maxOutValue, boolean inverse, boolean normalize) {

        double nFactor = -20d; // factor defined by default in Dicom standard ( -20*2/10 = -4 )
        double outRange = maxOutValue - minOutValue;

        double minValue = 0, maxValue = 0, outRescaleRatio = 1;

        if (normalize) {
            double lowLevel = center - width / 2d;
            double highLevel = center + width / 2d;

            minValue = minOutValue + outRange / (1d + Math.exp((2d * nFactor / 10d) * (lowLevel - center) / width));
            maxValue = minOutValue + outRange / (1d + Math.exp((2d * nFactor / 10d) * (highLevel - center) / width));

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

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static void setWindowLevelExponentialLut(float width, float center, float minInValue, Object outLut,
        float minOutValue, float maxOutValue, boolean inverse) {

        setWindowLevelExponentialLut(width, center, minInValue, outLut, minOutValue, maxOutValue, inverse, true);
    }

    private static void setWindowLevelExponentialLut(float width, float center, float minInValue, Object outLut,
        float minOutValue, float maxOutValue, boolean inverse, boolean normalize) {

        double nFactor = 20d;
        double outRange = maxOutValue - minOutValue;

        double minValue = 0, maxValue = 0, outRescaleRatio = 1;

        if (normalize) {
            double lowLevel = center - width / 2d;
            double highLevel = center + width / 2d;

            minValue = minOutValue + outRange * Math.exp((nFactor / 10d) * (lowLevel - center) / width);
            maxValue = minOutValue + outRange * Math.exp((nFactor / 10d) * (highLevel - center) / width);

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

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static void setWindowLevelLogarithmicLut(float width, float center, float minInValue, Object outLut,
        float minOutValue, float maxOutValue, boolean inverse) {

        setWindowLevelLogarithmicLut(width, center, minInValue, outLut, minOutValue, maxOutValue, inverse, true);
    }

    private static void setWindowLevelLogarithmicLut(float width, float center, float minInValue, Object outLut,
        float minOutValue, float maxOutValue, boolean inverse, boolean normalize) {

        double nFactor = 20d;
        double outRange = maxOutValue - minOutValue;

        double minValue = 0, maxValue = 0, outRescaleRatio = 1;

        if (normalize) {
            double lowLevel = center - width / 2d;
            double highLevel = center + width / 2d;

            minValue = minOutValue + outRange * Math.log((nFactor / 10d) * (1 + (lowLevel - center) / width));
            maxValue = minOutValue + outRange * Math.log((nFactor / 10d) * (1 + (highLevel - center) / width));

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

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static Object getLutDataArray(LookupTableJAI lookup) {
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

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

    private static void setWindowLevelSequenceLut(float width, float center, LookupTableJAI lookupSequence,
        float minInValue, float maxInValue, Object outLut, float minOutValue, float maxOutValue, boolean inverse) {

        final Object inLutDataArray = getLutDataArray(lookupSequence);

        if (inLutDataArray == null) {
            return;
        }

        // Use this mask to get positive value assuming inLutData value is always unsigned
        final int lutDataValueMask =
            (inLutDataArray instanceof byte[] ? 0x000000FF : (inLutDataArray instanceof short[] ? 0x0000FFFF
                : 0xFFFFFFFF));

        float lowLevel = center - width / 2f;
        float highLevel = center + width / 2f;

        int maxInLutIndex = Array.getLength(inLutDataArray) - 1;

        // Assuming lookupSequence is continuous, values at both ends should reflect maxima and minima
        // This assumption avoid computing min/max by scaning the full table
        // int minLookupValue = lutDataValueMask & Array.getInt(inLutDataArray, 0);
        // int maxLookupValue = lutDataValueMask & Array.getInt(inLutDataArray, lookupRangeSize);
        // int lookupValueRange = Math.abs(maxLookupValue - minLookupValue);

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

        float widthRescaleRatio = maxInLutIndex / width;
        float outRescaleRatio = (maxOutValue - minOutValue) / lookupValueRange;

        for (int i = 0; i < Array.getLength(outLut); i++) {
            int value;
            float inValueRescaled;

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
            value =
                (inValueRoundUp == inValueRoundDown) ? valueDown : //
                    Math.round(valueDown + (inValueRescaled - inValueRoundDown) * (valueUp - valueDown)
                        / (inValueRoundUp - inValueRoundDown));

            value = Math.round(value * outRescaleRatio);
            // }

            value = (int) ((value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value));
            value = (int) (inverse ? (maxOutValue + minOutValue - value) : value);

            if (outLut instanceof byte[]) {
                Array.set(outLut, i, (byte) value);
            } else if (outLut instanceof short[]) {
                Array.set(outLut, i, (short) value);
            }
        }
    }

    static void compareDataLuts(Object outLut, Object outLut2) {

        int countDiff = 0;
        for (int i = 0; i < Array.getLength(outLut); i++) {
            if (outLut instanceof byte[]) {
                int value1 = ((Byte) Array.get(outLut, i)).intValue();
                int value2 = ((Byte) Array.get(outLut2, i)).intValue();
                if (value1 != value2) {
                    countDiff++;
                    System.out.println("value1 /value2 : " + value1 + "/" + value2);
                }
            } else if (outLut instanceof short[]) {
                int value1 = ((Short) Array.get(outLut, i)).intValue();
                int value2 = ((Short) Array.get(outLut2, i)).intValue();
                if (value1 != value2) {
                    countDiff++;
                    System.out.println("value1 /value2 : " + value1 + "/" + value2);
                }
            }
        }
        if (countDiff > 0) {
            System.out.println("countDiff : " + countDiff);
        }

    };

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // TODO make test class instead
    public static void main(String[] args) {
        short sh = -1;
        int in = sh;

        in = 65500;
        sh = (short) in;

        in = sh & 0x0000FFFF;

        byte by = (byte) 200;
        in = by;
        in = by & 0x00FF;

        in = -100;
        by = (byte) in;

        int bitsStored = 16;
        boolean signed = true;
        float outRangeSize = (1 << bitsStored) - 1;
        float maxOutValue = signed ? (1 << (bitsStored - 1)) - 1 : outRangeSize;
        signed = false;
        maxOutValue = signed ? (1 << (bitsStored - 1)) - 1 : outRangeSize;
        bitsStored = 8;
        outRangeSize = (1 << bitsStored) - 1;
        maxOutValue = signed ? (1 << (bitsStored - 1)) - 1 : outRangeSize;
        signed = true;
        maxOutValue = signed ? (1 << (bitsStored - 1)) - 1 : outRangeSize;

        // maxOutValue = (maxOutValue << 2);

        System.out.println(4095);

        int castTest = (int) 2.95f;
        castTest = (int) 2.55f;
        castTest = (int) 9.49f;
        castTest = (int) -7.72f;
        castTest = (int) -2.2;
        castTest = -5;

        double db = Math.ceil(11.1);
        db = Math.ceil(11.9);
        db = Math.floor(12.1);
        db = Math.floor(12.55);

        System.out.println(Math.log(0));
        System.out.println(Math.log(Math.E));
        System.out.println(Math.log10(1));
        System.out.println(Math.log10(10));
        // System.out.println(Math.log1p(1));
        // System.out.println(Math.log1p(Math.E));
        System.out.println(Math.E);
        System.out.println(Math.exp(-2));
        System.out.println();

        double factor = 20;

        double firstVal = 1d / (1d + Math.exp(0.2d * factor * -0.5d));
        double lastVal = 1d / (1d + Math.exp(0.2d * factor * 0.5d));

        double realLast = (lastVal - firstVal) / (firstVal * lastVal);

        System.out.println(firstVal);
        System.out.println(lastVal);
        System.out.println(firstVal * lastVal);
        System.out.println(realLast);

        System.out.println(Math.log10(10000));
        System.out.println(Math.pow(10, 4));
        System.out.println(Math.log(10000));
        System.out.println(Math.exp(9.210340371976184));
        System.out.println(Math.log1p(0));
        System.out.println(Math.log1p(1));
        System.out.println(Math.log1p(10));
        System.out.println(Math.log1p(100));
        System.out.println(Math.log1p(1000));
        System.out.println(Math.log10(1));
        System.out.println(Math.log10(2));
        System.out.println(Math.log10(11));
        System.out.println(Math.log10(101));
        System.out.println(Math.log10(1001));

        System.out.println();

    }

    public static boolean hasPlatformNativeImageioCodecs() {
        return ImageIO.getImageReadersByFormatName("JPEG-LS").hasNext(); //$NON-NLS-1$
    }

    public static float pixel2rescale(HashMap<TagW, Object> tagList, float pixelValue) {
        if (tagList != null) {
            LookupTableJAI lookup = (LookupTableJAI) tagList.get(TagW.ModalityLUTData);
            if (lookup != null) {
                if (pixelValue >= lookup.getOffset() && pixelValue <= lookup.getOffset() + lookup.getNumEntries() - 1) {
                    return lookup.lookup(0, (int) pixelValue);
                }
            } else {
                // value = pixelValue * rescale slope + intercept value
                Float slope = (Float) tagList.get(TagW.RescaleSlope);
                Float intercept = (Float) tagList.get(TagW.RescaleIntercept);
                if (slope != null || intercept != null) {
                    return (pixelValue * (slope == null ? 1.0f : slope) + (intercept == null ? 0.0f : intercept));
                }
            }
        }
        return pixelValue;
    }

    // ////////////////////////////////////////////////////////////////////////////
    // Take from dcm4che3, should be public

    public static int[] lutDescriptor(Attributes ds, int descTag) {
        int[] desc = ds.getInts(descTag);
        if (desc == null) {
            throw new IllegalArgumentException("Missing LUT Descriptor!");
        }
        if (desc.length != 3) {
            throw new IllegalArgumentException("Illegal number of LUT Descriptor values: " + desc.length);
        }
        if (desc[0] < 0) {
            throw new IllegalArgumentException("Illegal LUT Descriptor: len=" + desc[0]);
        }
        int bits = desc[2];
        if (bits != 8 && bits != 16) {
            throw new IllegalArgumentException("Illegal LUT Descriptor: bits=" + bits);
        }
        return desc;
    }

    public static byte[] lutData(Attributes ds, int[] desc, int dataTag, int segmTag) {
        int len = desc[0] == 0 ? 0x10000 : desc[0];
        int bits = desc[2];
        byte[] data = ds.getSafeBytes(dataTag);
        if (data == null) {
            int[] segm = ds.getInts(segmTag);
            if (segm == null) {
                throw new IllegalArgumentException("Missing LUT Data!");
            }
            if (bits == 8) {
                throw new IllegalArgumentException("Segmented LUT Data with LUT Descriptor: bits=8");
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
        throw new IllegalArgumentException("Running out of data inflating segmented LUT");
    }

    private static int linearSegment(int y1, byte[] out, int x, int n) {
        if (x == 0) {
            throw new IllegalArgumentException("Linear segment cannot be the first segment");
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
        throw new IllegalArgumentException("Number of entries in inflated segmented LUT exceeds specified value: "
            + descLen + " in LUT Descriptor");
    }

    private static void lutLengthMismatch(int lutLen, int descLen) {
        throw new IllegalArgumentException("Number of actual LUT entries: " + lutLen + " mismatch specified value: "
            + descLen + " in LUT Descriptor");
    }

    private static void illegalOpcode(int op, int i) {
        throw new IllegalArgumentException("illegal op code:" + op + ", index:" + i);
    }
}
