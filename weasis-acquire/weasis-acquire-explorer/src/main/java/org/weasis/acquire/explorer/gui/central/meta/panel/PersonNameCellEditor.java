/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.meta.panel;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;

/** Cell editor opening {@link PersonNameView} to edit each component of a DICOM person name. */
public class PersonNameCellEditor extends AbstractCellEditor
    implements TableCellEditor, ActionListener {

  private final JButton buttonOpen;
  private String currentValue;
  private String title;
  private JTable table;
  private int row;
  private int column;

  public PersonNameCellEditor() {
    this.buttonOpen = new JButton();
    buttonOpen.setOpaque(true);
    buttonOpen.addActionListener(this);
  }

  @Override
  public Object getCellEditorValue() {
    return currentValue;
  }

  @Override
  public Component getTableCellEditorComponent(
      JTable table, Object value, boolean isSelected, int row, int column) {
    this.table = table;
    this.row = row;
    this.column = column;
    this.currentValue = toPersonName(value);
    Object tag = table.getModel().getValueAt(row, 0);
    this.title = tag instanceof TagW tagW ? tagW.getDisplayedName() : null;
    String name = TagD.getDicomPersonName(currentValue);
    buttonOpen.setText(StringUtil.hasText(name) ? name : "..."); // NON-NLS
    return buttonOpen;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    PersonNameView nameView = new PersonNameView(currentValue);
    int res =
        JOptionPane.showConfirmDialog(
            buttonOpen, nameView, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    if (res == JOptionPane.OK_OPTION) {
      this.currentValue = nameView.getDicomPersonName();
      // The modal dialog has already terminated the editing session, so update the model directly
      table.setValueAt(currentValue, row, column);
    }
    table.removeEditor();
  }

  private static String toPersonName(Object value) {
    return switch (value) {
      case String s -> s;
      case String[] values -> values.length == 0 ? null : values[0];
      case null, default -> null;
    };
  }
}
