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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.util.List;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.ResolutionSyntax;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.PrinterResolution;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.print.PageSpec.Orientation;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SystemPrinterDestinationTest {

  private static PrintService service(MediaSizeName defaultMedia, MediaSizeName... medias) {
    PrintService service = mock(PrintService.class);
    when(service.getName()).thenReturn("Mock printer");
    when(service.isDocFlavorSupported(any())).thenReturn(true);
    when(service.isAttributeValueSupported(any(), any(), any())).thenReturn(true);
    when(service.getSupportedAttributeValues(eq(Media.class), isNull(), isNull()))
        .thenReturn(medias.length == 0 ? null : medias);
    when(service.getDefaultAttributeValue(Media.class)).thenReturn(defaultMedia);
    return service;
  }

  @Test
  void papers_come_from_the_service_with_its_default_first() {
    SystemPrinterDestination printer =
        new SystemPrinterDestination(
            service(
                MediaSizeName.NA_LETTER,
                MediaSizeName.ISO_A4,
                MediaSizeName.NA_LETTER,
                MediaSizeName.ISO_A3,
                MediaSizeName.ISO_A4),
            true);
    assertAll(
        () -> assertEquals(List.of(Paper.LETTER, Paper.A4, Paper.A3), printer.papers()),
        () -> assertEquals(Paper.LETTER, printer.defaultPaper()),
        () -> assertEquals(Color.WHITE, printer.defaultBackground()),
        () -> assertTrue(printer.isColor()),
        () -> assertEquals("printer:Mock printer", printer.id()),
        () -> assertTrue(printer.title().startsWith("Mock printer")));
  }

  @Test
  void a_service_without_media_offers_the_catalogue() {
    SystemPrinterDestination printer = new SystemPrinterDestination(service(null), false);
    List<Paper> papers = printer.papers();
    assertAll(
        () -> assertEquals(SystemPrinterDestination.localeDefault(), papers.getFirst()),
        () -> assertEquals(Paper.CATALOGUE.size(), papers.size()),
        () -> assertEquals("Mock printer", printer.title()));
  }

  @Test
  void an_unknown_media_becomes_a_paper_named_after_it() {
    Paper paper = SystemPrinterDestination.paperOf(MediaSizeName.JIS_B5).orElseThrow();
    assertAll(
        () -> assertEquals("jis-b5", paper.id()),
        () -> assertEquals(182, paper.widthMm(), 0.5),
        () -> assertEquals(257, paper.heightMm(), 0.5),
        () -> assertEquals(MediaSizeName.JIS_B5, paper.media()),
        () -> assertEquals(Paper.A4, SystemPrinterDestination.paperOf(MediaSizeName.ISO_A4).get()));
  }

  @Test
  void the_job_attributes_carry_media_orientation_copies_and_default_margins() {
    SystemPrinterDestination printer =
        new SystemPrinterDestination(service(MediaSizeName.ISO_A4, MediaSizeName.ISO_A4), true);
    PrintRequestAttributeSet aset = printer.attributes(Paper.A4, Orientation.LANDSCAPE, 3);
    MediaPrintableArea area = (MediaPrintableArea) aset.get(MediaPrintableArea.class);
    assertAll(
        () -> assertEquals(MediaSizeName.ISO_A4, aset.get(Media.class)),
        () -> assertEquals(OrientationRequested.LANDSCAPE, aset.get(OrientationRequested.class)),
        () -> assertEquals(3, ((Copies) aset.get(Copies.class)).getValue()),
        () -> assertEquals(6, area.getX(MediaPrintableArea.MM), 0.01),
        () -> assertEquals(210 - 12, area.getWidth(MediaPrintableArea.MM), 0.01),
        () -> assertEquals(297 - 12, area.getHeight(MediaPrintableArea.MM), 0.01));
  }

  @Test
  void the_printable_area_of_the_service_is_preferred_to_the_default_margins() {
    PrintService service = service(MediaSizeName.ISO_A4, MediaSizeName.ISO_A4);
    when(service.getSupportedAttributeValues(eq(MediaPrintableArea.class), isNull(), any()))
        .thenReturn(
            new MediaPrintableArea[] {
              new MediaPrintableArea(4, 4, 202, 289, MediaPrintableArea.MM)
            });
    SystemPrinterDestination printer = new SystemPrinterDestination(service, true);
    PrintRequestAttributeSet aset = printer.attributes(Paper.A4, Orientation.PORTRAIT, 1);
    MediaPrintableArea area = (MediaPrintableArea) aset.get(MediaPrintableArea.class);
    assertAll(
        () -> assertEquals(4, area.getX(MediaPrintableArea.MM), 0.01),
        () -> assertEquals(202, area.getWidth(MediaPrintableArea.MM), 0.01),
        // An area that does not fit the paper is not the paper's: the default margins apply
        () ->
            assertEquals(
                6,
                ((MediaPrintableArea)
                        printer
                            .attributes(Paper.A5, Orientation.PORTRAIT, 1)
                            .get(MediaPrintableArea.class))
                    .getX(MediaPrintableArea.MM),
                0.01));
  }

  @Test
  void a_printable_area_returned_for_another_media_is_dropped() {
    SystemPrinterDestination printer =
        new SystemPrinterDestination(service(MediaSizeName.ISO_A4, MediaSizeName.ISO_A4), true);
    PrintRequestAttributeSet fromDialog = new HashPrintRequestAttributeSet();
    fromDialog.add(MediaSizeName.NA_LETTER);
    fromDialog.add(new MediaPrintableArea(10, 10, 195, 259, MediaPrintableArea.MM));
    fromDialog.add(new PrinterResolution(600, 600, ResolutionSyntax.DPI));
    printer.getAttributes().addAll(fromDialog);

    PrintRequestAttributeSet same = printer.attributes(Paper.LETTER, Orientation.PORTRAIT, 1);
    PrintRequestAttributeSet other = printer.attributes(Paper.A4, Orientation.PORTRAIT, 1);
    MediaPrintableArea kept = (MediaPrintableArea) same.get(MediaPrintableArea.class);
    MediaPrintableArea replaced = (MediaPrintableArea) other.get(MediaPrintableArea.class);
    assertAll(
        () -> assertEquals(10, kept.getX(MediaPrintableArea.MM), 0.01),
        () -> assertEquals(6, replaced.getX(MediaPrintableArea.MM), 0.01),
        () -> assertEquals(600, printer.resolutionDpi().getAsInt()),
        () -> assertEquals(Paper.LETTER, printer.paper().orElseThrow()),
        () -> assertFalse(printer.orientation().isPresent()),
        () -> assertFalse(printer.copies().isPresent()));
  }

  @Test
  void the_page_spec_is_the_one_the_print_job_builds() {
    SystemPrinterDestination printer =
        new SystemPrinterDestination(service(MediaSizeName.ISO_A4, MediaSizeName.ISO_A4), true);
    PageSpec spec = printer.pageSpec(Paper.A4, Orientation.LANDSCAPE, Color.BLACK, null);
    assertAll(
        () -> assertEquals(Paper.A4, spec.paper()),
        () -> assertEquals(Orientation.LANDSCAPE, spec.orientation()),
        () -> assertEquals(297, spec.widthMm(), 0.1),
        () -> assertEquals(210, spec.heightMm(), 0.1),
        () -> assertEquals(6, spec.margins().left(), 0.1),
        () -> assertEquals(6, spec.margins().top(), 0.1),
        () -> assertEquals(Color.BLACK, spec.background()));
  }
}
