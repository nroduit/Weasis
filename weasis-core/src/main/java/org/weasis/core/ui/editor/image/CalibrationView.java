/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Objects;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.StringUtil;

public class CalibrationView extends JPanel {

  private static final Logger LOGGER = LoggerFactory.getLogger(CalibrationView.class);

  private final ViewCanvas<?> view2d;
  private final LineGraphic line;

  private final JComboBox<Unit> jComboBoxUnit;
  private final JFormattedTextField jTextFieldLineWidth = new JFormattedTextField();

  private final ButtonGroup ratioGroup = new ButtonGroup();
  private final JRadioButton radioButtonSeries =
      new JRadioButton(Messages.getString("CalibrationView.series"));
  private final JRadioButton radioButtonImage =
      new JRadioButton(Messages.getString("CalibrationView.current"));

  public CalibrationView(LineGraphic line, ViewCanvas<?> view2d, boolean selectSeries) {
    this.line = line;
    this.view2d = view2d;
    List<Unit> units = Unit.getUnitExceptPixel();
    this.jComboBoxUnit = new JComboBox<>(units.toArray(new Unit[0]));
    try {
      jbInit();
      radioButtonSeries.setSelected(selectSeries);
      if (!selectSeries) {
        radioButtonImage.setSelected(true);
      }
      initialize();
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  void jbInit() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(GuiUtils.getEmptyBorder(10, 15, 10, 15));

    GuiUtils.setPreferredWidth(jTextFieldLineWidth, 170);
    jTextFieldLineWidth.setLocale(LocalUtil.getLocaleFormat());
    jTextFieldLineWidth.setFormatterFactory(
        DecFormatter.setPreciseDoubleFormat(0.000005d, Double.MAX_VALUE));
    jTextFieldLineWidth.setValue(1.0);
    GuiUtils.addCheckAction(jTextFieldLineWidth);

    JLabel jLabelKnownDist =
        new JLabel(Messages.getString("CalibrationView.known") + StringUtil.COLON);
    JLabel lblApplyTo = new JLabel(Messages.getString("CalibrationView.apply") + StringUtil.COLON);
    ratioGroup.add(radioButtonSeries);
    ratioGroup.add(radioButtonImage);

    add(GuiUtils.getFlowLayoutPanel(jLabelKnownDist, jTextFieldLineWidth, jComboBoxUnit));
    add(GuiUtils.getFlowLayoutPanel(lblApplyTo, radioButtonSeries, radioButtonImage));
  }

  public boolean isApplyingToSeries() {
    return radioButtonSeries.isSelected();
  }

  private void initialize() {
    ImageElement image = view2d.getImage();
    if (image != null) {
      Unit unit = image.getPixelSpacingUnit();
      if (!Unit.PIXEL.equals(unit)) {
        Point2D ptA = line.getStartPoint();
        Point2D ptB = line.getEndPoint();
        if (Objects.nonNull(ptA) && Objects.nonNull(ptB)) {
          jTextFieldLineWidth.setValue(ptA.distance(ptB) * image.getPixelSize());
        }
      } else {
        JTextArea area = new JTextArea(Messages.getString("CalibrationView.warn"));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setColumns(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(
            GuiUtils.getDimension(300, GuiUtils.getBigIconButtonSize(area).height * 4));
        add(scroll, 0);
        unit = Unit.MILLIMETER;
      }

      jComboBoxUnit.setSelectedItem(unit);
    }
  }

  public void removeCalibration() {
    applyCalibration(1.0, Unit.PIXEL);
  }

  private void applyCalibration(double ratio, Unit unit) {
    ImageElement image = view2d.getImage();
    if (image != null) {
      if (radioButtonSeries.isSelected()) {
        MediaSeries<?> seriesList = view2d.getSeries();
        if (Objects.nonNull(seriesList)) {
          Iterable<?> list = seriesList.getMedias(null, null);
          synchronized (seriesList) {
            for (Object media : list) {
              if (media instanceof ImageElement img && media != image) {
                img.setPixelSpacingUnit(unit);
                img.setPixelSize(ratio);
              }
            }
          }
        }
      }
      image.setPixelSize(ratio);
      image.setPixelSpacingUnit(unit);

      ImageViewerEventManager<?> manager = view2d.getEventManager();
      if (manager.getSelectedViewPane() == view2d) {
        manager.getAction(ActionW.SPATIAL_UNIT).ifPresent(c -> c.setSelectedItem(unit));
      }
      view2d.getGraphicManager().updateLabels(Boolean.TRUE, view2d);
    }
  }

  public void applyNewCalibration() {
    ImageElement image = view2d.getImage();
    if (image != null) {
      Number inputCalibVal = GuiUtils.getFormattedValue(jTextFieldLineWidth);
      if (inputCalibVal != null) {
        double imgRatio = image.getPixelSize();
        Unit unit = (Unit) jComboBoxUnit.getSelectedItem();
        Unit imgUnit = image.getPixelSpacingUnit();
        if (!Unit.PIXEL.equals(unit)) {
          image.setPixelSpacingUnit(unit);
          Double lineLength = null;
          Point2D ptA = line.getStartPoint();
          Point2D ptB = line.getEndPoint();
          if (Objects.nonNull(ptA) && Objects.nonNull(ptB)) {
            lineLength = ptA.distance(ptB);
          }

          if (Objects.isNull(lineLength) || lineLength < 1d) {
            lineLength = 1.0;
          }
          double newRatio = inputCalibVal.doubleValue() / lineLength;
          if (MathUtil.isDifferent(newRatio, imgRatio) || !Objects.equals(unit, imgUnit)) {
            applyCalibration(newRatio, unit);
          }
        }
      }
    }
  }
}
