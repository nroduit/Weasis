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

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.ui.dialog.PropertiesDialog;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.graphic.Graphic;

public class GraphicPrefView extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(GraphicPrefView.class);

  public static final String PAGE_NAME = ActionW.DRAW_GRAPHICS.getTitle();

  private final JSpinner spinner = new JSpinner();

  private final JCheckBox checkboxFilled =
      new JCheckBox(Messages.getString("PropertiesDialog.fill_shape"));

  private final JSliderW sliderOpacity;

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
    add(GuiUtils.getFlowLayoutPanel(0, ITEM_SEPARATOR_LARGE, checkboxFilled));
    JButton button = MeasureTool.buildLineColorButton(this);
    MeasureTool.viewSetting.initLineWidthSpinner(spinner);
    JPanel linePane = GuiUtils.getFlowLayoutPanel(button, spinner);
    linePane.setBorder(GuiUtils.getTitledBorder(Messages.getString("MeasureToolBar.line")));
    add(linePane);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    JPanel shapePane = GuiUtils.getVerticalBoxLayoutPanel();
    shapePane.add(
        GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, checkboxFilled));

    MigLayout layout2 = new MigLayout("fillx, ins 5lp", "[fill]", ""); // NON-NLS
    new JPanel(layout2).add(sliderOpacity);
    shapePane.add(GuiUtils.getHorizontalBoxLayoutPanel(ITEM_SEPARATOR_SMALL, sliderOpacity));
    shapePane.setBorder(GuiUtils.getTitledBorder(Messages.getString("closed.shape")));
    add(shapePane);

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

    int opacity = (int) (settings.getFillOpacity() * 100);
    sliderOpacity.setValue(opacity);
    PropertiesDialog.updateSlider(sliderOpacity, PropertiesDialog.FILL_OPACITY);
  }

  @Override
  public void closeAdditionalWindow() {
    ViewSetting settings = MeasureTool.viewSetting;
    settings.setFilled(checkboxFilled.isSelected());
    settings.setFillOpacity(sliderOpacity.getValue() / 100f);
    MeasureTool.updateMeasureProperties();
  }

  @Override
  public void resetToDefaultValues() {
    ViewSetting settings = MeasureTool.viewSetting;
    settings.setLineWidth(Graphic.DEFAULT_LINE_THICKNESS.intValue());
    settings.setLineColor(Graphic.DEFAULT_COLOR);
    settings.setFilled(Graphic.DEFAULT_FILLED);
    settings.setFillOpacity(Graphic.DEFAULT_FILL_OPACITY);
    initialize();
  }
}
