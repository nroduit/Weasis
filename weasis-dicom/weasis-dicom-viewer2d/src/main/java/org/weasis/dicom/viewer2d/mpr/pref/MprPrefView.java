/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.pref;

import java.awt.FlowLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.mpr.MprFactory;

public class MprPrefView extends AbstractItemDialogPage {

  private final JComboBox<String> comboBox3DCursorMode;
  private final JSpinner spinnerCrossGapSize;

  public MprPrefView() {
    super(MprFactory.NAME, 507);
    this.comboBox3DCursorMode = new JComboBox<>();
    this.spinnerCrossGapSize = new JSpinner(new SpinnerNumberModel(40, 0, 60, 1));
    GuiUtils.setSpinnerWidth(spinnerCrossGapSize, 3);
    initGUI();
  }

  private void initGUI() {
    EventManager eventManager = EventManager.getInstance();
    JLabel lblMode = new JLabel("Auto center axes" + StringUtil.COLON);

    comboBox3DCursorMode.addItem("Never");
    comboBox3DCursorMode.addItem("Only when the center is hidden");
    comboBox3DCursorMode.addItem("Always");

    int mode = eventManager.getOptions().getIntProperty(View2d.P_CROSSHAIR_MODE, 1);
    if (mode < 0 && mode >= comboBox3DCursorMode.getModel().getSize()) {
      mode = 1;
    }
    comboBox3DCursorMode.setSelectedIndex(mode);

    int shiftX = ITEM_SEPARATOR - ITEM_SEPARATOR_SMALL;
    JPanel panel1 =
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING,
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            GuiUtils.boxHorizontalStrut(shiftX),
            lblMode,
            comboBox3DCursorMode);
    add(panel1);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    JLabel labelGapSize = new JLabel("Crosshair gap at the center" + StringUtil.COLON); // NON-NLS
    int gapSize = eventManager.getOptions().getIntProperty(View2d.P_CROSSHAIR_CENTER_GAP, 40);
    spinnerCrossGapSize.setValue(gapSize);
    add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING,
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            GuiUtils.boxHorizontalStrut(shiftX),
            labelGapSize,
            spinnerCrossGapSize));

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "mpr"); // NON-NLS
  }

  @Override
  public void closeAdditionalWindow() {
    EventManager eventManager = EventManager.getInstance();

    int mode = comboBox3DCursorMode.getSelectedIndex();
    eventManager.getOptions().putIntProperty(View2d.P_CROSSHAIR_MODE, mode);
    int gapSize = (int) spinnerCrossGapSize.getValue();
    eventManager.getOptions().putIntProperty(View2d.P_CROSSHAIR_CENTER_GAP, gapSize);

    BundleTools.saveSystemPreferences();
  }

  @Override
  public void resetToDefaultValues() {

    comboBox3DCursorMode.setSelectedIndex(1);

    // Get the default server configuration and if no value take the default value in parameter.
    WProperties properties = EventManager.getInstance().getOptions();
    properties.resetProperty(View2d.P_CROSSHAIR_CENTER_GAP, null);
    int gapSize = properties.getIntProperty(View2d.P_CROSSHAIR_CENTER_GAP, 40);
    spinnerCrossGapSize.setValue(gapSize);
  }
}
