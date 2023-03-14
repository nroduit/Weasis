/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.pref;

import java.awt.Color;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import org.osgi.framework.Version;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.viewer3d.OpenGLInfo;
import org.weasis.dicom.viewer3d.View3DContainer;
import org.weasis.dicom.viewer3d.View3DFactory;
import org.weasis.dicom.viewer3d.geometry.Camera;
import org.weasis.dicom.viewer3d.geometry.CameraView;
import org.weasis.dicom.viewer3d.vr.RenderingLayer;

public class Viewer3dPrefView extends AbstractItemDialogPage {

  private final JButton bckColor = new JButton(ResourceUtil.getIcon(ActionIcon.PIPETTE));
  private final JButton lightColor = new JButton(ResourceUtil.getIcon(ActionIcon.PIPETTE));
  private final JSlider sliderDynamic =
      new JSlider(0, 100, RenderingLayer.DEFAULT_DYNAMIC_QUALITY_RATE);
  private final JComboBox<GridBagLayoutModel> comboBoxLayouts =
      new JComboBox<>(View3DContainer.LAYOUT_LIST.toArray(new GridBagLayoutModel[0]));

  private final JComboBox<CameraView> comboBoxOrientations = new JComboBox<>(CameraView.values());

  public Viewer3dPrefView() {
    super(View3DFactory.NAME, 503);
    initGUI();
  }

  private void initGUI() {
    int quality =
        BundleTools.LOCAL_UI_PERSISTENCE.getIntProperty(
            RenderingLayer.DYNAMIC_QUALITY, RenderingLayer.DEFAULT_DYNAMIC_QUALITY_RATE);
    sliderDynamic.setValue(quality);
    sliderDynamic.addChangeListener(
        e ->
            BundleTools.LOCAL_UI_PERSISTENCE.putIntProperty(
                RenderingLayer.DYNAMIC_QUALITY, sliderDynamic.getValue()));

    String pickColor = org.weasis.core.ui.Messages.getString("MeasureTool.pick_color");
    bckColor.setToolTipText(pickColor);
    bckColor.addActionListener(
        e -> {
          Color newColor =
              JColorChooser.showDialog(
                  SwingUtilities.getWindowAncestor(this), pickColor, getBackgroundColor());
          if (newColor != null) {
            BundleTools.SYSTEM_PREFERENCES.putColorProperty(RenderingLayer.BCK_COLOR, newColor);
          }
        });

    lightColor.setToolTipText(pickColor);
    lightColor.addActionListener(
        e -> {
          Color newColor =
              JColorChooser.showDialog(
                  SwingUtilities.getWindowAncestor(this), pickColor, getLightColor());
          if (newColor != null) {
            BundleTools.SYSTEM_PREFERENCES.putColorProperty(RenderingLayer.LIGHT_COLOR, newColor);
          }
        });

    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    int shiftX = ITEM_SEPARATOR - ITEM_SEPARATOR_SMALL;

    JPanel openglPanel = GuiUtils.getVerticalBoxLayoutPanel();
    openglPanel.setBorder(GuiUtils.getTitledBorder("OpenGL Support"));
    JCheckBox enableHA = new JCheckBox("Enable");
    enableHA.setSelected(View3DFactory.isOpenglEnable());
    openglPanel.add(GuiUtils.getFlowLayoutPanel(enableHA));

    OpenGLInfo info = View3DFactory.getOpenGLInfo();
    if (info == null) {
      String alert =
          GuiUtils.HTML_COLOR_PATTERN.formatted(
              IconColor.ACTIONS_RED.getHtmlCode(), "No graphic card found for OpenGL");
      JLabel labelCard = new JLabel(alert);
      openglPanel.add(GuiUtils.getFlowLayoutPanel(labelCard));
    } else {
      JLabel labelCard =
          new JLabel(
              "Graphic card" + StringUtil.COLON_AND_SPACE + info.vendor() + ", " + info.renderer());
      JLabel version =
          new JLabel("Driver version" + StringUtil.COLON_AND_SPACE + info.shortVersion());
      JLabel texture =
          new JLabel(
              "Max 3D texture dimension length"
                  + StringUtil.COLON_AND_SPACE
                  + info.max3dTextureSize());
      openglPanel.add(GuiUtils.getFlowLayoutPanel(labelCard));
      openglPanel.add(GuiUtils.getFlowLayoutPanel(version));
      openglPanel.add(GuiUtils.getFlowLayoutPanel(texture));

      // This minimal version must match with the shader version
      Version minimalVersion = new Version(4, 3, 0);
      boolean versionIssue = info.getVersion().compareTo(minimalVersion) < 0;
      if (versionIssue || info.max3dTextureSize() < RenderingLayer.MAX_QUALITY) {
        String alert =
            GuiUtils.HTML_COLOR_PATTERN.formatted(
                IconColor.ACTIONS_RED.getHtmlCode(), "These capabilities are not sufficient");
        openglPanel.add(GuiUtils.getFlowLayoutPanel(new JLabel(alert)));
        if (versionIssue) {
          alert =
              GuiUtils.HTML_COLOR_PATTERN
                  .formatted(
                      IconColor.ACTIONS_RED.getHtmlCode(), "The version should be at least %s")
                  .formatted(minimalVersion);
          openglPanel.add(GuiUtils.getFlowLayoutPanel(new JLabel(alert)));
        }

        if (info.max3dTextureSize() < RenderingLayer.MAX_QUALITY) {
          alert =
              GuiUtils.HTML_COLOR_PATTERN.formatted(
                  IconColor.ACTIONS_RED.getHtmlCode(),
                  "Texture 3D has a maximum size of %sx%sx%s"
                      .formatted(
                          info.max3dTextureSize(),
                          info.max3dTextureSize(),
                          info.max3dTextureSize()));
          openglPanel.add(GuiUtils.getFlowLayoutPanel(new JLabel(alert)));
        }
      }
    }

    openglPanel.add(GuiUtils.boxVerticalStrut(ITEM_SEPARATOR));
    add(openglPanel);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    JPanel view3d = GuiUtils.getVerticalBoxLayoutPanel();
    view3d.setBorder(GuiUtils.getTitledBorder(View3DFactory.NAME));

    JLabel labelLayout = new JLabel("Default layout" + StringUtil.COLON);
    setDefaultLayout();

    view3d.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING,
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            GuiUtils.boxHorizontalStrut(shiftX),
            labelLayout,
            comboBoxLayouts));
    view3d.add(GuiUtils.boxVerticalStrut(ITEM_SEPARATOR));
    add(view3d);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    final JPanel otherPanel = GuiUtils.getVerticalBoxLayoutPanel();
    otherPanel.setBorder(GuiUtils.getTitledBorder("Volume rendering"));
    otherPanel.add(GuiUtils.boxVerticalStrut(5));
    otherPanel.add(
        GuiUtils.getHorizontalBoxLayoutPanel(
            ITEM_SEPARATOR, new JLabel("Dynamic quality"), sliderDynamic));

    labelLayout = new JLabel("Default orientation" + StringUtil.COLON);
    otherPanel.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING,
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            GuiUtils.boxHorizontalStrut(shiftX),
            labelLayout,
            comboBoxOrientations));

    otherPanel.add(
        GuiUtils.getFlowLayoutPanel(
            new JLabel("Background color"),
            bckColor,
            GuiUtils.boxHorizontalStrut(10),
            new JLabel("Light color"),
            lightColor));
    add(otherPanel);

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
  }

  private void setDefaultLayout() {
    comboBoxLayouts.setSelectedItem(View3DFactory.getDefaultGridBagLayoutModel());
    if (comboBoxLayouts.getSelectedIndex() < 0) {
      comboBoxLayouts.setSelectedItem(0);
    }

    comboBoxOrientations.setSelectedItem(Camera.getDefaultOrientation());
    if (comboBoxOrientations.getSelectedIndex() < 0) {
      comboBoxOrientations.setSelectedItem(0);
    }
  }

  private Color getBackgroundColor() {
    return BundleTools.SYSTEM_PREFERENCES.getColorProperty(RenderingLayer.BCK_COLOR, Color.GRAY);
  }

  private Color getLightColor() {
    return BundleTools.SYSTEM_PREFERENCES.getColorProperty(RenderingLayer.LIGHT_COLOR, Color.GRAY);
  }

  @Override
  public void closeAdditionalWindow() {
    BundleTools.SYSTEM_PREFERENCES.put(
        View3DFactory.P_DEFAULT_LAYOUT,
        ((GridBagLayoutModel) comboBoxLayouts.getSelectedItem()).getId());
    BundleTools.SYSTEM_PREFERENCES.put(
        Camera.P_DEFAULT_ORIENTATION, ((CameraView) comboBoxOrientations.getSelectedItem()).name());
    BundleTools.saveSystemPreferences();
  }

  @Override
  public void resetToDefaultValues() {
    sliderDynamic.setValue(RenderingLayer.DEFAULT_DYNAMIC_QUALITY_RATE);
    BundleTools.SYSTEM_PREFERENCES.putColorProperty(RenderingLayer.BCK_COLOR, Color.GRAY);
    BundleTools.SYSTEM_PREFERENCES.putColorProperty(RenderingLayer.LIGHT_COLOR, Color.WHITE);
    BundleTools.SYSTEM_PREFERENCES.put(
        View3DFactory.P_DEFAULT_LAYOUT, View3DContainer.VIEWS_vr.getId());
    BundleTools.SYSTEM_PREFERENCES.put(Camera.P_DEFAULT_ORIENTATION, CameraView.INITIAL.name());
    setDefaultLayout();
  }
}
