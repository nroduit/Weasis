/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.util.LabelHighlighted;

public class TagSearchTablePanel extends AbstractTagSearchPanel {

  private final TreeSet<Integer> searchPositions = new TreeSet<>();
  private int currentSearchIndex = -1;
  private final TableRowSorter<? extends TableModel> rowSorter;
  private final JToggleButton filterButton =
      new JToggleButton(ResourceUtil.getIcon(ActionIcon.FILTER));
  private final JTable table;

  public TagSearchTablePanel(JTable table) {
    this.table = Objects.requireNonNull(table);
    RowSorter<? extends TableModel> sorter = table.getRowSorter();
    if (sorter == null) {
      table.setAutoCreateRowSorter(true);
      sorter = table.getRowSorter();
    }
    if (sorter instanceof TableRowSorter<? extends TableModel> tableRowSorter) {
      this.rowSorter = tableRowSorter;
    } else {
      this.rowSorter = new TableRowSorter<>(table.getModel());
    }
    table.setRowSorter(rowSorter);

    RendererHighlighted renderer = new RendererHighlighted(textFieldSearch, searchPositions);
    table.setDefaultRenderer(Object.class, renderer);

    filterButton.setSelected(true);
    filterButton.addActionListener(evt -> filter());
    navigateToolbar.add(filterButton);
  }

  protected void previous() {
    if (!searchPositions.isEmpty()) {
      int index = Math.max(currentSearchIndex - 1, 0);
      Integer val = searchPositions.floor(index);
      if (val == null) {
        val = searchPositions.first();
      }
      if (val != null) {
        currentSearchIndex = val;
        scrollRow(currentSearchIndex);
      }
    }
  }

  protected void next() {
    if (!searchPositions.isEmpty()) {
      int index = Math.min(currentSearchIndex + 1, table.getModel().getRowCount() - 1);
      Integer val = searchPositions.ceiling(index);
      if (val == null) {
        val = searchPositions.last();
      }
      if (val != null) {
        currentSearchIndex = val;
        scrollRow(currentSearchIndex);
      }
    }
  }

  public void scrollRow(int row) {
    JViewport viewport = WinUtil.getParentOfClass(table, JViewport.class);
    if (viewport != null) {
      int viewRow = table.convertRowIndexToView(row);
      Rectangle r = table.getCellRect(viewRow, 0, true);
      int extentHeight = viewport.getExtentSize().height;
      int viewHeight = viewport.getViewSize().height;

      int y = Math.max(0, r.y - ((extentHeight - r.height) / 2));
      y = Math.min(y, viewHeight - extentHeight);
      viewport.setViewPosition(new Point(0, y));
      table.setRowSelectionInterval(viewRow, viewRow);
    }
  }

  @Override
  protected void filter() {
    searchPositions.clear();
    currentSearchIndex = -1;

    String text = textFieldSearch.getText();
    if (text.trim().length() == 0) {
      rowSorter.setRowFilter(null);
    } else {
      rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text)); // NON-NLS
      for (int row = 0; row < table.getRowCount(); row++) {
        searchPositions.add(table.convertRowIndexToModel(row));
      }
      if (!filterButton.isSelected()) {
        rowSorter.setRowFilter(null);
      }
    }

    table.revalidate();
    table.repaint();
    if (!searchPositions.isEmpty()) {
      Integer position = searchPositions.first();
      if (position != null) {
        scrollRow(position);
      }
    }
  }

  public static class RendererHighlighted extends DefaultTableCellRenderer {
    private final JTextField searchField;
    private final Set<Integer> searchPositions;
    private final LabelHighlighted label = new LabelHighlighted(IconColor.ACTIONS_BLUE.color);

    public RendererHighlighted(JTextField searchField, Set<Integer> searchPositions) {
      this.searchField = Objects.requireNonNull(searchField);
      this.searchPositions = Objects.requireNonNull(searchPositions);
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      Component c =
          super.getTableCellRendererComponent(table, value, selected, hasFocus, row, column);
      JLabel original = (JLabel) c;
      label.setFont(original.getFont());
      label.setText(original.getText());
      label.setBackground(original.getBackground());
      label.setForeground(original.getForeground());
      label.setHorizontalTextPosition(original.getHorizontalTextPosition());
      label.highlightText(searchField.getText());
      return label;
    }
  }
}
