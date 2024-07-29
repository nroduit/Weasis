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
import com.formdev.flatlaf.icons.FlatClearIcon;
import com.formdev.flatlaf.icons.FlatSearchIcon;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class SearchableComboBox<E> extends JComboBox<E> {
  private final DefaultComboBoxModel<E> originalModel;
  private final AtomicBoolean isFiltering = new AtomicBoolean(false);

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
    this.originalModel = new DefaultComboBoxModel<>();
    copyModel(comboBoxModel);

    this.addItemListener(
        e -> {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            if (!isFiltering.get() && !comboBoxModel.equals(originalModel)) {
              E item = (E) getSelectedItem();
              super.setModel(originalModel);
              setSelectedItem(item);
            }
          }
        });
    this.setEditable(true);
    JTextField editorComponent = (JTextField) editor.getEditorComponent();
    JButton clearButton = new JButton(new FlatClearIcon());
    editorComponent.putClientProperty(
        FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, new JLabel(new FlatSearchIcon(true)));
    editorComponent.putClientProperty(
        FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, clearButton);
    editorComponent.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyReleased(KeyEvent ke) {
            filter(editorComponent.getText());
          }
        });
    clearButton.addActionListener(
        _ -> {
          editorComponent.setText("");
          super.setModel(originalModel);
          setSelectedItem(null);
        });
  }

  private void copyModel(ComboBoxModel<E> comboBoxModel) {
    if (originalModel == null || comboBoxModel == null) {
      return;
    }
    originalModel.removeAllElements();
    for (int i = 0; i < comboBoxModel.getSize(); i++) {
      originalModel.addElement(comboBoxModel.getElementAt(i));
    }
  }

  @Override
  public void setModel(ComboBoxModel<E> aModel) {
    copyModel(aModel);
    super.setModel(aModel);
  }

  protected void filter(String text) {
    isFiltering.set(true);
    Vector<E> entriesFiltered = new Vector<>();
    String compText = text.trim().toLowerCase();
    if (compText.length() > 1) {
      for (int i = 0; i < originalModel.getSize(); i++) {
        E val = originalModel.getElementAt(i);
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
              getUI().setPopupVisible(this, false);
            }
          } else {
            DefaultComboBoxModel<E> model = new DefaultComboBoxModel<>(entriesFiltered);
            model.setSelectedItem(entriesFiltered.getFirst());
            super.setModel(model);
            setSelectedItem(text);
            getUI().setPopupVisible(this, true);
          }
          isFiltering.set(false);
        });
  }
}
