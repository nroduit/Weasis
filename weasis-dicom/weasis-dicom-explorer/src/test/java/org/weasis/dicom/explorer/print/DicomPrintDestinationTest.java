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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.print.PageSpec;
import org.weasis.core.ui.print.PageSpec.Orientation;
import org.weasis.core.ui.print.Paper;
import org.weasis.dicom.explorer.pref.node.DicomPrintNode;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomPrintDestinationTest {

  private static DicomPrintNode node() {
    DicomPrintOptions options = new DicomPrintOptions();
    options.setFilmSizeId(Paper.IN_14X17);
    options.setFilmOrientation("LANDSCAPE");
    options.setEmptyDensity("WHITE");
    options.setColorPrint(true);
    options.setFilmResolution(300);
    return new DicomPrintNode("Film printer", "PRINT_SCP", "printer.example.org", 104, options);
  }

  @Test
  void the_destination_describes_the_node_and_its_defaults() {
    DicomPrintDestination destination = new DicomPrintDestination(node());
    assertAll(
        () -> assertEquals("dicom:PRINT_SCP@printer.example.org:104", destination.id()),
        () -> assertEquals("Film printer", destination.title()),
        () -> assertEquals(Paper.IN_14X17, destination.defaultPaper()),
        () -> assertEquals(Paper.IN_14X17, destination.papers().getFirst()),
        () -> assertTrue(destination.papers().stream().allMatch(Paper::hasFilmSizeId)),
        () -> assertEquals(1, destination.papers().stream().filter(Paper.IN_14X17::equals).count()),
        () -> assertEquals(Color.WHITE, destination.defaultBackground()),
        () -> assertTrue(destination.isColor()),
        () -> assertEquals(Orientation.LANDSCAPE, destination.preferredOrientation().orElseThrow()),
        () -> assertEquals(300, destination.resolutionDpi().getAsInt()));
  }

  @Test
  void the_film_has_a_small_margin_and_the_job_options_are_a_copy() {
    DicomPrintNode node = node();
    DicomPrintDestination destination = new DicomPrintDestination(node);
    PageSpec spec = destination.pageSpec(Paper.A4, Orientation.PORTRAIT, Color.BLACK, null);
    destination.getOptions().setColorPrint(false);
    assertAll(
        () -> assertEquals(DicomPrintDestination.FILM_MARGIN_MM, spec.margins().left()),
        () -> assertEquals(210, spec.widthMm(), 0.01),
        () -> assertEquals(Color.BLACK, spec.background()),
        () -> assertNotSame(node.getPrintOptions(), destination.getOptions()),
        () -> assertTrue(node.getPrintOptions().isColorPrint()),
        () -> assertFalse(destination.isColor()));
  }

  @Test
  void a_node_without_description_is_named_after_its_ae_title() {
    DicomPrintNode node = new DicomPrintNode(null, "PRINT_SCP", "host", 104);
    DicomPrintDestination destination = new DicomPrintDestination(node);
    assertAll(
        () -> assertEquals("PRINT_SCP", destination.title()),
        () -> assertEquals(Color.BLACK, destination.defaultBackground()),
        () -> assertEquals(Orientation.PORTRAIT, destination.preferredOrientation().orElseThrow()),
        () -> assertEquals(150, destination.resolutionDpi().getAsInt()));
  }
}
