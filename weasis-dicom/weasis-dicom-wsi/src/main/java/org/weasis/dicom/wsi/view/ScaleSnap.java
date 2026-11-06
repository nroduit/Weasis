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

import org.weasis.dicom.wsi.model.PyramidLevel;
import org.weasis.dicom.wsi.model.Slide;

/**
 * Snaps a relative zoom to the native scales of a slide, those at which a level is shown pixel for
 * pixel (crisp, without resampling). A zoom snaps when it ends close to a native scale while coming
 * nearer to it or passing it, never when it leaves it, so small steps (touchpads) are never held.
 */
public final class ScaleSnap {

  /** How close to a native scale a zoom snaps to it, as a ratio (below the 1.15 of a notch). */
  static final double TOLERANCE = 1.08;

  private ScaleSnap() {}

  /** The scales at which each level of the slide is shown pixel for pixel. */
  public static double[] nativeScales(Slide slide) {
    return slide.levels().stream()
        .mapToDouble(PyramidLevel::downsampleX)
        .map(d -> 1.0 / d)
        .toArray();
  }

  /**
   * @param from the scale the zoom starts from
   * @param to the scale asked for
   * @return the native scale nearest to {@code to} within the tolerance, when the zoom comes nearer
   *     to it or passes it; {@code to} otherwise
   */
  public static double snap(double from, double to, double[] scales) {
    double best = to;
    double bestDistance = Math.log(TOLERANCE);
    for (double s : scales) {
      double distance = Math.abs(Math.log(to / s));
      boolean passed = (from - s) * (to - s) < 0;
      if (distance < bestDistance && (passed || distance < Math.abs(Math.log(from / s)))) {
        best = s;
        bestDistance = distance;
      }
    }
    return best;
  }
}
