/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.dockable;

import java.text.DecimalFormat;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.viewer2d.Messages;

/**
 * Lower and Upper Window Level sliders, as NM practice requires (IHE RAD TF-2 4.16.4.2.2.3.3): each
 * bound moves on its own, the other stays fixed. They drive the Window and Level actions, so the
 * values stay stored as Window Width and Center and presets, synchronization and pseudo-color keep
 * working.
 */
final class WindowBoundsPanel extends JPanel {
  private static final int STEPS = 4096;

  private final SliderChangeListener window;
  private final SliderChangeListener level;
  private final JSliderW lower = slider("ImageTool.lower");
  private final JSliderW upper = slider("ImageTool.upper");
  private final DecimalFormat format = new DecimalFormat("#,##0.##"); // NON-NLS
  private boolean updating;

  WindowBoundsPanel(SliderChangeListener window, SliderChangeListener level, int gap) {
    this.window = window;
    this.level = level;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    add(lower);
    add(GuiUtils.boxVerticalStrut(gap));
    add(upper);
    add(GuiUtils.boxVerticalStrut(gap));
    window.getSliderModel().addChangeListener(_ -> refresh());
    level.getSliderModel().addChangeListener(_ -> refresh());
    lower.addChangeListener(_ -> boundChanged(true));
    upper.addChangeListener(_ -> boundChanged(false));
    refresh();
  }

  double lowerValue() {
    return level.getRealValue() - window.getRealValue() / 2.0;
  }

  double upperValue() {
    return level.getRealValue() + window.getRealValue() / 2.0;
  }

  /** Sets both bounds; the width stays at least the smallest window. */
  void setBounds(double lowerBound, double upperBound) {
    double minWidth = Math.max(window.toModelValue(window.getSliderMin()), 1.0);
    double lo = Math.min(lowerBound, upperBound - minWidth);
    updating = true;
    try {
      window.setRealValue(upperBound - lo);
      level.setRealValue((upperBound + lo) / 2.0);
    } finally {
      updating = false;
    }
    refresh();
  }

  private void boundChanged(boolean lowerMoved) {
    if (updating) {
      return;
    }
    double lo = lowerMoved ? toReal(lower.getValue()) : lowerValue();
    double hi = lowerMoved ? upperValue() : toReal(upper.getValue());
    if (!lowerMoved) {
      double minWidth = Math.max(window.toModelValue(window.getSliderMin()), 1.0);
      hi = Math.max(hi, lo + minWidth);
    }
    setBounds(lo, hi);
  }

  private void refresh() {
    if (updating) {
      return;
    }
    updating = true;
    try {
      update(lower, "ImageTool.lower", lowerValue());
      update(upper, "ImageTool.upper", upperValue());
    } finally {
      updating = false;
    }
  }

  private void update(JSliderW slider, String key, double value) {
    slider.setValue(toSlider(value));
    SliderChangeListener.updateSliderProperties(
        slider, Messages.getString(key) + StringUtil.COLON_AND_SPACE + format.format(value));
  }

  // The bounds share the range of the Level slider: the value range of the image
  private double min() {
    return level.toModelValue(level.getSliderMin());
  }

  private double max() {
    return level.toModelValue(level.getSliderMax());
  }

  private int toSlider(double value) {
    double range = max() - min();
    if (range <= 0) {
      return 0;
    }
    return (int) Math.round(Math.clamp((value - min()) / range, 0.0, 1.0) * STEPS);
  }

  private double toReal(int sliderValue) {
    return min() + sliderValue * (max() - min()) / STEPS;
  }

  private static JSliderW slider(String key) {
    JSliderW slider = new JSliderW(0, STEPS, 0);
    slider.setDisplayValueInTitle(true);
    slider.setPaintTicks(true);
    slider.setBorder(
        new TitledBorder(
            BorderFactory.createEmptyBorder(),
            Messages.getString(key),
            TitledBorder.LEADING,
            TitledBorder.DEFAULT_POSITION,
            FontItem.MEDIUM.getFont(),
            null));
    slider.setToolTipText(Messages.getString(key + ".tip"));
    GuiUtils.setPreferredWidth(slider, 100);
    return slider;
  }
}
