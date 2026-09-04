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

import java.awt.Component;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.PageItem;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.graphic.MidpointHandles;
import org.weasis.core.ui.model.utils.bean.GraphicClipboard;
import org.weasis.core.ui.model.utils.bean.GraphicClipboard.PasteMode;

public class DrawPrefView extends AbstractItemDialogPage {

  private final JPanel menuPanel = new JPanel();

  private final JCheckBox checkboxConfirmDeleteMeasurement =
      new JCheckBox(Messages.getString("DrawPrefView.confirm_delete"));

  private final JCheckBox checkboxMidpointHandles =
      new JCheckBox(Messages.getString("DrawPrefView.midpoint_handles"));

  private final JSpinner spinnerMinSpacing =
      new JSpinner(new SpinnerNumberModel(MidpointHandles.DEFAULT_MIN_SPACING, 0.0, 10.0, 0.5));

  private final JComboBox<PasteMode> comboPasteMode = new JComboBox<>(PasteMode.values());

  public DrawPrefView(PreferenceDialog dialog) {
    super(MeasureTool.BUTTON_NAME, 700);

    comboPasteMode.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            setText(pasteModeName(value));
            return this;
          }
        });

    menuPanel.setLayout(new GridLayout(0, 2));
    add(menuPanel);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    add(GuiUtils.getFlowLayoutPanel(0, ITEM_SEPARATOR_LARGE, checkboxConfirmDeleteMeasurement));
    add(GuiUtils.getFlowLayoutPanel(0, ITEM_SEPARATOR_LARGE, checkboxMidpointHandles));
    add(
        GuiUtils.getFlowLayoutPanel(
            0,
            ITEM_SEPARATOR_LARGE,
            new JLabel(Messages.getString("DrawPrefView.handle_spacing")),
            spinnerMinSpacing));
    add(
        GuiUtils.getFlowLayoutPanel(
            0,
            ITEM_SEPARATOR_LARGE,
            new JLabel(Messages.getString("DrawPrefView.paste_mode")),
            comboPasteMode));
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "draw-measure"); // NON-NLS
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());

    initialize();

    List<AbstractItemDialogPage> childPages =
        List.of(new GraphicPrefView(), new ProfilePrefView(), new LabelsPrefView());
    childPages.forEach(p -> addSubPage(p, _ -> dialog.showPage(p.getTitle()), menuPanel));
  }

  protected void initialize() {
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    checkboxConfirmDeleteMeasurement.setSelected(
        preferences.getBooleanProperty(UICore.CONFIRM_DELETE_MEASUREMENT, true));
    checkboxMidpointHandles.setSelected(
        preferences.getBooleanProperty(
            MidpointHandles.P_MIDPOINT, MidpointHandles.DEFAULT_MIDPOINT));
    spinnerMinSpacing.setValue(
        preferences.getDoubleProperty(
            MidpointHandles.P_MIN_SPACING, MidpointHandles.DEFAULT_MIN_SPACING));
    comboPasteMode.setSelectedItem(GraphicClipboard.defaultPasteMode());
  }

  private static String pasteModeName(Object value) {
    String key =
        value == PasteMode.IN_PLACE ? "DrawPrefView.paste_at_origin" : "DrawPrefView.paste_cursor";
    return Messages.getString(key);
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
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    preferences.putBooleanProperty(
        UICore.CONFIRM_DELETE_MEASUREMENT, checkboxConfirmDeleteMeasurement.isSelected());
    preferences.putBooleanProperty(
        MidpointHandles.P_MIDPOINT, checkboxMidpointHandles.isSelected());
    preferences.putDoubleProperty(
        MidpointHandles.P_MIN_SPACING, ((Number) spinnerMinSpacing.getValue()).doubleValue());
    preferences.setProperty(
        GraphicClipboard.P_PASTE_MODE,
        comboPasteMode.getSelectedItem() == PasteMode.IN_PLACE ? "inPlace" : "cursor"); // NON-NLS
    MidpointHandles.reload();
  }

  @Override
  public void resetToDefaultValues() {
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    preferences.resetProperty(UICore.CONFIRM_DELETE_MEASUREMENT, Boolean.TRUE.toString());
    preferences.resetProperty(
        MidpointHandles.P_MIDPOINT, Boolean.toString(MidpointHandles.DEFAULT_MIDPOINT));
    preferences.resetProperty(
        MidpointHandles.P_MIN_SPACING, Double.toString(MidpointHandles.DEFAULT_MIN_SPACING));
    preferences.resetProperty(GraphicClipboard.P_PASTE_MODE, "cursor"); // NON-NLS
    MidpointHandles.reload();
    initialize();
  }
}
