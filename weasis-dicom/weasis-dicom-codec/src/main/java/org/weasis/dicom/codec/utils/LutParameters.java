package org.weasis.dicom.codec.utils;

public class LutParameters {
    private final float intercept;
    private final float slope;
    private final Integer paddingMinValue;
    private final Integer paddingMaxValue;
    private final int bitsStored;
    private final boolean signed;
    private final boolean applyPadding;
    private final boolean outputSigned;

    public LutParameters(float intercept, float slope, boolean applyPadding, Integer paddingMinValue,
        Integer paddingMaxValue, int bitsStored, boolean signed, boolean outputSigned) {
        this.intercept = intercept;
        this.slope = slope;
        this.paddingMinValue = paddingMinValue;
        this.paddingMaxValue = paddingMaxValue;
        this.bitsStored = bitsStored;
        this.signed = signed;
        this.applyPadding = applyPadding;
        this.outputSigned = outputSigned;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LutParameters) {
            LutParameters p = (LutParameters) obj;
            return p.intercept == intercept && p.slope == slope && p.applyPadding == applyPadding
                && p.paddingMinValue == paddingMinValue && p.paddingMaxValue == paddingMaxValue
                && p.bitsStored == bitsStored && p.signed == signed && p.isOutputSigned() == outputSigned;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash =
            (signed ? 2 : 3) + (applyPadding ? 4 : 5) + (outputSigned ? 6 : 7) + bitsStored * 9
                + Float.floatToIntBits(intercept) * 25 + Float.floatToIntBits(intercept) * 31;
        if (paddingMinValue != null) {
            hash += paddingMinValue * 14;
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

    public boolean isApplyPadding() {
        return applyPadding;
    }

    public boolean isOutputSigned() {
        return outputSigned;
    }

}