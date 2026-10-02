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

import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.weasis.opencv.data.PlanarImage;

/** Bin counts of single-channel values, computed once over a full range and re-binned on demand. */
public final class ValueHistogram {

  /** Bins of a full-range histogram: fine enough to re-bin any editor range from it. */
  public static final int FULL_RANGE_BINS = 4096;

  /** Bin counts over {@code [min, max]}. */
  public record Bins(double[] counts, double min, double max) { // NOSONAR never compared
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
    Mat mat = image.toMat();
    if (mat.empty() || mat.channels() != 1) {
      return null;
    }
    double[] counts = new double[bins];
    accumulate(mat, min, max, counts);
    return new Bins(counts, min, max);
  }

  /**
   * Adds the values of a single-channel image to the bins covering {@code [min, max]}; values
   * outside are ignored.
   */
  public static void accumulate(Mat mat, double min, double max, double[] counts) {
    int depth = mat.depth();
    boolean direct = depth == CvType.CV_8U || depth == CvType.CV_16U || depth == CvType.CV_32F;
    Mat values = direct ? mat : new Mat();
    Mat hist = new Mat();
    Mat atMax = new Mat();
    try {
      if (!direct) {
        mat.convertTo(values, CvType.CV_32F);
      }
      Imgproc.calcHist(
          List.of(values),
          new MatOfInt(0),
          new Mat(),
          hist,
          new MatOfInt(counts.length),
          new MatOfFloat((float) min, (float) max));
      float[] binned = new float[counts.length];
      hist.get(0, 0, binned);
      for (int i = 0; i < binned.length; i++) {
        counts[i] += binned[i];
      }
      // The range of calcHist excludes its upper bound, which belongs to the last bin here
      Core.compare(values, new Scalar(max), atMax, Core.CMP_EQ);
      counts[counts.length - 1] += Core.countNonZero(atMax);
    } finally {
      if (!direct) {
        values.release();
      }
      hist.release();
      atMax.release();
    }
  }
}
