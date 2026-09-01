/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import java.util.Map;
import java.util.WeakHashMap;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.opencv.data.PlanarImage;

/** Value histograms of a volume, sampled on a subset of its axial slices and cached per volume. */
public final class VolumeHistogram {

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

  /** Slices sampled at most; enough for the shape, cheap enough to compute on demand. */
  static final int SLICES = 48;

  /** Bins of the cached full-range histogram: fine enough to re-bin any editor range from it. */
  static final int FULL_RANGE_BINS = 4096;

  private static final Map<Volume<?, ?>, Bins> FULL_RANGE = new WeakHashMap<>();

  private VolumeHistogram() {}

  /** Histogram over the whole value range of the volume, computed once per volume. */
  public static Bins fullRange(Volume<?, ?> volume) {
    synchronized (FULL_RANGE) {
      return FULL_RANGE.computeIfAbsent(
          volume,
          v ->
              of(
                  v,
                  v.getMinimumAsDouble(),
                  Math.max(v.getMaximumAsDouble(), v.getMinimumAsDouble() + 1),
                  FULL_RANGE_BINS));
    }
  }

  public static Bins of(Volume<?, ?> volume, double min, double max, int bins) {
    double[] counts = new double[bins];
    int slices = volume.getSizeZ();
    int stride = Math.max(1, slices / SLICES);
    Mat asFloat = new Mat();
    try {
      for (int z = 0; z < slices; z += stride) {
        PlanarImage slice = volume.getAxialSlice(z);
        if (slice == null) {
          continue;
        }
        try {
          Mat mat = slice.toMat();
          if (mat.empty() || mat.channels() != 1) {
            continue;
          }
          mat.convertTo(asFloat, CvType.CV_32F);
          float[] values = new float[(int) asFloat.total()];
          asFloat.get(0, 0, values);
          binValues(values, min, max, counts);
        } finally {
          slice.release();
        }
      }
    } finally {
      asFloat.release();
    }
    return new Bins(counts, min, max);
  }

  /** Adds each value to the bin covering it; values outside {@code [min, max]} are ignored. */
  static void binValues(float[] values, double min, double max, double[] counts) {
    int bins = counts.length;
    double scale = bins / (max - min);
    for (float v : values) {
      if (v >= min && v <= max) {
        counts[Math.min(bins - 1, (int) ((v - min) * scale))]++;
      }
    }
  }
}
