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
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.PageItem;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;

public class DrawPrefView extends AbstractItemDialogPage {

  private final JPanel menuPanel = new JPanel();

  private final JCheckBox checkboxConfirmDeleteMeasurement =
      new JCheckBox(Messages.getString("DrawPrefView.confirm_delete"));

  public DrawPrefView(PreferenceDialog dialog) {
    super(MeasureTool.BUTTON_NAME, 700);

    menuPanel.setLayout(new GridLayout(0, 2));
    add(menuPanel);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    add(GuiUtils.getFlowLayoutPanel(0, ITEM_SEPARATOR_LARGE, checkboxConfirmDeleteMeasurement));
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "draw-measure"); // NON-NLS
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());

    initialize();

    List<AbstractItemDialogPage> childPages = List.of(new GraphicPrefView(), new LabelsPrefView());
    childPages.forEach(p -> addSubPage(p, _ -> dialog.showPage(p.getTitle()), menuPanel));
  }

  protected void initialize() {
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    checkboxConfirmDeleteMeasurement.setSelected(
        preferences.getBooleanProperty(UICore.CONFIRM_DELETE_MEASUREMENT, true));
  }

  @Override
  public JPanel getMenuPanel() {
    return menuPanel;
  }

  @Override
  public void closeAdditionalWindow() {
    for (PageItem subpage : getSubPages()) {
      subpage.closeAdditionalWindow();
    }
    GuiUtils.getUICore()
        .getSystemPreferences()
        .putBooleanProperty(
            UICore.CONFIRM_DELETE_MEASUREMENT, checkboxConfirmDeleteMeasurement.isSelected());
  }

  @Override
  public void resetToDefaultValues() {
    GuiUtils.getUICore()
        .getSystemPreferences()
        .resetProperty(UICore.CONFIRM_DELETE_MEASUREMENT, Boolean.TRUE.toString());
    initialize();
  }
}
