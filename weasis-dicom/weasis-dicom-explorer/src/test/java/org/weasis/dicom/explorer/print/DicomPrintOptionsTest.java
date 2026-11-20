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

import java.awt.Color;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.print.Paper;
import org.weasis.core.ui.print.TextSize;
import org.weasis.core.ui.util.PrintOptions;

/**
 * The carrier of the DICOM Print attributes (film session and film box, PS3.3 C.13): its defaults
 * drive every printed film, so they are pinned here.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomPrintOptionsTest {

  @Test
  void extends_the_page_options() {
    assertInstanceOf(PrintOptions.class, new DicomPrintOptions());
  }

  @Test
  void defaults_are_the_film_standard_values() {
    DicomPrintOptions opts = new DicomPrintOptions();
    assertAll(
        () -> assertEquals("BLUE FILM", opts.getMediumType()),
        () -> assertEquals("LOW", opts.getPriority()),
        () -> assertEquals("MAGAZINE", opts.getFilmDestination()),
        () -> assertEquals(1, opts.getNumOfCopies()),
        () -> assertEquals("PORTRAIT", opts.getFilmOrientation()),
        () -> assertEquals(Paper.IN_8X10, opts.getFilmSizeId()),
        () -> assertEquals("STANDARD\\1,1", opts.getImageDisplayFormat()),
        () -> assertEquals("CUBIC", opts.getMagnificationType()),
        () -> assertEquals("MEDIUM", opts.getSmoothingType()),
        () -> assertEquals("WHITE", opts.getBorderDensity()),
        () -> assertEquals("NO", opts.getTrim()),
        () -> assertEquals("BLACK", opts.getEmptyDensity()),
        () -> assertEquals(0, opts.getMinDensity()),
        () -> assertEquals(0, opts.getMaxDensity(), "unset: the printer default"),
        () -> assertEquals(150, opts.getFilmResolution()),
        () -> assertTrue(opts.isShowingAnnotations()),
        () -> assertFalse(opts.isColorPrint(), "film is gray unless asked"));
  }

  @Test
  void film_sizes_are_the_papers_with_a_dicom_id() {
    assertAll(
        () -> assertTrue(DicomPrintOptions.filmSizes().contains(Paper.IN_14X17)),
        () -> assertTrue(DicomPrintOptions.filmSizes().contains(Paper.A4)),
        () -> assertFalse(DicomPrintOptions.filmSizes().contains(Paper.A5)),
        () -> assertTrue(DicomPrintOptions.filmSizes().stream().allMatch(Paper::hasFilmSizeId)));
  }

  @Test
  void a_stored_film_size_is_a_dicom_id_or_the_name_of_the_old_enum() {
    Paper def = Paper.IN_8X10;
    assertAll(
        () -> assertEquals(Paper.IN_14X17, DicomPrintOptions.filmSizeOf("14INX17IN", def)),
        () -> assertEquals(Paper.IN_14X17, DicomPrintOptions.filmSizeOf("IN14X17", def)),
        () -> assertEquals(Paper.LETTER, DicomPrintOptions.filmSizeOf("IN8_5X11", def)),
        () -> assertEquals(Paper.CM_24X30, DicomPrintOptions.filmSizeOf("CM24X30", def)),
        () -> assertEquals(Paper.A4, DicomPrintOptions.filmSizeOf("A4", def)),
        () -> assertEquals(def, DicomPrintOptions.filmSizeOf("UNKNOWN", def)),
        () -> assertEquals(def, DicomPrintOptions.filmSizeOf(null, def)),
        () -> assertEquals(def, DicomPrintOptions.filmSizeOf(" ", def)));
  }

  @Test
  void a_stored_film_resolution_is_a_number_or_the_name_of_the_old_enum() {
    assertAll(
        () -> assertEquals(300, DicomPrintOptions.filmResolutionOf("300", 150)),
        () -> assertEquals(300, DicomPrintOptions.filmResolutionOf("DPI_300", 150)),
        () -> assertEquals(150, DicomPrintOptions.filmResolutionOf("DPI_X", 150)),
        () -> assertEquals(150, DicomPrintOptions.filmResolutionOf("-5", 150)),
        () -> assertEquals(150, DicomPrintOptions.filmResolutionOf(null, 150)));
  }

  @Test
  void setters_round_trip_and_null_film_size_falls_back() {
    DicomPrintOptions opts = new DicomPrintOptions();
    opts.setMediumType("CLEAR FILM");
    opts.setPriority("HIGH");
    opts.setFilmDestination("PROCESSOR");
    opts.setNumOfCopies(5);
    opts.setFilmOrientation("LANDSCAPE");
    opts.setFilmSizeId(Paper.A4);
    opts.setImageDisplayFormat("STANDARD\\2,3");
    opts.setMagnificationType("REPLICATE");
    opts.setSmoothingType("SHARP");
    opts.setBorderDensity("BLACK");
    opts.setTrim("YES");
    opts.setEmptyDensity("WHITE");
    opts.setMinDensity(10);
    opts.setMaxDensity(245);
    opts.setFilmResolution(300);
    assertAll(
        () -> assertEquals("CLEAR FILM", opts.getMediumType()),
        () -> assertEquals("HIGH", opts.getPriority()),
        () -> assertEquals("PROCESSOR", opts.getFilmDestination()),
        () -> assertEquals(5, opts.getNumOfCopies()),
        () -> assertEquals("LANDSCAPE", opts.getFilmOrientation()),
        () -> assertEquals(Paper.A4, opts.getFilmSizeId()),
        () -> assertEquals("STANDARD\\2,3", opts.getImageDisplayFormat()),
        () -> assertEquals("REPLICATE", opts.getMagnificationType()),
        () -> assertEquals("SHARP", opts.getSmoothingType()),
        () -> assertEquals("BLACK", opts.getBorderDensity()),
        () -> assertEquals("YES", opts.getTrim()),
        () -> assertEquals("WHITE", opts.getEmptyDensity()),
        () -> assertEquals(10, opts.getMinDensity()),
        () -> assertEquals(245, opts.getMaxDensity()),
        () -> assertEquals(300, opts.getFilmResolution()));

    opts.setFilmSizeId(null);
    opts.setFilmResolution(0);
    assertAll(
        () -> assertEquals(Paper.IN_8X10, opts.getFilmSizeId()),
        () -> assertEquals(150, opts.getFilmResolution()));
  }

  @Test
  void a_copy_is_independent_of_its_source() {
    DicomPrintOptions source = new DicomPrintOptions();
    source.setColorPrint(true);
    source.setFilmSizeId(Paper.A3);
    source.setFilmResolution(300);
    source.setTextSize(TextSize.LARGE);
    source.setBackground(Color.WHITE);
    source.setMediumType("PAPER");

    DicomPrintOptions copy = source.copy();
    copy.setMediumType("CLEAR FILM");
    copy.setColorPrint(false);

    assertAll(
        () -> assertEquals(Paper.A3, copy.getFilmSizeId()),
        () -> assertEquals(300, copy.getFilmResolution()),
        () -> assertEquals(TextSize.LARGE, copy.getTextSize()),
        () -> assertEquals(Color.WHITE, copy.getBackground()),
        () -> assertEquals("PAPER", source.getMediumType()),
        () -> assertTrue(source.isColorPrint()));
  }
}
