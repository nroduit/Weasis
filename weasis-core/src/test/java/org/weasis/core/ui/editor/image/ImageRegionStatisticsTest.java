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

import org.junit.jupiter.api.Test;

/**
 * Tests the {@link ImageRegionStatistics#medianBin(float[], double)} histogram-median kernel.
 *
 * <p>This is the function that produces the IMAGE_MEDIAN measurement displayed next to ROI
 * statistics; an off-by-one or biased interpolation here directly distorts numbers a clinician acts
 * on.
 */
class ImageRegionStatisticsTest {

  private static final double EPS = 1.0e-9;

  @Test
  void medianBin_nullArrayReturnsZero() {
    assertEquals(0.0, ImageRegionStatistics.medianBin(null, 5.0), EPS);
  }

  @Test
  void medianBin_emptyArrayReturnsZero() {
    assertEquals(0.0, ImageRegionStatistics.medianBin(new float[0], 5.0), EPS);
  }

  @Test
  void medianBin_singleBinReturnsLinearInterpolationFraction() {
    // halfEntries lands at 50% of the bin's mass -> fractional position 0.5 within bin 0.
    assertEquals(0.5, ImageRegionStatistics.medianBin(new float[] {10.0f}, 5.0), EPS);
  }

  @Test
  void medianBin_singleBinHalfEntriesAtBinEdgeReturnsFullBin() {
    // halfEntries equals the bin's total mass — interpolation reaches the right edge of bin 0.
    assertEquals(1.0, ImageRegionStatistics.medianBin(new float[] {10.0f}, 10.0), EPS);
  }

  @Test
  void medianBin_twoBinsHalfEntriesExactlyAtBoundary() {
    // [5, 5], halfEntries=5: i=0 sum=5 ≥ 5 → dif=5, frac=5/5=1.0 → returns 1.0
    assertEquals(1.0, ImageRegionStatistics.medianBin(new float[] {5.0f, 5.0f}, 5.0), EPS);
  }

  @Test
  void medianBin_twoBinsHalfEntriesInsideSecondBin() {
    // [5, 5], halfEntries=6: dif=1, frac=1/5=0.2 → returns 1.2
    assertEquals(1.2, ImageRegionStatistics.medianBin(new float[] {5.0f, 5.0f}, 6.0), EPS);
  }

  @Test
  void medianBin_symmetricHistogramReturnsCenter() {
    // [1,2,3,4,3,2,1], total=16, halfEntries=8: cumulative reaches 6 at idx 2, then idx 3 (sum=10).
    // dif = 8 - 6 = 2, frac = 2/4 = 0.5 → returns 3.5 (true center of the symmetric distribution).
    assertEquals(3.5, ImageRegionStatistics.medianBin(new float[] {1, 2, 3, 4, 3, 2, 1}, 8.0), EPS);
  }

  @Test
  void medianBin_asymmetricHistogramShiftsToHeavySide() {
    // Heavy mass in bin 0: 10 entries; bin 1 has 2. halfEntries=6 → bin 0 carries it.
    // dif=6, frac=6/10=0.6 → returns 0.6
    assertEquals(0.6, ImageRegionStatistics.medianBin(new float[] {10.0f, 2.0f}, 6.0), EPS);
  }

  @Test
  void medianBin_leadingZeroBinsAreSkipped() {
    // [0, 0, 10], halfEntries=5 → cumulative is 0,0,10; halfway reached in bin 2.
    // dif = 5 - 0 = 5, frac = 5/10 = 0.5 → returns 2.5
    assertEquals(2.5, ImageRegionStatistics.medianBin(new float[] {0, 0, 10.0f}, 5.0), EPS);
  }

  @Test
  void medianBin_halfEntriesBeyondTotalMassReturnsZeroFallback() {
    // [1, 1], total=2, halfEntries=10: never reached → fallthrough returns 0.
    // This is the documented escape path; a clinician sees a 0 median rather than NaN.
    assertEquals(0.0, ImageRegionStatistics.medianBin(new float[] {1.0f, 1.0f}, 10.0), EPS);
  }

  @Test
  void medianBin_zeroHalfEntriesReturnsZero() {
    // halfEntries=0 satisfies sum>=0 at idx 0; dif=0, frac=0/10=0 → returns 0.
    assertEquals(0.0, ImageRegionStatistics.medianBin(new float[] {10.0f}, 0.0), EPS);
  }

  @Test
  void medianBin_zeroMassBinDoesNotDivideByZero() {
    // Reaching the half-entries point inside a zero-mass bin: code uses frac=0 fallback
    // when bin value is not > 0, avoiding division-by-zero -> NaN propagation.
    // [5, 0, 5], halfEntries=5: i=0 sum=5 ≥ 5 → dif=5, frac=5/5=1.0 → returns 1.0
    assertEquals(1.0, ImageRegionStatistics.medianBin(new float[] {5.0f, 0.0f, 5.0f}, 5.0), EPS);
  }

  @Test
  void medianBin_realisticHistogramMatchesExpectedPercentile() {
    // CT-bone-window-like distribution: small left tail, peak in the middle, light right tail.
    // Total mass = 100, halfEntries=50.
    // Cumulative: 5, 15, 35, 65, 85, 95, 100
    // Half (50) crosses at bin 3: dif=50-35=15, frac=15/30=0.5 → returns 3.5
    float[] bin = {5, 10, 20, 30, 20, 10, 5};
    assertEquals(3.5, ImageRegionStatistics.medianBin(bin, 50.0), EPS);
  }
}
