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
import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.service.WProperties;
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
    GuiUtils.setPreferredWidth(comboBoxLang, 300, 150);
    GuiUtils.setPreferredWidth(comboBoxFormat, 300, 150);
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
              <font size="-1">
              %s (%s): %s<BR>
              %2$s (%s): %s<BR>
              %2$s (%s): %s<BR>
              %2$s (%s): %s<BR>
              %s: %s
              </font>
              <p><font color="%s">%s</font></p>
            </html>
            """
        .formatted(
            Messages.getString("GeneralSetting.regionalTitle"),
            Messages.getString("GeneralSetting.date"),
            Messages.getString("GeneralSetting.short"),
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(now),
            Messages.getString("GeneralSetting.medium"),
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(now),
            Messages.getString("GeneralSetting.long"),
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).format(now),
            Messages.getString("GeneralSetting.full"),
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).format(now),
            Messages.getString("GeneralSetting.nb"),
            NumberFormat.getNumberInstance().format(2543456.3465),
            IconColor.ACTIONS_RED.getHtmlCode(),
            Messages.getString("GeneralSetting.alertNote"));
  }

  protected void initialize() {
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    comboBoxLang.selectLocale(preferences.getProperty("locale.lang.code"));
    comboBoxFormat.selectLocale();
  }

  @Override
  public void closeAdditionalWindow() {
    // save preferences
    GuiUtils.getUICore().saveSystemPreferences();
  }

  @Override
  public void resetToDefaultValues() {
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    preferences.resetProperty("locale.lang.code", "en"); // NON-NLS
    // Reset cache of locale format
    Locale.setDefault(Locale.ENGLISH);
    // Reset format to the base.json value or null (default system value)
    preferences.resetProperty("locale.format.code", null);

    initialize();
  }
}
