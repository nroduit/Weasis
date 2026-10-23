/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_ENTROPY;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_KURTOSIS;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MAX;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MEAN;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MEDIAN;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MIN;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_PIXELS;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_SKEW;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_STD;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_SUM;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;

/**
 * Tests the {@link ImageRegionStatistics#medianBin(float[], double)} histogram-median kernel and
 * the {@link ImageRegionStatistics#getStatistics} distribution measures computed from a histogram.
 *
 * <p>These produce the numbers displayed next to ROI statistics; an off-by-one or biased
 * interpolation, or a degenerate histogram, directly distorts values a clinician acts on.
 */
class ImageRegionStatisticsTest {

  private static final double EPS = 1.0e-9;

  /**
   * Runs {@link ImageRegionStatistics#getStatistics} over a synthetic histogram with an identity +
   * {@code intercept} modality LUT, returning the emitted measures keyed by measurement.
   */
  private static Map<Measurement, Double> statsFor(
      float[] bins, double pixMin, double pixMax, double intercept) {
    MeasurableLayer layer = mock(MeasurableLayer.class);
    when(layer.hasContent()).thenReturn(true);
    when(layer.getPixelValueUnit()).thenReturn("HU"); // NON-NLS
    when(layer.getSourceTagValue(any())).thenReturn(null);
    when(layer.pixelToRealValue(any()))
        .thenAnswer(inv -> ((Number) inv.getArgument(0)).doubleValue() + intercept);
    HistogramData data =
        new HistogramData(bins, null, 0, HistogramData.Model.GRAY, null, pixMin, pixMax, layer);
    Map<Measurement, Double> map = new HashMap<>();
    for (MeasureItem item : ImageRegionStatistics.getStatistics(data, null, true)) {
      map.put(item.getMeasurement(), ((Number) item.getValue()).doubleValue());
    }
    return map;
  }

  @Test
  void getStatistics_distributedHistogramProducesExpectedMeasures() {
    // Symmetric distribution over stored levels 0..6 (binFactor 1), identity modality LUT.
    // sum=16; mean=48/16=3; m2=40 -> var=40/15, std=sqrt(40/15); median (8th and 9th entries)=3.
    Map<Measurement, Double> s = statsFor(new float[] {1, 2, 3, 4, 3, 2, 1}, 0.0, 6.0, 0.0);
    assertEquals(16.0, s.get(IMAGE_PIXELS), EPS);
    assertEquals(0.0, s.get(IMAGE_MIN), EPS);
    assertEquals(6.0, s.get(IMAGE_MAX), EPS);
    assertEquals(3.0, s.get(IMAGE_MEAN), EPS);
    assertEquals(3.0, s.get(IMAGE_MEDIAN), EPS);
    assertEquals(Math.sqrt(40.0 / 15.0), s.get(IMAGE_STD), 1.0e-6);
    assertEquals(0.0, s.get(IMAGE_SKEW), EPS); // symmetric -> zero skew
    assertTrue(Double.isFinite(s.get(IMAGE_KURTOSIS)));
    assertTrue(s.get(IMAGE_ENTROPY) > 0.0); // a spread-out histogram carries information
  }

  @Test
  void getStatistics_appliesModalityInterceptToRealValues() {
    // Same shape, CT intercept -1024: every real value shifts by -1024.
    Map<Measurement, Double> s = statsFor(new float[] {1, 2, 3, 4, 3, 2, 1}, 0.0, 6.0, -1024.0);
    assertEquals(-1024.0, s.get(IMAGE_MIN), EPS);
    assertEquals(-1018.0, s.get(IMAGE_MAX), EPS);
    assertEquals(-1021.0, s.get(IMAGE_MEAN), EPS);
    assertEquals(-1021.0, s.get(IMAGE_MEDIAN), EPS);
  }

  @Test
  void getStatistics_sumIsTheTotalOfTheRealValues() {
    // Levels 0..6 weighted {1,2,3,4,3,2,1}: 48 stored, 16 pixels; the intercept applies per pixel.
    assertEquals(
        48.0, statsFor(new float[] {1, 2, 3, 4, 3, 2, 1}, 0.0, 6.0, 0.0).get(IMAGE_SUM), EPS);
    assertEquals(
        48.0 + 16 * 10.0,
        statsFor(new float[] {1, 2, 3, 4, 3, 2, 1}, 0.0, 6.0, 10.0).get(IMAGE_SUM),
        EPS);
  }

  @Test
  void getStatistics_singleBinHistogramStaysFiniteAndUniform() {
    // Regression guard: a 1-bin histogram (uniform ROI, or the OpenCV-5 read-only-bin-0 bug) must
    // NOT divide by (bins.length-1)==0 and yield an infinite median. All mass sits at one level.
    Map<Measurement, Double> s = statsFor(new float[] {20}, 5.0, 5.0, 0.0);
    assertEquals(20.0, s.get(IMAGE_PIXELS), EPS);
    assertEquals(5.0, s.get(IMAGE_MIN), EPS);
    assertEquals(5.0, s.get(IMAGE_MAX), EPS);
    assertEquals(5.0, s.get(IMAGE_MEAN), EPS);
    assertEquals(5.0, s.get(IMAGE_MEDIAN), EPS); // finite, not Infinity
    assertTrue(Double.isFinite(s.get(IMAGE_MEDIAN)));
    assertEquals(0.0, s.get(IMAGE_STD), EPS);
    assertEquals(0.0, s.get(IMAGE_SKEW), EPS);
    assertEquals(0.0, s.get(IMAGE_KURTOSIS), EPS);
    assertEquals(0.0, s.get(IMAGE_ENTROPY), EPS);
  }

  @Test
  void medianBin_nullArrayReturnsZero() {
    assertEquals(0.0, ImageRegionStatistics.medianBin(null, 5.0), EPS);
  }

  @Test
  void medianBin_emptyArrayReturnsZero() {
    assertEquals(0.0, ImageRegionStatistics.medianBin(new float[0], 5.0), EPS);
  }

  @Test
  void medianBin_singleBinIsThatBin() {
    // A uniform region (e.g. all zero) has its median at its only level, not half a bin above.
    assertEquals(0.0, ImageRegionStatistics.medianBin(new float[] {10.0f}, 5.0), EPS);
  }

  @Test
  void medianBin_oddCountTakesTheMiddleEntry() {
    // [3, 1, 3], 7 entries: the 4th is in bin 1.
    assertEquals(1.0, ImageRegionStatistics.medianBin(new float[] {3.0f, 1.0f, 3.0f}, 3.5), EPS);
  }

  @Test
  void medianBin_evenCountSplitBetweenTwoBinsAveragesThem() {
    // [5, 5]: the 5th entry is in bin 0, the 6th in bin 1.
    assertEquals(0.5, ImageRegionStatistics.medianBin(new float[] {5.0f, 5.0f}, 5.0), EPS);
  }

  @Test
  void medianBin_symmetricHistogramReturnsCenter() {
    // [1,2,3,4,3,2,1], 16 entries: the 8th and 9th are both in bin 3, the center.
    assertEquals(3.0, ImageRegionStatistics.medianBin(new float[] {1, 2, 3, 4, 3, 2, 1}, 8.0), EPS);
  }

  @Test
  void medianBin_asymmetricHistogramShiftsToHeavySide() {
    // Heavy mass in bin 0: 10 entries; bin 1 has 2. The 6th and 7th entries are in bin 0.
    assertEquals(0.0, ImageRegionStatistics.medianBin(new float[] {10.0f, 2.0f}, 6.0), EPS);
  }

  @Test
  void medianBin_leadingZeroBinsAreSkipped() {
    // [0, 0, 10]: every entry is in bin 2.
    assertEquals(2.0, ImageRegionStatistics.medianBin(new float[] {0, 0, 10.0f}, 5.0), EPS);
  }

  @Test
  void medianBin_takesTheMiddleEntriesAcrossEmptyBins() {
    // Even count split in two clusters: the mean of the 4th (bin 1) and 5th (bin 5) entries.
    // Odd count: the 5th entry (bin 1), whatever the empty bins after it.
    float[] bin = {0, 4, 0, 0, 0, 4, 0};
    assertEquals(3.0, ImageRegionStatistics.medianBin(bin, 4.0), EPS);
    assertEquals(1.0, ImageRegionStatistics.medianBin(new float[] {0, 5, 0, 4}, 4.5), EPS);
  }

  @Test
  void medianBin_halfEntriesBeyondTotalMassReturnsZeroFallback() {
    // [1, 1], total=2, halfEntries=10: never reached → fallthrough returns 0.
    // This is the documented escape path; a clinician sees a 0 median rather than NaN.
    assertEquals(0.0, ImageRegionStatistics.medianBin(new float[] {1.0f, 1.0f}, 10.0), EPS);
  }

  @Test
  void medianBin_zeroHalfEntriesReturnsZero() {
    // halfEntries=0: the first entry, in bin 0.
    assertEquals(0.0, ImageRegionStatistics.medianBin(new float[] {10.0f}, 0.0), EPS);
  }

  @Test
  void medianBin_zeroMassBinDoesNotDivideByZero() {
    // [5, 0, 5]: the 5th entry is in bin 0, the 6th in bin 2; the empty bin adds nothing.
    assertEquals(1.0, ImageRegionStatistics.medianBin(new float[] {5.0f, 0.0f, 5.0f}, 5.0), EPS);
  }

  @Test
  void medianBin_realisticHistogramMatchesExpectedPercentile() {
    // CT-bone-window-like distribution: small left tail, peak in the middle, light right tail.
    // Total mass = 100, halfEntries=50.
    // Cumulative: 5, 15, 35, 65, 85, 95, 100
    // The 50th and 51st entries are both in bin 3.
    float[] bin = {5, 10, 20, 30, 20, 10, 5};
    assertEquals(3.0, ImageRegionStatistics.medianBin(bin, 50.0), EPS);
  }
}
