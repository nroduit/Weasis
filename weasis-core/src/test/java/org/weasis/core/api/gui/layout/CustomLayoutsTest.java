/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.layout;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.gui.layout.CustomLayouts.Grid;
import org.weasis.core.api.gui.layout.CustomLayouts.Span;
import org.weasis.core.api.gui.layout.CustomLayouts.Tiles;

class CustomLayoutsTest {

  @Test
  void aGridReadsRowsThenColumns() {
    MigLayoutModel model = new Grid("user.1", "Wide", 1, 3, List.of()).toModel("x"); // NON-NLS
    Dimension size = model.getGridSize();
    assertAll(
        () -> assertEquals(3, size.width),
        () -> assertEquals(1, size.height),
        () -> assertEquals(3, model.getCellCount()),
        () -> assertEquals("Wide", model.getUIName()),
        () -> assertEquals("2x3", new Tiles(2, 3).toString())); // NON-NLS
  }

  @Test
  void mergingAbsorbsTheSpansItTouchesAndSplittingReturnsTheCells() {
    Grid grid = new Grid("user.1", "g", 3, 3, List.of()); // NON-NLS
    Grid left = grid.merge(0, 0, 2, 1);
    assertEquals(List.of(new Span(0, 0, 2, 1)), left.merged());
    assertEquals(8, left.cellCount());
    // a rectangle touching the merged cell grows to hold it
    Grid block = left.merge(1, 0, 1, 2);
    assertEquals(List.of(new Span(0, 0, 2, 2)), block.merged());
    assertEquals(6, block.cellCount());
    assertEquals(6, block.toModel("x").getCellCount()); // NON-NLS
    assertTrue(block.split(1, 1, 1, 1).merged().isEmpty());
  }

  @Test
  void aSmallerGridDropsTheSpansThatLeaveIt() {
    Grid grid = new Grid("user.1", "g", 3, 3, List.of(new Span(0, 2, 2, 1), new Span(2, 0, 1, 2)));
    Grid smaller = grid.resized(3, 2);
    assertEquals(List.of(new Span(2, 0, 1, 2)), smaller.merged());
    // a one-cell span and an overlapping one are not kept
    Grid odd =
        new Grid(
            "user.2",
            null,
            2,
            2,
            List.of(new Span(0, 0, 1, 1), new Span(0, 0, 2, 1), new Span(1, 0, 1, 2)));
    assertEquals(List.of(new Span(0, 0, 2, 1)), odd.merged());
    assertEquals("2x2", odd.name()); // NON-NLS
  }

  @Test
  void theDocumentKeepsGridsAndTileGrids(@TempDir Path dir) throws Exception {
    Path file = dir.resolve(CustomLayouts.DOCUMENT);
    CustomLayouts.install(file);
    Grid grid = new Grid("user.1", "Reading", 2, 3, List.of(new Span(0, 0, 2, 1))); // NON-NLS
    CustomLayouts.replace(List.of(grid), List.of(new Tiles(5, 2), new Tiles(1, 3)));
    CustomLayouts.install(file); // read again from the file
    assertAll(
        () -> assertEquals(List.of(grid), CustomLayouts.grids()),
        () -> assertEquals(List.of(new Tiles(1, 3), new Tiles(5, 2)), CustomLayouts.tiles()),
        () -> assertEquals("user.2", CustomLayouts.newId(Set.of())), // NON-NLS
        () -> assertEquals("user.3", CustomLayouts.newId(Set.of("user.2"))), // NON-NLS
        () -> assertTrue(CustomLayouts.isCustom(CustomLayouts.models("x").getFirst().getId())));
    CustomLayouts.replace(List.of(), List.of(new Tiles(1, 3)));
    assertAll(
        () -> assertTrue(CustomLayouts.grids().isEmpty()),
        () -> assertEquals(List.of(new Tiles(1, 3)), CustomLayouts.tiles()));
    CustomLayouts.install(null);
  }

  @Test
  void aCellHoldsAComponentAndOneViewportAlwaysRemains() {
    Grid grid = new Grid("user.1", "g", 1, 2, List.of()); // NON-NLS
    Grid withChart = grid.withContent(0, 1, "histogram"); // NON-NLS
    assertAll(
        () -> assertEquals("histogram", withChart.contentAt(0, 1)), // NON-NLS
        () -> assertEquals(1, withChart.viewCount()),
        () -> assertEquals(2, withChart.cellCount()),
        // the last viewport cannot become a component
        () -> assertEquals(withChart, withChart.withContent(0, 0, "histogram")), // NON-NLS
        // back to a viewport
        () -> assertEquals(2, withChart.withContent(0, 1, null).viewCount()));

    MigLayoutModel model =
        withChart.toModel("view", id -> "histogram".equals(id) ? "chart" : null); // NON-NLS
    assertAll(
        () -> assertEquals("view", model.getCells().get(0).type()), // NON-NLS
        () -> assertEquals("chart", model.getCells().get(1).type())); // NON-NLS
    // the title names the components, so the menu tells such a layout from a plain grid
    assertEquals(
        "g (Chart)", // NON-NLS
        withChart.toModel("view", id -> "chart", id -> "Chart").getUIName()); // NON-NLS
    // a component whose plugin is absent leaves a viewport
    MigLayoutModel plain = withChart.toModel("view", id -> null); // NON-NLS
    assertEquals("view", plain.getCells().get(1).type()); // NON-NLS
  }

  @Test
  void theContentFollowsMergedCellsAndIsSaved(@TempDir Path dir) throws Exception {
    Grid grid =
        new Grid("user.1", "g", 2, 2, List.of()) // NON-NLS
            .merge(0, 1, 2, 1)
            .withContent(1, 1, "dicom-information"); // NON-NLS
    // set through any cell of the merged cell, kept at its top left
    assertEquals(
        List.of(new CustomLayouts.Content(0, 1, "dicom-information")), grid.contents()); // NON-NLS
    // a content that is no longer the top left of a cell is dropped
    assertTrue(grid.withContent(0, 0, "histogram").merge(0, 0, 2, 2).contents().isEmpty());

    Path file = dir.resolve(CustomLayouts.DOCUMENT);
    CustomLayouts.install(file);
    CustomLayouts.replace(List.of(grid), List.of());
    CustomLayouts.install(file);
    assertEquals(List.of(grid), CustomLayouts.grids());
    assertEquals(
        "tags", // NON-NLS
        CustomLayouts.models("view", id -> "tags").getFirst().getCells().get(1).type()); // NON-NLS
    CustomLayouts.install(null);
  }
}
