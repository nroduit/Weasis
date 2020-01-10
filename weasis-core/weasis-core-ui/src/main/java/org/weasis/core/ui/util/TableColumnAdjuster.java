/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.util;

import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

public class TableColumnAdjuster {
    public static final int VISIBLE_ROWS = 0;
    public static final int ALL_ROWS = 1;
    public static final int NO_ROWS = 2;

    private TableColumnAdjuster() {
    }

    private static int preferredWidth(JTable table, int col) {
        TableColumn tableColumn = table.getColumnModel().getColumn(col);
        int width = (int) table.getTableHeader().getDefaultRenderer()
            .getTableCellRendererComponent(table, tableColumn.getIdentifier(), false, false, -1, col).getPreferredSize()
            .getWidth();

        if (table.getRowCount() != 0) {
            Rectangle rect = table.getVisibleRect();
            int from = table.rowAtPoint(rect.getLocation());
            int to = table.rowAtPoint(new Point((int) rect.getMaxX(), (int) rect.getMaxY())) + 1;

            for (int row = from; row < to; row++) {
                int preferedWidth = (int) table.getCellRenderer(row, col)
                    .getTableCellRendererComponent(table, table.getValueAt(row, col), false, false, row, col)
                    .getPreferredSize().getWidth();
                width = Math.max(width, preferedWidth);
            }
        }
        return width + table.getIntercellSpacing().width;
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
        if (extra > 0) {

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
}
