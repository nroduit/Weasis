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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.BoundedRangeModel;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.SliderChangeListener;

class WindowBoundsPanelTest {

  // One slider step of a 0..2000 range over 4096 steps
  private static final double STEP = 0.5;

  @Test
  void the_bounds_are_level_minus_and_plus_half_the_window() {
    SliderChangeListener window = slider(ActionW.WINDOW, 1, 2000, 200);
    SliderChangeListener level = slider(ActionW.LEVEL, 0, 2000, 500);
    WindowBoundsPanel bounds = new WindowBoundsPanel(window, level, 0);

    assertAll(
        () -> assertEquals(400, bounds.lowerValue(), STEP),
        () -> assertEquals(600, bounds.upperValue(), STEP));
  }

  @Test
  void each_bound_moves_without_moving_the_other() {
    SliderChangeListener window = slider(ActionW.WINDOW, 1, 2000, 200);
    SliderChangeListener level = slider(ActionW.LEVEL, 0, 2000, 500);
    WindowBoundsPanel bounds = new WindowBoundsPanel(window, level, 0);

    bounds.setBounds(100, bounds.upperValue());
    assertAll(
        () -> assertEquals(100, bounds.lowerValue(), STEP),
        () -> assertEquals(600, bounds.upperValue(), STEP),
        () -> assertEquals(500, window.getRealValue(), STEP, "stored as window width"),
        () -> assertEquals(350, level.getRealValue(), STEP, "and window center"));

    bounds.setBounds(bounds.lowerValue(), 900);
    assertAll(
        () -> assertEquals(100, bounds.lowerValue(), STEP),
        () -> assertEquals(900, bounds.upperValue(), STEP));
  }

  @Test
  void a_lower_bound_above_the_upper_one_keeps_the_smallest_window() {
    SliderChangeListener window = slider(ActionW.WINDOW, 1, 2000, 200);
    SliderChangeListener level = slider(ActionW.LEVEL, 0, 2000, 500);
    WindowBoundsPanel bounds = new WindowBoundsPanel(window, level, 0);

    bounds.setBounds(800, bounds.upperValue());

    assertTrue(bounds.lowerValue() < bounds.upperValue());
    assertEquals(600, bounds.upperValue(), 1.0);
  }

  @Test
  void only_nm_images_are_nuclear_medicine() {
    assertFalse(ImageTool.isNuclearMedicine(null));
  }

  private static SliderChangeListener slider(
      Feature<? extends ActionState> action, double min, double max, double value) {
    return new SliderChangeListener(action, min, max, value, true, 1.0, 4096) {
      @Override
      public void stateChanged(BoundedRangeModel model) {
        // no viewer behind the test
      }
    };
  }
}
