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
        () -> assertTrue(preset.toColorMap().domain().isRelative(), "declared map kept"));
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
