/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.dialog.PropertiesDialog;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.util.StringUtil;

public class GraphicPrefView extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(GraphicPrefView.class);

  public static final String PAGE_NAME = ActionW.DRAW_GRAPHICS.getTitle();

  private final JSpinner spinner = new JSpinner();

  private final JCheckBox checkboxFilled =
      new JCheckBox(Messages.getString("PropertiesDialog.fill_shape"));

  private final JSliderW sliderOpacity;

  private final JCheckBox checkboxOutline =
      new JCheckBox(Messages.getString("GraphicPrefView.outline"));

  private final JCheckBox checkboxUpright =
      new JCheckBox(Messages.getString("GraphicPrefView.upright"));

  private final JSpinner spinnerCircleRadius =
      new JSpinner(new SpinnerNumberModel(ViewSetting.DEFAULT_CIRCLE_RADIUS, 0.5, 100.0, 0.5));

  public GraphicPrefView() {
    super(PAGE_NAME, 702);

    this.sliderOpacity = PropertiesDialog.createOpacitySlider(PropertiesDialog.FILL_OPACITY);
    try {
      jbInit();
      initialize();
    } catch (Exception e) {
      LOGGER.error("Cannot initialize", e);
    }
  }

  private void jbInit() {
    JButton button = MeasureTool.buildLineColorButton(this);
    MeasureTool.viewSetting.initLineWidthSpinner(spinner);
    JPanel linePane = GuiUtils.getFlowLayoutPanel(button, spinner, checkboxOutline);
    linePane.setBorder(GuiUtils.getTitledBorder(Messages.getString("MeasureToolBar.line")));
    add(linePane);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    // Every element starts on the same left edge; the labels are short, the tooltips and the
    // help page give the details
    JPanel shapePane =
        new JPanel(new MigLayout("ins 5lp, fillx, wrap 1", "[left]", "[]5lp[]")) { // NON-NLS
          /** As wide as the page, like the block above it, and no taller than its content. */
          @Override
          public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
          }
        };
    // A MigLayout panel is left-aligned; the page stacks centred blocks and would shift it
    shapePane.setAlignmentX(Component.CENTER_ALIGNMENT);
    shapePane.add(checkboxFilled);
    shapePane.add(sliderOpacity, "growx"); // NON-NLS
    shapePane.add(checkboxUpright);
    JLabel radiusLabel =
        new JLabel(Messages.getString("GraphicPrefView.circle_radius") + StringUtil.COLON);
    JLabel radiusUnit = new JLabel(Unit.MILLIMETER.getAbbreviation());
    shapePane.add(radiusLabel, "split 3"); // NON-NLS
    shapePane.add(spinnerCircleRadius);
    shapePane.add(radiusUnit);
    shapePane.setBorder(GuiUtils.getTitledBorder(Messages.getString("closed.shape")));
    add(shapePane);

    checkboxOutline.setToolTipText(Messages.getString("GraphicPrefView.outline_tip"));
    checkboxUpright.setToolTipText(Messages.getString("GraphicPrefView.upright_tip"));
    String radiusTip = Messages.getString("GraphicPrefView.circle_radius_tip");
    radiusLabel.setToolTipText(radiusTip);
    spinnerCircleRadius.setToolTipText(radiusTip);

    add(GuiUtils.boxYLastElement(5));

    sliderOpacity.setValue((int) (MeasureTool.viewSetting.getFillOpacity() * 100));
    PropertiesDialog.updateSlider(sliderOpacity, PropertiesDialog.FILL_OPACITY);
    sliderOpacity.addChangeListener(
        _ -> PropertiesDialog.updateSlider(sliderOpacity, PropertiesDialog.FILL_OPACITY));

    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "draw-measure/#preferences"); // NON-NLS
  }

  protected void initialize() {
    ViewSetting settings = MeasureTool.viewSetting;

    spinner.setValue(settings.getLineWidth());
    checkboxFilled.setSelected(settings.isFilled());
    checkboxOutline.setSelected(settings.isOutline());
    checkboxUpright.setSelected(settings.isUprightByDrag());
    spinnerCircleRadius.setValue(settings.getCircleRadius());

    int opacity = (int) (settings.getFillOpacity() * 100);
    sliderOpacity.setValue(opacity);
    PropertiesDialog.updateSlider(sliderOpacity, PropertiesDialog.FILL_OPACITY);
  }

  @Override
  public void closeAdditionalWindow() {
    ViewSetting settings = MeasureTool.viewSetting;
    settings.setFilled(checkboxFilled.isSelected());
    settings.setFillOpacity(sliderOpacity.getValue() / 100f);
    settings.setOutline(checkboxOutline.isSelected());
    settings.setUprightByDrag(checkboxUpright.isSelected());
    settings.setCircleRadius(((Number) spinnerCircleRadius.getValue()).doubleValue());
    MeasureTool.updateMeasureProperties();
    MeasureTool.refreshViewLabels();
  }

  @Override
  public void resetToDefaultValues() {
    ViewSetting settings = MeasureTool.viewSetting;
    settings.setLineWidth(Graphic.DEFAULT_LINE_THICKNESS.intValue());
    settings.setLineColor(ViewSetting.DEFAULT_LINE_COLOR);
    settings.setOutline(true);
    settings.setFilled(Graphic.DEFAULT_FILLED);
    settings.setFillOpacity(Graphic.DEFAULT_FILL_OPACITY);
    settings.setUprightByDrag(false);
    settings.setCircleRadius(ViewSetting.DEFAULT_CIRCLE_RADIUS);
    initialize();
  }
}
