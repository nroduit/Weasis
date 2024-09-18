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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.launcher.Launcher.ApplicationConfiguration;
import org.weasis.core.ui.launcher.Launcher.Compatibility;
import org.weasis.core.ui.launcher.Launcher.Configuration;
import org.weasis.core.ui.launcher.Launcher.URIConfiguration;
import org.weasis.core.util.StringUtil;

public class ConfigurationDialog extends JDialog {

  private final JTextField uriField = new JTextField(25);
  private final JTextField binaryPathField = new JTextField(25);
  private final JTextField workingDirectoryField = new JTextField(25);
  private final JTextArea parametersArea = new JTextArea(3, 25);
  private final JTextArea environmentVariablesArea = new JTextArea(3, 25);
  private final JComboBox<Compatibility> compatibilityField =
      new JComboBox<>(Compatibility.values());
  private final JRadioButton uriRadioButton = new JRadioButton(Messages.getString("uri"));
  private final JRadioButton appRadioButton = new JRadioButton(Messages.getString("application"));
  private final JPanel cardPanel;
  private final Launcher launcher;

  public ConfigurationDialog(Window parent, Launcher launcher, Launcher.Type type) {
    super(parent, Messages.getString("launcher.type"), ModalityType.APPLICATION_MODAL);
    setLayout(new MigLayout("insets 10lp 15lp 10lp 15lp", "[][grow]", "[][]")); // NON-NLS

    ButtonGroup group = new ButtonGroup();
    group.add(uriRadioButton);
    group.add(appRadioButton);
    uriRadioButton.setSelected(true);

    this.launcher = launcher;
    initialize(launcher);

    add(uriRadioButton, "cell 0 0"); // NON-NLS
    add(appRadioButton, "cell 1 0"); // NON-NLS

    cardPanel = new JPanel(new CardLayout());
    cardPanel.setBorder(BorderFactory.createTitledBorder(Messages.getString("configuration")));

    JPanel uriPanel = new JPanel(new MigLayout("insets 10", "[][grow]", "[]")); // NON-NLS
    uriPanel.add(new JLabel(Messages.getString("uri") + StringUtil.COLON), "cell 0 0"); // NON-NLS
    uriPanel.add(uriField, "cell 1 0, growx"); // NON-NLS

    JPanel appPanel = new JPanel(new MigLayout("insets 10", "[][grow]", "[][][][][]")); // NON-NLS
    appPanel.add(
        new JLabel(Messages.getString("binary.path") + StringUtil.COLON), "cell 0 0"); // NON-NLS
    appPanel.add(binaryPathField, "cell 1 0, growx"); // NON-NLS

    appPanel.add(
        new JLabel(Messages.getString("working.dir") + StringUtil.COLON), "cell 0 1"); // NON-NLS
    appPanel.add(workingDirectoryField, "cell 1 1, growx"); // NON-NLS

    appPanel.add(
        new JLabel(Messages.getString("parameters") + StringUtil.COLON), "cell 0 2"); // NON-NLS
    appPanel.add(new JScrollPane(parametersArea), "cell 1 2, growx"); // NON-NLS

    appPanel.add(
        new JLabel(Messages.getString("env.variables") + StringUtil.COLON), "cell 0 3"); // NON-NLS
    appPanel.add(new JScrollPane(environmentVariablesArea), "cell 1 3, growx"); // NON-NLS

    appPanel.add(
        new JLabel(Messages.getString("compatibility") + StringUtil.COLON), "cell 0 4"); // NON-NLS
    appPanel.add(compatibilityField, "cell 1 4, growx"); // NON-NLS

    cardPanel.add(uriPanel, uriRadioButton.getText());
    cardPanel.add(appPanel, appRadioButton.getText());

    add(cardPanel, "cell 0 1 2 1, grow"); // NON-NLS

    uriRadioButton.addActionListener(_ -> showCard());
    appRadioButton.addActionListener(_ -> showCard());

    JButton saveButton = new JButton(Messages.getString("save"));
    saveButton.addActionListener(_ -> saveConfiguration());
    JButton jButtonHelp = GuiUtils.createHelpButton("launcher-external"); // NON-NLS
    add(jButtonHelp, "cell 1 3, split 3, align right, gapright 15, gaptop 15"); // NON-NLS
    add(saveButton, "align right, gaptop 15"); // NON-NLS
    JButton cancelButton = new JButton(Messages.getString("cancel"));
    cancelButton.addActionListener(_ -> dispose());
    add(cancelButton);

    showCard();

    pack();
    setLocationRelativeTo(parent);
  }

  private void initialize(Launcher launcher) {
    if (launcher != null) {
      Configuration config = launcher.getConfiguration();
      if (config instanceof Launcher.URIConfiguration uriConfig) {
        uriRadioButton.setSelected(true);
        uriField.setText(uriConfig.getUri());
      } else if (config instanceof Launcher.ApplicationConfiguration appConfig) {
        appRadioButton.setSelected(true);
        binaryPathField.setText(appConfig.getBinaryPath());
        workingDirectoryField.setText(appConfig.getWorkingDirectory());
        parametersArea.setText(String.join("\n", appConfig.getParameters()));
        environmentVariablesArea.setText(
            appConfig.getEnvironmentVariables().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse(""));
        compatibilityField.setSelectedItem(appConfig.getCompatibility());
      }
    }
  }

  private void showCard() {
    CardLayout cl = (CardLayout) (cardPanel.getLayout());
    if (appRadioButton.isSelected()) cl.show(cardPanel, appRadioButton.getText());
    else cl.show(cardPanel, uriRadioButton.getText());
  }

  private void saveConfiguration() {
    Configuration config = launcher.getConfiguration();
    if (appRadioButton.isSelected()) {
      config = setApplicationConfiguration(config);
    } else {
      config = setUriConfiguration(config);
    }
    launcher.setConfiguration(config);

    if (!config.isValid()) {
      config.showInvalidField(this);
      return;
    }

    dispose();
  }

  private ApplicationConfiguration setApplicationConfiguration(Configuration config) {
    ApplicationConfiguration appConfig;
    if (config instanceof ApplicationConfiguration) {
      appConfig = (ApplicationConfiguration) config;
    } else {
      appConfig = new ApplicationConfiguration();
    }
    appConfig.setBinaryPath(binaryPathField.getText());
    appConfig.setWorkingDirectory(workingDirectoryField.getText());
    appConfig.setParameters(
        new ArrayList<>(
            Arrays.stream(parametersArea.getText().split("\\n")) // NON-NLS
                .filter(StringUtil::hasText)
                .toList()));
    appConfig.setEnvironmentVariables(
        parseEnvironmentVariables(environmentVariablesArea.getText()));
    appConfig.setCompatibility((Compatibility) compatibilityField.getSelectedItem());
    return appConfig;
  }

  private Map<String, String> parseEnvironmentVariables(String text) {
    Map<String, String> env = new HashMap<>();
    for (String line : text.split("\\n")) { // NON-NLS
      String[] parts = line.split("=");
      if (parts.length == 2) {
        env.put(parts[0], parts[1]);
      }
    }
    return env;
  }

  private URIConfiguration setUriConfiguration(Configuration config) {
    URIConfiguration uriConfig;
    if (config instanceof URIConfiguration) {
      uriConfig = (URIConfiguration) config;
    } else {
      uriConfig = new URIConfiguration();
    }
    uriConfig.setUri(uriField.getText());
    return uriConfig;
  }
}
