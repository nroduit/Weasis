/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi;

import java.util.Arrays;
import java.util.Optional;
import org.weasis.core.api.setting.Setting;
import org.weasis.core.api.setting.SettingType;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.SystemMemory;
import org.weasis.core.ui.editor.image.SmoothZoomAnimator;

/** The settings of the slide viewer: how it zooms and pans, and how the tiles are loaded. */
public final class WsiSettings {

  private static final String GROUP = "weasis-dicom-wsi"; // NON-NLS

  /** The objective magnifications of the presets when the setting cannot be read. */
  public static final String DEFAULT_PRESETS = "1.25, 2.5, 5, 10, 20, 40"; // NON-NLS

  /** Most magnification presets: the toolbar list and the keys 1 to 9. */
  public static final int MAX_PRESETS = 9;

  private static final double MAX_MAGNIFICATION = 1000;

  /** Share of the native budget the tiles may take when no budget is set. */
  private static final double TILE_BUDGET_SHARE = 0.25;

  /** Whether the wheel, the double click and the presets zoom through an animation. */
  public static final Setting<Boolean> ZOOM_ANIMATION =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.wsi.zoom.animation", true) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Animate the zoom of the wheel, the double click and the presets")
          .declare();

  /** Zoom of one wheel notch, in percent of the scale. */
  public static final Setting<Integer> ZOOM_STEP =
      Setting.builder(SettingType.INTEGER, GROUP, "weasis.wsi.zoom.step", 15) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Zoom of one wheel notch, in percent")
          .declare();

  /** Whether the wheel and the double click zoom around the pointer rather than the center. */
  public static final Setting<Boolean> ZOOM_AT_CURSOR =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.wsi.zoom.cursor", true) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Zoom with the mouse around the pointer")
          .declare();

  /** Whether a wheel zoom ending near a native magnification of the slide snaps to it. */
  public static final Setting<Boolean> ZOOM_SNAP =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.wsi.zoom.snap", true) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Snap the wheel zoom to the native magnifications of the slide")
          .declare();

  /** Objective magnifications of the presets, comma separated, ascending, up to nine. */
  public static final Setting<String> ZOOM_PRESETS =
      Setting.builder(
              SettingType.STRING, GROUP, "weasis.wsi.zoom.presets", DEFAULT_PRESETS) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Magnification presets of the toolbar and of the keys 1 to 9")
          .declare();

  /** Whether a pan released with some speed glides on. */
  public static final Setting<Boolean> PAN_INERTIA =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.wsi.pan.inertia", true) // NON-NLS
          .since("5.0") // NON-NLS
          .description("A pan released with some speed glides on")
          .declare();

  /** Memory of the decoded tiles in bytes; 0 for a quarter of the native budget. */
  public static final Setting<Long> TILE_MEMORY =
      Setting.builder(SettingType.LONG, GROUP, "weasis.wsi.tile.memory", 0L) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Memory of the decoded tiles in bytes, 0 for a quarter of the native budget")
          .declare();

  /** Most tiles of a view read at once; 0 for the processors, between 2 and 8. */
  public static final Setting<Integer> TILE_THREADS =
      Setting.builder(SettingType.INTEGER, GROUP, "weasis.wsi.tile.threads", 0) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Most tiles of a view decoded at once, 0 for the number of processors")
          .declare();

  /** Most bulk annotation items painted in a view at once. */
  public static final Setting<Integer> ANNOTATIONS_MAX =
      Setting.builder(SettingType.INTEGER, GROUP, "weasis.wsi.annotations.max", 20000) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Most bulk annotation items drawn in a slide view at once")
          .declare();

  /** Whether a slide view shows the overview of the slide with the area in view. */
  public static final Setting<Boolean> OVERVIEW =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.wsi.overview", true) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Show the overview of the slide in a slide view")
          .declare();

  /** Whether a slide view shows the level, the tiles and the paint time of what it draws. */
  public static final Setting<Boolean> RENDERING_INFO =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.wsi.rendering.info", false) // NON-NLS
          .since("5.0") // NON-NLS
          .description("Show the pyramid level, the tiles and the paint time in a slide view")
          .declare();

  private WsiSettings() {}

  /** The zoom factor of one wheel notch. */
  public static double wheelStep() {
    return 1.0 + Math.clamp(Settings.get(ZOOM_STEP), 1, 400) / 100.0;
  }

  /** The length of a zoom animation in seconds, zero when the zoom is not animated. */
  public static double zoomDuration() {
    return Settings.get(ZOOM_ANIMATION) ? SmoothZoomAnimator.DURATION_SECONDS : 0.0;
  }

  /** The magnification presets, ascending; the built-in ones when the setting is not valid. */
  public static double[] presets() {
    return parsePresets(Settings.get(ZOOM_PRESETS))
        .orElseGet(() -> parsePresets(DEFAULT_PRESETS).orElseThrow());
  }

  /**
   * @return the magnifications of a comma separated list, ascending without repeats; empty when one
   *     is not a number, not within (0, 1000], or when there are none or more than nine
   */
  public static Optional<double[]> parsePresets(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    String[] parts = text.split(",");
    if (parts.length > MAX_PRESETS) {
      return Optional.empty();
    }
    double[] values = new double[parts.length];
    for (int i = 0; i < parts.length; i++) {
      try {
        values[i] = Double.parseDouble(parts[i].trim());
      } catch (NumberFormatException e) {
        return Optional.empty();
      }
      if (!(values[i] > 0 && values[i] <= MAX_MAGNIFICATION)) {
        return Optional.empty();
      }
    }
    double[] sorted = Arrays.stream(values).distinct().sorted().toArray();
    return Optional.of(sorted);
  }

  /** The tile memory budget in bytes. */
  public static long tileMemory() {
    long configured = Settings.get(TILE_MEMORY);
    return configured > 0
        ? configured
        : (long) (SystemMemory.getNativeMemoryBudget() * TILE_BUDGET_SHARE);
  }

  /** The most tiles of a view read at once. */
  public static int tileThreads() {
    int configured = Settings.get(TILE_THREADS);
    return configured > 0
        ? Math.min(configured, 64)
        : Math.clamp(Runtime.getRuntime().availableProcessors(), 2, 8);
  }
}
