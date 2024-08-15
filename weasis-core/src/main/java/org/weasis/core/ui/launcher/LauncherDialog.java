/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.launcher;

import java.awt.*;
import java.util.List;
import java.util.Objects;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.ui.launcher.Launcher.URIConfiguration;
import org.weasis.core.util.StringUtil;

public class LauncherDialog extends JDialog {

  private final JTextField nameField = new JTextField(25);
  private final JTextField iconNameField = new JTextField(25);
  private final JCheckBox enableCheckBox = new JCheckBox(Messages.getString("enable"), true);
  private final JCheckBox buttonCheckBox = new JCheckBox(Messages.getString("button"));
  private final JComboBox<Launcher> nodesComboBox;
  private final Launcher launcher;
  private final Launcher.Type type;

  public LauncherDialog(
      Window parent, Launcher.Type type, Launcher launcher, JComboBox<Launcher> nodeComboBox) {
    super(parent, type.toString(), ModalityType.APPLICATION_MODAL);
    setLayout(
        new MigLayout("insets 10lp 15lp 10lp 15lp, wrap 2", "[][grow]", "[][][][]")); // NON-NLS
    this.type = Objects.requireNonNull(type);
    this.nodesComboBox = nodeComboBox;
    if (launcher == null) {
      this.launcher = new Launcher();
      this.launcher.setConfiguration(new URIConfiguration());
    } else {
      this.launcher = launcher;
      nameField.setText(launcher.getName());
      iconNameField.setText(launcher.getIconPath());
      enableCheckBox.setSelected(launcher.isEnable());
      buttonCheckBox.setSelected(launcher.isButton());
    }

    add(new JLabel(Messages.getString("name") + StringUtil.COLON), "align right"); // NON-NLS
    add(nameField, "growx"); // NON-NLS

    add(new JLabel(Messages.getString("icon.path") + StringUtil.COLON), "align right"); // NON-NLS
    add(iconNameField, "growx"); // NON-NLS

    add(enableCheckBox, "span, split 3"); // NON-NLS
    add(buttonCheckBox, "growx"); // NON-NLS

    JButton configurationButton = new JButton(Messages.getString("configure"));
    configurationButton.addActionListener(
        _ -> new ConfigurationDialog(LauncherDialog.this, this.launcher, type).setVisible(true));
    add(configurationButton, "align right"); // NON-NLS

    JButton saveButton = new JButton(Messages.getString("save"));
    saveButton.addActionListener(_ -> saveLauncher());
    add(saveButton, "cell 1 4, split 2, align right, gapright 15, gaptop 15"); // NON-NLS
    JButton cancelButton = new JButton(Messages.getString("cancel"));
    cancelButton.addActionListener(_ -> dispose());
    add(cancelButton);

    pack();
    setLocationRelativeTo(parent);
  }

  private void saveLauncher() {
    String name = nameField.getText();
    if (!StringUtil.hasText(name)) {
      String fieldName = Messages.getString("name");
      ShowRequiredValue(this, fieldName);
      return;
    }

    if (!launcher.getConfiguration().isValid()) {
      launcher.getConfiguration().showInvalidField(this);
      return;
    }

    launcher.setName(name);
    launcher.setIconPath(iconNameField.getText());
    launcher.setEnable(enableCheckBox.isSelected());
    launcher.setButton(buttonCheckBox.isSelected());

    List<Launcher> list = Launcher.getLaunchers(type);
    if (!list.contains(launcher)) {
      list.add(launcher);
      nodesComboBox.addItem(launcher);
      nodesComboBox.setSelectedItem(launcher);
    }
    nodesComboBox.repaint();

    Launcher.saveLaunchers(type);
    dispose();
  }

  static void ShowRequiredValue(Window parent, String fieldName) {
    JOptionPane.showMessageDialog(
        WinUtil.getValidComponent(parent),
        String.format(Messages.getString("is.required"), fieldName),
        Messages.getString("error"),
        JOptionPane.ERROR_MESSAGE);
  }
}
