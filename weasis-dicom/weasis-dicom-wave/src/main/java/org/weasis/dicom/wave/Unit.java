package org.weasis.dicom.wave;

public class Unit {

    private final String fullName;
    private final String abbreviation;
    private final double scalingFactor;

    public Unit(String fullName, String abbreviation, double scalingFactor) {
        this.fullName = fullName;
        this.abbreviation = abbreviation;
        this.scalingFactor = scalingFactor;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public double getScalingFactor() {
        return scalingFactor;
    }
}
