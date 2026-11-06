/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.model;

import java.awt.Color;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * One pyramid of a whole slide series: its levels from the base (largest) to the top, and the
 * thumbnail, overview and label images of the series. All coordinates are base level pixels.
 */
public final class Slide {

  /** Pixel size at 1×, by the usual convention that 0.25 µm per pixel is a 40× scan. */
  private static final double SPACING_AT_1X_MM = 0.01;

  /** Relative slack on a level downsample, which rounding of the matrix sizes moves off 2ⁿ. */
  private static final double DOWNSAMPLE_TOLERANCE = 1.01;

  private final String pyramidUID;
  private final List<PyramidLevel> levels;
  private final Map<WsiImageType, AssociatedImage> associatedImages;
  private final Double objectiveLensPower;
  private final Color background;

  Slide(
      String pyramidUID,
      List<PyramidLevel> levels,
      Collection<AssociatedImage> associatedImages,
      Double objectiveLensPower,
      Color background) {
    if (levels.isEmpty()) {
      throw new IllegalArgumentException("A slide needs at least one level");
    }
    this.pyramidUID = pyramidUID;
    this.levels = List.copyOf(levels);
    this.objectiveLensPower = objectiveLensPower;
    this.background = background;
    Map<WsiImageType, AssociatedImage> map = new EnumMap<>(WsiImageType.class);
    associatedImages.forEach(a -> map.putIfAbsent(a.type(), a));
    this.associatedImages = map;
  }

  /** The colour of the slide where no tile covers it, also the colour of empty tiles. */
  public Color background() {
    return background;
  }

  public Optional<String> pyramidUID() {
    return Optional.ofNullable(pyramidUID);
  }

  public List<PyramidLevel> levels() {
    return levels;
  }

  public PyramidLevel base() {
    return levels.getFirst();
  }

  public int width() {
    return base().totalColumns();
  }

  public int height() {
    return base().totalRows();
  }

  public Optional<AssociatedImage> associatedImage(WsiImageType type) {
    return Optional.ofNullable(associatedImages.get(type));
  }

  /** Objective Lens Power (0048,0112) of the optical path. */
  public OptionalDouble objectiveLensPower() {
    return objectiveLensPower == null
        ? OptionalDouble.empty()
        : OptionalDouble.of(objectiveLensPower);
  }

  /**
   * Nominal magnification of the base level: the objective power, else estimated from the spacing.
   */
  public OptionalDouble magnification() {
    if (objectiveLensPower != null) {
      return OptionalDouble.of(objectiveLensPower);
    }
    return base()
        .pixelSpacing()
        .map(s -> OptionalDouble.of(SPACING_AT_1X_MM / s.column()))
        .orElseGet(OptionalDouble::empty);
  }

  /**
   * The coarsest level that still has at least the requested resolution, the base level when none
   * does.
   *
   * @param downsample base level pixels per screen pixel
   */
  public PyramidLevel levelForDownsample(double downsample) {
    for (PyramidLevel level : levels.reversed()) {
      if (level.downsampleX() <= downsample * DOWNSAMPLE_TOLERANCE) {
        return level;
      }
    }
    return base();
  }

  @Override
  public String toString() {
    return "Slide %dx%d, %d levels".formatted(width(), height(), levels.size());
  }
}
