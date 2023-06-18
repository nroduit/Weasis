/*
 * Copyright (c) 2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.geometry;

import org.joml.Vector3d;
import org.weasis.core.util.MathUtil;

public class VolumeGeometry {

  private static final double EPSILON = 1e-3;
  private double[] lastPixelSpacing = null;
  private double lastSpace = 0.0;
  private double spaceAccumulator = 0.0;

  private boolean variablePixelSpacing = false;
  private double[] orientationPatient;
  private int spaceNumber = 0;

  public double[] getPixelSpacing() {
    if (lastPixelSpacing == null) {
      return new double[] {1.0, 1.0};
    }
    return lastPixelSpacing;
  }

  public void setLastPixelSpacing(double[] pixelSpacing) {
    if (pixelSpacing != null && pixelSpacing.length > 1) {
      if (lastPixelSpacing != null
          && (Math.abs(lastPixelSpacing[0] - pixelSpacing[0]) > EPSILON
              || Math.abs(lastPixelSpacing[1] - pixelSpacing[1]) > EPSILON)) {
        this.variablePixelSpacing = true;
      }
      this.lastPixelSpacing = pixelSpacing;
    }
  }

  public boolean isVariablePixelSpacing() {
    return variablePixelSpacing;
  }

  public void setLastDepthSpacing(double space) {
    if (MathUtil.isDifferentFromZero(lastSpace) && Math.abs(lastSpace - space) > EPSILON) {
      this.variablePixelSpacing = true;
    }
    this.lastSpace = space;
    this.spaceNumber++;
    this.spaceAccumulator += Math.abs(space);
  }

  public Vector3d getDimensionMFactor() {
    double zSpace = spaceAccumulator / spaceNumber;
    double[] spacing = getPixelSpacing();
    if (spacing[0] <= 0 || spacing[1] <= 0 || zSpace <= 0) {
      return new Vector3d(1, 1, 1);
    }
    // By convention, use y as the main dimension
    return new Vector3d(1.0, spacing[0] / spacing[1], zSpace / spacing[1]);
  }

  public void setOrientationPatient(final double[] orientation) {
    this.orientationPatient = orientation;
  }

  public double[] getOrientationPatient() {
    if (orientationPatient == null) {
      return null;
    }
    return orientationPatient.clone();
  }
}
