/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable.components.actions.annotate;

import java.awt.Component;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.border.Border;
import org.weasis.acquire.Messages;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.util.StringUtil;

public class AnnotationOptionsPanel extends JPanel {

  public AnnotationOptionsPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    Border spaceY = GuiUtils.getEmptyBorder(10, 5, 2, 5);
    setBorder(
        BorderFactory.createCompoundBorder(
            spaceY,
            GuiUtils.getTitledBorder(Messages.getString("AnnotationOptionsPanel.options"))));

    add(createLineStylePanel());

    Optional<ComboItemListener<Unit>> spUnitAction =
        EventManager.getInstance().getAction(ActionW.SPATIAL_UNIT);
    spUnitAction.ifPresent(
        comboItemListener -> {
          JLabel label =
              new JLabel(org.weasis.core.Messages.getString("MeasureTool.unit") + StringUtil.COLON);
          JComboBox<?> unitComboBox = comboItemListener.createCombo(120);
          unitComboBox.setSelectedItem(Unit.PIXEL);
          add(GuiUtils.getFlowLayoutPanel(label, unitComboBox));
        });

    Optional<ToggleButtonListener> drawOnceAction =
        EventManager.getInstance().getAction(ActionW.DRAW_ONLY_ONCE);
    drawOnceAction.ifPresent(
        toggleButtonListener -> {
          JCheckBox checkDraw =
              toggleButtonListener.createCheckBox(ActionW.DRAW_ONLY_ONCE.getTitle());
          checkDraw.setSelected(MeasureTool.viewSetting.isDrawOnlyOnce());
          checkDraw.setAlignmentX(Component.LEFT_ALIGNMENT);
          add(GuiUtils.getFlowLayoutPanel(checkDraw));
        });
  }

  private JPanel createLineStylePanel() {
    JLabel label =
        new JLabel(org.weasis.core.Messages.getString("MeasureToolBar.line") + StringUtil.COLON);
    JButton button = MeasureTool.buildLineColorButton(this);

    JSpinner spinner = new JSpinner();
    MeasureTool.viewSetting.initLineWidthSpinner(spinner);

    return GuiUtils.getFlowLayoutPanel(label, button, spinner);
  }
}
