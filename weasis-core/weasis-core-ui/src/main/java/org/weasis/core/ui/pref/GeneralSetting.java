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

import java.awt.GridLayout;
import java.io.File;
import java.util.List;
import javax.swing.BoxLayout;
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
import org.weasis.core.api.gui.util.PageItem;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.AuditLog.LEVEL;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.Messages;
import org.weasis.core.util.StringUtil;

public class GeneralSetting extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(GeneralSetting.class);

  public static final String PAGE_NAME = Messages.getString("GeneralSetting.gen");

  private final JCheckBox checkboxConfirmClosing =
      new JCheckBox(Messages.getString("GeneralSetting.closingConfirmation"));

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

  public GeneralSetting(PreferenceDialog dialog) {
    super(PAGE_NAME);
    setComponentPosition(0);
    setBorder(GuiUtils.getEmptydBorder(15, 10, 10, 10));
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    JPanel panel = new JPanel();
    try {
      GuiUtils.setNumberModel(
          spinner, getIntPreferences(AuditLog.LOG_FILE_NUMBER, 5, null), 1, 99, 1);
      GuiUtils.setNumberModel(
          spinner1, getIntPreferences(AuditLog.LOG_FILE_SIZE, 10, "MB"), 1, 99, 1);

      panel.setLayout(new GridLayout(0, 2));
      add(panel, 0);
      add(GuiUtils.createVerticalStrut(15), 1);

      jbInit();
      initialize(true);
    } catch (Exception e) {
      LOGGER.error("Cannot initialize GeneralSetting", e);
    }

    List<AbstractItemDialogPage> childPages =
        List.of(
            new LanguagelSetting(), new LooklSetting(), new ScreenPrefView(), new ProxyPrefView());
    childPages.forEach(this::addSubPage);



    childPages.forEach(
        p -> {
          JButton button = new JButton();
          button.setText(p.getTitle());
          button.addActionListener(a -> dialog.showPage(p.getTitle()));
          panel.add(button);
        });

  }

  private void jbInit() {

    add(GuiUtils.getComponentsInJPanel(0, 10, checkboxConfirmClosing));
    add(GuiUtils.createVerticalStrut(15));

    JPanel panel = new JPanel();
    panel.setBorder(GuiUtils.getTitledBorder("Logging"));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(
        GuiUtils.getComponentsInJPanel(
            2,
            10,
            checkboxFileLog,
            GuiUtils.createHorizontalStrut(15),
            labelNumber,
            spinner,
            GuiUtils.createHorizontalStrut(15),
            labelSize,
            spinner1));
    panel.add(
        GuiUtils.getComponentsInJPanel(
            0,
            10,
            lblLogLevel,
            comboBoxLogLevel,
            GuiUtils.createHorizontalStrut(15),
            lblStacktraceLimit,
            comboBoxStackLimit));
    add(panel);

    add(GuiUtils.getBoxYLastElement(15));

    checkboxFileLog.addActionListener(e -> checkRolingLog());

    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "locale");
  }

  private void checkRolingLog() {
    boolean rolling = checkboxFileLog.isSelected();
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
    checkboxConfirmClosing.setSelected(prfs.getBooleanProperty(BundleTools.CONFIRM_CLOSE, false));

    comboBoxLogLevel.setSelectedItem(LEVEL.getLevel(prfs.getProperty(AuditLog.LOG_LEVEL, "INFO")));
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

    checkboxFileLog.setSelected(StringUtil.hasText(prfs.getProperty(AuditLog.LOG_FILE, "")));
    spinner.setValue(getIntPreferences(AuditLog.LOG_FILE_NUMBER, 5, null));
    spinner1.setValue(getIntPreferences(AuditLog.LOG_FILE_SIZE, 10, "MB"));
    checkRolingLog();
  }

  @Override
  public void closeAdditionalWindow() {
    for (PageItem subpage : getSubPages()) {
      subpage.closeAdditionalWindow();
    }

    BundleTools.SYSTEM_PREFERENCES.putBooleanProperty(
        "weasis.confirm.closing", checkboxConfirmClosing.isSelected());

    String limit = (String) comboBoxStackLimit.getSelectedItem();
    BundleTools.SYSTEM_PREFERENCES.setProperty(
        AuditLog.LOG_STACKTRACE_LIMIT, StringUtil.hasText(limit) ? limit : "-1");

    LEVEL level = (LEVEL) comboBoxLogLevel.getSelectedItem();
    BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_LEVEL, level.toString());
    BundleTools.SYSTEM_PREFERENCES.setProperty(
        AuditLog.LOG_FILE_ACTIVATION, String.valueOf(checkboxFileLog.isSelected()));
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
    BundleTools.SYSTEM_PREFERENCES.resetProperty(
        BundleTools.CONFIRM_CLOSE, Boolean.FALSE.toString());

    // Reset properties used by OSGI service (Sling Logger)
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_STACKTRACE_LIMIT, "3");
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_LEVEL, "INFO");
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_FILE, "");
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_FILE_NUMBER, "5");
    BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_FILE_SIZE, "10MB"); // NON-NLS

    initialize(false);
  }
}
