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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterGraphics;
import java.awt.print.PrinterJob;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.ResolutionSyntax;
import javax.print.attribute.standard.PrinterResolution;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PrintResolutionTest {

  /** A Graphics2D of a printer, whose default transform scales points to device pixels. */
  abstract static class PrinterG2d extends Graphics2D implements PrinterGraphics {
    @Override
    public PrinterJob getPrinterJob() {
      return null;
    }
  }

  private static Graphics2D printerGraphics(double dpi) {
    PrinterG2d g = mock(PrinterG2d.class);
    GraphicsConfiguration gc = mock(GraphicsConfiguration.class);
    when(gc.getDefaultTransform()).thenReturn(AffineTransform.getScaleInstance(dpi / 72, dpi / 72));
    when(g.getDeviceConfiguration()).thenReturn(gc);
    return g;
  }

  private static PrintService service(Integer dpi) {
    PrintService service = mock(PrintService.class);
    when(service.getDefaultAttributeValue(PrinterResolution.class))
        .thenReturn(dpi == null ? null : new PrinterResolution(dpi, dpi, ResolutionSyntax.DPI));
    return service;
  }

  @Test
  void the_service_resolution_wins_over_the_graphics() {
    assertEquals(1200, PrintResolution.resolve(service(1200), printerGraphics(600), 2400));
  }

  @Test
  void the_graphics_of_a_printer_gives_the_resolution_when_the_service_has_none() {
    assertAll(
        () -> assertEquals(600, PrintResolution.resolve(service(null), printerGraphics(600), 600)),
        () -> assertEquals(600, PrintResolution.resolve(null, printerGraphics(600), 1200)));
  }

  @Test
  void an_image_graphics_is_not_a_printer_and_falls_back_to_the_default() {
    Graphics2D g = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB).createGraphics();
    assertAll(
        () -> assertTrue(PrintResolution.fromGraphics(g).isEmpty()),
        () -> assertEquals(PrintResolution.DEFAULT_DPI, PrintResolution.resolve(null, g, 600)));
  }

  @Test
  void the_cap_bounds_the_device_resolution() {
    assertAll(
        () -> assertEquals(600, PrintResolution.resolve(service(1200), null, 600)),
        () -> assertEquals(72, PrintResolution.resolve(service(1200), null, 10)),
        // A resolution under 72 dpi is not a printer's: it is ignored for the default
        () -> assertEquals(300, PrintResolution.resolve(service(10), null, 600)));
  }

  @Test
  void a_failing_service_is_ignored() {
    PrintService service = mock(PrintService.class);
    when(service.getDefaultAttributeValue(PrinterResolution.class))
        .thenThrow(new IllegalStateException("offline"));
    assertEquals(300, PrintResolution.resolve(service, null, 600));
  }

  @Test
  void the_resolution_requested_by_the_job_wins_over_the_service() {
    PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
    aset.add(new PrinterResolution(1200, 1200, ResolutionSyntax.DPI));
    assertAll(
        () -> assertEquals(1200, PrintResolution.fromAttributes(aset).getAsInt()),
        () -> assertTrue(PrintResolution.fromAttributes(null).isEmpty()),
        () ->
            assertTrue(
                PrintResolution.fromAttributes(new HashPrintRequestAttributeSet()).isEmpty()),
        () -> assertEquals(1200, PrintResolution.resolve(aset, service(300), null, 2400)),
        () -> assertEquals(600, PrintResolution.resolve(aset, service(300), null, 600)),
        () ->
            assertEquals(
                300,
                PrintResolution.resolve(
                    new HashPrintRequestAttributeSet(), service(300), null, 600)));
  }

  @Test
  void pixel_budget_shrinks_only_oversized_rasters() {
    assertAll(
        () -> assertEquals(1.0, PrintResolution.budgetScale(2000, 1500, 32_000_000L)),
        () -> assertEquals(0.5, PrintResolution.budgetScale(8000, 4000, 8_000_000L), 1e-9),
        () -> assertEquals(1.0, PrintResolution.budgetScale(0, 0, 1)));
  }

  @Test
  void pixels_per_millimetre() {
    assertEquals(600 / 25.4, PrintResolution.pixelsPerMm(600), 1e-9);
  }
}
