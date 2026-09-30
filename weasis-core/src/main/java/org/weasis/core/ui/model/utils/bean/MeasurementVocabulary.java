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

import java.util.Locale;
import java.util.Map;
import org.weasis.core.api.image.util.Unit;

/**
 * How a measured value is named and measured outside the user interface. A caller of the command
 * surface gets the stable key of the measurement, an English name that does not follow the language
 * of the interface, and a UCUM code instead of the localized abbreviation shown on screen.
 */
public final class MeasurementVocabulary {

  /** UCUM code of the Hounsfield unit. */
  public static final String HOUNSFIELD = "[hnsf'U]"; // NON-NLS

  /** UCUM code of a count of pixels. */
  public static final String PIXEL_COUNT = "{pixel}"; // NON-NLS

  /** UCUM code of a plane angle in degrees. */
  public static final String DEGREE = "deg"; // NON-NLS

  private static final Map<String, String> NAMES =
      Map.ofEntries(
          Map.entry("anchor.x", "Point X"), // NON-NLS
          Map.entry("anchor.y", "Point Y"), // NON-NLS
          Map.entry("angle", "Angle"), // NON-NLS
          Map.entry("angle.complementary", "Complementary angle"), // NON-NLS
          Map.entry("angle.reflex", "Reflex angle"), // NON-NLS
          Map.entry("area", "Area"), // NON-NLS
          Map.entry("azimuth", "Azimuth"), // NON-NLS
          Map.entry("center.x", "Center X"), // NON-NLS
          Map.entry("center.y", "Center Y"), // NON-NLS
          Map.entry("ctr.cardiac", "Cardiac width"), // NON-NLS
          Map.entry("ctr.mld", "Midline to left heart border"), // NON-NLS
          Map.entry("ctr.mrd", "Midline to right heart border"), // NON-NLS
          Map.entry("ctr.ratio", "Cardiothoracic ratio"), // NON-NLS
          Map.entry("ctr.thoracic", "Thoracic width"), // NON-NLS
          Map.entry("diameter", "Diameter"), // NON-NLS
          Map.entry("distance", "Distance"), // NON-NLS
          Map.entry("first.x", "First point X"), // NON-NLS
          Map.entry("first.y", "First point Y"), // NON-NLS
          Map.entry("height", "Height"), // NON-NLS
          Map.entry("last.x", "Last point X"), // NON-NLS
          Map.entry("last.y", "Last point Y"), // NON-NLS
          Map.entry("length", "Length"), // NON-NLS
          Map.entry("length.dx", "Horizontal length"), // NON-NLS
          Map.entry("length.dy", "Vertical length"), // NON-NLS
          Map.entry("length.first", "First length"), // NON-NLS
          Map.entry("length.long", "Long axis"), // NON-NLS
          Map.entry("length.second", "Second length"), // NON-NLS
          Map.entry("length.short", "Short axis"), // NON-NLS
          Map.entry("ombb.length", "Oriented bounding box length"), // NON-NLS
          Map.entry("ombb.orientation", "Oriented bounding box orientation"), // NON-NLS
          Map.entry("ombb.width", "Oriented bounding box width"), // NON-NLS
          Map.entry("orientation", "Orientation"), // NON-NLS
          Map.entry("perimeter", "Perimeter"), // NON-NLS
          Map.entry("radius", "Radius"), // NON-NLS
          Map.entry("ratio", "Ratio"), // NON-NLS
          Map.entry("stats.entropy", "Entropy"), // NON-NLS
          Map.entry("stats.kurtosis", "Kurtosis"), // NON-NLS
          Map.entry("stats.max", "Maximum"), // NON-NLS
          Map.entry("stats.mean", "Mean"), // NON-NLS
          Map.entry("stats.median", "Median"), // NON-NLS
          Map.entry("stats.min", "Minimum"), // NON-NLS
          Map.entry("stats.pixels", "Pixel count"), // NON-NLS
          Map.entry("stats.skew", "Skewness"), // NON-NLS
          Map.entry("stats.stdev", "Standard deviation"), // NON-NLS
          Map.entry("stats.sum", "Sum"), // NON-NLS
          Map.entry("topleft.x", "Top left X"), // NON-NLS
          Map.entry("topleft.y", "Top left Y"), // NON-NLS
          Map.entry("width", "Width")); // NON-NLS

  private MeasurementVocabulary() {}

  /**
   * English name of a measurement, whatever the language of the interface. A measurement built at
   * runtime, such as one pixel channel, keeps the name it carries.
   */
  public static String nameOf(Measurement measurement) {
    String name = NAMES.get(measurement.getKey());
    return name == null ? measurement.getName() : name;
  }

  /**
   * UCUM code of what a measurement of this kind holds, or {@code null} when the value has no unit.
   *
   * @param spatial the unit lengths and areas are measured in
   * @param pixelValueUnit the unit of the image values, as the image declares it
   */
  public static String unitOf(Measurement measurement, Unit spatial, String pixelValueUnit) {
    Unit unit = spatial == null ? Unit.PIXEL : spatial;
    return switch (measurement.getKind()) {
      case LENGTH, COORDINATE -> unit.getUcum();
      case AREA -> unit.getUcumArea();
      case ANGLE -> DEGREE;
      case COUNT -> PIXEL_COUNT;
      case PIXEL_VALUE, PIXEL_STATISTIC -> pixelValue(pixelValueUnit);
      case RATIO, MOMENT, OTHER -> null;
    };
  }

  /**
   * UCUM code of an image value unit. The Hounsfield unit and the standardized uptake value of a
   * PET are recognized; anything else becomes a UCUM annotation, which carries the term the image
   * declares without claiming a dimension for it.
   */
  public static String pixelValue(String unit) {
    if (unit == null || unit.isBlank()) {
      return null;
    }
    String text = unit.trim();
    if ("HU".equalsIgnoreCase(text)) { // NON-NLS
      return HOUNSFIELD;
    }
    if (text.toUpperCase(Locale.ROOT).startsWith("SUV")) { // NON-NLS
      return "g/mL"; // NON-NLS
    }
    return "{" + text.replaceAll("[{}\\s]", "") + "}";
  }
}
