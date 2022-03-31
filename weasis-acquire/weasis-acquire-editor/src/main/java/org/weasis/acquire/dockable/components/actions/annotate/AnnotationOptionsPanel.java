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

import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import org.weasis.acquire.Messages;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.MeasureToolBar;
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

    ActionState spUnitAction = EventManager.getInstance().getAction(ActionW.SPATIAL_UNIT);
    if (spUnitAction instanceof ComboItemListener<?> comboListener) {
      JLabel label =
          new JLabel(org.weasis.core.ui.Messages.getString("MeasureTool.unit") + StringUtil.COLON);
      JComboBox<?> unitComboBox = comboListener.createCombo(120);
      unitComboBox.setSelectedItem(Unit.PIXEL);
      add(GuiUtils.getFlowLayoutPanel(label, unitComboBox));
    }

    ActionState drawOnceAction = EventManager.getInstance().getAction(ActionW.DRAW_ONLY_ONCE);
    if (drawOnceAction instanceof ToggleButtonListener toggleListener) {
      JCheckBox checkDraw = toggleListener.createCheckBox(ActionW.DRAW_ONLY_ONCE.getTitle());
      checkDraw.setSelected(MeasureTool.viewSetting.isDrawOnlyOnce());
      checkDraw.setAlignmentX(Component.LEFT_ALIGNMENT);
      add(GuiUtils.getFlowLayoutPanel(checkDraw));
    }
  }

  private JPanel createLineStylePanel() {
    JLabel label =
        new JLabel(org.weasis.core.ui.Messages.getString("MeasureToolBar.line") + StringUtil.COLON);
    JButton button = new JButton(ResourceUtil.getIcon(ActionIcon.PIPETTE));
    button.setToolTipText(org.weasis.core.ui.Messages.getString("MeasureTool.pick"));
    button.addActionListener(
        e -> {
          JButton b = (JButton) e.getSource();
          Color newColor =
              JColorChooser.showDialog(
                  SwingUtilities.getWindowAncestor(AnnotationOptionsPanel.this),
                  org.weasis.core.ui.Messages.getString("MeasureTool.pick_color"),
                  b.getBackground());
          if (newColor != null) {
            b.setBackground(newColor);
            MeasureTool.viewSetting.setLineColor(newColor);
            updateMeasureProperties();
          }
        });

    JSpinner spinner = new JSpinner();
    GuiUtils.setNumberModel(spinner, MeasureTool.viewSetting.getLineWidth(), 1, 8, 1);
    spinner.addChangeListener(
        e -> {
          Object val = ((JSpinner) e.getSource()).getValue();
          if (val instanceof Integer intVal) {
            MeasureTool.viewSetting.setLineWidth(intVal);
            updateMeasureProperties();
          }
        });

    return GuiUtils.getFlowLayoutPanel(label, button, spinner);
  }

  private void updateMeasureProperties() {
    MeasureToolBar.measureGraphicList.forEach(
        g -> MeasureToolBar.applyDefaultSetting(MeasureTool.viewSetting, g));
    MeasureToolBar.drawGraphicList.forEach(
        g -> MeasureToolBar.applyDefaultSetting(MeasureTool.viewSetting, g));
  }
}
