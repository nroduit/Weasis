/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.cmpr;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.View2d;

/**
 * 2D view for displaying curved MPR panoramic images.
 * 
 * <p>This view extends View2d and provides the standard WL/LUT/zoom/pan controls
 * for the panoramic image generated from a curved MPR path.
 */
public class CurvedMprView extends View2d {
  private static final Logger LOGGER = LoggerFactory.getLogger(CurvedMprView.class);

  private CurvedMprAxis curvedMprAxis;

  public CurvedMprView(ImageViewerEventManager<DicomImageElement> eventManager) {
    super(eventManager);
    LOGGER.info("CurvedMprView constructor called");
    getViewButtons().clear();
  }

  @Override
  protected void initActionWState() {
    super.initActionWState();
  }

  public CurvedMprAxis getCurvedMprAxis() {
    return curvedMprAxis;
  }

  public void setCurvedMprAxis(CurvedMprAxis axis) {
    this.curvedMprAxis = axis;
    if (axis != null) {
      axis.setView(this);
    }
  }

  @Override
  protected void setImage(DicomImageElement img) {
    LOGGER.info("CurvedMprView.setImage called, img={}", img != null ? "not null" : "null");
    super.setImage(img);
    LOGGER.info("super.setImage completed");
  }

  /**
   * Show a dialog to adjust the panoramic width parameter.
   */
  public void showWidthDialog() {
    if (curvedMprAxis == null) return;

    double currentWidth = curvedMprAxis.getWidthMm();
    double maxWidth = 200.0;
    double minPixelRatio = curvedMprAxis.getVolume().getMinPixelRatio();

    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING));
    SpinnerNumberModel spinnerModel =
        new SpinnerNumberModel(currentWidth, 1.0, maxWidth, minPixelRatio);
    JSpinner widthSpinner = new JSpinner(spinnerModel);
    JLabel conversionLabel = new JLabel();

    widthSpinner.addChangeListener(
        e -> {
          double value = (Double) widthSpinner.getValue();
          int pixels = (int) Math.round(value / minPixelRatio);
          conversionLabel.setText(
              " %.1f mm = %d pixels".formatted(value, pixels));
        });

    panel.add(new JLabel("Width (mm)" + StringUtil.COLON));
    panel.add(widthSpinner);
    panel.add(conversionLabel);

    FontMetrics metrics = conversionLabel.getFontMetrics(conversionLabel.getFont());
    String maxExpectedLabel = " %.1f mm = %d pixels".formatted(maxWidth, (int) (maxWidth / minPixelRatio));
    conversionLabel.setPreferredSize(
        new Dimension(metrics.stringWidth(maxExpectedLabel), metrics.getHeight()));

    widthSpinner.setValue(currentWidth);

    int result =
        JOptionPane.showConfirmDialog(
            GuiUtils.getUICore().getApplicationWindow(),
            panel,
            "Adjust Panoramic Width",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);

    if (result == JOptionPane.OK_OPTION) {
      double newWidth = (Double) widthSpinner.getValue();
      curvedMprAxis.setWidthMm(newWidth);
    }
  }

  /**
   * Show a dialog to adjust the sampling step parameter.
   */
  public void showStepDialog() {
    if (curvedMprAxis == null) return;

    double currentStep = curvedMprAxis.getStepMm();
    double minPixelRatio = curvedMprAxis.getVolume().getMinPixelRatio();
    double maxStep = 10.0;

    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING));
    SpinnerNumberModel spinnerModel =
        new SpinnerNumberModel(currentStep, minPixelRatio / 2, maxStep, minPixelRatio / 4);
    JSpinner stepSpinner = new JSpinner(spinnerModel);
    JLabel infoLabel = new JLabel();

    stepSpinner.addChangeListener(
        e -> {
          double value = (Double) stepSpinner.getValue();
          double totalArcLength = curvedMprAxis.getTotalArcLengthMm();
          int samples = (int) Math.ceil(totalArcLength / value);
          infoLabel.setText(" Step: %s mm (%d samples)"
              .formatted(DecFormatter.allNumber(value), samples));
        });

    panel.add(new JLabel("Step (mm)" + StringUtil.COLON));
    panel.add(stepSpinner);
    panel.add(infoLabel);

    FontMetrics metrics = infoLabel.getFontMetrics(infoLabel.getFont());
    String maxExpectedLabel = " Step: %s mm (%d samples)"
        .formatted(DecFormatter.allNumber(maxStep), 9999);
    infoLabel.setPreferredSize(
        new Dimension(metrics.stringWidth(maxExpectedLabel), metrics.getHeight()));

    stepSpinner.setValue(currentStep);

    int result =
        JOptionPane.showConfirmDialog(
            GuiUtils.getUICore().getApplicationWindow(),
            panel,
            "Adjust Sampling Step",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);

    if (result == JOptionPane.OK_OPTION) {
      double newStep = (Double) stepSpinner.getValue();
      curvedMprAxis.setStepMm(newStep);
    }
  }

}
