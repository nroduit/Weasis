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
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.PageItem;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.Messages;

public class GeneralSetting extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(GeneralSetting.class);

  public static final String PAGE_NAME = Messages.getString("GeneralSetting.gen");

  private final JCheckBox checkboxConfirmClosing =
      new JCheckBox(Messages.getString("GeneralSetting.closingConfirmation"));

  private final JPanel menuPanel = new JPanel();

  public GeneralSetting(PreferenceDialog dialog) {
    super(PAGE_NAME, 100);

    try {
      menuPanel.setLayout(new GridLayout(0, 2));
      add(menuPanel);
      add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

      jbInit();
      initialize();
    } catch (Exception e) {
      LOGGER.error("Cannot initialize GeneralSetting", e);
    }

    List<AbstractItemDialogPage> childPages =
        List.of(
            new LanguageSetting(),
            new ThemeSetting(),
            new ScreenPrefView(),
            new ProxyPrefView(),
            new LoggingPrefView());
    childPages.forEach(p -> addSubPage(p, a -> dialog.showPage(p.getTitle()), menuPanel));
  }

  private void jbInit() {
    add(GuiUtils.getFlowLayoutPanel(0, ITEM_SEPARATOR_LARGE, checkboxConfirmClosing));
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
  }

  @Override
  public JPanel getMenuPanel() {
    return menuPanel;
  }

  protected void initialize() {
    WProperties preferences = BundleTools.SYSTEM_PREFERENCES;
    checkboxConfirmClosing.setSelected(
        preferences.getBooleanProperty(BundleTools.CONFIRM_CLOSE, false));
  }

  @Override
  public void closeAdditionalWindow() {
    for (PageItem subpage : getSubPages()) {
      subpage.closeAdditionalWindow();
    }

    BundleTools.SYSTEM_PREFERENCES.putBooleanProperty(
        BundleTools.CONFIRM_CLOSE, checkboxConfirmClosing.isSelected());
  }

  @Override
  public void resetToDefaultValues() {
    BundleTools.SYSTEM_PREFERENCES.resetProperty(
        BundleTools.CONFIRM_CLOSE, Boolean.FALSE.toString());
    initialize();
  }
}
