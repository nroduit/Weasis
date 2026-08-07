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

import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.Volume;

/**
 * Series-wide display window applied to a fusion overlay before colorization.
 *
 * <p>The bounds are modality-LUT (real) values — the domain both fusion paths sample — and hold for
 * the whole series, so a given activity keeps the same color on every slice. {@link
 * #displayFactor()} converts them to the unit the user reads: SUVbw when the series carries a
 * {@link TagW#SuvFactor}, the raw DICOM pixel value unit otherwise.
 *
 * <p>It is derived from the series and never exposed as a control, as in other PET viewers, so the
 * {@link FusionColorBar} is the only place the resulting scale can be read.
 *
 * @param min the real value mapped to the bottom of the LUT (fully transparent)
 * @param max the real value mapped to the top of the LUT
 * @param displayFactor multiplies a real value to obtain the displayed value
 * @param displayUnit the unit of the displayed values, empty when unknown
 */
public record FusionWindow(double min, double max, double displayFactor, String displayUnit) {

  /**
   * Bottom of the SUVbw scale. Normal blood pool sits around 1, so everything below it is
   * background: leaving it out keeps the anatomy untinted where there is nothing to read.
   */
  public static final double SUV_MIN = 1.0;

  /** Narrowest top of the SUVbw scale: below this the scale mostly amplifies noise. */
  private static final double SUV_TOP_MIN = 4.0;

  /** Widest top: above this everything but excretion sits in the first tenth of the palette. */
  private static final double SUV_TOP_MAX = 30.0;

  private static final String SUV_UNIT = "SUVbw"; // NON-NLS

  public FusionWindow {
    if (displayFactor <= 0.0) {
      displayFactor = 1.0;
    }
    if (max <= min) {
      max = min + 1.0;
    }
    displayUnit = displayUnit == null ? StringUtil.EMPTY_STRING : displayUnit;
  }

  /**
   * Provisional window derived from the middle slice alone, cheap enough for the EDT. Used until
   * {@link #fromVolume} can measure the whole series.
   *
   * @return the window, or {@code null} when the series holds no image
   */
  public static FusionWindow fromSlice(MediaSeries<DicomImageElement> series) {
    DicomImageElement ref =
        series == null ? null : series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    return ref == null ? null : windowFor(ref, ref.getMaxValue(null));
  }

  /**
   * Window measured on the built volume: the upper bound excludes the physiologic outliers that
   * would otherwise own the scale (see {@link FusionWindowEstimator}). Scans the voxels, so it must
   * not run on the EDT.
   *
   * @return the window, or {@code null} when the series holds no image
   */
  public static FusionWindow fromVolume(
      MediaSeries<DicomImageElement> series, Volume<?, ?> volume) {
    DicomImageElement ref =
        series == null ? null : series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    if (ref == null) {
      return null;
    }
    double dataMax =
        volume != null && !volume.isBasic() ? volume.getMaximumAsDouble() : ref.getMaxValue(null);
    return windowFor(ref, FusionWindowEstimator.robustMax(volume, dataMax));
  }

  /**
   * {@value #SUV_MIN} to {@link #suvTop} SUVbw when the SUV factor is known, 0 to {@code dataMax}
   * otherwise. The lower bound is pinned in both cases: activity is a non-negative physical
   * quantity, so the bottom of the scale is background, not the lowest value that happens to occur.
   */
  private static FusionWindow windowFor(DicomImageElement ref, double dataMax) {
    if (ref.getTagValue(TagW.SuvFactor) instanceof Double suvFactor && suvFactor > 0.0) {
      return new FusionWindow(
          SUV_MIN / suvFactor, suvTop(dataMax * suvFactor) / suvFactor, suvFactor, SUV_UNIT);
    }
    return new FusionWindow(0.0, dataMax, 1.0, ref.getPixelValueUnit());
  }

  /**
   * Top of the SUVbw scale: the measured maximum rounded up to a whole SUVbw, so the scale follows
   * the study (a 3 SUV survey and a 12 SUV tumor both use the whole palette) while still reading on
   * round values, and stays within bounds a reader can act on.
   */
  private static double suvTop(double suvMax) {
    return Double.isFinite(suvMax)
        ? Math.clamp(Math.ceil(suvMax), SUV_TOP_MIN, SUV_TOP_MAX)
        : SUV_TOP_MIN;
  }

  /**
   * {@code true} when the displayed values are SUVbw. Anything else — {@code BQML}, {@code CNTS} —
   * depends on injected dose, patient weight and uptake time, so it is not comparable between two
   * acquisitions and carries no meaning a reader can act on.
   */
  public boolean isQuantitative() {
    return SUV_UNIT.equals(displayUnit);
  }

  public double displayMin() {
    return min * displayFactor;
  }

  public double displayMax() {
    return max * displayFactor;
  }
}
