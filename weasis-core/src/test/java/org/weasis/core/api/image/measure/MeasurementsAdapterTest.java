/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.measure;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MeasurementsAdapterTest {

  private static final double EPS = 1.0e-12;

  // -- Compact constructor validation --------------------------------------

  @Test
  void constructor_nanCalibrationRatioRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new MeasurementsAdapter(Double.NaN, 0, 0, false, 100, "mm"));
  }

  @Test
  void constructor_positiveInfinityCalibrationRatioRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new MeasurementsAdapter(Double.POSITIVE_INFINITY, 0, 0, false, 100, "mm"));
  }

  @Test
  void constructor_negativeInfinityCalibrationRatioRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new MeasurementsAdapter(Double.NEGATIVE_INFINITY, 0, 0, false, 100, "mm"));
  }

  @Test
  void constructor_zeroCalibrationRatioRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> new MeasurementsAdapter(0.0, 0, 0, false, 100, "mm"));
  }

  @Test
  void constructor_negativeCalibrationRatioRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new MeasurementsAdapter(-0.5, 0, 0, false, 100, "mm"));
  }

  @Test
  void constructor_nullUnitDefaultsToPx() {
    MeasurementsAdapter adapter = new MeasurementsAdapter(1.0, 0, 0, false, 100, null);

    assertEquals("px", adapter.unit());
  }

  @Test
  void constructor_imageHeightNormalizedToMaxMinusOne() {
    // imageHeight is stored as max(0, h-1) so a 100-row image yields stored value 99.
    MeasurementsAdapter adapter = new MeasurementsAdapter(1.0, 0, 0, false, 100, "mm");

    assertEquals(99, adapter.imageHeight());
  }

  @Test
  void constructor_zeroImageHeightClampedToZero() {
    MeasurementsAdapter adapter = new MeasurementsAdapter(1.0, 0, 0, false, 0, "mm");

    assertEquals(0, adapter.imageHeight());
  }

  @Test
  void constructor_negativeImageHeightClampedToZero() {
    MeasurementsAdapter adapter = new MeasurementsAdapter(1.0, 0, 0, false, -5, "mm");

    assertEquals(0, adapter.imageHeight());
  }

  // -- X coordinate transforms ---------------------------------------------

  @Test
  void getXUncalibratedValue_addsOffset() {
    MeasurementsAdapter adapter = new MeasurementsAdapter(1.0, 10, 0, false, 100, "mm");

    assertEquals(15.0, adapter.getXUncalibratedValue(5), EPS);
  }

  @Test
  void getXUncalibratedValue_withNegativeOffset() {
    MeasurementsAdapter adapter = new MeasurementsAdapter(1.0, -3, 0, false, 100, "mm");

    assertEquals(7.0, adapter.getXUncalibratedValue(10), EPS);
  }

  @Test
  void getXCalibratedValue_multipliesByCalibrationRatio() {
    // 0.5 mm/px: pixel 10 → 5 mm
    MeasurementsAdapter adapter = new MeasurementsAdapter(0.5, 0, 0, false, 100, "mm");

    assertEquals(5.0, adapter.getXCalibratedValue(10), EPS);
  }

  @Test
  void getXCalibratedValue_appliesOffsetThenCalibration() {
    // ratio = 0.5, offset = 4 → calibrated(x) = 0.5 * (x + 4)
    MeasurementsAdapter adapter = new MeasurementsAdapter(0.5, 4, 0, false, 100, "mm");

    assertEquals(10.0, adapter.getXCalibratedValue(16), EPS);
  }

  // -- Y coordinate transforms (axis-flip is the critical clinical case) ---

  @Test
  void getYUncalibratedValue_downwardAxisAddsOffsetOnly() {
    // upYAxis=false → no flip; y=20 + offsetY=5 → 25
    MeasurementsAdapter adapter = new MeasurementsAdapter(1.0, 0, 5, false, 100, "mm");

    assertEquals(25.0, adapter.getYUncalibratedValue(20), EPS);
  }

  @Test
  void getYUncalibratedValue_upwardAxisFlipsAroundImageHeight() {
    // upYAxis=true, imageHeight stored = 99 (h=100). y=20 → (99 - 20) + offsetY=0 → 79.
    MeasurementsAdapter adapter = new MeasurementsAdapter(1.0, 0, 0, true, 100, "mm");

    assertEquals(79.0, adapter.getYUncalibratedValue(20), EPS);
  }

  @Test
  void getYUncalibratedValue_upwardAxisAtTopOfImage() {
    // y=0 in upward axis = top of image -> (99 - 0) = row 99.
    MeasurementsAdapter adapter = new MeasurementsAdapter(1.0, 0, 0, true, 100, "mm");

    assertEquals(99.0, adapter.getYUncalibratedValue(0), EPS);
  }

  @Test
  void getYUncalibratedValue_upwardAxisAtBottomOfImage() {
    // y=99 in upward axis = bottom -> (99 - 99) = row 0.
    MeasurementsAdapter adapter = new MeasurementsAdapter(1.0, 0, 0, true, 100, "mm");

    assertEquals(0.0, adapter.getYUncalibratedValue(99), EPS);
  }

  @Test
  void getYCalibratedValue_downwardAxisWithRatioAndOffset() {
    // ratio 0.5, offsetY 4 → calibrated(y) = 0.5 * (y + 4)
    MeasurementsAdapter adapter = new MeasurementsAdapter(0.5, 0, 4, false, 100, "mm");

    assertEquals(10.0, adapter.getYCalibratedValue(16), EPS);
  }

  @Test
  void getYCalibratedValue_upwardAxisAppliesFlipBeforeCalibration() {
    // ratio 0.5, upYAxis, h=100 (stored 99). y=20 → flipped 79 → calibrated 39.5
    MeasurementsAdapter adapter = new MeasurementsAdapter(0.5, 0, 0, true, 100, "mm");

    assertEquals(39.5, adapter.getYCalibratedValue(20), EPS);
  }

  // -- isCalibrated --------------------------------------------------------

  @Test
  void isCalibrated_falseForUnityRatio() {
    assertFalse(new MeasurementsAdapter(1.0, 0, 0, false, 100, "px").isCalibrated());
  }

  @Test
  void isCalibrated_trueForNonUnityRatio() {
    assertTrue(new MeasurementsAdapter(0.5, 0, 0, false, 100, "mm").isCalibrated());
  }

  @Test
  void isCalibrated_trueForRatioJustBelowOne() {
    // Compare with Double.compare; even tiny deviations should be considered calibrated.
    assertTrue(new MeasurementsAdapter(0.9999, 0, 0, false, 100, "mm").isCalibrated());
  }

  // -- Record accessors + toString -----------------------------------------

  @Test
  void recordAccessors_returnStoredFields() {
    MeasurementsAdapter adapter = new MeasurementsAdapter(0.7, 3, -2, true, 200, "mm");

    assertAll(
        () -> assertEquals(0.7, adapter.calibrationRatio(), EPS),
        () -> assertEquals(3, adapter.offsetX()),
        () -> assertEquals(-2, adapter.offsetY()),
        () -> assertTrue(adapter.upYAxis()),
        () -> assertEquals(199, adapter.imageHeight(), "200 stored as 199 (max(0, h-1))"),
        () -> assertEquals("mm", adapter.unit()));
  }

  @Test
  void toString_includesAllSignificantFields() {
    String s = new MeasurementsAdapter(0.5, 3, -2, true, 100, "mm").toString();

    // The ratio is formatted with %.3f, which respects the platform locale (decimal separator
    // may be '.' or ','); match both forms rather than hard-coding either.
    assertAll(
        () -> assertTrue(s.matches(".*ratio=0[.,]500.*"), "ratio rendered to 3 decimals: " + s),
        () -> assertTrue(s.contains("mm")),
        () -> assertTrue(s.contains("offsetX=3")),
        () -> assertTrue(s.contains("offsetY=-2")),
        () -> assertTrue(s.contains("upYAxis=true")),
        () -> assertTrue(s.contains("imageHeight=99")));
  }
}
