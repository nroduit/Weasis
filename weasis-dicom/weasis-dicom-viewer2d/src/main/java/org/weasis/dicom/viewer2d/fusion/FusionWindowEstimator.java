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

import org.joml.Vector3i;
import org.weasis.dicom.viewer2d.mpr.Volume;

/**
 * Estimates the upper bound of a fusion display window from the voxel distribution.
 *
 * <p>The plain maximum is unusable for PET: physiologic excretion (bladder) and the injection site
 * are several times hotter than any diagnostic structure, so windowing to them pushes marrow,
 * muscle and lesions into the bottom tenth of the color scale. A high percentile of the active
 * voxels discards those few outliers and keeps the scale on the tissue actually being read — the
 * bladder simply saturates at the top of the LUT, which is what other PET viewers show.
 */
public final class FusionWindowEstimator {

  /**
   * Fraction of the active voxels that must fall below the window maximum. Set so the handful of
   * excretion/injection voxels saturate while genuine focal uptake still stays inside the scale.
   */
  private static final double PERCENTILE = 0.995;

  /** Voxels at or below this fraction of the maximum are background, not signal. */
  private static final double BACKGROUND_FRACTION = 0.001;

  private static final int BIN_COUNT = 2048;

  /** Upper bound on sampled voxels; larger volumes are strided down to roughly this many. */
  private static final int MAX_SAMPLES = 4_000_000;

  private FusionWindowEstimator() {}

  /**
   * The percentile-based maximum of {@code volume}, falling back to {@code dataMax} when the volume
   * cannot be sampled. Scans a strided subset of the voxels, so it must not run on the EDT.
   */
  public static double robustMax(Volume<?, ?> volume, double dataMax) {
    if (volume == null || volume.isBasic() || dataMax <= 0.0) {
      return dataMax;
    }
    Vector3i size = volume.getSize();
    if (size == null || size.x <= 0 || size.y <= 0 || size.z <= 0) {
      return dataMax;
    }

    int stride = strideFor((long) size.x * size.y * size.z);
    double background = dataMax * BACKGROUND_FRACTION;
    long[] histogram = new long[BIN_COUNT];
    long total = 0;
    double binWidth = dataMax / BIN_COUNT;

    for (int z = 0; z < size.z; z += stride) {
      for (int y = 0; y < size.y; y += stride) {
        for (int x = 0; x < size.x; x += stride) {
          double v = volume.getNearestDouble(x, y, z, 0);
          if (Double.isNaN(v) || v <= background) {
            continue;
          }
          int bin = Math.min(BIN_COUNT - 1, (int) (v / binWidth));
          histogram[bin]++;
          total++;
        }
      }
    }
    if (total == 0) {
      return dataMax;
    }

    long target = (long) Math.ceil(total * PERCENTILE);
    long cumulated = 0;
    for (int bin = 0; bin < BIN_COUNT; bin++) {
      cumulated += histogram[bin];
      if (cumulated >= target) {
        // Upper edge of the bin holding the percentile.
        return Math.min(dataMax, (bin + 1) * binWidth);
      }
    }
    return dataMax;
  }

  /** Step keeping the sampled voxel count near {@link #MAX_SAMPLES}. */
  private static int strideFor(long voxelCount) {
    int stride = 1;
    while (voxelCount / ((long) stride * stride * stride) > MAX_SAMPLES) {
      stride++;
    }
    return stride;
  }
}
