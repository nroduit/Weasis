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
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class SearchableComboBox<E> extends JComboBox<E> {
  private ComboBoxModel<E> baseModel;
  private final AtomicBoolean isFiltering = new AtomicBoolean(false);
  private transient Function<? super E, String> matcher;
  private transient Consumer<String> searchCallback;

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
    this.baseModel = comboBoxModel;

    this.addItemListener(
        e -> {
          if (e.getStateChange() == ItemEvent.SELECTED
              && !isFiltering.get()
              && getModel() != baseModel) {
            E item = (E) getSelectedItem();
            super.setModel(baseModel);
            setSelectedItem(item);
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
          if (getModel() != baseModel) {
            super.setModel(baseModel);
          }
          setSelectedItem(null);
          if (searchCallback != null) {
            searchCallback.accept("");
          }
        });
  }

  /**
   * Sets the function used to derive the searchable text of an item (defaults to {@code toString}).
   */
  public void setMatcher(Function<? super E, String> matcher) {
    this.matcher = matcher;
  }

  /** Registers a callback invoked with the trimmed lowercase text on each keystroke. */
  public void setSearchCallback(Consumer<String> searchCallback) {
    this.searchCallback = searchCallback;
  }

  private String matchText(E value) {
    if (value == null) {
      return null;
    }
    return matcher != null ? matcher.apply(value) : value.toString();
  }

  /** Returns true while the popup shows a filtered subset (used to ignore transient selections). */
  public boolean isFiltering() {
    return isFiltering.get();
  }

  @Override
  public void setModel(ComboBoxModel<E> aModel) {
    this.baseModel = aModel;
    super.setModel(aModel);
  }

  protected void filter(String text) {
    isFiltering.set(true);
    Vector<E> entriesFiltered = new Vector<>();
    String compText = text.trim().toLowerCase();
    if (compText.length() > 1) {
      for (int i = 0; i < baseModel.getSize(); i++) {
        E val = baseModel.getElementAt(i);
        String searchable = matchText(val);
        if (searchable != null && searchable.toLowerCase().contains(compText)) {
          entriesFiltered.add(val);
        }
      }
    }

    if (searchCallback != null) {
      searchCallback.accept(compText);
    }

    SwingUtilities.invokeLater(
        () -> {
          if (entriesFiltered.isEmpty()) {
            if (getModel() != baseModel) {
              super.setModel(baseModel);
            }
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
