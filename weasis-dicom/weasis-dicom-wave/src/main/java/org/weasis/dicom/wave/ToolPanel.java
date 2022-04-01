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

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.util.StringUtil;

class ToolPanel extends JPanel {

  public enum Speed {
    AUTO(WaveLayoutManager.AUTO_SPEED),
    TWELVE(12.5),
    TWENTY_FIVE(25.0),
    FIFTY(50.0),
    CENT(100.0);

    private final double value;

    Speed(double value) {
      this.value = value;
    }

    public double getValue() {
      return value;
    }

    @Override
    public String toString() {
      if (this == AUTO) {
        return "auto mm/s"; // NON-NLS
      }
      return DecFormatter.allNumber(value) + " mm/s"; // NON-NLS
    }

    public static Speed fromValue(double value) {
      for (Speed s : values()) {
        if (s.getValue() == value) {
          return s;
        }
      }
      return AUTO;
    }
  }

  public enum Amplitude {
    AUTO(WaveLayoutManager.AUTO_AMPLITUDE),
    THREE(3),
    FIVE(5),
    TEN(10),
    FIFTEEN(15),
    TWENTY(20),
    THIRTY(30);

    private final int value;

    Amplitude(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }

    @Override
    public String toString() {
      if (this == AUTO) {
        return "auto mm/mV"; // NON-NLS
      }
      return String.format("%d mm/mV", value); // NON-NLS
    }

    public static Amplitude fromValue(int value) {
      for (Amplitude a : values()) {
        if (a.getValue() == value) {
          return a;
        }
      }
      return AUTO;
    }
  }

  private final WaveView view;
  private JComboBox<Format> formatCombo;

  public ToolPanel(WaveView view) {
    this.view = view;
    init();
  }

  private void init() {
    JLabel zoomLabel = new JLabel(Messages.getString("ToolPanel.zoom"));
    this.add(zoomLabel);

    JComboBox<Speed> speed = new JComboBox<>(Speed.values());
    speed.addActionListener(e -> view.setSpeed(((Speed) speed.getSelectedItem()).getValue()));
    speed.setSelectedItem(Speed.fromValue(view.getSpeed()));
    speed.setFocusable(false);
    this.add(speed);

    JComboBox<Amplitude> amplitude = new JComboBox<>(Amplitude.values());
    amplitude.addActionListener(
        e -> view.setAmplitude(((Amplitude) amplitude.getSelectedItem()).getValue()));
    amplitude.setSelectedItem(Amplitude.fromValue(view.getAmplitude()));
    amplitude.setFocusable(false);
    this.add(amplitude);

    if (view.getChannelNumber() >= 12) {
      addDisplayFormatComponent();
    }
  }

  private void addDisplayFormatComponent() {
    JLabel formatLabel = new JLabel(Messages.getString("ToolPanel.disp_format") + StringUtil.COLON);
    this.add(formatLabel);

    formatCombo = new JComboBox<>(Format.values());
    formatCombo.setFocusable(false);
    formatCombo.setSelectedItem(view.getCurrentFormat());
    formatCombo.addActionListener(e -> view.setFormat((Format) formatCombo.getSelectedItem()));
    this.add(formatCombo);
  }
}
