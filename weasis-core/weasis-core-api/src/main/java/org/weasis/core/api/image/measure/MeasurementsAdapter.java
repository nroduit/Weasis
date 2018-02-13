/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.image.measure;

public class MeasurementsAdapter {
    private final double calibRatio;
    private final boolean upYAxis;
    private final int offsetX;
    private final int offsetY;
    private final int imageHeight;
    private final String unit;

    public MeasurementsAdapter(double calibRatio, int offsetX, int offsetY, boolean upYAxis, int imageHeight,
        String unit) {
        this.offsetY = offsetY;
        this.offsetX = offsetX;
        this.upYAxis = upYAxis;
        this.calibRatio = calibRatio;
        this.imageHeight = imageHeight - 1;
        this.unit = unit;
    }

    public double getCalibRatio() {
        return calibRatio;
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
        return (upYAxis ? imageHeight - yVal : yVal) + offsetY;
    }

    public double getXCalibratedValue(double xVal) {
        return calibRatio * (xVal + offsetX);
    }

    public double getYCalibratedValue(double yVal) {
        return calibRatio * ((upYAxis ? imageHeight - yVal : yVal) + offsetY);
    }

}
