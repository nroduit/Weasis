/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Vector;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class SearchableComboBox<E> extends JComboBox<E> {
  private final JComboBox<E> filteredCombobox = new JComboBox<>();

  public SearchableComboBox() {
    this(new DefaultComboBoxModel<>());
  }

  public SearchableComboBox(E[] items) {
    this(new DefaultComboBoxModel<>(items));
  }

  public SearchableComboBox(Vector<E> items) {
    this(new DefaultComboBoxModel<>(items));
  }

  public SearchableComboBox(ComboBoxModel<E> comboBoxModel) {
    super(comboBoxModel);
    this.setEditable(true);
    JTextField editorComponent = (JTextField) editor.getEditorComponent();
    editorComponent.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    editorComponent.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyReleased(KeyEvent ke) {
            filter(editorComponent.getText());
          }
        });
  }

  protected void filter(String text) {
    Vector<E> entriesFiltered = new Vector<>();
    String compText = text.trim().toLowerCase();
    if (compText.length() > 1) {
      for (int i = 0; i < dataModel.getSize(); i++) {
        E val = dataModel.getElementAt(i);
        if (val != null && val.toString().toLowerCase().contains(compText)) {
          entriesFiltered.add(val);
        }
      }
    }

    SwingUtilities.invokeLater(
        () -> {
          if (entriesFiltered.isEmpty()) {
            setSelectedItem(text);
            if (compText.length() > 1) {
              filteredCombobox.getUI().setPopupVisible(filteredCombobox, false);
            }
          } else {
            DefaultComboBoxModel<E> model = new DefaultComboBoxModel<>(entriesFiltered);
            model.setSelectedItem(entriesFiltered.get(0));
            filteredCombobox.setModel(model);
            setSelectedItem(text);
            filteredCombobox.getUI().setPopupVisible(filteredCombobox, true);
          }
        });
  }
}
