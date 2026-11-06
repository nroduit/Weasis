/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.view;

import java.util.Optional;

/**
 * A scale bar of a round length (1, 2 or 5 times a power of ten) close to a wanted width.
 *
 * @param lengthPx the length of the bar in view pixels
 * @param label the length it stands for, in µm below 1 mm, else in mm
 */
public record ScaleBar(double lengthPx, String label) {

  private static final double[] STEPS = {1, 2, 5};

  /**
   * @param mmPerViewPixel the physical size of one view pixel
   * @param maxPx the longest bar wanted, in view pixels
   */
  public static Optional<ScaleBar> of(double mmPerViewPixel, double maxPx) {
    if (!(mmPerViewPixel > 0) || !(maxPx > 0) || !Double.isFinite(mmPerViewPixel)) {
      return Optional.empty();
    }
    double maxMm = mmPerViewPixel * maxPx;
    double power = Math.pow(10, Math.floor(Math.log10(maxMm)));
    double lengthMm = power;
    for (double step : STEPS) {
      if (step * power <= maxMm) {
        lengthMm = step * power;
      }
    }
    return Optional.of(new ScaleBar(lengthMm / mmPerViewPixel, label(lengthMm)));
  }

  private static String label(double mm) {
    if (mm < 1) {
      return formatted(mm * 1000) + " µm"; // NON-NLS
    }
    return formatted(mm) + " mm"; // NON-NLS
  }

  // Lengths are 1, 2 or 5 times a power of ten: no decimals except below one unit
  private static String formatted(double value) {
    if (value >= 1) {
      return String.valueOf(Math.round(value));
    }
    return String.valueOf(Math.round(value * 1000) / 1000.0);
  }
}
