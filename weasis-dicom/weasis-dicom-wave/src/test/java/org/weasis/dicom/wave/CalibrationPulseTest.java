/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class CalibrationPulseTest {

  private static final double PX_PER_MM = 4;

  private static double[] edges(double mmPerSecond) {
    double gutter = Math.round(WaveView.gutterMillimeters(mmPerSecond) * PX_PER_MM);
    return LeadPanel.pulseEdges(
        gutter, mmPerSecond * PX_PER_MM, WaveView.PULSE_MARGIN_MM * PX_PER_MM);
  }

  @Test
  void the_gutter_holds_200_ms_and_its_margins_and_never_shrinks_under_6_mm() {
    assertEquals(6, WaveView.gutterMillimeters(12.5));
    assertEquals(6, WaveView.gutterMillimeters(25));
    assertEquals(11, WaveView.gutterMillimeters(50));
    assertEquals(21, WaveView.gutterMillimeters(100));
  }

  @Test
  void the_pulse_is_200_ms_wide_at_every_paper_speed() {
    for (double speed : new double[] {12.5, 25, 50, 100}) {
      double[] x = edges(speed);
      assertEquals(0.2 * speed * PX_PER_MM, x[2] - x[1], 1e-9, "speed " + speed);
      assertTrue(x[0] >= 0 && x[0] <= x[1] && x[2] <= x[3], "speed " + speed);
    }
    // 5 mm at 25 mm/s
    double[] x = edges(25);
    assertEquals(5 * PX_PER_MM, x[2] - x[1], 1e-9);
  }

  @Test
  void the_pulse_follows_a_slower_automatic_speed_in_the_gutter_kept_for_25_mm_per_second() {
    double gutter = WaveView.gutterMillimeters(25) * PX_PER_MM;
    double[] x = LeadPanel.pulseEdges(gutter, 18 * PX_PER_MM, WaveView.PULSE_MARGIN_MM * PX_PER_MM);
    assertEquals(0.2 * 18 * PX_PER_MM, x[2] - x[1], 1e-9);
    assertEquals(gutter - WaveView.PULSE_MARGIN_MM * PX_PER_MM, x[2], 1e-9);
  }

  @Test
  void a_gutter_too_narrow_keeps_the_edges_in_order() {
    double[] x = LeadPanel.pulseEdges(10, 400, 2);
    assertEquals(2, x[1], 1e-9);
    assertEquals(8, x[2], 1e-9);
  }
}
