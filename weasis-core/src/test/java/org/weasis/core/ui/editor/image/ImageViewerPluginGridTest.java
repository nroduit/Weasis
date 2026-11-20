/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.layout.MigLayoutModel;

/** The grids reached by their id and the grid fitted to a number of series. */
class ImageViewerPluginGridTest {

  @Test
  void anIdReadsRowsThenColumns() {
    MigLayoutModel grid = ImageViewerPlugin.gridOf("3x4").orElseThrow(); // NON-NLS
    Dimension size = grid.getGridSize();
    assertAll(
        () -> assertEquals("3x4", grid.getId()), // NON-NLS
        () -> assertEquals(4, size.width),
        () -> assertEquals(3, size.height),
        () -> assertEquals(12, grid.getCellCount()));
  }

  @Test
  void whatIsNotAGridOfAReasonableSizeIsRefused() {
    assertAll(
        () -> assertTrue(ImageViewerPlugin.gridOf(null).isEmpty()),
        () -> assertTrue(ImageViewerPlugin.gridOf("layout_dump").isEmpty()), // NON-NLS
        () -> assertTrue(ImageViewerPlugin.gridOf("0x3").isEmpty()), // NON-NLS
        () -> assertTrue(ImageViewerPlugin.gridOf("3x21").isEmpty()), // NON-NLS
        () -> assertTrue(ImageViewerPlugin.gridOf("3x").isEmpty()), // NON-NLS
        () -> assertTrue(ImageViewerPlugin.gridOf("20x20").isPresent())); // NON-NLS
  }

  @Test
  void theFittedGridFollowsTheShapeOfTheArea() {
    assertAll(
        // a landscape area takes more columns than rows, a portrait one the reverse
        () -> assertEquals("3x4", ImageViewerPlugin.fittingGrid(12, 16 / 9.0).getId()), // NON-NLS
        () -> assertEquals("4x3", ImageViewerPlugin.fittingGrid(12, 9 / 16.0).getId()), // NON-NLS
        () -> assertEquals("3x3", ImageViewerPlugin.fittingGrid(9, 16 / 9.0).getId()), // NON-NLS
        () -> assertEquals("3x5", ImageViewerPlugin.fittingGrid(13, 16 / 9.0).getId()), // NON-NLS
        // an area without a size yet is taken as a landscape screen
        () -> assertEquals("3x4", ImageViewerPlugin.fittingGrid(12, 0).getId()), // NON-NLS
        () -> assertEquals("1x1", ImageViewerPlugin.fittingGrid(0, 1.6).getId())); // NON-NLS
  }

  @Test
  void theFittedGridAlwaysHoldsTheSeries() {
    for (int n = 1; n <= 60; n++) {
      for (double ratio : new double[] {0.5, 1.0, 1.78, 2.4}) {
        MigLayoutModel grid = ImageViewerPlugin.fittingGrid(n, ratio);
        int count = n;
        assertTrue(grid.getCellCount() >= count, n + " series in " + grid.getId());
      }
    }
  }
}
