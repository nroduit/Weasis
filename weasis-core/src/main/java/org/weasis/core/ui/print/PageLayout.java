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

import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.api.gui.layout.MigLayoutModel;

/**
 * Places the cells of a layout grid on a page: a uniform grid inside the printable area, spans
 * included, with the gutter of the page between the cells.
 */
public final class PageLayout {

  private PageLayout() {}

  public static List<CellBox> layout(MigLayoutModel grid, PageSpec spec) {
    Rectangle2D area = spec.printableArea();
    Dimension size = grid.getGridSize();
    int cols = Math.max(1, size.width);
    int rows = Math.max(1, size.height);
    double gutter = spec.gutterMm();
    double colWidth = Math.max(0, (area.getWidth() - (cols - 1) * gutter) / cols);
    double rowHeight = Math.max(0, (area.getHeight() - (rows - 1) * gutter) / rows);

    return grid.getCells().stream()
        .map(
            cell -> {
              double x = area.getX() + cell.x() * (colWidth + gutter);
              double y = area.getY() + cell.y() * (rowHeight + gutter);
              double w = cell.spanX() * colWidth + (cell.spanX() - 1) * gutter;
              double h = cell.spanY() * rowHeight + (cell.spanY() - 1) * gutter;
              return new CellBox(cell, new Rectangle2D.Double(x, y, w, h));
            })
        .toList();
  }

  /** Splits a cell into {@code cols} by {@code rows} tiles in reading order, gutters between. */
  public static List<CellBox> tile(CellBox box, int cols, int rows, double gutterMm) {
    Rectangle2D area = box.boundsMm();
    int c = Math.max(1, cols);
    int r = Math.max(1, rows);
    double w = Math.max(0, (area.getWidth() - (c - 1) * gutterMm) / c);
    double h = Math.max(0, (area.getHeight() - (r - 1) * gutterMm) / r);
    List<CellBox> tiles = new java.util.ArrayList<>(c * r);
    for (int y = 0; y < r; y++) {
      for (int x = 0; x < c; x++) {
        MigCell cell =
            new MigCell(
                box.cell().position() * 1000 + y * c + x, box.cell().type(), "", x, y, 1, 1);
        tiles.add(
            new CellBox(
                cell,
                new Rectangle2D.Double(
                    area.getX() + x * (w + gutterMm), area.getY() + y * (h + gutterMm), w, h)));
      }
    }
    return tiles;
  }

  /** The box of the cell at this grid position, if the layout has one. */
  public static CellBox find(List<CellBox> boxes, MigCell cell) {
    return boxes.stream()
        .filter(b -> b.cell().position() == cell.position())
        .findFirst()
        .orElse(null);
  }
}
