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

import java.util.Objects;
import javax.swing.table.AbstractTableModel;

public class SimpleTableModel extends AbstractTableModel {

  private final String[] columnNames;
  private final Object[][] data;
  private final boolean editable;

  public SimpleTableModel(String[] columnNames, Object[][] data) {
    this(columnNames, data, false);
  }

  public SimpleTableModel(String[] columnNames, Object[][] data, boolean editable) {
    this.columnNames = Objects.requireNonNull(columnNames);
    this.data = Objects.requireNonNull(data);
    this.editable = editable;
  }

  @Override
  public int getColumnCount() {
    return columnNames.length;
  }

  @Override
  public int getRowCount() {
    return data.length;
  }

  @Override
  public String getColumnName(int col) {
    return columnNames[col];
  }

  @Override
  public boolean isCellEditable(int row, int col) {
    return editable;
  }

  @Override
  public Object getValueAt(int row, int col) {
    return data[row][col];
  }

  @Override
  public void setValueAt(Object value, int row, int col) {
    data[row][col] = value;
    fireTableCellUpdated(row, col);
  }

  @Override
  public Class<?> getColumnClass(int c) {
    return getValueAt(0, c).getClass();
  }
}
