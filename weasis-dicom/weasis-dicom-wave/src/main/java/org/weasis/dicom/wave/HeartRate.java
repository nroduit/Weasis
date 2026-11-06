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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Estimates the heart rate from one ECG lead: QRS complexes are detected with a derivative,
 * squaring and moving-window integration (the Pan-Tompkins outline) and an adaptive threshold, and
 * the rate is the median R-R interval. The result is an estimate for orientation, not a
 * measurement.
 */
public final class HeartRate {

  private HeartRate() {}

  /**
   * The sample indexes of the QRS complexes found in a lead (baseline roughly removed), in order.
   *
   * @param signal one lead in any unit
   * @param fs sampling frequency in Hz
   */
  public static List<Integer> peaks(double[] signal, double fs) {
    int n = signal.length;
    List<Integer> peaks = new ArrayList<>();
    if (n < fs / 2 || fs <= 0) {
      return peaks;
    }
    double[] feature = new double[n];
    for (int i = 2; i < n - 2; i++) {
      double d = (2 * signal[i + 2] + signal[i + 1] - signal[i - 1] - 2 * signal[i - 2]) / 8;
      feature[i] = d * d;
    }
    int window = Math.max(1, (int) Math.round(0.15 * fs));
    double[] integrated = new double[n];
    SignalFilter.movingAverage(feature, n, window, integrated);

    int refractory = (int) Math.round(0.25 * fs);
    int segment = (int) Math.round(2 * fs);
    for (int start = 0; start < n; start += segment) {
      int end = Math.min(n, start + segment);
      double max = 0;
      for (int i = start; i < end; i++) {
        max = Math.max(max, integrated[i]);
      }
      double threshold = 0.4 * max;
      if (threshold <= 0) {
        continue;
      }
      int i = start;
      while (i < end) {
        if (integrated[i] > threshold) {
          int best = i;
          int limit = Math.min(n, i + refractory);
          for (int j = i; j < limit; j++) {
            if (integrated[j] > integrated[best]) {
              best = j;
            }
          }
          // The integrated QRS has a flat top: take the middle of its upper part, not its first
          // maximum, so that the R-R intervals do not jitter by the width of the plateau
          double level = 0.9 * integrated[best];
          int from = best;
          while (from > i && integrated[from - 1] >= level) {
            from--;
          }
          int to = best;
          while (to + 1 < limit && integrated[to + 1] >= level) {
            to++;
          }
          int centre = (from + to) / 2;
          if (peaks.isEmpty() || centre - peaks.get(peaks.size() - 1) > refractory) {
            peaks.add(centre);
          }
          i = to + refractory;
        } else {
          i++;
        }
      }
    }
    return peaks;
  }

  /**
   * @param signal one lead in any unit, baseline roughly removed
   * @param fs sampling frequency in Hz
   * @return beats per minute, or null when fewer than three regular beats are found
   */
  public static Double estimate(double[] signal, double fs) {
    List<Integer> peaks = peaks(signal, fs);
    if (peaks.size() < 4) {
      return null;
    }
    double[] rr = new double[peaks.size() - 1];
    for (int i = 0; i < rr.length; i++) {
      rr[i] = (peaks.get(i + 1) - peaks.get(i)) / fs;
    }
    Arrays.sort(rr);
    double median = rr[rr.length / 2];
    double bpm = 60 / median;
    if (bpm < 20 || bpm > 300) {
      return null;
    }
    return bpm;
  }
}
