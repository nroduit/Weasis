/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.viewer3d.Messages;

/** Edits the lighting of one view: the shininess and the cinematic lighting parameters. */
public class ShadingPrefDialog extends JDialog {

  private static final int SLIDER_RANGE = 512;

  /** One editable value: its range, its default and how it maps to the shading options. */
  private record Param(
      String title,
      float min,
      float max,
      float defaultValue,
      Function<ShadingOptions, Float> getter,
      BiConsumer<ShadingOptions, Float> setter,
      Function<Float, String> formatter) {

    int toSlider(float value) {
      return Math.round((Math.clamp(value, min, max) - min) * SLIDER_RANGE / (max - min));
    }

    float toValue(int slider) {
      return min + slider * (max - min) / SLIDER_RANGE;
    }
  }

  private record Row(Param param, JSliderW slider, float initialValue) {}

  private final View3d view3d;
  private final List<Row> rows = new ArrayList<>();
  private final JComboBox<EnvironmentMap> environmentCombo =
      new JComboBox<>(EnvironmentMap.values());
  private final EnvironmentMap initialEnvironment;
  private boolean render = true;

  public ShadingPrefDialog(View3d view3d) {
    super(
        SwingUtilities.getWindowAncestor(view3d),
        Messages.getString("shading.options"),
        ModalityType.APPLICATION_MODAL);
    this.view3d = view3d;
    this.initialEnvironment = options().getEnvironment();
    this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    this.setIconImage(ResourceUtil.getIcon(ActionIcon.VOLUME).getImage());
    init();
    GuiUtils.setPreferredWidth(this, 550);
    pack();
  }

  private ShadingOptions options() {
    return view3d.getRenderingLayer().getShadingOptions();
  }

  private void init() {
    JPanel contentPane = GuiUtils.getVerticalBoxLayoutPanel();
    contentPane.setBorder(GuiUtils.getEmptyBorder(10, 15, 10, 15));

    Param shininess =
        new Param(
            Messages.getString("shininess"),
            1f,
            100f,
            view3d.getVolumePreset().getSpecularPower(),
            ShadingOptions::getSpecularPower,
            ShadingOptions::setSpecularPower,
            DecFormatter::oneDecimal);
    contentPane.add(GuiUtils.boxVerticalStrut(Insertable.BLOCK_SEPARATOR));
    contentPane.add(createSlider(shininess));

    JPanel cinematicPanel = GuiUtils.getVerticalBoxLayoutPanel();
    cinematicPanel.setBorder(GuiUtils.getTitledBorder(Messages.getString("cinematic.lighting")));
    for (Param p : cinematicParams()) {
      cinematicPanel.add(GuiUtils.boxVerticalStrut(Insertable.BLOCK_SEPARATOR));
      cinematicPanel.add(createSlider(p));
    }
    environmentCombo.setSelectedItem(initialEnvironment);
    environmentCombo.addActionListener(
        e -> {
          if (render && environmentCombo.getSelectedItem() instanceof EnvironmentMap map) {
            options().setEnvironment(map);
          }
        });
    cinematicPanel.add(GuiUtils.boxVerticalStrut(Insertable.BLOCK_SEPARATOR));
    cinematicPanel.add(
        GuiUtils.getFlowLayoutPanel(
            new JLabel(Messages.getString("environment") + StringUtil.COLON), environmentCombo));
    cinematicPanel.add(createSlider(environmentStrengthParam()));
    contentPane.add(GuiUtils.boxVerticalStrut(Insertable.BLOCK_SEPARATOR));
    contentPane.add(cinematicPanel);

    JButton restoreButton = new JButton(Messages.getString("restore.default.values"));
    restoreButton.addActionListener(
        e -> apply(row -> row.param().defaultValue(), ShadingOptions.DEFAULT_ENVIRONMENT));
    contentPane.add(GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 5, 5, restoreButton));

    JButton okButton = new JButton(Messages.getString("ok"));
    okButton.addActionListener(
        e -> {
          options().persistCinematic(GuiUtils.getUICore().getLocalPersistence());
          dispose();
        });
    JButton cancelButton = new JButton(Messages.getString("cancel"));
    cancelButton.addActionListener(
        e -> {
          apply(Row::initialValue, initialEnvironment);
          dispose();
        });
    JButton helpButton = GuiUtils.createHelpButton("dicom-3d-viewer/#volume-rendering"); // NON-NLS

    JPanel panel =
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.TRAILING,
            5,
            5,
            helpButton,
            GuiUtils.boxHorizontalStrut(20),
            okButton,
            GuiUtils.boxHorizontalStrut(20),
            cancelButton);
    panel.setBorder(GuiUtils.getEmptyBorder(20, 15, 10, 15));
    contentPane.add(panel);
    contentPane.add(GuiUtils.boxYLastElement(1));
    setContentPane(contentPane);
  }

  private static List<Param> cinematicParams() {
    return List.of(
        new Param(
            Messages.getString("shadow.strength"),
            0f,
            1f,
            ShadingOptions.DEFAULT_SHADOW_STRENGTH,
            ShadingOptions::getShadowStrength,
            ShadingOptions::setShadowStrength,
            DecFormatter::twoDecimal),
        new Param(
            Messages.getString("ambient.occlusion"),
            0f,
            1f,
            ShadingOptions.DEFAULT_AO_STRENGTH,
            ShadingOptions::getAoStrength,
            ShadingOptions::setAoStrength,
            DecFormatter::twoDecimal),
        new Param(
            Messages.getString("exposure"),
            0.25f,
            3f,
            ShadingOptions.DEFAULT_EXPOSURE,
            ShadingOptions::getExposure,
            ShadingOptions::setExposure,
            DecFormatter::twoDecimal),
        new Param(
            Messages.getString("light.azimuth"),
            -90f,
            90f,
            ShadingOptions.DEFAULT_LIGHT_AZIMUTH,
            ShadingOptions::getLightAzimuth,
            ShadingOptions::setLightAzimuth,
            DecFormatter::oneDecimal),
        new Param(
            Messages.getString("light.elevation"),
            -90f,
            90f,
            ShadingOptions.DEFAULT_LIGHT_ELEVATION,
            ShadingOptions::getLightElevation,
            ShadingOptions::setLightElevation,
            DecFormatter::oneDecimal));
  }

  private static Param environmentStrengthParam() {
    return new Param(
        Messages.getString("environment.strength"),
        0f,
        2f,
        ShadingOptions.DEFAULT_ENVIRONMENT_STRENGTH,
        ShadingOptions::getEnvironmentStrength,
        ShadingOptions::setEnvironmentStrength,
        DecFormatter::twoDecimal);
  }

  private JSliderW createSlider(Param param) {
    float initial = param.getter().apply(options());
    DefaultBoundedRangeModel model =
        new DefaultBoundedRangeModel(param.toSlider(initial), 0, 0, SLIDER_RANGE);
    TitledBorder titledBorder =
        new TitledBorder(
            BorderFactory.createEmptyBorder(),
            title(param, initial),
            TitledBorder.LEADING,
            TitledBorder.DEFAULT_POSITION,
            FontItem.MEDIUM.getFont(),
            null);
    JSliderW s = new JSliderW(model);
    s.setLabelDivision(2);
    s.setDisplayValueInTitle(true);
    s.setPaintTicks(true);
    s.setShowLabels(true);
    s.setPaintLabels(true);
    s.setBorder(titledBorder);
    SliderChangeListener.setSliderLabelValues(
        s, 0, SLIDER_RANGE, (double) param.min(), (double) param.max());
    s.addChangeListener(
        l -> {
          float v = param.toValue(model.getValue());
          if (render) {
            param.setter().accept(options(), v);
          }
          SliderChangeListener.updateSliderProperties(s, title(param, v));
        });
    rows.add(new Row(param, s, initial));
    return s;
  }

  private static String title(Param param, float value) {
    return param.title() + StringUtil.COLON_AND_SPACE + param.formatter().apply(value);
  }

  // Moves every control to the given value, then applies them all in one repaint.
  private void apply(Function<Row, Float> value, EnvironmentMap environment) {
    render = false;
    view3d.getRenderingLayer().setEnableRepaint(false);
    for (Row row : rows) {
      float v = value.apply(row);
      row.slider().setValue(row.param().toSlider(v));
      row.param().setter().accept(options(), v);
    }
    environmentCombo.setSelectedItem(environment);
    options().setEnvironment(environment);
    view3d.getRenderingLayer().setEnableRepaint(true);
    render = true;
    view3d.getRenderingLayer().fireLayerChanged();
  }
}
