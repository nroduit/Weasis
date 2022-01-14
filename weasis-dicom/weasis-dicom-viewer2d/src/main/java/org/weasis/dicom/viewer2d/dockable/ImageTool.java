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
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
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
  private final Border spaceY = BorderFactory.createEmptyBorder(10, 3, 0, 3);

  public ImageTool(String pluginName) {
    super(BUTTON_NAME, pluginName, Insertable.Type.TOOL, 20);
    dockable.setTitleIcon(ResourceUtil.getIcon(OtherIcon.IMAGE_EDIT));
    setDockableWidth(300);
    jbInit();
  }

  private void jbInit() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    add(getWindowLevelPanel());
    add(getTransformPanel());
    add(getSlicePanel());
    add(getResetPanel());

    JPanel panel1 = new JPanel();
    panel1.setAlignmentY(Component.TOP_ALIGNMENT);
    panel1.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel1.setLayout(new GridBagLayout());
    add(panel1);
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
    JPanel panel2 = new JPanel();
    panel2.setAlignmentY(Component.TOP_ALIGNMENT);
    panel2.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel2.setLayout(new FlowLayout(FlowLayout.LEFT));
    panel2.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder(Messages.getString("ResetTools.reset"))));
    JComboBox<ResetTools> resetComboBox = new JComboBox<>(ResetTools.values());
    panel2.add(resetComboBox);

    JButton resetButton = new JButton();
    resetButton.setText(Messages.getString("ResetTools.reset"));
    resetButton.addActionListener(
        e -> EventManager.getInstance().reset((ResetTools) resetComboBox.getSelectedItem()));
    panel2.add(resetButton);
    ActionState resetAction = EventManager.getInstance().getAction(ActionW.RESET);
    if (resetAction != null) {
      resetAction.registerActionState(resetButton);
    }
    return panel2;
  }

  public JPanel getSlicePanel() {

    JPanel framePanel = new JPanel();
    framePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    framePanel.setAlignmentY(Component.TOP_ALIGNMENT);
    framePanel.setLayout(new BoxLayout(framePanel, BoxLayout.Y_AXIS));
    framePanel.setBorder(
        BorderFactory.createCompoundBorder(spaceY, GuiUtils.getTitledBorder("Cine")));

    ActionState sequence = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
    if (sequence instanceof SliderCineListener sliderItem) {
      JSliderW frameSlider = sliderItem.createSlider(2, true);
      framePanel.add(frameSlider.getParent());

      JPanel panel3 = new JPanel();
      panel3.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));
      JLabel speedLabel = new JLabel();
      speedLabel.setText("Speed (fps)" + StringUtil.COLON);
      panel3.add(speedLabel);

      JSpinner speedSpinner = new JSpinner(sliderItem.getSpeedModel());
      GuiUtils.formatCheckAction(speedSpinner);
      panel3.add(speedSpinner);
      JButton startButton = new JButton();
      startButton.setActionCommand(ActionW.CINESTART.cmd());
      startButton.setToolTipText(Messages.getString("ImageTool.cine_start"));
      startButton.setIcon(ResourceUtil.getIcon(ActionIcon.EXECUTE));
      startButton.addActionListener(EventManager.getInstance());
      panel3.add(startButton);
      sliderItem.registerActionState(startButton);

      JButton stopButton = new JButton();
      stopButton.setActionCommand(ActionW.CINESTOP.cmd());
      stopButton.setToolTipText(Messages.getString("ImageTool.cine_stop"));
      stopButton.setIcon(ResourceUtil.getIcon(ActionIcon.SUSPEND));
      stopButton.addActionListener(EventManager.getInstance());
      panel3.add(stopButton);
      sliderItem.registerActionState(stopButton);
      framePanel.add(panel3);
    }
    return framePanel;
  }

  public JPanel getWindowLevelPanel() {

    JPanel winLevelPanel = new JPanel();
    winLevelPanel.setAlignmentY(Component.TOP_ALIGNMENT);
    winLevelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    winLevelPanel.setLayout(new BoxLayout(winLevelPanel, BoxLayout.Y_AXIS));
    winLevelPanel.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder(Messages.getString("ImageTool.wl"))));
    ActionState winAction = EventManager.getInstance().getAction(ActionW.WINDOW);
    if (winAction instanceof SliderChangeListener sliderItem) {
      JSliderW windowSlider = sliderItem.createSlider(2, true);
      winLevelPanel.add(windowSlider.getParent());
    }
    ActionState levelAction = EventManager.getInstance().getAction(ActionW.LEVEL);
    if (levelAction instanceof SliderChangeListener sliderItem) {
      JSliderW levelSlider = sliderItem.createSlider(2, true);
      winLevelPanel.add(levelSlider.getParent());
    }

    ActionState presetAction = EventManager.getInstance().getAction(ActionW.PRESET);
    if (presetAction instanceof ComboItemListener<?> comboItem) {
      JPanel panel3 = new JPanel();
      panel3.setLayout(new FlowLayout(FlowLayout.LEFT));
      JLabel presetsLabel = new JLabel();
      panel3.add(presetsLabel);
      presetsLabel.setText(Messages.getString("ImageTool.presets") + StringUtil.COLON);
      JComboBox<?> presetComboBox = comboItem.createCombo();
      presetComboBox.setMaximumRowCount(10);
      panel3.add(presetComboBox);
      winLevelPanel.add(panel3);
    }

    ActionState lutShapeAction = EventManager.getInstance().getAction(ActionW.LUT_SHAPE);
    if (lutShapeAction instanceof ComboItemListener<?> comboItem) {
      JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT));
      JLabel label = new JLabel(ActionW.LUT_SHAPE.getTitle() + StringUtil.COLON);
      pane.add(label);
      JComboBox<?> combo = comboItem.createCombo();
      combo.setMaximumRowCount(10);
      pane.add(combo);
      winLevelPanel.add(pane);
    }

    ActionState lutAction = EventManager.getInstance().getAction(ActionW.LUT);
    if (lutAction instanceof ComboItemListener<?> comboItem) {
      JPanel panel4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
      JLabel lutLabel = new JLabel();
      lutLabel.setText(Messages.getString("ImageTool.lut") + StringUtil.COLON);
      panel4.add(lutLabel);
      JComboBox<?> lutComboBox = comboItem.createCombo();
      panel4.add(lutComboBox);
      ActionState inverseLutAction = EventManager.getInstance().getAction(ActionW.INVERT_LUT);
      if (inverseLutAction instanceof ToggleButtonListener toggleButton) {
        FlatSVGIcon icon = ResourceUtil.getIcon(ActionIcon.INVERSE_LUT);
        JToggleButton checkBox = toggleButton.createJToggleButton(icon);
        checkBox.setPreferredSize(GuiUtils.getBigIconButtonSize(checkBox));
        checkBox.setToolTipText(Messages.getString("ImageTool.inverse"));
        panel4.add(checkBox);
      }
      winLevelPanel.add(panel4);
    }

    ActionState filterAction = EventManager.getInstance().getAction(ActionW.FILTER);
    if (filterAction instanceof ComboItemListener<?> comboItem) {
      JPanel panel4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
      JLabel lutLabel = new JLabel();
      lutLabel.setText(Messages.getString("ImageTool.filter") + StringUtil.COLON);
      panel4.add(lutLabel);
      JComboBox<?> combo = comboItem.createCombo();
      panel4.add(combo);
      winLevelPanel.add(panel4);
    }
    return winLevelPanel;
  }

  public JPanel getTransformPanel() {
    JPanel transform = new JPanel();
    transform.setAlignmentY(Component.TOP_ALIGNMENT);
    transform.setAlignmentX(Component.LEFT_ALIGNMENT);
    transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
    transform.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder(Messages.getString("ImageTool.transform"))));
    ActionState zoomAction = EventManager.getInstance().getAction(ActionW.ZOOM);
    if (zoomAction instanceof SliderChangeListener sliderItem) {
      JSliderW zoomSlider = sliderItem.createSlider(0, true);
      transform.add(zoomSlider.getParent());
    }
    ActionState rotateAction = EventManager.getInstance().getAction(ActionW.ROTATION);
    if (rotateAction instanceof SliderChangeListener sliderItem) {
      JSliderW rotationSlider = sliderItem.createSlider(5, true);
      transform.add(rotationSlider.getParent());
    }
    ActionState flipAction = EventManager.getInstance().getAction(ActionW.FLIP);
    if (flipAction instanceof ToggleButtonListener sliderItem) {
      JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT));
      pane.add(sliderItem.createCheckBox(Messages.getString("View2dContainer.flip_h")));
      transform.add(pane);
    }
    return transform;
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // Do nothing
  }
}
