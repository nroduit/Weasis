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

import org.weasis.core.api.image.util.ValueHistogram;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapSampler;

/**
 * How heavy a preset is to render on the loaded volume: the share of voxels its alpha curve lets
 * the ray marcher accumulate, plus one level when every visible sample also needs a gradient
 * (shading or edge emphasis).
 *
 * @param level 1 (light) to 3 (heavy)
 * @param visibleFraction share of the volume's voxels with a non-zero opacity, in {@code [0, 1]}
 */
public record PresetCost(int level, double visibleFraction) {

  public static final int MAX_LEVEL = 3;

  /** Below this share of visible voxels the ray marcher mostly skips. */
  static final double LIGHT_FRACTION = 0.05;

  /** Above this share most rays accumulate through the volume. */
  static final double HEAVY_FRACTION = 0.25;

  private static final String DOT = "●";

  /** Cost of {@code map} on a volume with the given full-range histogram. */
  public static PresetCost estimate(ColorMap map, ValueHistogram.Bins bins) {
    double total = bins.total();
    double visible = 0;
    if (total > 0) {
      ColorMapSampler sampler = map.sampler();
      for (int i = 0; i < bins.counts().length; i++) {
        double count = bins.counts()[i];
        if (count > 0 && sampler.sample(bins.center(i)).alpha() > 0f) {
          visible += count;
        }
      }
    }
    double fraction = total > 0 ? visible / total : 0;
    int level = fraction < LIGHT_FRACTION ? 1 : fraction < HEAVY_FRACTION ? 2 : 3;
    boolean gradient =
        map.lighting() != null
            && (map.lighting().shade() || map.lighting().gradientOpacity() != null);
    return new PresetCost(Math.min(MAX_LEVEL, level + (gradient ? 1 : 0)), fraction);
  }

  /** One to three dots. */
  public String badge() {
    return DOT.repeat(level);
  }
}
