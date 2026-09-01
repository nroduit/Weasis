/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.lut;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.weasis.opencv.op.lut.ByteLut;

/** What the editor needs from the viewer that opened it. */
public interface ColorMapEditorHost {

  /** Shows {@code lut} on the active view; called while editing when live preview is on. */
  void preview(ByteLut lut);

  /** The registry content changed (a map was saved or deleted): rebuild the LUT menus. */
  void mapsChanged();

  /** DICOM modality of the displayed image, or null; pre-selects the modality filter. */
  default String currentModality() {
    return null;
  }

  /** Whether the host renders volumes, so the editor lists 3D presets first. */
  default boolean isVolume() {
    return false;
  }

  /**
   * The LUT shown on the active view now; read once when the editor opens and restored on close
   * when nothing was saved.
   */
  ByteLut currentLut();

  /** Value span of the displayed data as {@code {min, max}}, to anchor a relative map on it. */
  default Optional<double[]> valueRange() {
    return Optional.empty();
  }

  /** Whether {@link #pickValue} can read a value from the active view. */
  default boolean canPickValue() {
    return false;
  }

  /**
   * Arms a one-shot pick: the next click on the active view hands its real pixel value to {@code
   * consumer}; a right click cancels.
   */
  default void pickValue(Consumer<Double> consumer) {}

  /**
   * Counts of the displayed data in {@code bins} equal bins over {@code [min, max]}, drawn behind
   * the curve so stops can be placed where the tissues are; null when unavailable.
   */
  default double[] histogram(double min, double max, int bins) {
    return null;
  }

  /**
   * File formats offered for import and, for those that can write, export. JSON only by default; a
   * viewer adds the legacy table or DICOM color palettes.
   */
  default List<ColorMapFormat> formats() {
    return List.of(ColorMapFormats.JSON);
  }
}
