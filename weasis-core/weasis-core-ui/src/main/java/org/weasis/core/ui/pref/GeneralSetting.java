/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.PageProps;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.AuditLog.LEVEL;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.Messages;
import org.weasis.core.util.StringUtil;

public class GeneralSetting extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(GeneralSetting.class);

  public static final String PAGE_NAME = Messages.getString("GeneralSetting.gen");

  private final GridBagLayout gridBagLayout1 = new GridBagLayout();

  private final JCheckBox chckbxConfirmClosing =
      new JCheckBox(Messages.getString("GeneralSetting.closingConfirmation"));

  private final JCheckBox chckbxFileLog =
      new JCheckBox(Messages.getString("GeneralSetting.rol_log"));
  private final JPanel panel = new JPanel();
  private final JLabel lblLogLevel =
      new JLabel(Messages.getString("GeneralSetting.log_level") + StringUtil.COLON);
  private final JComboBox<LEVEL> comboBoxLogLevel = new JComboBox<>(LEVEL.values());
  private final Component horizontalStrut = Box.createHorizontalStrut(10);
  private final JLabel labelNumber =
      new JLabel(Messages.getString("GeneralSetting.log_nb") + StringUtil.COLON);
  private final JSpinner spinner = new JSpinner();
  private final JLabel labelSize =
      new JLabel(Messages.getString("GeneralSetting.log_size") + StringUtil.COLON);
  private final JSpinner spinner1 = new JSpinner();
  private final Component horizontalStrut1 = Box.createHorizontalStrut(10);
  private final Component horizontalStrut2 = Box.createHorizontalStrut(10);
  private final JPanel panel1 = new JPanel();
  private final JLabel lblStacktraceLimit =
      new JLabel(Messages.getString("GeneralSetting.stack_limit") + StringUtil.COLON);
  private final JComboBox<String> comboBoxStackLimit =
      new JComboBox<>(new String[] {"", "0", "1", "3", "5", "10", "20", "50", "100"}); // NON-NLS

  public GeneralSetting() {
    super(PAGE_NAME);
    setComponentPosition(0);
    try {
      GuiUtils.setNumberModel(
          spinner, getIntPreferences(AuditLog.LOG_FILE_NUMBER, 5, null), 1, 99, 1);
      GuiUtils.setNumberModel(
          spinner1, getIntPreferences(AuditLog.LOG_FILE_SIZE, 10, "MB"), 1, 99, 1);
      jbInit();
      initialize(true);
    } catch (Exception e) {
      LOGGER.error("Cannot initialize GeneralSetting", e);
    }

    addSubPage(new LanguagelSetting());
    addSubPage(new LooklSetting());
    addSubPage(new ScreenPrefView());
    addSubPage(new ProxyPrefView());
  }

  private void jbInit() {
    this.setLayout(gridBagLayout1);

    GridBagConstraints gbcChckbxConfirmationMessageWhen = new GridBagConstraints();
    gbcChckbxConfirmationMessageWhen.gridwidth = 4;
    gbcChckbxConfirmationMessageWhen.anchor = GridBagConstraints.WEST;
    gbcChckbxConfirmationMessageWhen.insets = new Insets(10, 10, 5, 0);
    gbcChckbxConfirmationMessageWhen.gridx = 0;
    gbcChckbxConfirmationMessageWhen.gridy = 4;
    add(chckbxConfirmClosing, gbcChckbxConfirmationMessageWhen);

    GridBagConstraints gbcPanel = new GridBagConstraints();
    gbcPanel.anchor = GridBagConstraints.WEST;
    gbcPanel.gridwidth = 4;
    gbcPanel.insets = new Insets(5, 5, 0, 10);
    gbcPanel.fill = GridBagConstraints.HORIZONTAL;
    gbcPanel.gridx = 0;
    gbcPanel.gridy = 5;
    FlowLayout flowLayout = (FlowLayout) panel.getLayout();
    flowLayout.setAlignment(FlowLayout.LEADING);
    add(panel, gbcPanel);
    chckbxFileLog.addActionListener(e -> checkRolingLog());
    panel.add(chckbxFileLog);

    panel.add(horizontalStrut1);

    panel.add(labelNumber);

    panel.add(spinner);

    panel.add(horizontalStrut2);

    panel.add(labelSize);

    panel.add(spinner1);

    GridBagConstraints gbcPanel1 = new GridBagConstraints();
    gbcPanel1.gridwidth = 4;
    gbcPanel1.insets = new Insets(0, 10, 5, 10);
    gbcPanel1.fill = GridBagConstraints.BOTH;
    gbcPanel1.gridx = 0;
    gbcPanel1.gridy = 6;
    FlowLayout flowLayout2 = (FlowLayout) panel1.getLayout();
    flowLayout2.setAlignment(FlowLayout.LEADING);
    add(panel1, gbcPanel1);
    panel1.add(lblLogLevel);

    JPanel panel2 = new JPanel();
    FlowLayout flowLayout1 = (FlowLayout) panel2.getLayout();
    flowLayout1.setHgap(10);
    flowLayout1.setAlignment(FlowLayout.RIGHT);
    flowLayout1.setVgap(7);
    GridBagConstraints gbcPanel2 = new GridBagConstraints();
    gbcPanel2.weighty = 1.0;
    gbcPanel2.weightx = 1.0;
    gbcPanel2.anchor = GridBagConstraints.SOUTHWEST;
    gbcPanel2.gridwidth = 4;
    gbcPanel2.insets = new Insets(5, 10, 0, 10);
    gbcPanel2.fill = GridBagConstraints.HORIZONTAL;
    gbcPanel2.gridx = 0;
    gbcPanel2.gridy = 7;
    add(panel2, gbcPanel2);
    JButton btnNewButton = new JButton(Messages.getString("restore.values"));
    panel2.add(GuiUtils.createHelpButton("locale", true)); // NON-NLS
    panel2.add(btnNewButton);
    btnNewButton.addActionListener(
        e -> {
          resetoDefaultValues();
          initialize(false);
        });
  }

  private void checkRolingLog() {
    boolean rolling = chckbxFileLog.isSelected();
    spinner.setEnabled(rolling);
    spinner1.setEnabled(rolling);
  }

  private static int getIntPreferences(String key, int defaultvalue, String removedSuffix) {
    if (key != null) {
      String s = BundleTools.SYSTEM_PREFERENCES.getProperty(key);
      if (s != null) {
        if (removedSuffix != null) {
          int index = s.lastIndexOf(removedSuffix);
          if (index > 0) {
            s = s.substring(0, index);
          }
        }
        try {
          return Integer.parseInt(s);
        } catch (NumberFormatException ignore) {
        }
      }
    }
    return defaultvalue;
  }

  protected void initialize(boolean afirst) {
    WProperties prfs = BundleTools.SYSTEM_PREFERENCES;
    chckbxConfirmClosing.setSelected(prfs.getBooleanProperty(BundleTools.CONFIRM_CLOSE, false));
    panel1.add(comboBoxLogLevel);
    comboBoxLogLevel.setSelectedItem(LEVEL.getLevel(prfs.getProperty(AuditLog.LOG_LEVEL, "INFO")));
    panel1.add(horizontalStrut);

    panel1.add(lblStacktraceLimit);

    int limit = getIntPreferences(AuditLog.LOG_STACKTRACE_LIMIT, 3, null);
    if (limit > 0
        && limit != 1
        && limit != 3
        && limit != 5
        && limit != 10
        && limit != 20
        && limit != 50
        && limit != 100) {
      comboBoxStackLimit.addItem(Integer.toString(limit));
    }
    comboBoxStackLimit.setSelectedItem(limit >= 0 ? Integer.toString(limit) : "");
    panel1.add(comboBoxStackLimit);
    chckbxFileLog.setSelected(StringUtil.hasText(prfs.getProperty(AuditLog.LOG_FILE, "")));
    spinner.setValue(getIntPreferences(AuditLog.LOG_FILE_NUMBER, 5, null));
    spinner1.setValue(getIntPreferences(AuditLog.LOG_FILE_SIZE, 10, "MB"));
    checkRolingLog();
  }

  @Override
  public void closeAdditionalWindow() {
    for (PageProps subpage : getSubPages()) {
      subpage.closeAdditionalWindow();
    }

    BundleTools.SYSTEM_PREFERENCES.putBooleanProperty(
        "weasis.confirm.closing", chckbxConfirmClosing.isSelected());

    String limit = (String) comboBoxStackLimit.getSelectedItem();
    BundleTools.SYSTEM_PREFERENCES.setProperty(
        AuditLog.LOG_STACKTRACE_LIMIT, StringUtil.hasText(limit) ? limit : "-1");

    LEVEL level = (LEVEL) comboBoxLogLevel.getSelectedItem();
    BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_LEVEL, level.toString());
    BundleTools.SYSTEM_PREFERENCES.setProperty(
        AuditLog.LOG_FILE_ACTIVATION, String.valueOf(chckbxFileLog.isSelected()));
    String logFile =
        chckbxFileLog.isSelected()
            ? AppProperties.WEASIS_PATH + File.separator + "log" + File.separator + "default.log"
            : ""; // NON-NLS
    BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_FILE, logFile);
    String fileNb = null;
    String fileSize = null;
    if (chckbxFileLog.isSelected()) {
      fileNb = spinner.getValue().toString();
      fileSize = spinner1.getValue().toString() + "MB";
      BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_FILE_NUMBER, fileNb);
      BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_FILE_SIZE, fileSize);
    }
    String pattern =
        BundleTools.SYSTEM_PREFERENCES.getProperty(
            AuditLog.LOG_PATTERN, "{0,date,dd.MM.yyyy HH:mm:ss.SSS} *{4}* [{2}] {3}: {5}");
    BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
    AuditLog.createOrUpdateLogger(
        context,
        "default.log",
        new String[] {"org"}, // NON-NLS
        level.toString(),
        logFile,
        pattern,
        fileNb,
        fileSize,
        limit);
  }

  @Override
  public void resetoDefaultValues() {
    BundleTools.SYSTEM_PREFERENCES.resetProperty(
        BundleTools.CONFIRM_CLOSE, Boolean.FALSE.toString());

    // Reset properties used by OSGI service (Sling Logger)
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_STACKTRACE_LIMIT, "3");
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_LEVEL, "INFO");
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_FILE, "");
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_FILE_NUMBER, "5");
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_FILE_SIZE, "10MB"); // NON-NLS
  }
}
