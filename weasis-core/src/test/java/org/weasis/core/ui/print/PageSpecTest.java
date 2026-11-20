/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.print.PageSpec.Margins;
import org.weasis.core.ui.print.PageSpec.Orientation;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PageSpecTest {

  @Test
  void landscape_swaps_the_paper_sides() {
    PageSpec portrait = PageSpec.of(Paper.A4, Orientation.PORTRAIT, Color.WHITE);
    PageSpec landscape = PageSpec.of(Paper.A4, Orientation.LANDSCAPE, Color.WHITE);
    assertAll(
        () -> assertEquals(210, portrait.widthMm()),
        () -> assertEquals(297, portrait.heightMm()),
        () -> assertEquals(297, landscape.widthMm()),
        () -> assertEquals(210, landscape.heightMm()));
  }

  @Test
  void printable_area_is_inside_the_margins() {
    PageSpec spec =
        new PageSpec(
            Paper.A4,
            Orientation.LANDSCAPE,
            new Margins(5, 10, 15, 20),
            PageSpec.DEFAULT_GUTTER_MM,
            Color.WHITE,
            null);
    Rectangle2D area = spec.printableArea();
    assertAll(
        () -> assertEquals(10, area.getX()),
        () -> assertEquals(5, area.getY()),
        () -> assertEquals(297 - 30, area.getWidth(), 1e-9),
        () -> assertEquals(210 - 20, area.getHeight(), 1e-9));
  }

  @Test
  void built_from_a_java_page_format() {
    PageFormat format = new PageFormat();
    java.awt.print.Paper paper = new java.awt.print.Paper();
    paper.setSize(Paper.mmToPoints(210), Paper.mmToPoints(297));
    paper.setImageableArea(
        Paper.mmToPoints(10), Paper.mmToPoints(12), Paper.mmToPoints(190), Paper.mmToPoints(270));
    format.setPaper(paper);
    format.setOrientation(PageFormat.LANDSCAPE);

    PageSpec spec = PageSpec.of(format, Color.BLACK, null);
    Rectangle2D area = spec.printableArea();
    assertAll(
        () -> assertSame(Paper.A4, spec.paper()),
        () -> assertEquals(Orientation.LANDSCAPE, spec.orientation()),
        () -> assertEquals(297, spec.widthMm()),
        // Java rotates the paper: its bottom margin (297 - 12 - 270) leads the landscape page and
        // its left margin becomes the top one
        () -> assertEquals(15, area.getX(), 0.01),
        () -> assertEquals(10, area.getY(), 0.01),
        () -> assertEquals(270, area.getWidth(), 0.01),
        () -> assertEquals(190, area.getHeight(), 0.01),
        () -> assertEquals(Color.BLACK, spec.background()));
  }

  @Test
  void a_printable_area_up_to_the_edge_keeps_a_margin() {
    PageFormat format = new PageFormat();
    java.awt.print.Paper paper = new java.awt.print.Paper();
    paper.setSize(Paper.mmToPoints(210), Paper.mmToPoints(297));
    paper.setImageableArea(0, 0, Paper.mmToPoints(210), Paper.mmToPoints(297));
    format.setPaper(paper);

    Rectangle2D area = PageSpec.of(format, Color.WHITE, null).printableArea();
    double margin = PageSpec.MIN_MARGIN_MM;
    assertAll(
        () -> assertEquals(margin, area.getX(), 0.01),
        () -> assertEquals(margin, area.getY(), 0.01),
        () -> assertEquals(210 - 2 * margin, area.getWidth(), 0.01),
        () -> assertEquals(297 - 2 * margin, area.getHeight(), 0.01));
  }

  @Test
  void the_header_band_takes_its_height_from_the_printable_area() {
    PageSpec plain = PageSpec.of(Paper.A4, PageSpec.Orientation.PORTRAIT, java.awt.Color.WHITE);
    PageSpec header = plain.withHeader(true);
    assertAll(
        () -> assertFalse(plain.hasHeader()),
        () -> assertTrue(header.headerArea().isEmpty() == false),
        () -> assertEquals(PageSpec.HEADER_MM, header.headerArea().getHeight()),
        () ->
            assertEquals(
                plain.printableArea().getY() + PageSpec.HEADER_MM, header.printableArea().getY()),
        () ->
            assertEquals(
                plain.printableArea().getHeight() - PageSpec.HEADER_MM,
                header.printableArea().getHeight()),
        () -> assertEquals(plain.printableArea().getWidth(), header.headerArea().getWidth()),
        () -> assertFalse(header.withHeader(false).hasHeader()));
  }
}
