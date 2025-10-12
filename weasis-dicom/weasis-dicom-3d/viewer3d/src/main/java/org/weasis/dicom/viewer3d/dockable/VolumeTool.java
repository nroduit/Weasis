/*
 * Copyright (c) 2012 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.dockable;

import bibliothek.gui.dock.common.CLocation;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.border.Border;

import org.weasis.core.api.gui.util.*;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.viewer2d.dockable.ImageTool;
import org.weasis.dicom.viewer3d.ActionVol;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.Messages;
import org.weasis.dicom.viewer3d.vr.ShadingPrefDialog;
import org.weasis.dicom.viewer3d.vr.View3d;

public class VolumeTool extends PluginTool {

    private final Feature.SliderChangeListenerValue slider = new Feature.SliderChangeListenerValue("Slicing", "zoom", 90, 0, Feature.getSvgCursor("zoom.svg", "Slicing", 0.5F, 0.5F));

    public static final String BUTTON_NAME = Messages.getString("3d.tool");

  private final JScrollPane rootPane = new JScrollPane();
  private final Border spaceY = GuiUtils.getEmptyBorder(15, 3, 0, 3);

  public VolumeTool(String pluginName) {
    super(pluginName, Type.TOOL, 20);
    dockable.setTitleIcon(ResourceUtil.getIcon(OtherIcon.IMAGE_EDIT));
    setDockableWidth(290);
    init();
  }

  private void init() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    add(getWindowLevelPanel());
    add(getSlicePanel());
    add(getSliceNormPanel());
    add(getVolumetricPanel());
    add(getTransformPanel());
    add(GuiUtils.boxYLastElement(3));
    rootPane.setBorder(BorderFactory.createEmptyBorder()); // remove default line
  }

  @Override
  public Component getToolComponent() {
    return getToolComponentFromJScrollPane(rootPane);
  }

  private JPanel getWindowLevelPanel() {
    int gabY = 7;
    JPanel winLevelPanel = ImageTool.getWindowLevelPanel(EventManager.getInstance(), spaceY, false);

    EventManager.getInstance()
        .getAction(ActionVol.VOL_PRESET)
        .ifPresent(
            comboItem -> {
              JLabel label = new JLabel(ActionVol.VOL_PRESET.getTitle() + StringUtil.COLON);
              JComboBox<?> combo = comboItem.createCombo(140);
              combo.setMaximumRowCount(15);
              JPanel lutPanel = GuiUtils.getHorizontalBoxLayoutPanel(5, label, combo);
              EventManager.getInstance()
                  .getAction(ActionW.INVERT_LUT)
                  .ifPresent(
                      toggleButton -> {
                        FlatSVGIcon icon = ResourceUtil.getIcon(ActionIcon.INVERSE_LUT);
                        JToggleButton checkBox = toggleButton.createJToggleButton(icon);
                        checkBox.setPreferredSize(GuiUtils.getBigIconButtonSize(checkBox));
                        checkBox.setToolTipText(toggleButton.getActionW().getTitle());
                        lutPanel.add(checkBox);
                      });
              winLevelPanel.add(lutPanel);
              winLevelPanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    return winLevelPanel;
  }

  private JPanel getVolumetricPanel() {
    int gabY = 7;
    final JPanel volumePanel = GuiUtils.getVerticalBoxLayoutPanel();
    volumePanel.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder(Messages.getString("volume.rendering"))));

    EventManager.getInstance()
        .getAction(ActionVol.RENDERING_TYPE)
        .ifPresent(
            c -> {
              JLabel label = new JLabel(ActionVol.RENDERING_TYPE.getTitle() + StringUtil.COLON);
              JComboBox<?> combo = c.createCombo(140);
              combo.setMaximumRowCount(10);
              volumePanel.add(GuiUtils.getHorizontalBoxLayoutPanel(5, label, combo));
              volumePanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    EventManager.getInstance()
        .getAction(ActionVol.VOL_QUALITY)
        .ifPresent(
            s -> {
              JSliderW slider = s.createSlider(0, true);
              GuiUtils.setPreferredWidth(slider, 100);
              volumePanel.add(slider);
            });

    EventManager.getInstance()
        .getAction(ActionVol.VOL_OPACITY)
        .ifPresent(
            s -> {
              JSliderW slider = s.createSlider(0, true);
              GuiUtils.setPreferredWidth(slider, 100);
              volumePanel.add(slider);
            });

    EventManager.getInstance()
        .getAction(ActionVol.VOL_SHADING)
        .ifPresent(
            b -> {
              JPanel pane = GuiUtils.getFlowLayoutPanel();
              pane.add(b.createCheckBox(ActionVol.VOL_SHADING.getTitle()));
              JButton btnOptions = new JButton(Messages.getString("more.options"));
              btnOptions.addActionListener(
                  e -> {
                    if (EventManager.getInstance().getSelectedViewPane() instanceof View3d view3d) {
                      ShadingPrefDialog dialog = new ShadingPrefDialog(view3d);
                      GuiUtils.showCenterScreen(dialog);
                    }
                  });
              JCheckBox box = b.createCheckBox(ActionVol.VOL_SHADING.getTitle());
              volumePanel.add(
                  GuiUtils.getFlowLayoutPanel(box, GuiUtils.boxHorizontalStrut(10), btnOptions));
              volumePanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    EventManager.getInstance()
        .getAction(ActionVol.MIP_TYPE)
        .ifPresent(
            c -> {
              JPanel panel = GuiUtils.getHorizontalBoxLayoutPanel(5);
              EventManager.getInstance()
                  .getAction(ActionVol.MIP_DEPTH)
                  .ifPresent(
                      s -> {
                        JSliderW slider = s.createSlider(0, true);
                        GuiUtils.setPreferredWidth(slider, 140);
                        panel.add(slider);
                      });
              JComboBox<?> combo = c.createCombo();
              GuiUtils.setWidth(combo, 80);
              panel.add(combo);
              volumePanel.add(panel);
              volumePanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    return volumePanel;
  }

  private JPanel getTransformPanel() {
    JPanel transform = GuiUtils.getVerticalBoxLayoutPanel();
    transform.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY,
            GuiUtils.getTitledBorder(
                org.weasis.dicom.viewer2d.Messages.getString("ImageTool.transform"))));

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
              JSliderW slider = sliderItem.createSlider(5, true);
              GuiUtils.setPreferredWidth(slider, 140);
              JPanel panel = GuiUtils.getHorizontalBoxLayoutPanel(5, slider);
              EventManager.getInstance()
                  .getAction(ActionVol.VOL_AXIS)
                  .ifPresent(
                      c -> {
                        JComboBox<?> combo = c.createCombo();
                        GuiUtils.setWidth(combo, 55);
                        panel.add(combo);
                      });

              transform.add(panel);
            });

    EventManager.getInstance()
        .getAction(ActionW.FLIP)
        .ifPresent(
            toggleButton -> {
              JPanel pane = GuiUtils.getFlowLayoutPanel();
              pane.add(
                  toggleButton.createCheckBox(
                      org.weasis.dicom.viewer2d.Messages.getString("View2dContainer.flip_h")));
              transform.add(pane);
            });
    return transform;
  }

  private JPanel getSlicePanel() {
    JPanel slice = GuiUtils.getVerticalBoxLayoutPanel();
    slice.setBorder(
            BorderFactory.createCompoundBorder(
                    spaceY,
                    GuiUtils.getTitledBorder("Slicing")));



    EventManager.getInstance()
            .getAction(ActionW.SLICE_X)
            .ifPresent(
                    sliderItem -> {
                      JSliderW xSliceSlider = sliderItem.createSlider(0, true);
                      GuiUtils.setPreferredWidth(xSliceSlider, 100);
                      slice.add(xSliceSlider);

                    });
    EventManager.getInstance()
            .getAction(ActionW.SLICE_Y)
            .ifPresent(
                    sliderItem -> {
                      JSliderW sliceSlider = sliderItem.createSlider(0, true);
                      GuiUtils.setPreferredWidth(sliceSlider, 100);
                      slice.add(sliceSlider);

                    });
    EventManager.getInstance()
            .getAction(ActionW.SLICE_Z)
            .ifPresent(
                    sliderItem -> {
                      JSliderW sliceSlider = sliderItem.createSlider(0, true);
                      GuiUtils.setPreferredWidth(sliceSlider, 100);
                      slice.add(sliceSlider);

                    });
    EventManager.getInstance()
            .getAction(ActionW.SLICE_ENABLE)
            .ifPresent(
                    toggleButton -> {
                      JPanel pane = GuiUtils.getFlowLayoutPanel();
                      pane.add(
                              toggleButton.createCheckBox(
                                      "Slicing Enabled"));
                      slice.add(pane);
                    });
    return slice;
  }
    private JPanel getSliceNormPanel() {
        JPanel slice = GuiUtils.getVerticalBoxLayoutPanel();
        slice.setBorder(
                BorderFactory.createCompoundBorder(
                        spaceY,
                        GuiUtils.getTitledBorder("Slicing Orientation")));



        EventManager.getInstance()
                .getAction(ActionW.SLICE_X_NORM)
                .ifPresent(
                        sliderItem -> {
                            JSliderW sliceSlider = sliderItem.createSlider(0, true);
                            GuiUtils.setPreferredWidth(sliceSlider, 100);
                            slice.add(sliceSlider);

                        });
        EventManager.getInstance()
                .getAction(ActionW.SLICE_Y_NORM)
                .ifPresent(
                        sliderItem -> {
                            JSliderW sliceSlider = sliderItem.createSlider(0, true);
                            GuiUtils.setPreferredWidth(sliceSlider, 100);
                            slice.add(sliceSlider);

                        });
        return slice;
    }


    @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // Do nothing
  }
}
