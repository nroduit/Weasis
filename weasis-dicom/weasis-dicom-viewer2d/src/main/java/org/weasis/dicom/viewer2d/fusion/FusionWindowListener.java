/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import javax.swing.BoundedRangeModel;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.util.StringUtil;

/**
 * Slider moving the top of the overlay display window, in the unit the {@link FusionColorBar} reads
 * (SUVbw when known). The bottom stays where the series or the color map put it, so the scale is
 * read from zero as nuclear medicine does. Dragging the color bar drives this same slider.
 */
public class FusionWindowListener extends SliderChangeListener {

  private static final int SLIDER_RANGE = 1000;

  /**
   * Fraction of the range kept between the bottom and the lowest top, so the window never closes.
   */
  private static final double MIN_SPAN = 0.01;

  private String unit = StringUtil.EMPTY_STRING;

  public FusionWindowListener() {
    super(FusionAction.WINDOW_TOP, 0.0, 1.0, 1.0, true, 1.0, SLIDER_RANGE);
  }

  @Override
  public void stateChanged(BoundedRangeModel model) {
    FusionController.applyWindowTop(toModelValue(model.getValue()));
  }

  @Override
  public void updateSliderProperties(JSliderW slider) {
    String title = getActionW().getTitle() + StringUtil.COLON_AND_SPACE + getValueToDisplay();
    SliderChangeListener.updateSliderProperties(
        slider, StringUtil.hasText(unit) ? title + StringUtil.SPACE + unit : title);
  }

  /**
   * Follows {@code window} without applying anything: the range goes from just above its bottom to
   * {@code topLimit}, both in the display unit.
   */
  public void sync(FusionWindow window, double topLimit) {
    if (window == null || !Double.isFinite(topLimit)) {
      return;
    }
    unit = window.displayUnit();
    double bottom = window.displayMin();
    double top = Math.max(topLimit, window.displayMax());
    setRealMinMaxValue(bottom + (top - bottom) * MIN_SPAN, top, window.displayMax(), false);
  }
}
