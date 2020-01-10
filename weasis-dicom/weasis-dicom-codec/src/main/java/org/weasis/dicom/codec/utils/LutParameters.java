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

public class LutParameters {
    private final double intercept;
    private final double slope;
    private final Integer paddingMinValue;
    private final Integer paddingMaxValue;
    private final int bitsStored;
    private final boolean signed;
    private final boolean applyPadding;
    private final boolean outputSigned;
    private final int bitsOutput;
    private final boolean inversePaddingMLUT;

    public LutParameters(double intercept, double slope, boolean applyPadding, Integer paddingMinValue,
        Integer paddingMaxValue, int bitsStored, boolean signed, boolean outputSigned, int bitsOutput,
        boolean inversePaddingMLUT) {
        this.intercept = intercept;
        this.slope = slope;
        this.paddingMinValue = paddingMinValue;
        this.paddingMaxValue = paddingMaxValue;
        this.bitsStored = bitsStored;
        this.signed = signed;
        this.applyPadding = applyPadding;
        this.outputSigned = outputSigned;
        this.bitsOutput = bitsOutput;
        this.inversePaddingMLUT = inversePaddingMLUT;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        LutParameters other = (LutParameters) obj;
        if (applyPadding != other.applyPadding) {
            return false;
        }
        if (bitsOutput != other.bitsOutput) {
            return false;
        }
        if (bitsStored != other.bitsStored) {
            return false;
        }
        if (Double.doubleToLongBits(intercept) != Double.doubleToLongBits(other.intercept)) {
            return false;
        }
        if (inversePaddingMLUT != other.inversePaddingMLUT) {
            return false;
        }
        if (outputSigned != other.outputSigned) {
            return false;
        }
        if (paddingMaxValue == null) {
            if (other.paddingMaxValue != null) {
                return false;
            }
        } else if (!paddingMaxValue.equals(other.paddingMaxValue)) {
            return false;
        }
        if (paddingMinValue == null) {
            if (other.paddingMinValue != null) {
                return false;
            }
        } else if (!paddingMinValue.equals(other.paddingMinValue)) {
            return false;
        }
        if (signed != other.signed) {
            return false;
        }
        if (Double.doubleToLongBits(slope) != Double.doubleToLongBits(other.slope)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (applyPadding ? 1231 : 1237);
        result = prime * result + bitsOutput;
        result = prime * result + bitsStored;
        long temp;
        temp = Double.doubleToLongBits(intercept);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + (inversePaddingMLUT ? 1231 : 1237);
        result = prime * result + (outputSigned ? 1231 : 1237);
        result = prime * result + ((paddingMaxValue == null) ? 0 : paddingMaxValue.hashCode());
        result = prime * result + ((paddingMinValue == null) ? 0 : paddingMinValue.hashCode());
        result = prime * result + (signed ? 1231 : 1237);
        temp = Double.doubleToLongBits(slope);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    public double getIntercept() {
        return intercept;
    }

    public double getSlope() {
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

    public int getBitsOutput() {
        return bitsOutput;
    }

    public boolean isInversePaddingMLUT() {
        return inversePaddingMLUT;
    }

}