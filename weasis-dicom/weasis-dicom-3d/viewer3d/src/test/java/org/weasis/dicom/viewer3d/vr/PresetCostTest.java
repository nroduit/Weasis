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

import java.awt.Color;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.util.ValueHistogram;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;
import org.weasis.opencv.op.lut.colormap.GradientOpacity;
import org.weasis.opencv.op.lut.colormap.Lighting;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PresetCostTest {

  /** 100 bins over -1000..1000 HU: 90 % air below -500, 10 % tissue above. */
  private static ValueHistogram.Bins volume() {
    double[] counts = new double[100];
    for (int i = 0; i < 100; i++) {
      counts[i] = i < 25 ? 36 : 10.0 / 75 * 10;
    }
    return new ValueHistogram.Bins(counts, -1000, 1000);
  }

  private static ColorMap map(double transparentBelow, Lighting lighting) {
    return ColorMap.builder("m")
        .domain(ColorMapDomain.absolute("HU", -1000, 1000))
        .stop(-1000, Color.BLACK, 0f)
        .alphaStop(transparentBelow, 0f)
        .alphaStop(transparentBelow, 1f)
        .stop(1000, Color.WHITE)
        .lighting(lighting)
        .build();
  }

  @Test
  void level_follows_the_visible_share_and_the_need_for_a_gradient() {
    PresetCost bone = PresetCost.estimate(map(300, new Lighting(false, 10f)), volume());
    PresetCost tissue = PresetCost.estimate(map(-400, new Lighting(false, 10f)), volume());
    PresetCost all = PresetCost.estimate(map(-1000, new Lighting(false, 10f)), volume());
    PresetCost shaded = PresetCost.estimate(map(300, Lighting.DEFAULT), volume());
    PresetCost edges =
        PresetCost.estimate(
            map(300, new Lighting(false, 10f, GradientOpacity.edgeEmphasis(0.5f))), volume());
    PresetCost heavyShaded = PresetCost.estimate(map(-1000, Lighting.DEFAULT), volume());

    assertAll(
        () -> assertEquals(1, bone.level()),
        () -> assertEquals(0.047, bone.visibleFraction(), 0.005),
        () -> assertEquals(2, tissue.level()),
        () -> assertEquals(3, all.level()),
        () -> assertEquals(2, shaded.level(), "a gradient per visible sample adds a level"),
        () -> assertEquals(2, edges.level()),
        () -> assertEquals(3, heavyShaded.level(), "capped at 3"),
        () -> assertEquals("\u25CF\u25CF\u25CF", all.badge()),
        () ->
            assertEquals(
                0,
                PresetCost.estimate(map(300, null), new ValueHistogram.Bins(new double[4], 0, 1))
                    .visibleFraction()));
  }
}
