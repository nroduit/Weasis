/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Objects;
import java.util.Optional;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.Copyable;

/**
 * Store all modifiable values. Enable to compare two objects for dirty check.
 *
 * @author Yannick LARVOR
 * @since 2.5.0
 */
public class AcquireImageValues implements Copyable<AcquireImageValues> {
  private Rectangle cropZone = null;
  private Point layerOffset = null;
  private int orientation = 0;
  private int rotation = 0;
  private int brightness = 0;
  private int contrast = 100;
  private boolean autoLevel = false;
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AcquireImageValues that = (AcquireImageValues) o;
    return orientation == that.orientation
        && rotation == that.rotation
        && brightness == that.brightness
        && contrast == that.contrast
        && autoLevel == that.autoLevel
        && Double.compare(that.calibrationRatio, calibrationRatio) == 0
        && Objects.equals(cropZone, that.cropZone)
        && Objects.equals(layerOffset, that.layerOffset)
        && calibrationUnit == that.calibrationUnit
        && Objects.equals(ratio, that.ratio);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        cropZone,
        layerOffset,
        orientation,
        rotation,
        brightness,
        contrast,
        autoLevel,
        calibrationUnit,
        calibrationRatio,
        ratio);
  }

  @Override
  public String toString() {
    return "AcquireImageValues[cropZone=>" // NON-NLS
        + cropZone
        + "layerOffset=>" // NON-NLS
        + layerOffset
        + "orientation=>" // NON-NLS
        + orientation
        + "rotation=>" // NON-NLS
        + rotation
        + "brightness=>" // NON-NLS
        + brightness
        + "contrast=>" // NON-NLS
        + contrast
        + "autoLevel=>" // NON-NLS
        + autoLevel
        + "calibrationUnit=>" // NON-NLS
        + calibrationUnit
        + "calibrationRatio=>" // NON-NLS
        + calibrationRatio
        + "ratio=>" // NON-NLS
        + ratio;
  }
}
