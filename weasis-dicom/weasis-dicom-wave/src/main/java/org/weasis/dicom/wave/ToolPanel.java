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

import java.awt.Component;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.WrapFlowLayout;
import org.weasis.core.util.StringUtil;

/** The display settings of the waveform view: paper, layout, lead sequence, filters, group. */
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

  /** Low-pass cut-offs offered, in Hz (0 = none). */
  static final double[] LOW_PASS_HZ = {0, 40, 100, 150};

  /** Mains frequencies offered for the notch, in Hz (0 = none). */
  static final double[] NOTCH_HZ = {0, 50, 60};

  private final WaveView view;
  private JComboBox<Format> formatCombo;

  public ToolPanel(WaveView view) {
    this.view = view;
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    // the rows wrap to the width of the view instead of being cut
    JPanel paper = new JPanel(new WrapFlowLayout(5, 2));
    JPanel processing = new JPanel(new WrapFlowLayout(5, 2));
    add(paper);
    add(processing);
    initPaper(paper);
    initProcessing(processing);
  }

  private void initPaper(JPanel panel) {
    JLabel zoomLabel = new JLabel(Messages.common("zoom"));
    panel.add(zoomLabel, WrapFlowLayout.KEEP_WITH_NEXT);

    JComboBox<Speed> speed = new JComboBox<>(Speed.values());
    speed.addActionListener(e -> view.setSpeed(((Speed) speed.getSelectedItem()).getValue()));
    speed.setSelectedItem(Speed.fromValue(view.getSpeed()));
    speed.setFocusable(false);
    panel.add(speed);

    JComboBox<Amplitude> amplitude = new JComboBox<>(Amplitude.values());
    amplitude.addActionListener(
        e -> view.setAmplitude(((Amplitude) amplitude.getSelectedItem()).getValue()));
    amplitude.setSelectedItem(Amplitude.fromValue(view.getAmplitude()));
    amplitude.setFocusable(false);
    panel.add(amplitude);

    addDisplayFormatComponent(panel);

    JCheckBox calibration =
        new JCheckBox(Messages.getString("ToolPanel.calibration"), view.isCalibrationPulse());
    calibration.setToolTipText(Messages.getString("ToolPanel.calibration_tooltip"));
    calibration.setFocusable(false);
    calibration.addActionListener(e -> view.setCalibrationPulse(calibration.isSelected()));
    panel.add(calibration);

    addMultiplexGroupComponent(panel);
  }

  /** The display filters: baseline wander, low-pass and mains notch. */
  private void initProcessing(JPanel panel) {
    panel.add(
        new JLabel(Messages.getString("ToolPanel.filter") + StringUtil.COLON),
        WrapFlowLayout.KEEP_WITH_NEXT);

    JCheckBox baseline =
        new JCheckBox(Messages.getString("ToolPanel.baseline"), view.getFilter().baseline());
    baseline.setToolTipText(Messages.getString("ToolPanel.baseline_tooltip"));
    baseline.setFocusable(false);
    baseline.addActionListener(
        e -> view.setFilter(view.getFilter().withBaseline(baseline.isSelected())));
    panel.add(baseline);

    panel.add(
        new JLabel(Messages.getString("ToolPanel.lowpass") + StringUtil.COLON),
        WrapFlowLayout.KEEP_WITH_NEXT);
    JComboBox<String> lowPass = new JComboBox<>(labels(LOW_PASS_HZ));
    lowPass.setFocusable(false);
    lowPass.setSelectedIndex(indexOf(LOW_PASS_HZ, view.getFilter().lowPassHz()));
    lowPass.addActionListener(
        e -> view.setFilter(view.getFilter().withLowPass(LOW_PASS_HZ[lowPass.getSelectedIndex()])));
    panel.add(lowPass);

    panel.add(
        new JLabel(Messages.getString("ToolPanel.notch") + StringUtil.COLON),
        WrapFlowLayout.KEEP_WITH_NEXT);
    JComboBox<String> notch = new JComboBox<>(labels(NOTCH_HZ));
    notch.setFocusable(false);
    notch.setSelectedIndex(indexOf(NOTCH_HZ, view.getFilter().notchHz()));
    notch.addActionListener(
        e -> view.setFilter(view.getFilter().withNotch(NOTCH_HZ[notch.getSelectedIndex()])));
    panel.add(notch);

    panel.add(
        new JLabel("    " + Messages.getString("ToolPanel.caliper") + StringUtil.COLON),
        WrapFlowLayout.KEEP_WITH_NEXT);
    JComboBox<String> kind =
        new JComboBox<>(
            new String[] {
              Messages.getString("ToolPanel.caliper_time"),
              Messages.getString("ToolPanel.caliper_amplitude")
            });
    kind.setFocusable(false);
    kind.setSelectedIndex(view.getCaliperKind() == Caliper.Kind.AMPLITUDE ? 1 : 0);
    kind.setToolTipText(Messages.getString("ToolPanel.caliper_tooltip"));
    kind.addActionListener(
        e ->
            view.setCaliperKind(
                kind.getSelectedIndex() == 1 ? Caliper.Kind.AMPLITUDE : Caliper.Kind.TIME));
    panel.add(kind);

    String[] labels = new String[Caliper.LABELS.length + 1];
    labels[0] = Messages.common("none");
    System.arraycopy(Caliper.LABELS, 0, labels, 1, Caliper.LABELS.length);
    JComboBox<String> label = new JComboBox<>(labels);
    label.setFocusable(false);
    label.setToolTipText(Messages.getString("ToolPanel.caliper_label_tooltip"));
    label.addActionListener(
        e ->
            view.setCaliperLabel(
                label.getSelectedIndex() == 0
                    ? null
                    : Caliper.LABELS[label.getSelectedIndex() - 1]));
    panel.add(label);

    JCheckBox allLeads =
        new JCheckBox(
            Messages.getString("ToolPanel.calipers_all_leads"), view.isCalipersOnAllLeads());
    allLeads.setFocusable(false);
    allLeads.addActionListener(e -> view.setCalipersOnAllLeads(allLeads.isSelected()));
    panel.add(allLeads);
  }

  private static String[] labels(double[] frequencies) {
    String[] labels = new String[frequencies.length];
    for (int i = 0; i < frequencies.length; i++) {
      labels[i] =
          frequencies[i] <= 0
              ? Messages.common("none")
              : DecFormatter.allNumber(frequencies[i]) + " Hz"; // NON-NLS
    }
    return labels;
  }

  private static int indexOf(double[] values, double value) {
    for (int i = 0; i < values.length; i++) {
      if (values[i] == value) {
        return i;
      }
    }
    return 0;
  }

  /**
   * A choice of the multiplex group when the object holds several (e.g. rhythm and median beat).
   */
  private void addMultiplexGroupComponent(JPanel panel) {
    List<String> groups = view.getMultiplexGroupLabels();
    if (groups.size() < 2) {
      return;
    }
    JLabel label = new JLabel(Messages.getString("ToolPanel.group") + StringUtil.COLON);
    panel.add(label, WrapFlowLayout.KEEP_WITH_NEXT);
    JComboBox<String> groupCombo = new JComboBox<>(groups.toArray(new String[0]));
    groupCombo.setFocusable(false);
    groupCombo.setSelectedIndex(Math.min(view.getMultiplexGroup(), groups.size() - 1));
    groupCombo.addActionListener(e -> view.setMultiplexGroup(groupCombo.getSelectedIndex()));
    panel.add(groupCombo);
  }

  private void addDisplayFormatComponent(JPanel panel) {
    JLabel formatLabel = new JLabel(Messages.getString("ToolPanel.disp_format") + StringUtil.COLON);
    panel.add(formatLabel, WrapFlowLayout.KEEP_WITH_NEXT);

    boolean twelveLeads = view.getChannelNumber() >= 12;
    Format[] formats =
        twelveLeads ? Format.values() : new Format[] {Format.DEFAULT, Format.SUPERIMPOSED};
    formatCombo = new JComboBox<>(formats);
    formatCombo.setFocusable(false);
    formatCombo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value == Format.DEFAULT && Math.abs(view.getSeconds() - 10) > 0.5) {
              setText(
                  Messages.getString("Format.full")
                      + " ("
                      + WaveFormats.SECONDS.format(view.getSeconds())
                      + ")");
            }
            return this;
          }
        });
    formatCombo.setSelectedItem(view.getCurrentFormat());
    formatCombo.addActionListener(e -> view.setFormat((Format) formatCombo.getSelectedItem()));
    panel.add(formatCombo);

    if (twelveLeads) {
      JCheckBox cabrera = new JCheckBox(Messages.getString("ToolPanel.cabrera"), view.isCabrera());
      cabrera.setToolTipText(Messages.getString("ToolPanel.cabrera_tooltip"));
      cabrera.setFocusable(false);
      cabrera.addActionListener(e -> view.setCabrera(cabrera.isSelected()));
      panel.add(cabrera);
    }
  }
}
