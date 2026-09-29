/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.model.utils.MeasureFormat;
import org.weasis.core.ui.model.utils.bean.MeasureItem;

/**
 * What the tables of measured values share: values shown with the decimals that suit them while the
 * model keeps the numbers, and a menu that copies them at full precision.
 */
public final class MeasureTables {

  private static final int MIN_NAME_WIDTH = 40;
  private static final int CELL_MARGIN = 7;

  private MeasureTables() {}

  /** Renderer of the value column of a table whose rows are the given items, in order. */
  public static TableCellRenderer valueRenderer(List<MeasureItem> items, MeasureFormat format) {
    return new ValueRenderer(List.copyOf(items), format);
  }

  /**
   * Puts a name / value table in its container so that it reads well at any width. With room to
   * spare the columns are as wide as their longest text and no wider, so a value stays next to its
   * name instead of drifting to the far side of a wide panel. Without enough room the value column
   * keeps its width and the names are shortened, with their full text as tooltip.
   */
  public static void fit(JTable table, JPanel container) {
    table.getTableHeader().setResizingColumn(null);
    TableColumn names = table.getColumnModel().getColumn(0);
    TableColumn values = table.getColumnModel().getColumn(1);
    int nameWidth = contentWidth(table, 0);
    int valueWidth = contentWidth(table, 1);
    names.setCellRenderer(new NameRenderer());
    names.setMinWidth(Math.min(nameWidth, GuiUtils.getScaleLength(MIN_NAME_WIDTH)));
    names.setPreferredWidth(nameWidth);
    names.setMaxWidth(nameWidth);
    values.setMinWidth(valueWidth);
    values.setPreferredWidth(valueWidth);
    values.setMaxWidth(valueWidth);

    JTableHeader header = table.getTableHeader();
    int height =
        table.getRowHeight() * table.getRowCount()
            + header.getPreferredSize().height
            + GuiUtils.insetHeight(container);
    container.setPreferredSize(new Dimension(nameWidth + valueWidth, height)); // NOSONAR fixed
    // Flush left, as wide as the content at most and as narrow as the container at least
    String cell = "growx, wmin 0, wmax " + (nameWidth + valueWidth); // NON-NLS
    JPanel grid = new JPanel(new MigLayout("ins 0, gap 0, wrap 1, fillx", "[left]")); // NON-NLS
    grid.add(header, cell);
    grid.add(table, cell);
    container.add(grid, BorderLayout.CENTER);
  }

  /** Width that shows the header and every cell of a column in full. */
  static int contentWidth(JTable table, int column) {
    TableColumn tableColumn = table.getColumnModel().getColumn(column);
    int width =
        table
            .getTableHeader()
            .getDefaultRenderer()
            .getTableCellRendererComponent(
                table, tableColumn.getHeaderValue(), false, false, -1, column)
            .getPreferredSize()
            .width;
    for (int row = 0; row < table.getRowCount(); row++) {
      width =
          Math.max(
              width,
              table
                  .prepareRenderer(table.getCellRenderer(row, column), row, column)
                  .getPreferredSize()
                  .width);
    }
    return width + table.getIntercellSpacing().width + GuiUtils.getScaleLength(CELL_MARGIN);
  }

  /** Adds the <i>Copy value</i> and <i>Copy table</i> actions to a two-column table. */
  public static void installCopyMenu(JTable table) {
    JPopupMenu popup = new JPopupMenu();
    JMenuItem copyValue = new JMenuItem(Messages.getString("MeasureTool.copy_value"));
    copyValue.addActionListener(
        _ -> {
          int row = table.getSelectedRow();
          if (row >= 0) {
            copyToClipboard(String.valueOf(table.getValueAt(row, 1)));
          }
        });
    JMenuItem copyTable = new JMenuItem(Messages.getString("MeasureTool.copy_table"));
    copyTable.addActionListener(_ -> copyToClipboard(asText(table)));
    popup.add(copyValue);
    popup.add(copyTable);
    table.setComponentPopupMenu(popup);
  }

  /** The table as tab-separated lines with a header, values at full precision. */
  public static String asText(JTable table) {
    StringBuilder text = new StringBuilder();
    TableModel model = table.getModel();
    for (int c = 0; c < model.getColumnCount(); c++) {
      text.append(c > 0 ? "\t" : "").append(model.getColumnName(c));
    }
    for (int r = 0; r < model.getRowCount(); r++) {
      text.append('\n');
      for (int c = 0; c < model.getColumnCount(); c++) {
        text.append(c > 0 ? "\t" : "").append(model.getValueAt(r, c));
      }
    }
    return text.toString();
  }

  private static void copyToClipboard(String text) {
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
  }

  /** A name that does not fit is shortened by the table; the tooltip gives it in full. */
  private static final class NameRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      setToolTipText(value == null ? null : value.toString());
      return this;
    }
  }

  private static final class ValueRenderer extends DefaultTableCellRenderer {
    private final transient List<MeasureItem> items;
    private final transient MeasureFormat format;

    ValueRenderer(List<MeasureItem> items, MeasureFormat format) {
      this.items = items;
      this.format = format;
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      boolean number = value instanceof Number && row < items.size();
      setHorizontalAlignment(number ? SwingConstants.RIGHT : SwingConstants.LEFT);
      if (number) {
        setText(format.format(items.get(row)));
      }
      return this;
    }
  }
}
