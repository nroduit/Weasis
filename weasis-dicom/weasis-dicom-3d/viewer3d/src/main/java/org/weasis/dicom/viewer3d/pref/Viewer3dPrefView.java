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
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerListModel;
import javax.swing.SwingUtilities;
import org.osgi.framework.Version;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.viewer3d.Messages;
import org.weasis.dicom.viewer3d.OpenGLInfo;
import org.weasis.dicom.viewer3d.View3DContainer;
import org.weasis.dicom.viewer3d.View3DFactory;
import org.weasis.dicom.viewer3d.geometry.Camera;
import org.weasis.dicom.viewer3d.geometry.CameraView;
import org.weasis.dicom.viewer3d.vr.RenderingLayer;

public class Viewer3dPrefView extends AbstractItemDialogPage {
  private final JCheckBox enableOpenGL = new JCheckBox(Messages.getString("enable"));
  private final JButton bckColor = new JButton(ResourceUtil.getIcon(ActionIcon.PIPETTE));
  private final JButton lightColor = new JButton(ResourceUtil.getIcon(ActionIcon.PIPETTE));
  private final JSlider sliderDynamic =
      new JSlider(0, 100, RenderingLayer.DEFAULT_DYNAMIC_QUALITY_RATE);
  private final JComboBox<GridBagLayoutModel> comboBoxLayouts =
      new JComboBox<>(View3DContainer.LAYOUT_LIST.toArray(new GridBagLayoutModel[0]));

  private final JComboBox<CameraView> comboBoxOrientations = new JComboBox<>(CameraView.values());
  private final JSpinner spinnerMaxXY;
  private final JSpinner spinnerMaxZ;

  public Viewer3dPrefView() {
    super(View3DFactory.NAME, 503);
    List<Integer> list = new ArrayList<>();
    int maxSize = View3DFactory.getMax3dTextureSize();
    list.add(64);
    int val = 128;
    while (val <= maxSize) {
      list.add(val);
      val *= 2;
    }
    if (list.getLast() != maxSize) {
      list.add(maxSize);
    }
    this.spinnerMaxXY = new JSpinner(new SpinnerListModel(list));
    this.spinnerMaxZ = new JSpinner(new SpinnerListModel(list));
    GuiUtils.setSpinnerWidth(spinnerMaxXY, 6);
    GuiUtils.setSpinnerWidth(spinnerMaxZ, 6);
    WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
    spinnerMaxXY.setValue(
        Math.min(maxSize, localPersistence.getIntProperty(RenderingLayer.P_MAX_TEX_XY, maxSize)));
    spinnerMaxZ.setValue(
        Math.min(maxSize, localPersistence.getIntProperty(RenderingLayer.P_MAX_TEX_Z, maxSize)));
    initGUI();
  }

  private void initGUI() {
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
    int quality =
        localPersistence.getIntProperty(
            RenderingLayer.P_DYNAMIC_QUALITY, RenderingLayer.DEFAULT_DYNAMIC_QUALITY_RATE);
    sliderDynamic.setValue(quality);
    sliderDynamic.addChangeListener(
        e ->
            localPersistence.putIntProperty(
                RenderingLayer.P_DYNAMIC_QUALITY, sliderDynamic.getValue()));

    String pickColor = org.weasis.core.Messages.getString("MeasureTool.pick_color");
    bckColor.setToolTipText(pickColor);
    bckColor.addActionListener(
        e -> {
          Color newColor =
              JColorChooser.showDialog(
                  SwingUtilities.getWindowAncestor(this), pickColor, getBackgroundColor());
          if (newColor != null) {
            preferences.putColorProperty(RenderingLayer.P_BCK_COLOR, newColor);
          }
        });

    lightColor.setToolTipText(pickColor);
    lightColor.addActionListener(
        e -> {
          Color newColor =
              JColorChooser.showDialog(
                  SwingUtilities.getWindowAncestor(this), pickColor, getLightColor());
          if (newColor != null) {
            preferences.putColorProperty(RenderingLayer.P_LIGHT_COLOR, newColor);
          }
        });

    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    int shiftX = ITEM_SEPARATOR - ITEM_SEPARATOR_SMALL;

    JPanel openglPanel = GuiUtils.getVerticalBoxLayoutPanel();
    openglPanel.setBorder(GuiUtils.getTitledBorder(Messages.getString("opengl.support")));
    enableOpenGL.setSelected(View3DFactory.isOpenglEnable());
    openglPanel.add(
        GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, enableOpenGL));

    OpenGLInfo info = View3DFactory.getOpenGLInfo();
    if (info == null) {
      String alert =
          GuiUtils.HTML_COLOR_PATTERN.formatted(
              IconColor.ACTIONS_RED.getHtmlCode(), Messages.getString("no.graphic.card"));
      JLabel labelCard = new JLabel(alert);
      openglPanel.add(GuiUtils.getFlowLayoutPanel(labelCard));
    } else {
      JLabel labelCard =
          new JLabel(
              Messages.getString("graphic.card")
                  + StringUtil.COLON_AND_SPACE
                  + info.vendor()
                  + ", "
                  + info.renderer());
      JLabel version =
          new JLabel(
              Messages.getString("driver.version")
                  + StringUtil.COLON_AND_SPACE
                  + info.shortVersion());
      JLabel texture =
          new JLabel(
              Messages.getString("max.3d.texture.dimension.length")
                  + StringUtil.COLON_AND_SPACE
                  + info.max3dTextureSize());
      openglPanel.add(GuiUtils.getFlowLayoutPanel(labelCard));
      openglPanel.add(GuiUtils.getFlowLayoutPanel(version));
      openglPanel.add(GuiUtils.getFlowLayoutPanel(texture));

      // This minimal version must match with the shader version
      Version minimalVersion = new Version(4, 3, 0);
      boolean versionIssue = !info.isVersionCompliant();
      boolean software = info.looksSoftware();
      if (versionIssue || software || info.max3dTextureSize() < RenderingLayer.MAX_QUALITY) {
        String alert =
            GuiUtils.HTML_COLOR_PATTERN.formatted(
                IconColor.ACTIONS_RED.getHtmlCode(),
                Messages.getString("capabilities.not.sufficient"));
        openglPanel.add(GuiUtils.getFlowLayoutPanel(new JLabel(alert)));
        if (versionIssue) {
          alert =
              GuiUtils.HTML_COLOR_PATTERN
                  .formatted(
                      IconColor.ACTIONS_RED.getHtmlCode(), Messages.getString("version.should.be"))
                  .formatted(minimalVersion);
          openglPanel.add(GuiUtils.getFlowLayoutPanel(new JLabel(alert)));
        }

        if (software) {
          alert =
              GuiUtils.HTML_COLOR_PATTERN
                  .formatted(
                      IconColor.ACTIONS_RED.getHtmlCode(),
                      Messages.getString("hardware.acceleration"))
                  .formatted(info.renderer());
          openglPanel.add(GuiUtils.getFlowLayoutPanel(new JLabel(alert)));
        }

        if (info.max3dTextureSize() < RenderingLayer.MAX_QUALITY) {
          alert =
              GuiUtils.HTML_COLOR_PATTERN.formatted(
                  IconColor.ACTIONS_RED.getHtmlCode(),
                  Messages.getString("texture.maximum.size.of")
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

    JLabel labelLayout = new JLabel(Messages.getString("default.layout") + StringUtil.COLON);
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

    JLabel labelTexLimit =
        new JLabel(Messages.getString("max.texture.size") + " X/Y" + StringUtil.COLON); // NON-NLS
    view3d.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING,
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            GuiUtils.boxHorizontalStrut(shiftX),
            labelTexLimit,
            spinnerMaxXY,
            GuiUtils.boxHorizontalStrut(ITEM_SEPARATOR),
            new JLabel("Z" + StringUtil.COLON), // NON-NLS
            spinnerMaxZ));
    add(view3d);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    final JPanel otherPanel = GuiUtils.getVerticalBoxLayoutPanel();
    otherPanel.setBorder(GuiUtils.getTitledBorder(Messages.getString("volume.rendering")));
    otherPanel.add(GuiUtils.boxVerticalStrut(5));
    otherPanel.add(
        GuiUtils.getHorizontalBoxLayoutPanel(
            ITEM_SEPARATOR, new JLabel(Messages.getString("dynamic.quality")), sliderDynamic));

    labelLayout = new JLabel(Messages.getString("default.orientation") + StringUtil.COLON);
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
            new JLabel(Messages.getString("bck.color")),
            bckColor,
            GuiUtils.boxHorizontalStrut(10),
            new JLabel(Messages.getString("light.color")),
            lightColor));
    add(otherPanel);

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "dicom-3d-viewer"); // NON-NLS
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
    return GuiUtils.getUICore()
        .getSystemPreferences()
        .getColorProperty(RenderingLayer.P_BCK_COLOR, Color.GRAY);
  }

  private Color getLightColor() {
    return GuiUtils.getUICore()
        .getSystemPreferences()
        .getColorProperty(RenderingLayer.P_LIGHT_COLOR, Color.GRAY);
  }

  @Override
  public void closeAdditionalWindow() {
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
    preferences.put(
        View3DFactory.P_DEFAULT_LAYOUT,
        ((GridBagLayoutModel) comboBoxLayouts.getSelectedItem()).getId());
    preferences.put(
        Camera.P_DEFAULT_ORIENTATION, ((CameraView) comboBoxOrientations.getSelectedItem()).name());
    localPersistence.putIntProperty(
        RenderingLayer.P_MAX_TEX_XY,
        spinnerMaxXY.getValue() instanceof Integer val ? val : View3DFactory.getMax3dTextureSize());
    localPersistence.putIntProperty(
        RenderingLayer.P_MAX_TEX_Z,
        spinnerMaxZ.getValue() instanceof Integer val ? val : View3DFactory.getMax3dTextureSize());
    boolean openglEnabled = enableOpenGL.isSelected();
    if (openglEnabled && !View3DFactory.isOpenglEnable()) {
      localPersistence.putBooleanProperty(View3DFactory.P_OPENGL_PREV_INIT, true);
      localPersistence.putBooleanProperty(View3DFactory.P_OPENGL_ENABLE, true);
    } else if (!openglEnabled) {
      localPersistence.putBooleanProperty(View3DFactory.P_OPENGL_PREV_INIT, false);
      localPersistence.putBooleanProperty(View3DFactory.P_OPENGL_ENABLE, false);
    }
    GuiUtils.getUICore().saveSystemPreferences();
  }

  @Override
  public void resetToDefaultValues() {
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
    enableOpenGL.setSelected(true);
    sliderDynamic.setValue(RenderingLayer.DEFAULT_DYNAMIC_QUALITY_RATE);
    int maxSize = View3DFactory.getMax3dTextureSize();
    localPersistence.putIntProperty(RenderingLayer.P_MAX_TEX_XY, maxSize);
    localPersistence.putIntProperty(RenderingLayer.P_MAX_TEX_Z, maxSize);
    spinnerMaxXY.setValue(maxSize);
    spinnerMaxZ.setValue(maxSize);
    preferences.putColorProperty(RenderingLayer.P_BCK_COLOR, Color.GRAY);
    preferences.putColorProperty(RenderingLayer.P_LIGHT_COLOR, Color.WHITE);
    preferences.put(View3DFactory.P_DEFAULT_LAYOUT, View3DContainer.VIEWS_vr.getId());
    preferences.put(Camera.P_DEFAULT_ORIENTATION, CameraView.INITIAL.name());
    setDefaultLayout();
  }
}
