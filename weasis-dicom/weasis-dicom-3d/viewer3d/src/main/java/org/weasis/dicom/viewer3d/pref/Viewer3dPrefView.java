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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.viewer3d.OpenGLInfo;
import org.weasis.dicom.viewer3d.View3DContainer;
import org.weasis.dicom.viewer3d.View3DFactory;
import org.weasis.dicom.viewer3d.vr.RenderingLayer;

public class Viewer3dPrefView extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(Viewer3dPrefView.class);

  private final JButton bckColor = new JButton(ResourceUtil.getIcon(ActionIcon.PIPETTE));
  private final JButton lightColor = new JButton(ResourceUtil.getIcon(ActionIcon.PIPETTE));
  private final JSlider sliderStatic = new JSlider(-100, 100, 0);
  private final JSlider sliderDynamic = new JSlider(-100, 100, 0);
  ;

  public Viewer3dPrefView() {
    super(View3DFactory.NAME, 503);
    initGUI();
  }

  private void initGUI() {
    int quality =
        BundleTools.LOCAL_UI_PERSISTENCE.getIntProperty(RenderingLayer.STATIC_QUALITY, 100);
    sliderStatic.setValue(quality);
    sliderStatic.addChangeListener(
        e ->
            BundleTools.LOCAL_UI_PERSISTENCE.putIntProperty(
                RenderingLayer.STATIC_QUALITY, sliderStatic.getValue()));

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
      JLabel labelCard = new JLabel("No graphic card found for OpenGL");
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
    }

    openglPanel.add(GuiUtils.boxVerticalStrut(ITEM_SEPARATOR));
    add(openglPanel);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    JPanel view3d = GuiUtils.getVerticalBoxLayoutPanel();
    view3d.setBorder(GuiUtils.getTitledBorder(View3DFactory.NAME));

    JLabel labelLayout = new JLabel("Default layout" + StringUtil.COLON);
    JComboBox<GridBagLayoutModel> comboBoxLayouts =
        new JComboBox<>(View3DContainer.LAYOUT_LIST.toArray(new GridBagLayoutModel[0]));
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
    otherPanel.add(
        GuiUtils.getHorizontalBoxLayoutPanel(
            ITEM_SEPARATOR, new JLabel("Static quality"), sliderStatic));
    otherPanel.add(GuiUtils.boxVerticalStrut(5));
    otherPanel.add(
        GuiUtils.getHorizontalBoxLayoutPanel(
            ITEM_SEPARATOR, new JLabel("Dynamic quality"), sliderDynamic));

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

  private Color getBackgroundColor() {
    return BundleTools.SYSTEM_PREFERENCES.getColorProperty(RenderingLayer.BCK_COLOR, Color.GRAY);
  }

  private Color getLightColor() {
    return BundleTools.SYSTEM_PREFERENCES.getColorProperty(RenderingLayer.LIGHT_COLOR, Color.GRAY);
  }

  @Override
  public void closeAdditionalWindow() {
    // BundleTools.saveSystemPreferences();
  }

  @Override
  public void resetToDefaultValues() {
    BundleTools.SYSTEM_PREFERENCES.putColorProperty(RenderingLayer.BCK_COLOR, Color.GRAY);
    BundleTools.SYSTEM_PREFERENCES.putColorProperty(RenderingLayer.LIGHT_COLOR, Color.WHITE);
  }
}
