package org.weasis.core.api.image.measure;

/**
 * @author Nicolas Roduit
 */
public class MeasurementsAdapter {
    private final double calibrationRatio;
    private final double calibrationRatioForPos;
    private final boolean upYAxis;
    private final int offsetX;
    private final int offsetY;
    private final int imageHeight;
    private final String unit;

    public MeasurementsAdapter(double calibrationRatio, boolean keepPositionUncalibrated, int offsetX, int offsetY,
        boolean upYAxis, int imageHeight, String unit) {
        this.offsetY = offsetY;
        this.offsetX = offsetX;
        this.upYAxis = upYAxis;
        this.calibrationRatioForPos = keepPositionUncalibrated ? 1.0d : calibrationRatio;
        this.calibrationRatio = calibrationRatio;
        this.imageHeight = imageHeight - 1;
        this.unit = unit;
    }

    public double getCalibrationRatio() {
        return calibrationRatio;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public boolean isUpYAxis() {
        return upYAxis;
    }

    public String getUnit() {
        return unit;
    }

    public double getXUncalibratedValue(double xVal) {
        return xVal + offsetX;
    }

    public double getYUncalibratedValue(double yVal) {
        if (upYAxis) {
            yVal = imageHeight - yVal;
        }
        return yVal + offsetY;
    }

    public double getXCalibratedValue(double xVal) {
        return calibrationRatioForPos * (xVal + offsetX);
    }

    public double getYCalibratedValue(double yVal) {
        if (upYAxis) {
            yVal = imageHeight - yVal;
        }
        return calibrationRatioForPos * (yVal + offsetY);
    }

}
