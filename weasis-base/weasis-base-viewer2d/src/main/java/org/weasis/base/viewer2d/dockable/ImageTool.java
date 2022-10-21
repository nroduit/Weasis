/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.viewer2d.dockable;

import bibliothek.gui.dock.common.CLocation;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Component;
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
import org.weasis.base.viewer2d.EventManager;
import org.weasis.base.viewer2d.Messages;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.image.ResetTools;
import org.weasis.core.util.StringUtil;

public class ImageTool extends PluginTool {

  public static final String BUTTON_NAME = Messages.getString("ImageTool.imgtool");

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
    JButton resetButton = new JButton(Messages.getString("ImageTool.reset"));
    resetButton.addActionListener(
        e -> {
          ResetTools reset = (ResetTools) resetComboBox.getSelectedItem();
          if (reset != null) {
            EventManager.getInstance().reset(reset);
          }
        });
    EventManager.getInstance()
        .getAction(ActionW.RESET)
        .ifPresent(
            a -> {
              a.registerActionState(resetComboBox);
              a.registerActionState(resetButton);
            });

    JPanel panel = GuiUtils.getFlowLayoutPanel(resetComboBox, resetButton);
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder(Messages.getString("ImageTool.reset"))));
    return panel;
  }

  public JPanel getSlicePanel() {
    JPanel framePanel = GuiUtils.getVerticalBoxLayoutPanel();
    framePanel.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder(Messages.getString("cine"))));

    EventManager.getInstance()
        .getAction(ActionW.SCROLL_SERIES)
        .ifPresent(
            sliderItem -> {
              JSliderW frameSlider = sliderItem.createSlider(2, true);

              JLabel speedLabel = new JLabel();
              speedLabel.setText(Messages.getString("speed.fps") + StringUtil.COLON);

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
              framePanel.add(frameSlider);
              framePanel.add(
                  GuiUtils.getFlowLayoutPanel(
                      3, 3, speedLabel, speedSpinner, startButton, stopButton));
            });
    return framePanel;
  }

  public JPanel getWindowLevelPanel() {
    int gabY = 7;
    JPanel winLevelPanel = GuiUtils.getVerticalBoxLayoutPanel();
    winLevelPanel.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder(Messages.getString("ImageTool.wl"))));
    EventManager.getInstance()
        .getAction(ActionW.WINDOW)
        .ifPresent(
            sliderItem -> {
              JSliderW windowSlider = sliderItem.createSlider(2, true);
              GuiUtils.setPreferredWidth(windowSlider, 100);
              winLevelPanel.add(windowSlider);
              winLevelPanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    EventManager.getInstance()
        .getAction(ActionW.LEVEL)
        .ifPresent(
            sliderItem -> {
              JSliderW levelSlider = sliderItem.createSlider(2, true);
              GuiUtils.setPreferredWidth(levelSlider, 100);
              winLevelPanel.add(levelSlider);
              winLevelPanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    EventManager.getInstance()
        .getAction(ActionW.LUT)
        .ifPresent(
            comboItem -> {
              JLabel lutLabel = new JLabel(Messages.getString("ImageTool.lut") + StringUtil.COLON);
              JComboBox<?> lutComboBox = comboItem.createCombo(140);
              JPanel lutPanel = GuiUtils.getHorizontalBoxLayoutPanel(5, lutLabel, lutComboBox);
              EventManager.getInstance()
                  .getAction(ActionW.INVERT_LUT)
                  .ifPresent(
                      toggleButton -> {
                        FlatSVGIcon icon = ResourceUtil.getIcon(ActionIcon.INVERSE_LUT);
                        JToggleButton checkBox = toggleButton.createJToggleButton(icon);
                        checkBox.setPreferredSize(GuiUtils.getBigIconButtonSize(checkBox));
                        checkBox.setToolTipText(Messages.getString("ImageTool.inverse"));
                        lutPanel.add(checkBox);
                      });
              winLevelPanel.add(lutPanel);
              winLevelPanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    EventManager.getInstance()
        .getAction(ActionW.FILTER)
        .ifPresent(
            comboItem -> {
              JLabel label = new JLabel(Messages.getString("ImageTool.filter") + StringUtil.COLON);
              JComboBox<?> combo = comboItem.createCombo(160);
              winLevelPanel.add(GuiUtils.getHorizontalBoxLayoutPanel(5, label, combo));
              winLevelPanel.add(GuiUtils.boxVerticalStrut(5));
            });
    return winLevelPanel;
  }

  public JPanel getTransformPanel() {
    JPanel transform = GuiUtils.getVerticalBoxLayoutPanel();
    transform.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder(Messages.getString("ImageTool.transform"))));

    EventManager.getInstance()
        .getAction(ActionW.ZOOM)
        .ifPresent(
            sliderItem -> {
              JSliderW zoomSlider = sliderItem.createSlider(0, true);
              GuiUtils.setPreferredWidth(zoomSlider, 100);
              transform.add(zoomSlider);
            });

    EventManager.getInstance()
        .getAction(ActionW.ROTATION)
        .ifPresent(
            sliderItem -> {
              JSliderW rotationSlider = sliderItem.createSlider(5, true);
              GuiUtils.setPreferredWidth(rotationSlider, 100);
              transform.add(rotationSlider);
            });

    EventManager.getInstance()
        .getAction(ActionW.FLIP)
        .ifPresent(
            toggleButton -> {
              JPanel pane = GuiUtils.getFlowLayoutPanel();
              pane.add(toggleButton.createCheckBox(Messages.getString("ImageTool.flip")));
              transform.add(pane);
            });
    return transform;
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // Do nothing
  }
}
