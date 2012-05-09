package org.weasis.dicom.codec.utils;

public class LutParameters {
    private final float intercept;
    private final float slope;
    private final int minValue;
    private final int maxValue;
    private final int bitsStored;
    private final boolean signed;
    private final boolean inverse;

    public LutParameters(float intercept, float slope, int minValue, int maxValue, int bitsStored, boolean signed,
        boolean inverse) {
        this.intercept = intercept;
        this.slope = slope;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.bitsStored = bitsStored;
        this.signed = signed;
        this.inverse = inverse;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LutParameters) {
            LutParameters p = (LutParameters) obj;
            return p.intercept == intercept && p.slope == slope && p.minValue == minValue && p.maxValue == maxValue
                && p.bitsStored == bitsStored && p.signed == signed && p.inverse == inverse;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (signed ? 2 : 3) + (inverse ? 4 : 5) + bitsStored * 7 + minValue * 13 + maxValue * 19
            + Float.floatToIntBits(intercept) * 25 + Float.floatToIntBits(intercept) * 31;
    }

    public float getIntercept() {
        return intercept;
    }

    public float getSlope() {
        return slope;
    }

    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public int getBitsStored() {
        return bitsStored;
    }

    public boolean isSigned() {
        return signed;
    }

    public boolean isInverse() {
        return inverse;
    }

}