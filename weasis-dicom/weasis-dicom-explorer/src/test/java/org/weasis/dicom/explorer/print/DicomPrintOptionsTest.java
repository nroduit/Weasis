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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.core.ui.util.PrintOptions.DotPerInches;
import org.weasis.dicom.explorer.print.DicomPrintDialog.FilmSize;

/**
 * Tests {@link DicomPrintOptions} — the carrier for DICOM Print SCU N-CREATE attributes (Basic Film
 * Box / Image Box, DICOM PS3.4 §H). The defaults here drive every printed film: a regression to the
 * wrong density / smoothing / orientation silently produces non-diagnostic prints.
 */
class DicomPrintOptionsTest {

  // -- Inheritance ----------------------------------------------------------

  @Test
  void extendsPrintOptions() {
    assertInstanceOf(PrintOptions.class, new DicomPrintOptions());
  }

  // -- Default values (pinned against drift) -------------------------------

  @Test
  void defaults_filmAttributesMatchClass() {
    DicomPrintOptions opts = new DicomPrintOptions();

    assertAll(
        () -> assertEquals("BLUE FILM", opts.getMediumType(), "medium type"),
        () -> assertEquals("LOW", opts.getPriority(), "priority"),
        () -> assertEquals("MAGAZINE", opts.getFilmDestination(), "film destination"),
        () -> assertEquals(1, opts.getNumOfCopies(), "default copy count"),
        () -> assertEquals("PORTRAIT", opts.getFilmOrientation(), "film orientation"),
        () -> assertEquals(FilmSize.IN8X10, opts.getFilmSizeId(), "film size"),
        () ->
            assertEquals(
                "STANDARD\\1,1", opts.getImageDisplayFormat(), "image display format 1x1 grid"),
        () -> assertEquals("CUBIC", opts.getMagnificationType(), "cubic interpolation by default"),
        () -> assertEquals("MEDIUM", opts.getSmoothingType(), "medium smoothing"),
        () -> assertEquals("WHITE", opts.getBorderDensity(), "white border"),
        () -> assertEquals("NO", opts.getTrim(), "no trim"),
        () -> assertEquals("BLACK", opts.getEmptyDensity(), "empty cell density"),
        () -> assertEquals(0, opts.getMinDensity(), "min density floor"),
        () -> assertEquals(255, opts.getMaxDensity(), "max density ceiling"),
        () -> assertFalse(opts.isPrintOnlySelectedView(), "default: print all visible views"));
  }

  @Test
  void defaults_inheritedAnnotationFlagsAreSafe() {
    DicomPrintOptions opts = new DicomPrintOptions();

    assertAll(
        () ->
            assertTrue(
                opts.isShowingAnnotations(),
                "annotations on by default (DEF_SHOW_ANNOTATIONS = true)"),
        () ->
            assertFalse(
                opts.isColorPrint(),
                "DICOM print defaults to grayscale (DEF_COLOR = false), even though local print"
                    + " defaults to colour"),
        () -> assertEquals(DotPerInches.DPI_150, opts.getDpi(), "150 DPI default"));
  }

  @Test
  void defaultConstants_areTheExpectedFilmStandardValues() {
    // Pin the public DEF_* constants — they're referenced from other modules; renaming/changing
    // them silently propagates to callers.
    assertAll(
        () -> assertEquals("BLUE FILM", DicomPrintOptions.DEF_MEDIUM_TYPE),
        () -> assertEquals("LOW", DicomPrintOptions.DEF_PRIORITY),
        () -> assertEquals("MAGAZINE", DicomPrintOptions.DEF_FILM_DEST),
        () -> assertEquals(1, DicomPrintOptions.DEF_NUM_COPIES),
        () -> assertFalse(DicomPrintOptions.DEF_COLOR),
        () -> assertEquals("PORTRAIT", DicomPrintOptions.DEF_FILM_ORIENTATION),
        () -> assertEquals("STANDARD\\1,1", DicomPrintOptions.DEF_IMG_DISP_FORMAT),
        () -> assertEquals(FilmSize.IN8X10, DicomPrintOptions.DEF_FILM_SIZE),
        () -> assertEquals("CUBIC", DicomPrintOptions.DEF_MAGNIFICATION_TYPE),
        () -> assertEquals("MEDIUM", DicomPrintOptions.DEF_SMOOTHING_TYPE),
        () -> assertEquals("WHITE", DicomPrintOptions.DEF_BORDER_DENSITY),
        () -> assertEquals("NO", DicomPrintOptions.DEF_TRIM),
        () -> assertEquals("BLACK", DicomPrintOptions.DEF_EMPTY_DENSITY),
        () -> assertTrue(DicomPrintOptions.DEF_SHOW_ANNOTATIONS),
        () -> assertFalse(DicomPrintOptions.DEF_PRINT_SEL_VIEW),
        () -> assertEquals(DotPerInches.DPI_150, DicomPrintOptions.DEF_DPI));
  }

  // -- Setter round-trips ---------------------------------------------------

  @Test
  void setMediumType_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setMediumType("CLEAR FILM");

    assertEquals("CLEAR FILM", opts.getMediumType());
  }

  @Test
  void setPriority_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setPriority("HIGH");

    assertEquals("HIGH", opts.getPriority());
  }

  @Test
  void setFilmDestination_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setFilmDestination("PROCESSOR");

    assertEquals("PROCESSOR", opts.getFilmDestination());
  }

  @Test
  void setNumOfCopies_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setNumOfCopies(5);

    assertEquals(5, opts.getNumOfCopies());
  }

  @Test
  void setFilmOrientation_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setFilmOrientation("LANDSCAPE");

    assertEquals("LANDSCAPE", opts.getFilmOrientation());
  }

  @Test
  void setFilmSizeId_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setFilmSizeId(FilmSize.A4);

    assertEquals(FilmSize.A4, opts.getFilmSizeId());
  }

  @Test
  void setFilmSizeId_nullCoercesToDefault() {
    // Critical: a null film-size preference must NOT propagate as null — downstream code uses
    // FilmSize.getWidth(dpi) which would NPE. The setter falls back to 8×10 inch (the
    // standard medical-imaging film size).
    DicomPrintOptions opts = new DicomPrintOptions();
    opts.setFilmSizeId(FilmSize.A4);

    opts.setFilmSizeId(null);

    assertEquals(FilmSize.IN8X10, opts.getFilmSizeId());
  }

  @Test
  void setImageDisplayFormat_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setImageDisplayFormat("STANDARD\\2,3");

    assertEquals("STANDARD\\2,3", opts.getImageDisplayFormat());
  }

  @Test
  void setMagnificationType_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setMagnificationType("REPLICATE");

    assertEquals("REPLICATE", opts.getMagnificationType());
  }

  @Test
  void setSmoothingType_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setSmoothingType("SHARP");

    assertEquals("SHARP", opts.getSmoothingType());
  }

  @Test
  void setBorderDensity_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setBorderDensity("BLACK");

    assertEquals("BLACK", opts.getBorderDensity());
  }

  @Test
  void setTrim_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setTrim("YES");

    assertEquals("YES", opts.getTrim());
  }

  @Test
  void setEmptyDensity_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setEmptyDensity("WHITE");

    assertEquals("WHITE", opts.getEmptyDensity());
  }

  @Test
  void setMinAndMaxDensity_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setMinDensity(10);
    opts.setMaxDensity(245);

    assertAll(
        () -> assertEquals(10, opts.getMinDensity()),
        () -> assertEquals(245, opts.getMaxDensity()));
  }

  @Test
  void setPrintOnlySelectedView_roundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setPrintOnlySelectedView(true);
    assertTrue(opts.isPrintOnlySelectedView());

    opts.setPrintOnlySelectedView(false);
    assertFalse(opts.isPrintOnlySelectedView());
  }

  // -- Inherited setters ---------------------------------------------------

  @Test
  void setShowingAnnotations_inheritedRoundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setShowingAnnotations(false);

    assertFalse(opts.isShowingAnnotations(), "inherited setter still works");
  }

  @Test
  void setColorPrint_inheritedRoundTrip() {
    DicomPrintOptions opts = new DicomPrintOptions();

    opts.setColorPrint(true);

    assertTrue(opts.isColorPrint());
  }
}
