/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.bean;

import java.util.Set;

/**
 * What a measurement value is, which decides how precisely it can honestly be displayed. Derived
 * from the measurement key by default, so tools that follow the key vocabulary need not state it.
 */
public enum QuantityKind {
  /** Whole number of things, such as pixels. */
  COUNT,
  ANGLE,
  /** Value without unit between two lengths. */
  RATIO,
  LENGTH,
  /** Position along an image axis. */
  COORDINATE,
  AREA,
  /** A value taken by pixels: minimum, maximum, probe, sum. */
  PIXEL_VALUE,
  /** A value computed over pixels, finer than the pixel values themselves: mean, median, stdev. */
  PIXEL_STATISTIC,
  /** Shape of a distribution: skewness, kurtosis, entropy. */
  MOMENT,
  OTHER;

  private static final Set<String> LENGTHS =
      Set.of(
          "length",
          "distance",
          "perimeter",
          "width",
          "height",
          "radius", // NON-NLS
          "diameter"); // NON-NLS
  private static final Set<String> ANGLES = Set.of("angle", "orientation", "azimuth"); // NON-NLS
  private static final Set<String> PIXEL_VALUES =
      Set.of("stats.min", "stats.max", "stats.sum"); // NON-NLS
  private static final Set<String> PIXEL_STATISTICS =
      Set.of("stats.mean", "stats.median", "stats.stdev"); // NON-NLS
  private static final Set<String> MOMENTS =
      Set.of("stats.skew", "stats.kurtosis", "stats.entropy"); // NON-NLS

  /** Kind implied by a measurement key such as {@code length.short}, {@code center.x}. */
  public static QuantityKind ofKey(String key) {
    if (key == null || key.isEmpty()) {
      return OTHER;
    }
    if ("stats.pixels".equals(key)) { // NON-NLS
      return COUNT;
    }
    if (PIXEL_VALUES.contains(key) || key.startsWith("pixel.")) { // NON-NLS
      return PIXEL_VALUE;
    }
    if (PIXEL_STATISTICS.contains(key)) {
      return PIXEL_STATISTIC;
    }
    if (MOMENTS.contains(key)) {
      return MOMENT;
    }
    String first = key.substring(0, key.indexOf('.') < 0 ? key.length() : key.indexOf('.'));
    String last = key.substring(key.lastIndexOf('.') + 1);
    if ("ratio".equals(last)) { // NON-NLS
      return RATIO;
    }
    if ("area".equals(last)) { // NON-NLS
      return AREA;
    }
    if (ANGLES.contains(first) || ANGLES.contains(last)) {
      return ANGLE;
    }
    if ("x".equals(last) || "y".equals(last)) { // NON-NLS
      return COORDINATE;
    }
    if (LENGTHS.contains(first) || LENGTHS.contains(last)) {
      return LENGTH;
    }
    return OTHER;
  }
}
