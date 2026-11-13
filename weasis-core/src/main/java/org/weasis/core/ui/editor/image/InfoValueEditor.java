/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.ParseException;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;

/**
 * The popup that edits a value of the corner text: number fields on top, applied as soon as they
 * change (arrows, Enter, losing the focus; Enter also closes the popup, Escape closes it), then the
 * shortcuts of the value (presets, best fit...).
 */
public final class InfoValueEditor {

  /** A number field of the editor. */
  public record Field(String label, double value, double min, double max, double step) {}

  private final JPopupMenu popup = new JPopupMenu();
  private final List<JSpinner> spinners;
  private final Consumer<double[]> apply;

  /** Set while the values are applied: committing one field must not apply them again. */
  private boolean applying;

  /**
   * @param fields the number fields, can be empty for an editor made of shortcuts only
   * @param apply receives the values of the fields, in their order, whenever one changes: an arrow
   *     of the field, Enter (which also closes the popup) or the field losing the focus, as the
   *     other spinners of Weasis
   */
  public InfoValueEditor(List<Field> fields, Consumer<double[]> apply) {
    this.apply = apply;
    this.spinners = fields.stream().map(InfoValueEditor::buildSpinner).toList();
    if (!fields.isEmpty()) {
      JPanel panel = new JPanel(new GridBagLayout());
      int gap = GuiUtils.getScaleLength(5);
      panel.setBorder(BorderFactory.createEmptyBorder(gap, gap * 2, gap, gap * 2));
      panel.setOpaque(false);
      GridBagConstraints c = new GridBagConstraints();
      c.insets = new Insets(gap / 2, 0, gap / 2, gap);
      for (int i = 0; i < fields.size(); i++) {
        c.gridy = i;
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        panel.add(new JLabel(fields.get(i).label() + StringUtil.COLON), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        JSpinner spinner = spinners.get(i);
        panel.add(spinner, c);
        spinner.addChangeListener(_ -> applyValues());
        // Enter commits the text (and so applies it), then closes the popup
        textField(spinner).addActionListener(_ -> popup.setVisible(false));
      }
      popup.add(panel);
      // A click elsewhere closes the popup before it reaches its target (another view, which would
      // become the selected one): commit a typed value now, while its view is still selected. A
      // value not edited does not change, so a preset just chosen is not overridden.
      popup.addPopupMenuListener(
          new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
              spinners.forEach(InfoValueEditor::commit);
            }

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
              // nothing to do
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
              // nothing to do
            }
          });
    }
  }

  private static void commit(JSpinner spinner) {
    try {
      spinner.commitEdit();
    } catch (ParseException _) {
      // an invalid text is reverted when the field loses the focus
    }
  }

  private void applyValues() {
    if (applying) {
      return;
    }
    applying = true;
    try {
      apply.accept(values());
    } finally {
      applying = false;
    }
  }

  private static JSpinner buildSpinner(Field field) {
    double value = Math.clamp(field.value(), field.min(), field.max());
    JSpinner spinner =
        new JSpinner(new SpinnerNumberModel(value, field.min(), field.max(), field.step()));
    JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.##");
    spinner.setEditor(editor);
    GuiUtils.setSpinnerWidth(spinner, 6);
    // Enter commits the text, as in the other spinners of Weasis
    GuiUtils.formatCheckAction(spinner);
    return spinner;
  }

  private static JFormattedTextField textField(JSpinner spinner) {
    return ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
  }

  private double[] values() {
    double[] values = new double[spinners.size()];
    for (int i = 0; i < values.length; i++) {
      JSpinner spinner = spinners.get(i);
      try {
        spinner.commitEdit();
      } catch (ParseException _) {
        // Keep the last valid value of the field
      }
      values[i] = ((Number) spinner.getValue()).doubleValue();
    }
    return values;
  }

  /** Adds the shortcuts below the fields. */
  public InfoValueEditor addItems(List<? extends JComponent> items) {
    if (!items.isEmpty()) {
      if (!spinners.isEmpty()) {
        popup.addSeparator();
      }
      items.forEach(popup::add);
    }
    return this;
  }

  public InfoValueEditor addItem(JMenuItem item) {
    return addItems(List.of(item));
  }

  public JPopupMenu getPopup() {
    return popup;
  }

  /**
   * Shows a popup whose bottom left corner is at a point of the invoker: the values of the corner
   * text are at the bottom of the view.
   */
  public static void showAbove(JPopupMenu popup, Component invoker, int x, int y) {
    popup.show(invoker, x, y - popup.getPreferredSize().height);
  }

  /**
   * Shows the popup above a point (see {@link #showAbove}), with the text of the first field
   * selected, ready to type a new value.
   */
  public void show(Component invoker, int x, int y) {
    showAbove(popup, invoker, x, y);
    if (!spinners.isEmpty()) {
      JFormattedTextField first = textField(spinners.getFirst());
      SwingUtilities.invokeLater(
          () -> {
            first.requestFocusInWindow();
            first.selectAll();
          });
    }
  }
}
