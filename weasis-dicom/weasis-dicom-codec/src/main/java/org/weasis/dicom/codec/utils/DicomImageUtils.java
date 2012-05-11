package org.weasis.dicom.codec.utils;

import java.awt.image.DataBuffer;
import java.lang.reflect.Array;

import javax.media.jai.LookupTableJAI;

import org.weasis.core.api.image.LutShape;

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

        // int outRangeSize = (1 << bitsStored) - 1;
        // float maxOutValue = isSigned ? (1 << (bitsStored - 1)) - 1 : outRangeSize;
        // float minOutValue = isSigned ? -(maxOutValue + 1) : 0;

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

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // public static LookupTableJAI createRescaleRampLut(LutParameters params) {
    // float intercept = params.getIntercept();
    // float slope = params.getSlope();
    // int bitsStored = params.getBitsStored();
    // boolean isSigned = params.isSigned();
    // boolean inverse = params.isInverse();
    //
    // bitsStored = (bitsStored > 16) ? bitsStored = 16 : ((bitsStored < 1) ? 1 : bitsStored);
    // // int outRangeSize = (1 << bitsStored) - 1;
    // // float maxOutValue = isSigned ? (1 << (bitsStored - 1)) - 1 : outRangeSize;
    // // float minOutValue = isSigned ? -(maxOutValue + 1) : 0;
    //
    // // TODO - use bitsAllocated as a parameter instead of extrapolated one
    // int bitsAllocated = (bitsStored <= 8) ? 8 : 16;
    // int outRangeSize = (1 << bitsAllocated) - 1;
    // float maxOutValue = isSigned ? (1 << (bitsAllocated - 1)) - 1 : outRangeSize;
    // float minOutValue = isSigned ? -(maxOutValue + 1) : 0;
    //
    // // float minInValue = Math.min(maxValue, minValue);
    // // float maxInValue = Math.max(maxValue, minValue);
    //
    // // int numEntries = (int) (maxInValue - minInValue + 1);
    // int numEntries = 1 << bitsStored;
    // Object outLut = (bitsStored <= 8) ? new byte[numEntries] : new short[numEntries];
    //
    // for (int i = 0; i < numEntries; i++) {
    // int value = Math.round(i * slope + intercept);
    //
    // value = (int) ((value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value));
    // value = (int) (inverse ? (maxOutValue + minOutValue - value) : value);
    //
    // if (outLut instanceof byte[]) {
    // Array.set(outLut, i, (byte) value);
    // } else if (outLut instanceof short[]) {
    // Array.set(outLut, i, (short) value);
    // }
    // }
    //
    // return (outLut instanceof byte[]) ? new LookupTableJAI((byte[]) outLut, 0) : //
    // new LookupTableJAI((short[]) outLut, 0, !isSigned);
    // }

    /**
     * @return LookupTable with full range of possible input entries according to bitStored.<br>
     *         Note that isSigned is relevant for both input and output values
     */

    public static LookupTableJAI createRescaleRampLut(LutParameters params) {
        return createRescaleRampLut(params.getIntercept(), params.getSlope(), params.getBitsStored(),
            params.isSigned(), params.isInverse());
    }

    public static LookupTableJAI createRescaleRampLut(float intercept, float slope, int bitsStored, boolean isSigned,
        boolean inverse) {

        return createRescaleRampLut(intercept, slope, Integer.MIN_VALUE, Integer.MAX_VALUE, bitsStored, isSigned,
            inverse);
    }

    public static LookupTableJAI createRescaleRampLut(float intercept, float slope, int minValue, int maxValue,
        int bitsStored, boolean isSigned, boolean inverse) {

        bitsStored = (bitsStored > 16) ? bitsStored = 16 : ((bitsStored < 1) ? 1 : bitsStored);

        int bitsAllocated = (bitsStored <= 8) ? 8 : 16;
        int outRangeSize = (1 << bitsAllocated) - 1;
        int maxOutValue = isSigned ? (1 << (bitsAllocated - 1)) - 1 : outRangeSize;
        int minOutValue = isSigned ? -(maxOutValue + 1) : 0;

        int maxInputRangeSize = (1 << bitsStored) - 1;
        int maxInValue = isSigned ? (1 << (bitsStored - 1)) - 1 : maxInputRangeSize;
        int minInValue = isSigned ? -(maxInValue + 1) : 0;

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
            new LookupTableJAI((short[]) outLut, minInValue, !isSigned);
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
            int value = Math.round((i + minInValue) * slope + intercept);

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

    private static void setWindowLevelSigmoidLut(float window, float level, float minInValue, Object outLut,
        float minOutValue, float maxOutValue, boolean inverse) {

        float nFactor = -20; // factor defined by default in Dicom standard (-20)

        for (int i = 0; i < Array.getLength(outLut); i++) {
            int value =
                Math.round((maxOutValue - minOutValue)
                    / (1 + (float) Math.exp((2f * nFactor / 10f) * ((i + minInValue) - level) / window)));

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

    private static void setWindowLevelExponentialLut(float window, float level, float minInValue, Object outLut,
        float minOutValue, float maxOutValue, boolean inverse) {

        float nFactor = 20;

        for (int i = 0; i < Array.getLength(outLut); i++) {
            int value =
                1 + Math.round((maxOutValue - minOutValue)
                    * (float) Math.expm1((nFactor / 10f) * ((i + minInValue) - (level - window / 2f)) / window));

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

    private static void setWindowLevelLogarithmicLut(float window, float level, float minInValue, Object outLut,
        float minOutValue, float maxOutValue, boolean inverse) {

        float nFactor = 20;

        for (int i = 0; i < Array.getLength(outLut); i++) {
            int value =
                Math.round((maxOutValue - minOutValue)
                    * (float) Math.log1p((nFactor / 10f) * ((i + minInValue) - (level - window / 2f)) / window));

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
     * @return a normalized LookupTableJAI based upon given lutSequence <br>
     */

    // TODO should use DataBuffer Interface instead of LookupTableJAI object

    private static void setWindowLevelSequenceLut(float window, float level, LookupTableJAI lookupSequence,
        float minInValue, float maxInValue, Object outLut, float minOutValue, float maxOutValue, boolean inverse) {

        final Object inLutDataArray = getLutDataArray(lookupSequence);

        if (inLutDataArray == null) {
            return;
        }

        // Use this mask to get positive value assuming inLutData value is always unsigned
        final int lutDataValueMask =
            (inLutDataArray instanceof byte[] ? 0x000000FF : (inLutDataArray instanceof short[] ? 0x0000FFFF
                : 0xFFFFFFFF));

        float lowLevel = level - window / 2f;
        float highLevel = level + window / 2f;

        int lookupRangeSize = Array.getLength(inLutDataArray) - 1;

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

        float widthRescaleRatio = lookupRangeSize / window;
        float outRescaleRatio = maxOutValue / lookupValueRange;

        for (int i = 0; i < Array.getLength(outLut); i++) {
            int value;
            float inValueRescaled;

            if ((i + minInValue) <= lowLevel) {
                inValueRescaled = 0;
            } else if ((i + minInValue) > highLevel) {
                inValueRescaled = lookupRangeSize;
            } else {
                inValueRescaled = (i + minInValue - lowLevel) * widthRescaleRatio;
            }

            int inValueRoundDown = Math.max(0, (int) Math.floor(inValueRescaled));
            int inValueRoundUp = Math.min(lookupRangeSize, (int) Math.ceil(inValueRescaled));

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

    @Deprecated
    protected static void setWindowLevelSequenceLutOld(float window, float level, LookupTableJAI lutSequence,
        float minInValue, Object outLut, float minOutValue, float maxOutValue, boolean inverse) {

        if (lutSequence == null) {
            return;
        }

        Object inLut;

        if (lutSequence.getDataType() == DataBuffer.TYPE_BYTE) {
            inLut = lutSequence.getByteData(0);
        } else if (lutSequence.getDataType() <= DataBuffer.TYPE_SHORT) {
            inLut = lutSequence.getShortData(0);
        } else {
            return;
        }

        float levelMin = level - window / 2f;
        float levelMax = level + window / 2f;

        int minValueLookup = lutSequence.getOffset();
        int maxValueLookup = lutSequence.getOffset() + Array.getLength(inLut) - 1;

        float inLutLevelMin = (levelMin > maxValueLookup) ? maxValueLookup : Math.max(levelMin, minValueLookup);
        float inLutLevelMax = (levelMax < minValueLookup) ? minValueLookup : Math.min(levelMax, maxValueLookup);

        float slope = (maxOutValue - minOutValue) / window;
        float intercept = maxOutValue - slope * (level + (window / 2f));

        int minOutLutValue = (int) (inLutLevelMin * slope + intercept);
        int maxOutLutValue = (int) (inLutLevelMax * slope + intercept);

        float inLutLevelMinValue = Array.getFloat(inLut, (int) inLutLevelMin - lutSequence.getOffset());
        float inLutLevelMaxValue = Array.getFloat(inLut, (int) inLutLevelMax - lutSequence.getOffset());

        slope = (maxOutLutValue - minOutLutValue) / (inLutLevelMaxValue - inLutLevelMinValue);
        intercept = maxOutLutValue - slope * inLutLevelMaxValue;

        for (int i = 0; i < Array.getLength(outLut); i++) {
            int value;

            if ((i + minInValue) < inLutLevelMin) {
                value = minOutLutValue;
            } else if ((i + minInValue) > inLutLevelMax) {
                value = maxOutLutValue;
            } else {
                float inLutValue = Array.getFloat(inLut, (int) (i + minInValue) - lutSequence.getOffset());
                value = Math.round(inLutValue * slope + intercept);
            }
            value = (int) (inverse ? (maxOutValue + minOutValue - value) : value);

            if (outLut instanceof byte[]) {
                Array.set(outLut, i, (byte) value);
            } else {
                Array.set(outLut, i, (short) value);
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // public static LookupTable createRescaleRampLut(float intercept, float slope, int minValue, int maxValue,
    // int bitsStored, boolean signed, boolean inverse) {
    //
    // bitsStored = (bitsStored > 16) ? bitsStored = 16 : ((bitsStored < 1) ? 1 : bitsStored);
    //
    // // when bitsStored=8 bits => outRangeSize=255 or bitsStored=16 bits => outRangeSize=65535 ...
    // int outRangeSize = (1 << bitsStored) - 1;
    // // when bitsStored=8 bits signed => maxOutValue=127 or bitsStored=16 bits signed => maxOutValue= 32767 ...
    // int maxOutValue = signed ? (1 << (bitsStored - 1)) - 1 : outRangeSize;
    // // when bitsStored=8 bits signed => minOutValue=-128 or bitsStored=16 bits signed => minOutValue= -32768 ...
    // int minOutValue = signed ? -(maxOutValue + 1) : 0;
    //
    // minValue = Math.min(maxValue, minValue);
    // maxValue = Math.max(maxValue, minValue);
    //
    // int numEntries = Math.abs(maxValue - minValue) + 1;
    // Object outLut = (bitsStored <= 8) ? new byte[numEntries] : new short[numEntries];
    //
    // for (int i = 0; i < numEntries; i++) {
    // int value = Math.round((i + minValue) * slope + intercept);
    // value = (value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value);
    //
    // value = inverse ? (outRangeSize - (value - minOutValue) + minOutValue) : value;
    // // this trick do the correct computation when signed is true and then minOutValue is negative
    //
    // if (bitsStored <= 8) {
    // Array.set(outLut, i, (byte) value);
    // } else {
    // Array.set(outLut, i, (short) value);
    // }
    // }
    //
    // if (bitsStored <= 8) {
    // return new ByteLookupTable(minValue, (byte[]) outLut);
    // } else {
    // return new ShortLookupTable(minValue, (short[]) outLut);
    // }
    // }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // protected static LookupTable createWindowLevelRampLut(float window, float level, int minValue, int maxValue,
    // int bitsStored, boolean signed, boolean inverse) {
    //
    // bitsStored = (bitsStored > 16) ? bitsStored = 16 : ((bitsStored < 1) ? 1 : bitsStored);
    // window = (window < 1f) ? 1f : window;
    //
    // // when bitsStored=8 bits => outRangeSize=255 or bitsStored=16 bits => outRangeSize=65535 ...
    // float outRangeSize = (1 << bitsStored) - 1;
    // // when bitsStored=8 bits signed => maxOutValue=127 or bitsStored=16 bits signed => maxOutValue= 32767 ...
    // float maxOutValue = signed ? (1 << (bitsStored - 1)) - 1 : outRangeSize;
    //
    // float slope = outRangeSize / window;
    // float intercept = maxOutValue - slope * (level + (window / 2f));
    //
    // return createRescaleRampLut(intercept, slope, minValue, maxValue, bitsStored, signed, inverse);
    //
    // /*
    // * Algo : with minimum and maximum image values do a linear mapping of (min, max) to (0, 255). From Euclidean
    // * Geometry we have two data points [(min, 0) and (max, 255)] and a straight line,thus (from y = mx + b)
    // */
    // }

    // public static LookupTable createWindowLevelLinearLut(float window, float level, int minValue, int maxValue,
    // int bitsStored, boolean signed, boolean inverse) {
    //
    // bitsStored = (bitsStored > 16) ? bitsStored = 16 : ((bitsStored < 1) ? 1 : bitsStored);
    // window = (window < 1f) ? 1f : window;
    //
    // // when bitsStored=8 bits => outRangeSize=255 or bitsStored=16 bits => outRangeSize=65535 ...
    // int outRangeSize = (1 << bitsStored) - 1;
    // // when bitsStored=8 bits signed => maxOutValue=127 or bitsStored=16 bits signed => maxOutValue= 32767 ...
    // int maxOutValue = signed ? (1 << (bitsStored - 1)) - 1 : outRangeSize;
    // // when bitsStored=8 bits signed => minOutValue=-128 or bitsStored=16 bits signed => minOutValue= -32768 ...
    // int minOutValue = signed ? -(maxOutValue + 1) : 0;
    //
    // minValue = Math.min(maxValue, minValue);
    // maxValue = Math.max(maxValue, minValue);
    //
    // int numEntries = Math.abs(maxValue - minValue) + 1;
    //
    // Object outLut = (bitsStored <= 8) ? new byte[numEntries] : new short[numEntries];
    //
    // float lowLevel = (level - 0.5f) - (window - 1f) / 2f;
    // float highLevel = (level - 0.5f) + (window - 1f) / 2f;
    //
    // for (int i = 0; i < numEntries; i++) {
    // int value;
    //
    // // Below is pseudo code defined in Dicom Standard 2011 - PS 3.3 § C.11.2 VOI LUT Module
    // if ((i + minValue) <= lowLevel) {
    // value = minOutValue;
    // } else if ((i + minValue) > highLevel) {
    // value = maxOutValue;
    // } else {
    // value =
    // (int) ((((i + minValue) - (level - 0.5f)) / (window - 1f) + 0.5f) * (outRangeSize) + minOutValue);
    // }
    //
    // value = (value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value);
    // value = inverse ? (outRangeSize - (value - minOutValue) + minOutValue) : value;
    // // this trick do the correct computation when signed is true and then minOutValue is negative
    //
    // if (bitsStored <= 8) {
    // Array.set(outLut, i, (byte) value);
    // } else {
    // Array.set(outLut, i, (short) value);
    // }
    // }
    //
    // if (bitsStored <= 8) {
    // return new ByteLookupTable(minValue, (byte[]) outLut);
    // } else {
    // return new ShortLookupTable(minValue, (short[]) outLut);
    // }
    // }

    // public static LookupTable createWindowLevelLogarithmicLut(float window, float level, int minValue, int maxValue,
    // int bitsStored, boolean signed, boolean inverse) {
    //
    // bitsStored = (bitsStored > 16) ? bitsStored = 16 : ((bitsStored < 1) ? 1 : bitsStored);
    // window = (window < 1f) ? 1f : window;
    //
    // // when bitsStored=8 bits => outRangeSize=255 or bitsStored=16 bits => outRangeSize=65535 ...
    // int outRangeSize = (1 << bitsStored) - 1;
    // // when bitsStored=8 bits signed => maxOutValue=127 or bitsStored=16 bits signed => maxOutValue= 32767 ...
    // int maxOutValue = signed ? (1 << (bitsStored - 1)) - 1 : outRangeSize;
    // // when bitsStored=8 bits signed => minOutValue=-128 or bitsStored=16 bits signed => minOutValue= -32768 ...
    // int minOutValue = signed ? -(maxOutValue + 1) : 0;
    //
    // minValue = Math.min(maxValue, minValue);
    // maxValue = Math.max(maxValue, minValue);
    //
    // int numEntries = Math.abs(maxValue - minValue) + 1;
    //
    // Object outLut = (bitsStored <= 8) ? new byte[numEntries] : new short[numEntries];
    //
    // float nFactor = 20; // factor defined by default in Dicom standard ???? unknown
    //
    // for (int i = 0; i < numEntries; i++) {
    //
    // int value =
    // Math.round(outRangeSize
    // * (float) Math.log1p((nFactor / 10d) * ((i + minValue) - (level - window / 2f)) / window));
    //
    // value = (value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value);
    // value = inverse ? (outRangeSize - (value - minOutValue) + minOutValue) : value;
    // // this trick do the correct computation when signed is true and then minOutValue is negative
    //
    // if (bitsStored <= 8) {
    // Array.set(outLut, i, (byte) value);
    // } else {
    // Array.set(outLut, i, (short) value);
    // }
    // }
    //
    // if (bitsStored <= 8) {
    // return new ByteLookupTable(minValue, (byte[]) outLut);
    // } else {
    // return new ShortLookupTable(minValue, (short[]) outLut);
    // }
    // }

    // public static LookupTable createWindowLevelExponentialLut(float window, float level, int minValue, int maxValue,
    // int bitsStored, boolean signed, boolean inverse) {
    //
    // bitsStored = (bitsStored > 16) ? bitsStored = 16 : ((bitsStored < 1) ? 1 : bitsStored);
    // window = (window < 1f) ? 1f : window;
    //
    // // when bitsStored=8 bits => outRangeSize=255 or bitsStored=16 bits => outRangeSize=65535 ...
    // int outRangeSize = (1 << bitsStored) - 1;
    // // when bitsStored=8 bits signed => maxOutValue=127 or bitsStored=16 bits signed => maxOutValue= 32767 ...
    // int maxOutValue = signed ? (1 << (bitsStored - 1)) - 1 : outRangeSize;
    // // when bitsStored=8 bits signed => minOutValue=-128 or bitsStored=16 bits signed => minOutValue= -32768 ...
    // int minOutValue = signed ? -(maxOutValue + 1) : 0;
    //
    // minValue = Math.min(maxValue, minValue);
    // maxValue = Math.max(maxValue, minValue);
    //
    // int numEntries = Math.abs(maxValue - minValue) + 1;
    //
    // Object outLut = (bitsStored <= 8) ? new byte[numEntries] : new short[numEntries];
    //
    // float nFactor = 20; // factor defined by default in Dicom standard ???? unknown
    //
    // for (int i = 0; i < numEntries; i++) {
    //
    // int value =
    // Math.round(outRangeSize
    // * (float) Math.exp((nFactor / 10d) * ((i + minValue) - (level - window / 2f)) / window));
    //
    // value = (value >= maxOutValue) ? maxOutValue : ((value <= minOutValue) ? minOutValue : value);
    // value = inverse ? (outRangeSize - (value - minOutValue) + minOutValue) : value;
    // // this trick do the correct computation when signed is true and then minOutValue is negative
    //
    // if (bitsStored <= 8) {
    // Array.set(outLut, i, (byte) value);
    // } else {
    // Array.set(outLut, i, (short) value);
    // }
    // }
    //
    // if (bitsStored <= 8) {
    // return new ByteLookupTable(minValue, (byte[]) outLut);
    // } else {
    // return new ShortLookupTable(minValue, (short[]) outLut);
    // }
    // }

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
}
