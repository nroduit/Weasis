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

import java.awt.geom.Rectangle2D;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.ui.util.PrintOptions;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PageFiguresTest {

  private static CellBox box(double widthMm, double heightMm) {
    return new CellBox(
        new MigCell(0, "view", "", 0, 0, 1, 1), // NON-NLS
        new Rectangle2D.Double(10, 10, widthMm, heightMm));
  }

  private static CellFit.SourceGeometry image(int width, int height) {
    return new CellFit.SourceGeometry(null, 0, null, width, height, 0);
  }

  @Test
  void a_small_image_in_a_large_cell_is_magnified() {
    PageFigures figures = PageFigures.of(image(64, 64), box(85, 64), new PrintOptions(), 600);
    assertAll(
        () -> assertEquals(85, figures.cellWidthMm()),
        () -> assertEquals(64, figures.cellHeightMm()),
        () -> assertEquals(TextSize.NORMAL, figures.textSize()),
        () -> assertTrue(figures.magnified()),
        // 64 image pixels fill the 64 mm of the cell height: 64 px per 2.52 in
        () -> assertEquals(64 / (64 / 25.4), figures.imagePixelsPerInch(), 0.5));
  }

  @Test
  void a_large_image_in_a_small_cell_is_reduced_with_small_text() {
    PageFigures figures = PageFigures.of(image(4000, 3000), box(50, 40), new PrintOptions(), 300);
    assertAll(
        () -> assertEquals(TextSize.SMALL, figures.textSize()),
        () -> assertFalse(figures.magnified()),
        // The width limits: 4000 image pixels over the 50 mm of the cell
        () -> assertEquals(4000 / (50 / 25.4), figures.imagePixelsPerInch(), 1.0));
  }

  @Test
  void the_chosen_text_size_wins_over_the_cell_width() {
    PrintOptions options = new PrintOptions();
    options.setTextSize(TextSize.LARGE);
    assertEquals(
        TextSize.LARGE, PageFigures.of(image(512, 512), box(50, 40), options, 300).textSize());
  }
}
