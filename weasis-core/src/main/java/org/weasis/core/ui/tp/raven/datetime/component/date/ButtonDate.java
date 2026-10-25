/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.tp.raven.datetime.component.date;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.weasis.core.ui.tp.raven.datetime.DatePicker;

/**
 * ButtonDate is a class that provides buttons for the DatePicker.
 *
 * @author Raven Laing
 * @see <a href="https://github.com/DJ-Raven/swing-datetime-picker">swing-datetime-picker</a>
 */
public class ButtonDate extends JButton {

  private final DatePicker datePicker;
  private final SingleDate date;
  private final int rowIndex;
  private boolean press;
  private boolean hover;

  public ButtonDate(DatePicker datePicker, SingleDate date, boolean enable, int rowIndex) {
    this.datePicker = datePicker;
    this.date = date;
    this.rowIndex = rowIndex;
    setText(date.getDay() + "");
    init(enable);
  }

  private void init(boolean enable) {
    setContentAreaFilled(false);
    addActionListener(
        e -> {
          if (datePicker.isEnabled()) {
            datePicker.getDateSelectionModel().selectDate(date);
            hover = false;
            PanelDate panelDate = (PanelDate) getParent();
            panelDate.checkSelection();
          }
        });
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (datePicker.isEnabled()
                && isEnabled()
                && datePicker.getDateSelectionMode()
                    == DatePicker.DateSelectionMode.SINGLE_DATE_SELECTED) {
              if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                datePicker.closePopup();
              }
            }
          }

          @Override
          public void mousePressed(MouseEvent e) {
            if (datePicker.isEnabled() && SwingUtilities.isLeftMouseButton(e)) {
              press = true;
            }
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            if (datePicker.isEnabled() && SwingUtilities.isLeftMouseButton(e)) {
              press = false;
            }
          }

          @Override
          public void mouseEntered(MouseEvent e) {
            if (datePicker.isEnabled()) {
              hover = true;
              if (datePicker.getDateSelectionModel().getDateSelectionMode()
                      == DatePicker.DateSelectionMode.BETWEEN_DATE_SELECTED
                  && datePicker.getDateSelectionModel().getDate() != null) {
                datePicker.getDateSelectionModel().setHoverDate(date);
              }
            }
          }

          @Override
          public void mouseExited(MouseEvent e) {
            if (datePicker.isEnabled()) {
              hover = false;
            }
          }
        });
    // Do not inherit Button.foreground (Auramed sets it black for orange buttons): day
    // numbers must stay readable on the dark calendar panel.
    if (enable) {
      putClientProperty(
          FlatClientProperties.STYLE,
          "margin:7,7,7,7;"
              + "focusWidth:2;"
              + "foreground:$Label.foreground;"
              + "selectedForeground:contrast($Component.accentColor,$Label.foreground,#0f172a)");
    } else {
      putClientProperty(
          FlatClientProperties.STYLE,
          "margin:7,7,7,7;"
              + "focusWidth:2;"
              + "foreground:$Label.disabledForeground;"
              + "selectedForeground:contrast($Component.accentColor,$Label.foreground,#0f172a)");
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    DefaultDateCellRenderer dateCellRenderer = datePicker.getDefaultDateCellRenderer();
    if (dateCellRenderer != null) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        FlatUIUtils.setRenderingHints(g2);
        dateCellRenderer.paint(g2, datePicker, this, date, getWidth(), getHeight());
      } finally {
        g2.dispose();
      }
    }
    super.paintComponent(g);
  }

  public boolean isDateSelected() {
    DateSelectionModel dateSelectionModel = datePicker.getDateSelectionModel();
    if (dateSelectionModel.getDateSelectionMode()
        == DatePicker.DateSelectionMode.SINGLE_DATE_SELECTED) {
      return date.same(dateSelectionModel.getDate());
    } else {
      return date.same(dateSelectionModel.getDate()) || date.same(dateSelectionModel.getToDate());
    }
  }

  public SingleDate getDate() {
    return date;
  }

  public boolean isPress() {
    return press;
  }

  public boolean isHover() {
    return hover;
  }

  public int getRowIndex() {
    return rowIndex;
  }
}
