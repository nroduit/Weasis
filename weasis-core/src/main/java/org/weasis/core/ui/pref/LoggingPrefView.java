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

import ch.qos.logback.classic.LoggerContext;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.AuditLog.LEVEL;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.util.StringUtil;

public class LoggingPrefView extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingPrefView.class);

  public static final String PAGE_NAME = Messages.getString("logging");

  private final JCheckBox checkboxFileLog =
      new JCheckBox(Messages.getString("GeneralSetting.rol_log"));
  private final JLabel lblLogLevel =
      new JLabel(Messages.getString("GeneralSetting.log_level") + StringUtil.COLON);
  private final JComboBox<LEVEL> comboBoxLogLevel = new JComboBox<>(LEVEL.values());
  private final JLabel labelNumber =
      new JLabel(Messages.getString("GeneralSetting.log_nb") + StringUtil.COLON);
  private final JSpinner spinner = new JSpinner();
  private final JLabel labelSize =
      new JLabel(Messages.getString("GeneralSetting.log_size") + StringUtil.COLON);
  private final JSpinner spinner1 = new JSpinner();
  private final JLabel lblStacktraceLimit =
      new JLabel(Messages.getString("GeneralSetting.stack_limit") + StringUtil.COLON);
  private final JComboBox<String> comboBoxStackLimit =
      new JComboBox<>(new String[] {"", "0", "1", "3", "5", "10", "20", "50", "100"}); // NON-NLS

  public LoggingPrefView() {
    super(PAGE_NAME, 115);

    try {
      GuiUtils.setNumberModel(
          spinner, getIntPreferences(AuditLog.LOG_FILE_NUMBER, 20, null), 1, 99, 1);
      GuiUtils.setNumberModel(
          spinner1, getIntPreferences(AuditLog.LOG_FILE_SIZE, 10, "MB"), 1, 99, 1);

      jbInit();
      initialize();
    } catch (Exception e) {
      LOGGER.error("Cannot initialize", e);
    }
  }

  private void jbInit() {
    add(GuiUtils.getFlowLayoutPanel(0, ITEM_SEPARATOR_LARGE, checkboxFileLog));
    add(
        GuiUtils.getFlowLayoutPanel(
            ITEM_SEPARATOR_SMALL,
            10,
            labelNumber,
            spinner,
            GuiUtils.boxHorizontalStrut(BLOCK_SEPARATOR),
            labelSize,
            spinner1));
    add(
        GuiUtils.getFlowLayoutPanel(
            0,
            10,
            lblLogLevel,
            comboBoxLogLevel,
            GuiUtils.boxHorizontalStrut(15),
            lblStacktraceLimit,
            comboBoxStackLimit));

    add(GuiUtils.boxYLastElement(5));

    checkboxFileLog.addActionListener(e -> checkRollingLog());

    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "logging"); // NON-NLS
  }

  private void checkRollingLog() {
    boolean rolling = checkboxFileLog.isSelected();
    spinner.setEnabled(rolling);
    spinner1.setEnabled(rolling);
  }

  private static int getIntPreferences(String key, int defaultValue, String removedSuffix) {
    if (key != null) {
      String s = GuiUtils.getUICore().getSystemPreferences().getProperty(key);
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
          // Do nothing
        }
      }
    }
    return defaultValue;
  }

  protected void initialize() {
    WProperties prefs = GuiUtils.getUICore().getSystemPreferences();

    comboBoxLogLevel.setSelectedItem(LEVEL.getLevel(prefs.getProperty(AuditLog.LOG_LEVEL, "INFO")));
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

    checkboxFileLog.setSelected(prefs.getBooleanProperty(AuditLog.LOG_FILE_ACTIVATION, false));
    spinner.setValue(getIntPreferences(AuditLog.LOG_FILE_NUMBER, 20, null));
    spinner1.setValue(getIntPreferences(AuditLog.LOG_FILE_SIZE, 10, "MB"));
    checkRollingLog();
  }

  @Override
  public void closeAdditionalWindow() {
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    String limit = (String) comboBoxStackLimit.getSelectedItem();
    preferences.setProperty(
        AuditLog.LOG_STACKTRACE_LIMIT, StringUtil.hasText(limit) ? limit : "-1");

    LEVEL level = (LEVEL) comboBoxLogLevel.getSelectedItem();
    if (level == null) {
      level = LEVEL.INFO;
    }
    preferences.setProperty(AuditLog.LOG_LEVEL, level.toString());
    preferences.putBooleanProperty(AuditLog.LOG_FILE_ACTIVATION, checkboxFileLog.isSelected());
    String logFile = checkboxFileLog.isSelected() ? AuditLog.LOG_FOLDER_PATH + "default.log" : "";
    preferences.setProperty(AuditLog.LOG_FILE, logFile);

    if (checkboxFileLog.isSelected()) {
      String fileNb = spinner.getValue().toString();
      String fileSize = spinner1.getValue().toString() + "MB";
      preferences.setProperty(AuditLog.LOG_FILE_NUMBER, fileNb);
      preferences.setProperty(AuditLog.LOG_FILE_SIZE, fileSize);
    }

    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

    AuditLog.applyConfig(preferences, loggerContext);
  }

  @Override
  public void resetToDefaultValues() {
    // Reset properties used by OSGI service (Sling Logger)
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    preferences.resetServiceProperty(AuditLog.LOG_STACKTRACE_LIMIT, "3");
    preferences.resetServiceProperty(AuditLog.LOG_LEVEL, "INFO");
    preferences.resetServiceProperty(AuditLog.LOG_FILE, "");
    preferences.resetServiceProperty(AuditLog.LOG_FILE_NUMBER, "20");
    preferences.resetServiceProperty(AuditLog.LOG_FILE_SIZE, "10MB"); // NON-NLS

    initialize();
  }
}
