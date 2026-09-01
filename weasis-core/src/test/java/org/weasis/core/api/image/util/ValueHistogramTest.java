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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.util.ValueHistogram.Bins;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ValueHistogramTest {

  @Test
  void values_are_binned_over_the_range_and_outsiders_ignored() {
    double[] counts = new double[4];

    ValueHistogram.bin(
        new float[] {-1000f, -1000f, -500f, -1f, 0f, 999f, 1000f, 1001f, -1001f},
        -1000,
        1000,
        counts);

    assertArrayEquals(new double[] {2, 2, 1, 2}, counts);
  }

  @Test
  void rebinning_spreads_counts_over_the_overlapping_target_bins() {
    Bins full = new Bins(new double[] {4, 8, 0, 2}, 0, 4);

    Bins narrower = full.rebin(1, 3, 4);
    Bins wider = full.rebin(-4, 4, 2);
    Bins outside = full.rebin(10, 12, 2);

    assertAll(
        () -> assertArrayEquals(new double[] {4, 4, 0, 0}, narrower.counts(), 1e-9),
        () -> assertArrayEquals(new double[] {0, 14}, wider.counts(), 1e-9),
        () -> assertArrayEquals(new double[] {0, 0}, outside.counts(), 1e-9),
        () -> assertEquals(14, wider.total(), 1e-9, "no count lost inside the range"),
        () -> assertEquals(1.25, narrower.center(0), 1e-9));
  }
}
