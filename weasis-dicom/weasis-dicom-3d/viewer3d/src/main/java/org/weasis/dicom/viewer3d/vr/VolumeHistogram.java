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
import org.opencv.core.Mat;
import org.weasis.core.api.image.util.ValueHistogram;
import org.weasis.core.api.image.util.ValueHistogram.Bins;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.opencv.data.PlanarImage;

/** Value histograms of a volume, sampled on a subset of its axial slices and cached per volume. */
/** Histogram of a volume, sampled over a few axial slices and cached per volume. */
public final class VolumeHistogram {

  /** Slices sampled at most; enough for the shape, cheap enough to compute on demand. */
  static final int SLICES = 48;

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
                  ValueHistogram.FULL_RANGE_BINS));
    }
  }

  public static Bins of(Volume<?, ?> volume, double min, double max, int bins) {
    double[] counts = new double[bins];
    int slices = volume.getSizeZ();
    int stride = Math.max(1, slices / SLICES);
    for (int z = 0; z < slices; z += stride) {
      PlanarImage slice = volume.getAxialSlice(z);
      if (slice == null) {
        continue;
      }
      try {
        Mat mat = slice.toMat();
        if (!mat.empty() && mat.channels() == 1) {
          ValueHistogram.accumulate(mat, min, max, counts);
        }
      } finally {
        slice.release();
      }
    }
    return new Bins(counts, min, max);
  }
}
