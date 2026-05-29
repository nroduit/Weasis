/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.print;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.weasis.core.ui.util.PrintOptions.DotPerInches;
import org.weasis.dicom.explorer.print.DicomPrintDialog.FilmSize;

/**
 * Tests {@link DicomPrintDialog.FilmSize} — the enum that maps DICOM film-size code-strings (e.g.
 * {@code 8INX10IN}, {@code A4}) to printable pixel dimensions at a given DPI.
 *
 * <p>These conversions are the load-bearing math for both local print and DICOM Print SCU. A
 * regression here means film images print at the wrong size: at best, wasted film; at worst, an
 * image cropped/scaled so a clinician misreads a feature's size.
 */
class FilmSizeTest {

  // Tolerance for the millimetre→inch conversion (exact rational is 1 in = 25.4 mm).
  private static final double INCH_EPS = 1.0e-9;

  // -- convertMM2Inch (exact ISO definition) -------------------------------

  @Test
  void convertMM2Inch_appliesIsoDefinition() {
    // ISO 31-1: 1 inch = 25.4 mm exactly. The method takes int millimetres so we verify with
    // concrete known values.
    assertAll(
        () -> assertEquals(254.0 / 25.4, FilmSize.convertMM2Inch(254), INCH_EPS, "254 mm = 10 in"),
        () -> assertEquals(210.0 / 25.4, FilmSize.convertMM2Inch(210), INCH_EPS, "A4 short side"),
        () -> assertEquals(297.0 / 25.4, FilmSize.convertMM2Inch(297), INCH_EPS, "A4 long side"),
        () -> assertEquals(0.0, FilmSize.convertMM2Inch(0), INCH_EPS, "zero in zero out"));
  }

  @Test
  void convertMM2Inch_a4LongSideIsApproximately11_69Inches() {
    // A4 = 210 × 297 mm. Long side ≈ 11.6929 inches.
    assertEquals(11.69291, FilmSize.convertMM2Inch(297), 1.0e-4);
  }

  @Test
  void convertMM2Inch_a3LongSideIsApproximately16_53Inches() {
    // A3 = 297 × 420 mm. Long side ≈ 16.5354 inches.
    assertEquals(16.53543, FilmSize.convertMM2Inch(420), 1.0e-4);
  }

  // -- getLengthFromInch (inches × DPI with rounding) ----------------------

  @Test
  void getLengthFromInch_eightInchesAt150DpiIs1200Pixels() {
    assertEquals(1200, FilmSize.getLengthFromInch(8.0, DotPerInches.DPI_150));
  }

  @Test
  void getLengthFromInch_tenInchesAt300DpiIs3000Pixels() {
    assertEquals(3000, FilmSize.getLengthFromInch(10.0, DotPerInches.DPI_300));
  }

  @Test
  void getLengthFromInch_appliesHalfEvenRounding() {
    // 8.5 × 100 = 850 — exact, no rounding.
    assertEquals(850, FilmSize.getLengthFromInch(8.5, DotPerInches.DPI_100));
    // 8.25 × 150 = 1237.5 — rounds to 1238 via the implementation's `(int)(val + 0.5)` rule.
    assertEquals(1238, FilmSize.getLengthFromInch(8.25, DotPerInches.DPI_150));
  }

  @Test
  void getLengthFromInch_nullDpiDefaultsTo150() {
    // Critical: a null DPI argument must not NPE — the implementation falls back to 150 DPI.
    assertEquals(1200, FilmSize.getLengthFromInch(8.0, null));
  }

  // -- Per-enum dimensions at 150 DPI --------------------------------------

  @Test
  void in8x10_pixelDimensionsAt150Dpi() {
    assertAll(
        () -> assertEquals(1200, FilmSize.IN8X10.getWidth(DotPerInches.DPI_150)),
        () -> assertEquals(1500, FilmSize.IN8X10.getHeight(DotPerInches.DPI_150)));
  }

  @Test
  void in14x17_pixelDimensionsAt300Dpi() {
    // Large radiology film (14×17 in) at high resolution.
    assertAll(
        () -> assertEquals(4200, FilmSize.IN14X17.getWidth(DotPerInches.DPI_300)),
        () -> assertEquals(5100, FilmSize.IN14X17.getHeight(DotPerInches.DPI_300)));
  }

  @Test
  void a4_pixelDimensionsAt150Dpi() {
    // A4 = 210×297 mm = 8.2677×11.6929 inches.
    // 8.2677 × 150 = 1240.16 → 1240; 11.6929 × 150 = 1753.94 → 1754.
    assertAll(
        () -> assertEquals(1240, FilmSize.A4.getWidth(DotPerInches.DPI_150)),
        () -> assertEquals(1754, FilmSize.A4.getHeight(DotPerInches.DPI_150)));
  }

  @Test
  void a3_pixelDimensionsAt150Dpi() {
    // A3 = 297×420 mm = 11.6929×16.5354 inches.
    // 11.6929 × 150 = 1753.94 → 1754; 16.5354 × 150 = 2480.31 → 2480.
    assertAll(
        () -> assertEquals(1754, FilmSize.A3.getWidth(DotPerInches.DPI_150)),
        () -> assertEquals(2480, FilmSize.A3.getHeight(DotPerInches.DPI_150)));
  }

  @Test
  void cm24x30_pixelDimensionsAt150Dpi() {
    // 24×30 cm → 9.4488×11.8110 inches → 1417×1772 px at 150 DPI.
    assertAll(
        () -> assertEquals(1417, FilmSize.CM24X30.getWidth(DotPerInches.DPI_150)),
        () -> assertEquals(1772, FilmSize.CM24X30.getHeight(DotPerInches.DPI_150)));
  }

  @Test
  void filmSize_dpiScalesLinearlyForSameSize() {
    // Doubling DPI must (approximately) double pixel count — a sanity check that protects
    // against a constant being hard-coded somewhere in the conversion chain.
    int widthAt150 = FilmSize.IN8X10.getWidth(DotPerInches.DPI_150);
    int widthAt300 = FilmSize.IN8X10.getWidth(DotPerInches.DPI_300);

    assertEquals(
        widthAt150 * 2, widthAt300, "300 DPI is exactly 2× 150 DPI for integer-inch sizes");
  }

  // -- toString + getInstance (UI binding) ---------------------------------

  @Test
  void toString_returnsDicomCompliantSizeName() {
    // The DICOM Basic Film Box Module (PS3.4 §H.4.2) uses "8INX10IN", "A4", etc. The toString
    // must match exactly — Print SCU sends this string in the N-CREATE request.
    assertAll(
        () -> assertEquals("8INX10IN", FilmSize.IN8X10.toString()),
        () -> assertEquals("14INX17IN", FilmSize.IN14X17.toString()),
        () -> assertEquals("A4", FilmSize.A4.toString()),
        () -> assertEquals("A3", FilmSize.A3.toString()),
        () -> assertEquals("24CMX30CM", FilmSize.CM24X30.toString()));
  }

  @Test
  void getInstance_resolvesKnownName() {
    assertEquals(FilmSize.A4, FilmSize.getInstance("A4", FilmSize.IN8X10));
  }

  @Test
  void getInstance_unknownReturnsDefault() {
    // Critical: an unknown name (e.g. a legacy preference file from an older version) must NOT
    // throw — it falls back to the caller's chosen default.
    assertEquals(FilmSize.IN8X10, FilmSize.getInstance("UNKNOWN", FilmSize.IN8X10));
  }

  @Test
  void getInstance_nullReturnsDefault() {
    assertEquals(FilmSize.IN8X10, FilmSize.getInstance(null, FilmSize.IN8X10));
  }

  @Test
  void getInstance_blankReturnsDefault() {
    assertEquals(FilmSize.IN8X10, FilmSize.getInstance("   ", FilmSize.IN8X10));
  }
}
