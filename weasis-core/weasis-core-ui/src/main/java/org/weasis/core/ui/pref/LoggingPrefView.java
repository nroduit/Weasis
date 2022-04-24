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

import java.io.File;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.AuditLog.LEVEL;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.Messages;
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
          spinner, getIntPreferences(AuditLog.LOG_FILE_NUMBER, 5, null), 1, 99, 1);
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
          // Do nothing
        }
      }
    }
    return defaultValue;
  }

  protected void initialize() {
    WProperties prefs = BundleTools.SYSTEM_PREFERENCES;

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

    checkboxFileLog.setSelected(prefs.getBooleanProperty(AuditLog.LOG_FILE, false));
    spinner.setValue(getIntPreferences(AuditLog.LOG_FILE_NUMBER, 5, null));
    spinner1.setValue(getIntPreferences(AuditLog.LOG_FILE_SIZE, 10, "MB"));
    checkRollingLog();
  }

  @Override
  public void closeAdditionalWindow() {
    String limit = (String) comboBoxStackLimit.getSelectedItem();
    BundleTools.SYSTEM_PREFERENCES.setProperty(
        AuditLog.LOG_STACKTRACE_LIMIT, StringUtil.hasText(limit) ? limit : "-1");

    LEVEL level = (LEVEL) comboBoxLogLevel.getSelectedItem();
    if (level == null) {
      level = LEVEL.INFO;
    }
    BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_LEVEL, level.toString());
    BundleTools.SYSTEM_PREFERENCES.putBooleanProperty(
        AuditLog.LOG_FILE_ACTIVATION, checkboxFileLog.isSelected());
    String logFile =
        checkboxFileLog.isSelected()
            ? AppProperties.WEASIS_PATH + File.separator + "log" + File.separator + "default.log"
            : ""; // NON-NLS
    BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_FILE, logFile);
    String fileNb = null;
    String fileSize = null;
    if (checkboxFileLog.isSelected()) {
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
  public void resetToDefaultValues() {
    // Reset properties used by OSGI service (Sling Logger)
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_STACKTRACE_LIMIT, "3");
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_LEVEL, "INFO");
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_FILE, "");
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_FILE_NUMBER, "5");
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_FILE_SIZE, "10MB"); // NON-NLS

    initialize();
  }
}
