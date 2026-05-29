/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weasis.core.ui.util.PrintOptions.DotPerInches;

/**
 * Tests {@link PrintOptions} — the configuration carrier for the local-print path. A regression in
 * any default value affects every print job (DPI silently changing distorts measurement scale on
 * paper; colorPrint=true changing to false would silently strip clinical color overlays).
 */
class PrintOptionsTest {

  // -- Constructor defaults -------------------------------------------------

  @Test
  void constructor_defaultsToShowAnnotationsCenterColorAndDpi150() {
    PrintOptions opts = new PrintOptions();

    assertAll(
        () -> assertTrue(opts.isShowingAnnotations(), "annotations on by default"),
        () -> assertTrue(opts.isCenter(), "center on by default"),
        () -> assertTrue(opts.isColorPrint(), "color-print on by default"),
        () -> assertEquals(DotPerInches.DPI_150, opts.getDpi(), "DPI defaults to 150"));
  }

  // -- Setter round-trips ---------------------------------------------------

  @Test
  void setShowingAnnotations_roundTrip() {
    PrintOptions opts = new PrintOptions();

    opts.setShowingAnnotations(false);
    assertFalse(opts.isShowingAnnotations());

    opts.setShowingAnnotations(true);
    assertTrue(opts.isShowingAnnotations());
  }

  @Test
  void setCenter_roundTrip() {
    PrintOptions opts = new PrintOptions();

    opts.setCenter(false);
    assertFalse(opts.isCenter());
  }

  @Test
  void setColorPrint_roundTrip() {
    // Critical: setting colorPrint=false converts every pixel to grayscale on print. Verify the
    // setter actually flips the flag (no swallowed argument).
    PrintOptions opts = new PrintOptions();

    opts.setColorPrint(false);
    assertFalse(opts.isColorPrint());

    opts.setColorPrint(true);
    assertTrue(opts.isColorPrint());
  }

  @Test
  void setDpi_roundTripForEveryEnumValue() {
    PrintOptions opts = new PrintOptions();
    for (DotPerInches dpi : DotPerInches.values()) {
      opts.setDpi(dpi);
      assertEquals(dpi, opts.getDpi(), "round-trip for " + dpi);
    }
  }

  @Test
  void setDpi_nullCoercesToDefault150() {
    // Critical: a null preference value must NOT propagate as a null DPI — every downstream
    // dimension calculation would NPE otherwise. The setter coerces to 150 DPI as a safe
    // fallback.
    PrintOptions opts = new PrintOptions();
    opts.setDpi(DotPerInches.DPI_300);

    opts.setDpi(null);

    assertEquals(DotPerInches.DPI_150, opts.getDpi());
  }

  // -- DotPerInches enum ----------------------------------------------------

  @Test
  void dotPerInches_valuesMatchEnumNames() {
    assertAll(
        () -> assertEquals(100, DotPerInches.DPI_100.getDpi()),
        () -> assertEquals(150, DotPerInches.DPI_150.getDpi()),
        () -> assertEquals(200, DotPerInches.DPI_200.getDpi()),
        () -> assertEquals(250, DotPerInches.DPI_250.getDpi()),
        () -> assertEquals(300, DotPerInches.DPI_300.getDpi()));
  }

  @Test
  void dotPerInches_toStringIsTheNumericValue() {
    // The UI combo box uses toString() to render the value. A regression to "DPI_150" would
    // show ugly raw enum names in the print dialog.
    assertEquals("150", DotPerInches.DPI_150.toString());
  }

  @Test
  void dotPerInches_getInstanceResolvesKnownName() {
    assertEquals(DotPerInches.DPI_300, DotPerInches.getInstance("DPI_300", DotPerInches.DPI_150));
  }

  @Test
  void dotPerInches_getInstanceUnknownReturnsDefault() {
    assertEquals(DotPerInches.DPI_200, DotPerInches.getInstance("NOT_A_DPI", DotPerInches.DPI_200));
  }

  @Test
  void dotPerInches_getInstanceNullReturnsDefault() {
    assertEquals(DotPerInches.DPI_200, DotPerInches.getInstance(null, DotPerInches.DPI_200));
  }

  @Test
  void dotPerInches_getInstanceBlankReturnsDefault() {
    assertEquals(DotPerInches.DPI_200, DotPerInches.getInstance("   ", DotPerInches.DPI_200));
  }
}
