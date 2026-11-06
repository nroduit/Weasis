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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.ui.print.PageSpec.Margins;
import org.weasis.core.ui.print.PageSpec.Orientation;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PageLayoutTest {

  private static PageSpec a4Landscape(double marginMm, double gutterMm) {
    return new PageSpec(
        Paper.A4, Orientation.LANDSCAPE, Margins.uniform(marginMm), gutterMm, Color.WHITE, null);
  }

  @Test
  void two_by_three_grid_on_a4_landscape() {
    MigLayoutModel grid = new MigLayoutModel("2x3", "2x3", 2, 3, "view"); // NON-NLS
    List<CellBox> boxes = PageLayout.layout(grid, a4Landscape(6, 2));

    // printable 285 x 198 mm; columns (285 - 2*2) / 3 = 93.667; rows (198 - 2) / 2 = 98
    assertEquals(6, boxes.size());
    CellBox first = boxes.getFirst();
    CellBox last = boxes.getLast();
    assertAll(
        () -> assertEquals(6, first.boundsMm().getX(), 1e-9),
        () -> assertEquals(6, first.boundsMm().getY(), 1e-9),
        () -> assertEquals(281.0 / 3, first.widthMm(), 1e-9),
        () -> assertEquals(98, first.heightMm(), 1e-9),
        () -> assertEquals(6 + 2 * (281.0 / 3 + 2), last.boundsMm().getX(), 1e-9),
        () -> assertEquals(6 + 98 + 2, last.boundsMm().getY(), 1e-9),
        () -> assertEquals(291, last.boundsMm().getMaxX(), 1e-9),
        () -> assertEquals(204, last.boundsMm().getMaxY(), 1e-9));
  }

  @Test
  void a_span_covers_its_columns_and_the_gutters_between_them() {
    MigCell wide = new MigCell(0, "view", "", 0, 0, 2, 1); // NON-NLS
    MigCell right = new MigCell(1, "view", "", 2, 0, 1, 1); // NON-NLS
    MigLayoutModel grid =
        new MigLayoutModel("1x3", "1x3", "", "", "", List.of(wide, right)); // NON-NLS

    List<CellBox> boxes = PageLayout.layout(grid, a4Landscape(0, 3));
    double column = (297 - 6) / 3.0;
    Rectangle2D wideBox = PageLayout.find(boxes, wide).boundsMm();
    Rectangle2D rightBox = PageLayout.find(boxes, right).boundsMm();
    assertAll(
        () -> assertEquals(2 * column + 3, wideBox.getWidth(), 1e-9),
        () -> assertEquals(210, wideBox.getHeight(), 1e-9),
        () -> assertEquals(2 * column + 6, rightBox.getX(), 1e-9),
        () -> assertEquals(297, rightBox.getMaxX(), 1e-9),
        () ->
            assertNull(PageLayout.find(boxes, new MigCell(7, "view", "", 0, 0, 1, 1)))); // NON-NLS
  }
}
