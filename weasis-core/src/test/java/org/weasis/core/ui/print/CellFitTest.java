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

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.print.CellFit.SourceGeometry;

@DisplayNameGeneration(ReplaceUnderscores.class)
class CellFitTest {

  private static final Point2D ORIGIN = new Point2D.Double();

  @Test
  void best_fit_takes_the_aspect_ratio_of_the_image_and_centers_it() {
    SourceGeometry source =
        new SourceGeometry(new Rectangle2D.Double(0, 0, 800, 600), 0.0, ORIGIN, 640, 480, 0);
    CellFit fit = CellFit.fit(source, 1000, 500, true);
    assertAll(
        () -> assertTrue(fit.bestFit()),
        () -> assertEquals(666, fit.viewportWidth()),
        () -> assertEquals(500, fit.viewportHeight()),
        () -> assertEquals(500.0 / 480, fit.zoom(), 1e-9),
        () -> assertEquals((1000 - 666) / 2.0, fit.padX(), 1e-9),
        () -> assertEquals(0, fit.padY(), 1e-9));
  }

  @Test
  void top_left_alignment_has_no_padding() {
    SourceGeometry source = new SourceGeometry(null, 0.0, ORIGIN, 100, 100, 0);
    CellFit fit = CellFit.fit(source, 300, 200, false);
    assertAll(
        () -> assertEquals(200, fit.viewportWidth()),
        () -> assertEquals(200, fit.viewportHeight()),
        () -> assertEquals(0, fit.padX()),
        () -> assertEquals(0, fit.padY()));
  }

  @Test
  void a_zoomed_view_reproduces_its_canvas_and_keeps_its_center() {
    Point2D center = new Point2D.Double(12.5, -4);
    // A 800 x 400 canvas at zoom 2 shows 400 x 200 image pixels
    SourceGeometry source =
        new SourceGeometry(new Rectangle2D.Double(0, 0, 800, 400), 2.0, center, 1024, 1024, 0);
    CellFit fit = CellFit.fit(source, 1000, 1000, true);
    assertAll(
        () -> assertFalse(fit.bestFit()),
        () -> assertEquals(1000, fit.viewportWidth()),
        () -> assertEquals(500, fit.viewportHeight()),
        () -> assertEquals(2.5, fit.zoom(), 1e-9),
        () -> assertEquals(center, fit.center()));
  }

  @Test
  void a_quarter_turn_swaps_the_sides_of_a_best_fit_image() {
    SourceGeometry source = new SourceGeometry(null, 0.0, ORIGIN, 640, 480, 90);
    CellFit fit = CellFit.fit(source, 1000, 1000, true);
    assertAll(
        () -> assertEquals(750, fit.viewportWidth()),
        () -> assertEquals(1000, fit.viewportHeight()),
        () -> assertEquals(1000.0 / 640, fit.zoom(), 1e-9));
    assertEquals(
        1000,
        CellFit.fit(new SourceGeometry(null, 0.0, ORIGIN, 640, 480, 270), 1000, 1000, true)
            .viewportHeight());
    assertEquals(
        1000,
        CellFit.fit(new SourceGeometry(null, 0.0, ORIGIN, 640, 480, 180), 1000, 1000, true)
            .viewportWidth());
  }

  @Test
  void a_degenerate_image_still_gives_a_viewport() {
    CellFit fit = CellFit.fit(new SourceGeometry(null, 0.0, ORIGIN, 0, 0, 0), 100, 100, true);
    assertAll(
        () -> assertEquals(100, fit.viewportWidth()),
        () -> assertEquals(100, fit.viewportHeight()));
  }
}
