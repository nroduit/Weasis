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

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.ZonedDateTime;
import java.time.format.FormatStyle;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.util.StringUtil;

public class LanguagelSetting extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(LanguagelSetting.class);

  public static final String PAGE_NAME = Messages.getString("GeneralSetting.language");

  private final JPanel panel = new JPanel();
  private final GridBagLayout gridBagLayout1 = new GridBagLayout();

  private final JTextPane txtpnNote = new JTextPane();
  private final JLabel labelLocale =
      new JLabel(Messages.getString("GeneralSetting.language") + StringUtil.COLON);
  private final JLabel labelLocale2 =
      new JLabel(Messages.getString("GeneralSetting.language.data") + StringUtil.COLON);
  private final JLocaleFormat comboBoxFormat =
      new JLocaleFormat() {
        @Override
        public void valueHasChanged() {
          txtpnNote.setText(getText());
        }
      };
  private final JLocaleLanguage comboBoxLang =
      new JLocaleLanguage() {
        @Override
        public void valueHasChanged() {
          comboBoxFormat.refresh();
        }
      };

  public LanguagelSetting() {
    super(PAGE_NAME);
    setComponentPosition(0);

    try {
      jbInit();
      initialize(true);
    } catch (Exception e) {
      LOGGER.error("Cannot initialize GeneralSetting", e);
    }
  }

  private void jbInit() {
    this.setLayout(gridBagLayout1);

    GridBagConstraints gbcLabel = new GridBagConstraints();
    gbcLabel.insets = new Insets(15, 10, 5, 5);
    gbcLabel.anchor = GridBagConstraints.LINE_END;
    gbcLabel.gridx = 0;
    gbcLabel.gridy = 1;
    add(labelLocale, gbcLabel);

    GridBagConstraints gbcComboBox = new GridBagConstraints();
    gbcComboBox.gridwidth = 3;
    gbcComboBox.anchor = GridBagConstraints.WEST;
    gbcComboBox.insets = new Insets(15, 0, 5, 0);
    gbcComboBox.gridx = 1;
    gbcComboBox.gridy = 1;
    add(comboBoxLang, gbcComboBox);

    GridBagConstraints gbcLabel2 = new GridBagConstraints();
    gbcLabel2.insets = new Insets(5, 10, 5, 5);
    gbcLabel2.anchor = GridBagConstraints.LINE_END;
    gbcLabel2.gridx = 0;
    gbcLabel2.gridy = 2;
    add(labelLocale2, gbcLabel2);

    GridBagConstraints gbcComboBox2 = new GridBagConstraints();
    gbcComboBox2.gridwidth = 3;
    gbcComboBox2.anchor = GridBagConstraints.WEST;
    gbcComboBox2.insets = new Insets(5, 0, 5, 0);
    gbcComboBox2.gridx = 1;
    gbcComboBox2.gridy = 2;
    add(comboBoxFormat, gbcComboBox2);

    GridBagConstraints gbcTxtpnNote = new GridBagConstraints();
    gbcTxtpnNote.anchor = GridBagConstraints.WEST;
    gbcTxtpnNote.gridwidth = 4;
    gbcTxtpnNote.insets = new Insets(5, 10, 5, 10);
    gbcTxtpnNote.fill = GridBagConstraints.HORIZONTAL;
    gbcTxtpnNote.gridx = 0;
    gbcTxtpnNote.gridy = 3;
    txtpnNote.setContentType("text/html");
    txtpnNote.setEditable(false);
    txtpnNote.setText(getText());
    add(txtpnNote, gbcTxtpnNote);

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

    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "locale");
  }

  private static String getText() {
    ZonedDateTime now = ZonedDateTime.now();
    return String.format(
        Messages.getString("GeneralSetting.txtNote"),
        LocalUtil.getDateTimeFormatter(FormatStyle.SHORT).format(now),
        LocalUtil.getDateTimeFormatter(FormatStyle.MEDIUM).format(now),
        LocalUtil.getDateTimeFormatter(FormatStyle.LONG).format(now),
        LocalUtil.getDateTimeFormatter(FormatStyle.FULL).format(now),
        LocalUtil.getNumberInstance().format(2543456.3465));
  }

  protected void initialize(boolean afirst) {
    WProperties prfs = BundleTools.SYSTEM_PREFERENCES;
    comboBoxLang.selectLocale(prfs.getProperty("locale.lang.code"));
    comboBoxFormat.selectLocale();
  }

  @Override
  public void closeAdditionalWindow() {
    // save preferences
    BundleTools.saveSystemPreferences();
  }

  @Override
  public void resetToDefaultValues() {
    BundleTools.SYSTEM_PREFERENCES.resetProperty("locale.lang.code", "en"); // NON-NLS
    // Reset cache of locale format
    LocalUtil.setLocaleFormat(null);
    // Reset format to the config.properties value or null (default system value)
    BundleTools.SYSTEM_PREFERENCES.resetProperty("locale.format.code", null);

    initialize(false);
  }
}
