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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weasis.core.ui.print.TextSize;

/**
 * Tests {@link PrintOptions} — the configuration carrier for the local-print path. A regression in
 * any default value affects every print job (DPI silently changing distorts measurement scale on
 * paper; colorPrint=true changing to false would silently strip clinical color overlays).
 */
class PrintOptionsTest {

  // -- Constructor defaults -------------------------------------------------

  @Test
  void constructor_defaultsToShowAnnotationsCenterAndColor() {
    PrintOptions opts = new PrintOptions();

    assertAll(
        () -> assertTrue(opts.isShowingAnnotations(), "annotations on by default"),
        () -> assertTrue(opts.isCenter(), "center on by default"),
        () -> assertTrue(opts.isColorPrint(), "color-print on by default"),
        () -> assertNull(opts.getTextSize(), "text size follows the cell width by default"),
        () -> assertEquals(java.awt.Color.WHITE, opts.getBackground(), "paper is white"),
        () -> assertNull(opts.getBorder(), "no cell border by default"));
  }

  @Test
  void textSize_background_and_border_roundTrip() {
    PrintOptions opts = new PrintOptions();
    opts.setTextSize(TextSize.LARGE);
    opts.setBackground(java.awt.Color.BLACK);
    opts.setBorder(java.awt.Color.WHITE);
    assertAll(
        () -> assertEquals(TextSize.LARGE, opts.getTextSize()),
        () -> assertEquals(java.awt.Color.BLACK, opts.getBackground()),
        () -> assertEquals(java.awt.Color.WHITE, opts.getBorder()));

    opts.setBackground(null);
    assertEquals(java.awt.Color.WHITE, opts.getBackground(), "a null background means paper");
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
}
