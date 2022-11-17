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
import java.time.ZonedDateTime;
import java.time.format.FormatStyle;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.util.StringUtil;

public class LanguageSetting extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(LanguageSetting.class);

  public static final String PAGE_NAME = Messages.getString("GeneralSetting.language");

  private final JTextPane textPane = new JTextPane();
  private final JLabel labelLocale =
      new JLabel(Messages.getString("GeneralSetting.language") + StringUtil.COLON);
  private final JLabel labelLocale2 =
      new JLabel(Messages.getString("GeneralSetting.language.data") + StringUtil.COLON);
  private final JLocaleFormat comboBoxFormat =
      new JLocaleFormat() {
        @Override
        public void valueHasChanged() {
          textPane.setText(getText());
        }
      };
  private final JLocaleLanguage comboBoxLang =
      new JLocaleLanguage() {
        @Override
        public void valueHasChanged() {
          comboBoxFormat.refresh();
        }
      };

  public LanguageSetting() {
    super(PAGE_NAME, 101);

    try {
      jbInit();
      initialize();
    } catch (Exception e) {
      LOGGER.error("Cannot initialize GeneralSetting", e);
    }
  }

  private void jbInit() {
    add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING,
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR_LARGE,
            labelLocale,
            comboBoxLang));
    add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING,
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            labelLocale2,
            comboBoxFormat));
    textPane.setContentType("text/html");
    textPane.setEditable(false);
    add(textPane);

    add(GuiUtils.boxYLastElement(5));

    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "locale"); // NON-NLS
  }

  private static String getText() {
    ZonedDateTime now = ZonedDateTime.now();
    return """
            <html>
              <h3>%s</h3>
              %s (%s): %s<BR>
              %2$s (%s): %s<BR>
              %2$s (%s): %s<BR>
              %2$s (%s): %s<BR>
              %s: %s
              <p><font color="%s">%s</font></p>
            </html>
            """
        .formatted(
            Messages.getString("GeneralSetting.regionalTitle"),
            Messages.getString("GeneralSetting.date"),
            Messages.getString("GeneralSetting.short"),
            LocalUtil.getDateTimeFormatter(FormatStyle.SHORT).format(now),
            Messages.getString("GeneralSetting.medium"),
            LocalUtil.getDateTimeFormatter(FormatStyle.MEDIUM).format(now),
            Messages.getString("GeneralSetting.long"),
            LocalUtil.getDateTimeFormatter(FormatStyle.LONG).format(now),
            Messages.getString("GeneralSetting.full"),
            LocalUtil.getDateTimeFormatter(FormatStyle.FULL).format(now),
            Messages.getString("GeneralSetting.nb"),
            LocalUtil.getNumberInstance().format(2543456.3465),
            IconColor.ACTIONS_RED.getHtmlCode(),
            Messages.getString("GeneralSetting.alertNote"));
  }

  protected void initialize() {
    WProperties preferences = BundleTools.SYSTEM_PREFERENCES;
    comboBoxLang.selectLocale(preferences.getProperty("locale.lang.code"));
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

    initialize();
  }
}
