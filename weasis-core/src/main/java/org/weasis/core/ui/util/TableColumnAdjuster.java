/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.weasis.core.api.gui.util.GuiUtils;

public class TableColumnAdjuster {

  private TableColumnAdjuster() {}

  private static int preferredWidth(JTable table, int col) {
    TableColumn tableColumn = table.getColumnModel().getColumn(col);
    int width =
        (int)
            table
                .getTableHeader()
                .getDefaultRenderer()
                .getTableCellRendererComponent(
                    table, tableColumn.getIdentifier(), false, false, -1, col)
                .getPreferredSize()
                .getWidth();

    if (table.getRowCount() != 0) {
      Rectangle rect = table.getVisibleRect();
      int from = table.rowAtPoint(rect.getLocation());
      int to = table.rowAtPoint(new Point((int) rect.getMaxX(), (int) rect.getMaxY())) + 1;
      if (to == 0) {
        to = Math.max(to, table.getRowCount());
      }

      for (int row = from; row < to; row++) {
        try {
          int preferredWidth =
              (int)
                  table
                      .getCellRenderer(row, col)
                      .getTableCellRendererComponent(
                          table, table.getValueAt(row, col), false, false, row, col)
                      .getPreferredSize()
                      .getWidth();
          width = Math.max(width, preferredWidth);
        } catch (Exception e) {
          // Do noting
        }
      }
    }
    return width + table.getIntercellSpacing().width + GuiUtils.getScaleLength(7);
  }

  public static void pack(JTable table) {
    if (!table.isShowing() || table.getColumnCount() == 0) {
      return;
    }

    int[] width = new int[table.getColumnCount()];
    int total = 0;
    for (int col = 0; col < width.length; col++) {
      width[col] = preferredWidth(table, col);
      total += width[col];
    }

    int extra = table.getVisibleRect().width - total;
    if (extra > 0 && width.length > 0 && table.getColumnCount() > 0) {
      int bonus = extra / table.getColumnCount();
      for (int i = 0; i < width.length; i++) {
        width[i] += bonus;
      }
      extra -= bonus * table.getColumnCount();

      width[width.length - 1] += extra;
    }

    TableColumnModel columnModel = table.getColumnModel();
    for (int col = 0; col < width.length; col++) {
      TableColumn tableColumn = columnModel.getColumn(col);
      table.getTableHeader().setResizingColumn(tableColumn);
      tableColumn.setWidth(width[col]);
    }
  }

  public static void adjustPreferredSizeForViewPort(JTable jtable, JPanel tableContainer) {
    pack(jtable);
    int height =
        (jtable.getRowHeight() + jtable.getRowMargin()) * jtable.getRowCount()
            + jtable.getTableHeader().getHeight()
            + GuiUtils.insetHeight(tableContainer);
    int width = jtable.getColumnModel().getTotalColumnWidth();
    tableContainer.setPreferredSize(new Dimension(width, height));
    tableContainer.add(jtable.getTableHeader(), BorderLayout.PAGE_START);
    tableContainer.add(jtable, BorderLayout.CENTER);
    pack(jtable);
  }
}
