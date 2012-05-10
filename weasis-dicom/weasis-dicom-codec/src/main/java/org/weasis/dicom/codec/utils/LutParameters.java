package org.weasis.dicom.codec.utils;

public class LutParameters {
    private final float intercept;
    private final float slope;
    private final Integer paddingMinValue;
    private final Integer paddingMaxValue;
    private final int bitsStored;
    private final boolean signed;
    private final boolean inverse;
    private final boolean applyPadding;

    public LutParameters(float intercept, float slope, boolean applyPadding, Integer paddingMinValue,
        Integer paddingMaxValue, int bitsStored, boolean signed, boolean inverse) {
        this.intercept = intercept;
        this.slope = slope;
        this.paddingMinValue = paddingMinValue;
        this.paddingMaxValue = paddingMaxValue;
        this.bitsStored = bitsStored;
        this.signed = signed;
        this.inverse = inverse;
        this.applyPadding = applyPadding;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LutParameters) {
            LutParameters p = (LutParameters) obj;
            return p.intercept == intercept && p.slope == slope && p.applyPadding == applyPadding
                && p.paddingMinValue == paddingMinValue && p.paddingMaxValue == paddingMaxValue
                && p.bitsStored == bitsStored && p.signed == signed && p.inverse == inverse;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash =
            (signed ? 2 : 3) + (inverse ? 4 : 5) + (inverse ? 6 : 7) + bitsStored * 9 + Float.floatToIntBits(intercept)
                * 25 + Float.floatToIntBits(intercept) * 31;
        if (paddingMinValue != null) {
            hash += paddingMinValue * 13;
        }
        if (paddingMaxValue != null) {
            hash += paddingMaxValue * 19;
        }
        return hash;
    }

    public float getIntercept() {
        return intercept;
    }

    public float getSlope() {
        return slope;
    }

    public Integer getPaddingMinValue() {
        return paddingMinValue;
    }

    public Integer getPaddingMaxValue() {
        return paddingMaxValue;
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