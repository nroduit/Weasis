/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.text.DecimalFormat;
import javax.swing.BoundedRangeModel;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.core.util.StringUtil;

/** The zoom slider, the lead under the cursor with its range and value, and the heart rate. */
class InfoPanel extends JPanel {

  private static final DecimalFormat VALUE_FORMAT = new DecimalFormat("#0.###"); // NON-NLS

  private final JLabel lead = new JLabel(" ");
  private final JLabel maximum = new JLabel();
  private final JLabel minimum = new JLabel();

  private final JLabel currentLabel = new JLabel();
  private final JLabel value = new JLabel();
  private final JLabel seconds = new JLabel();

  private final JLabel heartRateLabel = new JLabel();
  private final JLabel heartRate = new JLabel();

  public InfoPanel(double zoomRatio) {
    GridBagLayout gridBagLayout = new GridBagLayout();
    setLayout(gridBagLayout);

    SliderChangeListener sliderListener =
        new SliderChangeListener(
            ActionW.ZOOM, 1.0, DefaultViewModel.SCALE_MAX, zoomRatio, true, 0.3, 300) {

          @Override
          public void stateChanged(BoundedRangeModel model) {
            ImageViewerPlugin<?> container =
                WaveContainer.ECG_EVENT_MANAGER.getSelectedView2dContainer();
            if (container instanceof WaveContainer waveContainer) {
              waveContainer.setZoomRatio(toModelValue(model.getValue()));
            }
          }

          @Override
          public String getValueToDisplay() {
            return DecFormatter.percentTwoDecimal(getRealValue());
          }

          @Override
          public int toSliderValue(double viewScale) {
            double v = Math.log(viewScale) / Math.log(DefaultViewModel.SCALE_MAX) * getSliderMax();
            return (int) Math.round(v);
          }

          @Override
          public double toModelValue(int sliderValue) {
            double v = sliderValue / (double) getSliderMax();
            double viewScale = Math.exp(v * Math.log(DefaultViewModel.SCALE_MAX));
            return ImageViewerEventManager.roundAndCropViewScale(
                viewScale, 1.0, DefaultViewModel.SCALE_MAX);
          }
        };
    sliderListener.enableAction(true);
    JSliderW zoomSlider = sliderListener.createSlider(0, true);
    GuiUtils.setPreferredWidth(zoomSlider, 250, 250);
    GridBagConstraints gbcPanel = new GridBagConstraints();
    gbcPanel.fill = GridBagConstraints.NONE;
    gbcPanel.gridx = 0;
    gbcPanel.gridy = 0;
    add(zoomSlider, gbcPanel);

    JPanel main = new JPanel();
    main.setLayout(new GridLayout(3, 3, 25, 0));
    lead.setFont(FontItem.DEFAULT_SEMIBOLD.getFont());
    currentLabel.setFont(FontItem.DEFAULT_SEMIBOLD.getFont());
    heartRateLabel.setFont(FontItem.DEFAULT_SEMIBOLD.getFont());

    main.add(lead);
    main.add(currentLabel);
    main.add(heartRateLabel);

    main.add(minimum);
    main.add(seconds);
    main.add(heartRate);

    main.add(maximum);
    main.add(value);
    main.add(new JLabel());

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = GuiUtils.isRightToLeft() ? new Insets(0, 10, 0, 15) : new Insets(0, 15, 0, 10);
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridx = 1;
    gbc.gridy = 0;
    this.add(main, gbc);

    JPanel panel = new JPanel();
    GridBagConstraints gbcPanel2 = new GridBagConstraints();
    gbcPanel2.weightx = 1.0;
    gbcPanel2.fill = GridBagConstraints.BOTH;
    gbcPanel2.gridx = 2;
    gbcPanel2.gridy = 0;
    add(panel, gbcPanel2);
  }

  public void setLead(String lead) {
    this.lead.setText(lead);
  }

  /** The range of the lead under the cursor, in its display unit. */
  public void setMinMax(double minimum, double maximum, String unit) {
    this.minimum.setText(
        Messages.getString("InfoPanel.min")
            + StringUtil.COLON_AND_SPACE
            + VALUE_FORMAT.format(minimum)
            + " "
            + unit);
    this.maximum.setText(
        Messages.getString("InfoPanel.max")
            + StringUtil.COLON_AND_SPACE
            + VALUE_FORMAT.format(maximum)
            + " "
            + unit);
  }

  /** The time and value under the cursor; a negative time clears them. */
  public void setCurrentValues(double sec, double value, String unit) {
    if (sec < 0) {
      clearValue(currentLabel, seconds, this.value);
    } else {
      currentLabel.setText(Messages.getString("InfoPanel.cursor"));
      seconds.setText(WaveFormats.SECONDS.format(sec));
      this.value.setText(VALUE_FORMAT.format(value) + " " + (unit == null ? "" : unit));
    }
  }

  /** The heart rate estimated from the signal; null clears it. */
  public void setHeartRate(Double bpm) {
    if (bpm == null) {
      clearValue(heartRateLabel, heartRate);
    } else {
      heartRateLabel.setText(Messages.getString("InfoPanel.hr"));
      heartRate.setText(
          "≈ " + WaveFormats.bpm(bpm) + " (" + Messages.getString("InfoPanel.estimated") + ")");
    }
  }

  private void clearValue(JLabel... labels) {
    if (labels != null) {
      for (JLabel l : labels) {
        l.setText("");
      }
    }
  }
}
