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
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.core.util.StringUtil;

class InfoPanel extends JPanel {

  private final JLabel lead = new JLabel(" ");
  private final JLabel maximum = new JLabel();
  private final JLabel minimum = new JLabel();

  private final JLabel currentLabel = new JLabel();
  private final JLabel milliVolt = new JLabel();
  private final JLabel seconds = new JLabel();

  public InfoPanel(double zoomRatio) {
    GridBagLayout gridBagLayout = new GridBagLayout();
    setLayout(gridBagLayout);

    SliderChangeListener sliderListener =
        new SliderChangeListener(
            ActionW.ZOOM, 1.0, DefaultViewModel.SCALE_MAX, zoomRatio, true, 0.1, 100) {

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
    main.setLayout(new GridLayout(3, 2, 25, 0));
    lead.setFont(FontItem.DEFAULT_SEMIBOLD.getFont());
    currentLabel.setFont(FontItem.DEFAULT_SEMIBOLD.getFont());

    main.add(lead);
    main.add(currentLabel);

    main.add(minimum);
    main.add(seconds);

    main.add(maximum);
    main.add(milliVolt);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(0, 15, 0, 10);
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

  public void setMinMax(double minimum, double maximum) {
    StringBuilder min = new StringBuilder(Messages.getString("InfoPanel.min"));
    min.append(StringUtil.COLON_AND_SPACE);
    min.append("##.#### mV;"); // NON-NLS
    min.append(Messages.getString("InfoPanel.min"));
    min.append(StringUtil.COLON_AND_SPACE);
    min.append("-##.#### mV"); // NON-NLS

    StringBuilder max = new StringBuilder(Messages.getString("InfoPanel.max"));
    max.append(StringUtil.COLON_AND_SPACE);
    max.append("##.#### mV;"); // NON-NLS
    max.append(Messages.getString("InfoPanel.max"));
    max.append(StringUtil.COLON_AND_SPACE);
    max.append("-##.#### mV"); // NON-NLS
    this.minimum.setText(
        new DecimalFormat(min.toString(), LocalUtil.getDecimalFormatSymbols()).format(minimum));
    this.maximum.setText(
        new DecimalFormat(max.toString(), LocalUtil.getDecimalFormatSymbols()).format(maximum));
  }

  public void setCurrentValues(double sec, double mV) {
    if (sec < 0) {
      clearValue(currentLabel, seconds, milliVolt);
    } else {
      currentLabel.setText(Messages.getString("InfoPanel.cursor"));
      seconds.setText(MarkerAnnotation.secondFormatter.format(sec));
      milliVolt.setText(MarkerAnnotation.mVFormatter.format(mV));
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
