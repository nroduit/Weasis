/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.util;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.opencv.data.PlanarImage;

/** Bin counts of single-channel values, computed once over a full range and re-binned on demand. */
public final class ValueHistogram {

  /** Bins of a full-range histogram: fine enough to re-bin any editor range from it. */
  public static final int FULL_RANGE_BINS = 4096;

  /** Bin counts over {@code [min, max]}. */
  public record Bins(double[] counts, double min, double max) {
    public double total() {
      double sum = 0;
      for (double c : counts) {
        sum += c;
      }
      return sum;
    }

    /** Center value of bin {@code i}. */
    public double center(int i) {
      return min + (i + 0.5) * (max - min) / counts.length;
    }

    /**
     * The same counts redistributed over {@code bins} equal bins of {@code [newMin, newMax]}: a
     * source bin spreads evenly across the target bins it overlaps, values outside are dropped.
     */
    public Bins rebin(double newMin, double newMax, int bins) {
      double[] out = new double[bins];
      double srcWidth = (max - min) / counts.length;
      double dstWidth = (newMax - newMin) / bins;
      for (int i = 0; i < counts.length; i++) {
        double count = counts[i];
        if (count == 0) {
          continue;
        }
        double lo = min + i * srcWidth;
        double hi = lo + srcWidth;
        int first = Math.max(0, (int) Math.floor((lo - newMin) / dstWidth));
        int last = Math.min(bins - 1, (int) Math.floor((hi - newMin) / dstWidth));
        for (int j = first; j <= last; j++) {
          double overlap =
              Math.min(hi, newMin + (j + 1) * dstWidth) - Math.max(lo, newMin + j * dstWidth);
          if (overlap > 0) {
            out[j] += count * overlap / srcWidth;
          }
        }
      }
      return new Bins(out, newMin, newMax);
    }
  }

  private ValueHistogram() {}

  /**
   * Counts of the values of a single-channel image over {@code [min, max]}; null for a color image
   * or no image.
   */
  public static Bins of(PlanarImage image, double min, double max, int bins) {
    if (image == null) {
      return null;
    }
    double[] counts = new double[bins];
    Mat mat = image.toMat();
    if (mat.empty() || mat.channels() != 1) {
      return null;
    }
    Mat asFloat = new Mat();
    try {
      mat.convertTo(asFloat, CvType.CV_32F);
      float[] values = new float[(int) asFloat.total()];
      asFloat.get(0, 0, values);
      bin(values, min, max, counts);
    } finally {
      asFloat.release();
    }
    return new Bins(counts, min, max);
  }

  /** Adds each value to the bin covering it; values outside {@code [min, max]} are ignored. */
  public static void bin(float[] values, double min, double max, double[] counts) {
    int bins = counts.length;
    double scale = bins / (max - min);
    for (float v : values) {
      if (v >= min && v <= max) {
        counts[Math.min(bins - 1, (int) ((v - min) * scale))]++;
      }
    }
  }
}
