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

/**
 * Display filters applied to a lead: baseline wander removal, mains notch and low-pass. All are
 * zero-phase so that the morphology is not shifted in time; {@link #margin(double)} samples on each
 * side of a range are needed for the range to be filtered exactly.
 *
 * @param baseline remove the baseline wander (two cascaded moving averages of 0.2 s and 0.6 s, the
 *     classic estimate of the baseline, subtracted from the signal)
 * @param lowPassHz cut-off of a windowed-sinc low-pass filter, 0 for none
 * @param notchHz mains frequency removed by a moving average over one period (also removes its
 *     harmonics), 0 for none
 */
public record SignalFilter(boolean baseline, double lowPassHz, double notchHz) {

  public static final SignalFilter NONE = new SignalFilter(false, 0, 0);

  private static final double BASELINE_SHORT_S = 0.2;
  private static final double BASELINE_LONG_S = 0.6;

  public boolean isIdentity() {
    return !baseline && lowPassHz <= 0 && notchHz <= 0;
  }

  public SignalFilter withBaseline(boolean on) {
    return new SignalFilter(on, lowPassHz, notchHz);
  }

  public SignalFilter withLowPass(double hz) {
    return new SignalFilter(baseline, hz, notchHz);
  }

  public SignalFilter withNotch(double hz) {
    return new SignalFilter(baseline, lowPassHz, hz);
  }

  /** Samples needed on each side of a range so that the filtered range is exact. */
  public int margin(double fs) {
    int margin = 0;
    if (baseline) {
      margin += (int) Math.ceil((BASELINE_SHORT_S + BASELINE_LONG_S) / 2 * fs) + 1;
    }
    if (lowPassHz > 0) {
      margin += lowPassTaps(fs) / 2;
    }
    if (notchHz > 0) {
      margin += notchLength(fs) / 2 + 1;
    }
    return margin;
  }

  int lowPassTaps(double fs) {
    int taps = (int) Math.round(4 * fs / lowPassHz);
    taps = Math.max(5, Math.min(taps, 801));
    return taps % 2 == 0 ? taps + 1 : taps;
  }

  int notchLength(double fs) {
    return (int) Math.round(fs / notchHz);
  }

  /** Filters {@code values[0..n)} in place, sampled at {@code fs} Hz. */
  public void apply(double[] values, int n, double fs) {
    if (isIdentity() || n <= 0 || fs <= 0) {
      return;
    }
    if (notchHz > 0) {
      int length = notchLength(fs);
      if (length >= 2) {
        movingAverage(values, n, length, values);
      }
    }
    if (lowPassHz > 0 && lowPassHz < fs / 2) {
      lowPass(values, n, fs);
    }
    if (baseline) {
      double[] estimate = new double[n];
      movingAverage(values, n, (int) Math.max(1, Math.round(BASELINE_SHORT_S * fs)), estimate);
      movingAverage(estimate, n, (int) Math.max(1, Math.round(BASELINE_LONG_S * fs)), estimate);
      for (int i = 0; i < n; i++) {
        values[i] -= estimate[i];
      }
    }
  }

  /** Centred moving average of {@code length} samples; the window shrinks at the ends. */
  static void movingAverage(double[] in, int n, int length, double[] out) {
    double[] prefix = new double[n + 1];
    for (int i = 0; i < n; i++) {
      prefix[i + 1] = prefix[i] + in[i];
    }
    int before = (length - 1) / 2;
    int after = length - 1 - before;
    for (int i = 0; i < n; i++) {
      int from = Math.max(0, i - before);
      int to = Math.min(n - 1, i + after);
      out[i] = (prefix[to + 1] - prefix[from]) / (to - from + 1);
    }
  }

  private void lowPass(double[] values, int n, double fs) {
    int taps = lowPassTaps(fs);
    int half = taps / 2;
    double[] kernel = new double[taps];
    double fc = lowPassHz / fs;
    double sum = 0;
    for (int i = 0; i < taps; i++) {
      int k = i - half;
      double sinc = k == 0 ? 2 * fc : Math.sin(2 * Math.PI * fc * k) / (Math.PI * k);
      double window = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (taps - 1));
      kernel[i] = sinc * window;
      sum += kernel[i];
    }
    for (int i = 0; i < taps; i++) {
      kernel[i] /= sum;
    }
    double[] source = values.clone();
    for (int i = 0; i < n; i++) {
      double acc = 0;
      for (int j = 0; j < taps; j++) {
        int index = i + j - half;
        if (index < 0) {
          index = 0;
        } else if (index >= n) {
          index = n - 1;
        }
        acc += kernel[j] * source[index];
      }
      values[i] = acc;
    }
  }
}
