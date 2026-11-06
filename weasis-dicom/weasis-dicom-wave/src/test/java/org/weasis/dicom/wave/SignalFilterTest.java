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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SignalFilterTest {

  private static final double FS = 500;

  private static double[] tone(double hz, double amplitude, int n) {
    double[] values = new double[n];
    for (int i = 0; i < n; i++) {
      values[i] = amplitude * Math.sin(2 * Math.PI * hz * i / FS);
    }
    return values;
  }

  private static double rms(double[] values, int from, int to) {
    double sum = 0;
    for (int i = from; i < to; i++) {
      sum += values[i] * values[i];
    }
    return Math.sqrt(sum / (to - from));
  }

  private static void add(double[] target, double[] source) {
    for (int i = 0; i < target.length; i++) {
      target[i] += source[i];
    }
  }

  @Test
  void identity_filter_changes_nothing() {
    double[] values = tone(5, 100, 2000);
    double[] copy = values.clone();
    SignalFilter.NONE.apply(values, values.length, FS);
    assertEquals(0, SignalFilter.NONE.margin(FS));
    for (int i = 0; i < values.length; i++) {
      assertEquals(copy[i], values[i]);
    }
  }

  @Test
  void notch_removes_the_mains_and_keeps_the_qrs_band() {
    int n = 4000;
    double[] values = tone(10, 100, n);
    add(values, tone(50, 200, n));
    SignalFilter filter = SignalFilter.NONE.withNotch(50);
    filter.apply(values, n, FS);
    int margin = filter.margin(FS);
    double[] reference = tone(10, 100, n);
    double error = 0;
    for (int i = margin; i < n - margin; i++) {
      error = Math.max(error, Math.abs(values[i] - reference[i] * 0.98));
    }
    // the 50 Hz tone of 200 is gone, the 10 Hz tone is attenuated by the comb by about 2 %
    assertTrue(error < 8, "residual " + error);
  }

  @Test
  void low_pass_attenuates_above_the_cut_off() {
    int n = 4000;
    double[] low = tone(5, 100, n);
    double[] high = tone(120, 100, n);
    SignalFilter filter = SignalFilter.NONE.withLowPass(40);
    filter.apply(low, n, FS);
    filter.apply(high, n, FS);
    int margin = filter.margin(FS);
    assertTrue(rms(low, margin, n - margin) > 65, "pass band kept");
    assertTrue(rms(high, margin, n - margin) < 5, "stop band removed");
  }

  @Test
  void baseline_removal_takes_out_the_slow_wander_and_keeps_the_fast_wave() {
    int n = 6000;
    double[] values = tone(0.2, 500, n);
    add(values, tone(15, 100, n));
    SignalFilter filter = SignalFilter.NONE.withBaseline(true);
    filter.apply(values, n, FS);
    int margin = filter.margin(FS);
    double[] fast = tone(15, 100, n);
    double error = 0;
    for (int i = margin; i < n - margin; i++) {
      error = Math.max(error, Math.abs(values[i] - fast[i]));
    }
    assertTrue(error < 25, "residual wander " + error);
  }

  @Test
  void heart_rate_is_found_on_a_synthetic_rhythm() {
    double fs = 500;
    int n = (int) (30 * fs);
    double[] ecg = new double[n];
    double rr = 60.0 / 75; // 75 bpm
    for (int i = 0; i < n; i++) {
      double t = i / fs;
      double beat = t % rr;
      double qrs = beat < 0.04 ? 1500 * Math.sin(beat / 0.04 * Math.PI) : 0;
      double tWave = beat > 0.2 && beat < 0.4 ? 250 * Math.sin((beat - 0.2) / 0.2 * Math.PI) : 0;
      ecg[i] = qrs + tWave + 20 * Math.sin(2 * Math.PI * 0.3 * t);
    }
    Double bpm = HeartRate.estimate(ecg, fs);
    assertNotNull(bpm);
    assertEquals(75, bpm, 1.5);

    assertNull(HeartRate.estimate(new double[(int) fs], fs), "too short");
    assertNull(HeartRate.estimate(new double[n], fs), "flat line");
  }
}
