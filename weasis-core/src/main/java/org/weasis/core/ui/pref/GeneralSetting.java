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

import javax.swing.JCheckBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Settings;

public class GeneralSetting extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(GeneralSetting.class);

  public static final String PAGE_NAME = Messages.common("general");

  private final JCheckBox checkboxConfirmClosing =
      new JCheckBox(Messages.getString("GeneralSetting.closingConfirmation"));

  public GeneralSetting() {
    super(PAGE_NAME, 100);
    jbInit();
    initialize();
  }

  private void jbInit() {
    add(GuiUtils.getFlowLayoutPanel(0, ITEM_SEPARATOR_LARGE, checkboxConfirmClosing));
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
  }

  protected void initialize() {
    checkboxConfirmClosing.setSelected(Settings.get(CoreSettings.CONFIRM_CLOSING));
  }

  @Override
  public void closeAdditionalWindow() {
    Settings.set(CoreSettings.CONFIRM_CLOSING, checkboxConfirmClosing.isSelected());
  }

  @Override
  public void resetToDefaultValues() {
    Settings.reset(CoreSettings.CONFIRM_CLOSING);
    initialize();
  }
}
