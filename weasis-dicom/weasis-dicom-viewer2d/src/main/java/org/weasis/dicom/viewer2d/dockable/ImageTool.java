/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.dockable;

import bibliothek.gui.dock.common.CLocation;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Component;
import java.text.MessageFormat;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.border.Border;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.ResetTools;

public class ImageTool extends PluginTool {

  public static final String BUTTON_NAME = Messages.getString("ImageTool.img_tool");

  private final JScrollPane rootPane = new JScrollPane();
  private final Border spaceY = GuiUtils.getEmptyBorder(15, 3, 0, 3);

  public ImageTool(String pluginName) {
    super(BUTTON_NAME, pluginName, Insertable.Type.TOOL, 20);
    dockable.setTitleIcon(ResourceUtil.getIcon(OtherIcon.IMAGE_EDIT));
    setDockableWidth(290);
    init();
  }

  private void init() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    add(getWindowLevelPanel());
    add(getTransformPanel());
    add(getSlicePanel());
    add(getResetPanel());
    add(GuiUtils.boxYLastElement(3));
    rootPane.setBorder(BorderFactory.createEmptyBorder()); // remove default line
  }

  @Override
  public Component getToolComponent() {
    JViewport viewPort = rootPane.getViewport();
    if (viewPort == null) {
      viewPort = new JViewport();
      rootPane.setViewport(viewPort);
    }
    if (viewPort.getView() != this) {
      viewPort.setView(this);
    }
    return rootPane;
  }

  public JPanel getResetPanel() {
    JComboBox<ResetTools> resetComboBox = new JComboBox<>(ResetTools.values());
    JButton resetButton = new JButton();
    resetButton.setText(Messages.getString("ResetTools.reset"));
    resetButton.addActionListener(
        e -> EventManager.getInstance().reset((ResetTools) resetComboBox.getSelectedItem()));
    ActionState resetAction = EventManager.getInstance().getAction(ActionW.RESET);
    if (resetAction != null) {
      resetAction.registerActionState(resetButton);
    }
    JPanel panel = GuiUtils.getFlowLayoutPanel(resetComboBox, resetButton);
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder(Messages.getString("ResetTools.reset"))));
    return panel;
  }

  public JPanel getSlicePanel() {
    ActionState sequence = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
    if (sequence instanceof SliderCineListener sliderItem) {
      JSliderW frameSlider = sliderItem.createSlider(2, true);

      JLabel speedLabel = new JLabel();
      speedLabel.setText(MessageFormat.format(Messages.getString("speed.fps"), StringUtil.COLON));

      JSpinner speedSpinner = new JSpinner(sliderItem.getSpeedModel());
      GuiUtils.formatCheckAction(speedSpinner);

      JButton startButton = new JButton();
      startButton.setActionCommand(ActionW.CINESTART.cmd());
      startButton.setToolTipText(Messages.getString("ImageTool.cine_start"));
      startButton.setIcon(ResourceUtil.getIcon(ActionIcon.EXECUTE));
      startButton.addActionListener(EventManager.getInstance());
      sliderItem.registerActionState(startButton);

      JButton stopButton = new JButton();
      stopButton.setActionCommand(ActionW.CINESTOP.cmd());
      stopButton.setToolTipText(Messages.getString("ImageTool.cine_stop"));
      stopButton.setIcon(ResourceUtil.getIcon(ActionIcon.SUSPEND));
      stopButton.addActionListener(EventManager.getInstance());
      sliderItem.registerActionState(stopButton);

      JPanel panel =
          GuiUtils.getFlowLayoutPanel(3, 3, speedLabel, speedSpinner, startButton, stopButton);

      JPanel framePanel = GuiUtils.getVerticalBoxLayoutPanel(frameSlider, panel);
      framePanel.setBorder(
          BorderFactory.createCompoundBorder(
              spaceY, GuiUtils.getTitledBorder(Messages.getString("cine"))));
      return framePanel;
    }
    return new JPanel();
  }

  public JPanel getWindowLevelPanel() {
    int gabY = 7;
    JPanel winLevelPanel = GuiUtils.getVerticalBoxLayoutPanel();
    winLevelPanel.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder(Messages.getString("ImageTool.wl"))));
    ActionState winAction = EventManager.getInstance().getAction(ActionW.WINDOW);
    if (winAction instanceof SliderChangeListener sliderItem) {
      JSliderW windowSlider = sliderItem.createSlider(2, true);
      GuiUtils.setPreferredWidth(windowSlider, 100);
      winLevelPanel.add(windowSlider);
      winLevelPanel.add(GuiUtils.boxVerticalStrut(gabY));
    }
    ActionState levelAction = EventManager.getInstance().getAction(ActionW.LEVEL);
    if (levelAction instanceof SliderChangeListener sliderItem) {
      JSliderW levelSlider = sliderItem.createSlider(2, true);
      GuiUtils.setPreferredWidth(levelSlider, 100);
      winLevelPanel.add(levelSlider);
      winLevelPanel.add(GuiUtils.boxVerticalStrut(gabY));
    }

    ActionState presetAction = EventManager.getInstance().getAction(ActionW.PRESET);
    if (presetAction instanceof ComboItemListener<?> comboItem) {
      JLabel presetsLabel = new JLabel(Messages.getString("ImageTool.presets") + StringUtil.COLON);
      JComboBox<?> presetComboBox = comboItem.createCombo(160);
      presetComboBox.setMaximumRowCount(10);
      winLevelPanel.add(GuiUtils.getHorizontalBoxLayoutPanel(5, presetsLabel, presetComboBox));
      winLevelPanel.add(GuiUtils.boxVerticalStrut(gabY));
    }

    ActionState lutShapeAction = EventManager.getInstance().getAction(ActionW.LUT_SHAPE);
    if (lutShapeAction instanceof ComboItemListener<?> comboItem) {
      JLabel label = new JLabel(ActionW.LUT_SHAPE.getTitle() + StringUtil.COLON);
      JComboBox<?> combo = comboItem.createCombo(140);
      combo.setMaximumRowCount(10);
      winLevelPanel.add(GuiUtils.getHorizontalBoxLayoutPanel(5, label, combo));
      winLevelPanel.add(GuiUtils.boxVerticalStrut(gabY));
    }

    ActionState lutAction = EventManager.getInstance().getAction(ActionW.LUT);
    if (lutAction instanceof ComboItemListener<?> comboItem) {
      JLabel lutLabel = new JLabel(Messages.getString("ImageTool.lut") + StringUtil.COLON);
      JComboBox<?> lutComboBox = comboItem.createCombo(140);
      JPanel lutPanel = GuiUtils.getHorizontalBoxLayoutPanel(5, lutLabel, lutComboBox);

      ActionState inverseLutAction = EventManager.getInstance().getAction(ActionW.INVERT_LUT);
      if (inverseLutAction instanceof ToggleButtonListener toggleButton) {
        FlatSVGIcon icon = ResourceUtil.getIcon(ActionIcon.INVERSE_LUT);
        JToggleButton checkBox = toggleButton.createJToggleButton(icon);
        checkBox.setPreferredSize(GuiUtils.getBigIconButtonSize(checkBox));
        checkBox.setToolTipText(Messages.getString("ImageTool.inverse"));
        lutPanel.add(checkBox);
      }
      winLevelPanel.add(lutPanel);
      winLevelPanel.add(GuiUtils.boxVerticalStrut(gabY));
    }

    ActionState filterAction = EventManager.getInstance().getAction(ActionW.FILTER);
    if (filterAction instanceof ComboItemListener<?> comboItem) {
      JLabel label = new JLabel(Messages.getString("ImageTool.filter") + StringUtil.COLON);
      JComboBox<?> combo = comboItem.createCombo(160);
      winLevelPanel.add(GuiUtils.getHorizontalBoxLayoutPanel(5, label, combo));
      winLevelPanel.add(GuiUtils.boxVerticalStrut(5));
    }
    return winLevelPanel;
  }

  public JPanel getTransformPanel() {
    JPanel transform = GuiUtils.getVerticalBoxLayoutPanel();
    transform.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder(Messages.getString("ImageTool.transform"))));
    ActionState zoomAction = EventManager.getInstance().getAction(ActionW.ZOOM);
    if (zoomAction instanceof SliderChangeListener sliderItem) {
      JSliderW zoomSlider = sliderItem.createSlider(0, true);
      GuiUtils.setPreferredWidth(zoomSlider, 100);
      transform.add(zoomSlider);
    }
    ActionState rotateAction = EventManager.getInstance().getAction(ActionW.ROTATION);
    if (rotateAction instanceof SliderChangeListener sliderItem) {
      JSliderW rotationSlider = sliderItem.createSlider(5, true);
      GuiUtils.setPreferredWidth(rotationSlider, 100);
      transform.add(rotationSlider);
    }
    ActionState flipAction = EventManager.getInstance().getAction(ActionW.FLIP);
    if (flipAction instanceof ToggleButtonListener toggleButton) {
      JPanel pane = GuiUtils.getFlowLayoutPanel();
      pane.add(toggleButton.createCheckBox(Messages.getString("View2dContainer.flip_h")));
      transform.add(pane);
    }
    return transform;
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // Do nothing
  }
}
