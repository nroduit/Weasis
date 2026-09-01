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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.opencv.op.lut.colormap.ColorMap;

@DisplayNameGeneration(ReplaceUnderscores.class)
class VolumePresetHostTest {

  @Test
  void values_are_binned_over_the_range_and_outsiders_ignored() {
    double[] counts = new double[4];

    VolumeHistogram.binValues(
        new float[] {-1000f, -1000f, -500f, -1f, 0f, 999f, 1000f, 1001f, -1001f},
        -1000,
        1000,
        counts);

    assertArrayEquals(new double[] {2, 2, 1, 2}, counts);
  }

  @Test
  void rebinning_spreads_counts_over_the_overlapping_target_bins() {
    VolumeHistogram.Bins full = new VolumeHistogram.Bins(new double[] {4, 8, 0, 2}, 0, 4);

    VolumeHistogram.Bins narrower = full.rebin(1, 3, 4);
    VolumeHistogram.Bins wider = full.rebin(-4, 4, 2);
    VolumeHistogram.Bins outside = full.rebin(10, 12, 2);

    assertAll(
        () -> assertArrayEquals(new double[] {4, 4, 0, 0}, narrower.counts(), 1e-9),
        () -> assertArrayEquals(new double[] {0, 14}, wider.counts(), 1e-9),
        () -> assertArrayEquals(new double[] {0, 0}, outside.counts(), 1e-9),
        () -> assertEquals(14, wider.total(), 1e-9, "no count lost inside the range"));
  }

  @Test
  void a_relative_map_spans_the_modality_range_instead_of_one_texel() {
    ColorMap rainbow =
        ColorMap.builder("Rainbow")
            .modalities("CT")
            .stop(0, java.awt.Color.BLACK)
            .stop(1, java.awt.Color.WHITE)
            .build();

    Preset preset = Preset.of(rainbow, true);

    assertAll(
        () -> assertEquals(-1024, preset.getColorMin()),
        () -> assertEquals(3071, preset.getColorMax()),
        () -> assertTrue(preset.getWidth() > 1),
        () -> assertEquals("HU", preset.toColorMap().domain().unit()));
  }

  @Test
  void preview_presets_are_flagged_and_listed_ones_are_not() {
    ColorMap map = Preset.basicPresets.getFirst().toColorMap();
    assertAll(
        () -> assertTrue(Preset.preview(map).isPreview()),
        () -> assertTrue(Preset.preview(map).isCustom()),
        () -> assertFalse(Preset.of(map, true).isPreview()));
  }
}
