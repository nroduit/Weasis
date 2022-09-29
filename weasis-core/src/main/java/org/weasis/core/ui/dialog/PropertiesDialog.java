/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.util.StringUtil;

public abstract class PropertiesDialog extends JDialog {

  protected Color color;

  private final JPanel panel1 = new JPanel();
  private final JButton jButtonOk = new JButton();
  private final JButton jButtonCancel = new JButton();
  protected final JSpinner spinnerLineWidth = new JSpinner();
  protected final JLabel jLabelLineWidth = new JLabel();
  protected final JLabel jLabelLineColor = new JLabel();
  protected final JButton jButtonColor = new JButton(ResourceUtil.getIcon(ActionIcon.PIPETTE));
  protected final JCheckBox jCheckBoxFilled = new JCheckBox();
  protected final JButton overrideMultipleValues =
      new JButton(Messages.getString("PropertiesDialog.header_override"));
  protected final JCheckBox checkBoxColor = new JCheckBox();
  protected final JCheckBox checkBoxWidth = new JCheckBox();
  protected final JCheckBox checkBoxFill = new JCheckBox();

  protected final JPanel panelColor = GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 2, 5);
  protected final JPanel panelLine = GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 2, 5);
  protected final JPanel panelFilled = GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 2, 5);

  protected PropertiesDialog(Window parent, String title) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    init();
  }

  private void init() {
    panel1.setBorder(GuiUtils.getEmptyBorder(10, 15, 0, 15));
    panel1.setLayout(new BorderLayout());

    jButtonOk.setText(Messages.getString("PropertiesDialog.ok"));
    jButtonOk.addActionListener(e -> okAction());
    jButtonCancel.setText(Messages.getString("PropertiesDialog.cancel"));
    jButtonCancel.addActionListener(e -> quitWithoutSaving());

    GuiUtils.setNumberModel(spinnerLineWidth, 1, 1, 8, 1);
    jLabelLineWidth.setText(Messages.getString("PropertiesDialog.line_width") + StringUtil.COLON);
    jLabelLineColor.setText(Messages.getString("PropertiesDialog.line_color") + StringUtil.COLON);
    jButtonColor.setToolTipText(Messages.getString("MeasureTool.pick"));
    jButtonColor.addActionListener(e -> openColorChooser((JButton) e.getSource()));

    jCheckBoxFilled.setText(Messages.getString("PropertiesDialog.fill_shape"));

    getContentPane().add(panel1);

    JPanel jPanelFooter =
        GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 15, 15, jButtonOk, jButtonCancel);
    panel1.add(jPanelFooter, BorderLayout.SOUTH);

    overrideMultipleValues.setEnabled(false);

    checkBoxColor.addActionListener(
        e -> {
          JCheckBox box = (JCheckBox) e.getSource();
          jLabelLineColor.setEnabled(box.isSelected());
          jButtonColor.setEnabled(box.isSelected());
        });
    checkBoxWidth.addActionListener(
        e -> {
          JCheckBox box = (JCheckBox) e.getSource();
          jLabelLineWidth.setEnabled(box.isSelected());
          spinnerLineWidth.setEnabled(box.isSelected());
        });
    checkBoxFill.addActionListener(
        e -> {
          JCheckBox box = (JCheckBox) e.getSource();
          jCheckBoxFilled.setEnabled(box.isSelected());
        });

    JPanel centerPanel = GuiUtils.getVerticalBoxLayoutPanel();
    centerPanel.add(GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 2, 5, overrideMultipleValues));

    panelColor.add(jLabelLineColor);
    panelColor.add(jButtonColor);

    panelLine.add(jLabelLineWidth);
    panelLine.add(spinnerLineWidth);

    panelFilled.add(jCheckBoxFilled);

    centerPanel.add(panelColor);
    centerPanel.add(panelLine);
    centerPanel.add(panelFilled);
    centerPanel.add(GuiUtils.boxVerticalStrut(10));
    panel1.add(centerPanel, BorderLayout.CENTER);
  }

  // Overridden so we can exit when window is closed
  @Override
  protected void processWindowEvent(WindowEvent e) {
    if (e.getID() == WindowEvent.WINDOW_CLOSING) {
      quitWithoutSaving();
    }
    super.processWindowEvent(e);
  }

  protected abstract void okAction();

  protected void quitWithoutSaving() {
    dispose();
  }

  public void openColorChooser(JButton button) {
    if (button != null) {
      Color newColor =
          JColorChooser.showDialog(button, Messages.getString("MeasureTool.pick_color"), color);
      if (newColor != null) {
        color = newColor;
      }
    }
  }
}
