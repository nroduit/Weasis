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
package org.weasis.acquire.explorer;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Optional;

import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.Copyable;

import com.privatejgoodies.common.base.Objects;

/**
 * Store all modifiables values. Enable to compare two objects for dirty check.
 *
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-18 - ylar - Creation
 */
public class AcquireImageValues implements Copyable<AcquireImageValues> {
    private Rectangle cropZone = null;
    private Point layerOffset = null;
    private int orientation = 0;
    private int rotation = 0;
    private int brightness = 0;
    private int contrast = 100;
    private boolean autoLevel = false;
    private boolean flip = false;
    private Unit calibrationUnit = Unit.PIXEL;
    private double calibrationRatio = 1.0;
    private Double ratio = null;

    public AcquireImageValues() {
        super();
    }

    public AcquireImageValues(AcquireImageValues object) {
        setCropZone(Optional.ofNullable(object.cropZone).map(Rectangle::getBounds).orElse(null));
        setLayerOffset(Optional.ofNullable(object.layerOffset).map(Point::getLocation).orElse(null));
        setOrientation(object.orientation);
        setRotation(object.rotation);
        setBrightness(object.brightness);
        setContrast(object.contrast);
        setAutoLevel(object.autoLevel);
        setFlip(object.flip);
        setCalibrationUnit(object.calibrationUnit);
        setCalibrationRatio(object.calibrationRatio);
        setRatio(object.ratio);
    }

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public Rectangle getCropZone() {
        return cropZone;
    }

    public void setCropZone(Rectangle cropZone) {
        this.cropZone = cropZone;
    }

    public Point getLayerOffset() {
        return layerOffset;
    }

    public void setLayerOffset(Point layerOffset) {
        this.layerOffset = layerOffset;
    }

    public int getBrightness() {
        return brightness;
    }

    public void setBrightness(int brightness) {
        this.brightness = brightness;
    }

    public int getContrast() {
        return contrast;
    }

    public void setContrast(int contrast) {
        this.contrast = contrast;
    }

    public int getFullRotation() {
        return rotation + orientation;
    }

    public Unit getCalibrationUnit() {
        return calibrationUnit;
    }

    public void setCalibrationUnit(Unit calibrationUnit) {
        this.calibrationUnit = calibrationUnit;
    }

    public double getCalibrationRatio() {
        return calibrationRatio;
    }

    public void setCalibrationRatio(double calibrationRatio) {
        this.calibrationRatio = calibrationRatio;
    }

    @Override
    public AcquireImageValues copy() {
        return new AcquireImageValues(this);
    }

    public boolean isAutoLevel() {
        return autoLevel;
    }

    public void setAutoLevel(boolean autoLevel) {
        this.autoLevel = autoLevel;
    }

    public void toggleAutoLevel() {
        this.autoLevel = !this.autoLevel;
    }

    public boolean isFlip() {
        return flip;
    }

    public void setFlip(boolean flip) {
        this.flip = flip;
    }

    public void toggleFlip() {
        this.flip = !this.flip;
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    public Double getRatio() {
        return ratio;
    }

    public void setRatio(Double ratio) {
        this.ratio = ratio;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (autoLevel ? 1231 : 1237);
        result = prime * result + brightness;
        long temp;
        temp = Double.doubleToLongBits(calibrationRatio);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + ((calibrationUnit == null) ? 0 : calibrationUnit.hashCode());
        result = prime * result + contrast;
        result = prime * result + ((cropZone == null) ? 0 : cropZone.hashCode());
        result = prime * result + (flip ? 1231 : 1237);
        result = prime * result + ((layerOffset == null) ? 0 : layerOffset.hashCode());
        result = prime * result + orientation;
        result = prime * result + rotation;
        result = prime * result + ((ratio == null) ? 0 : ratio.hashCode());

        return result;
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
        AcquireImageValues other = (AcquireImageValues) obj;
        if (autoLevel != other.autoLevel) {
            return false;
        }
        if (brightness != other.brightness) {
            return false;
        }
        if (MathUtil.isDifferent(calibrationRatio, other.calibrationRatio)) {
            return false;
        }
        if (calibrationUnit != other.calibrationUnit) {
            return false;
        }
        if (contrast != other.contrast) {
            return false;
        }
        if (cropZone == null) {
            if (other.cropZone != null) {
                return false;
            }
        } else if (!cropZone.equals(other.cropZone)) {
            return false;
        }
        if (flip != other.flip) {
            return false;
        }
        if (layerOffset == null) {
            if (other.layerOffset != null) {
                return false;
            }
        } else if (!layerOffset.equals(other.layerOffset)) {
            return false;
        }
        if (orientation != other.orientation) {
            return false;
        }
        if (rotation != other.rotation) {
            return false;
        }
        return Objects.equals(ratio, other.ratio);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("AcquireImageValues[cropZone=>"); //$NON-NLS-1$
        builder.append(cropZone);
        builder.append("layerOffset=>"); //$NON-NLS-1$
        builder.append(layerOffset);
        builder.append("orientation=>"); //$NON-NLS-1$
        builder.append(orientation);
        builder.append("rotation=>"); //$NON-NLS-1$
        builder.append(rotation);
        builder.append("brightness=>"); //$NON-NLS-1$
        builder.append(brightness);
        builder.append("contrast=>"); //$NON-NLS-1$
        builder.append(contrast);
        builder.append("autoLevel=>"); //$NON-NLS-1$
        builder.append(autoLevel);
        builder.append("flip=>"); //$NON-NLS-1$
        builder.append(flip);
        builder.append("calibrationUnit=>"); //$NON-NLS-1$
        builder.append(calibrationUnit);
        builder.append("calibrationRatio=>"); //$NON-NLS-1$
        builder.append(calibrationRatio);
        builder.append("ratio=>"); //$NON-NLS-1$
        builder.append(ratio);
        return builder.toString();
    }

}
