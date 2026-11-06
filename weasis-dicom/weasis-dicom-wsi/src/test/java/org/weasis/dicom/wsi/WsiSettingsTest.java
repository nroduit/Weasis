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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.editor.image.SmoothZoomAnimator;

@DisplayNameGeneration(ReplaceUnderscores.class)
class WsiSettingsTest {

  @Test
  void presets_are_parsed_ascending_without_repeats() {
    assertAll(
        () ->
            assertArrayEquals(
                new double[] {1.25, 2.5, 5, 10, 20, 40},
                WsiSettings.parsePresets(WsiSettings.DEFAULT_PRESETS).orElseThrow()),
        () ->
            assertArrayEquals(
                new double[] {2, 4, 10}, WsiSettings.parsePresets(" 10,2 , 4, 4").orElseThrow()),
        () -> assertTrue(WsiSettings.parsePresets("").isEmpty()),
        () -> assertTrue(WsiSettings.parsePresets("a, 2").isEmpty(), "not a number"),
        () -> assertTrue(WsiSettings.parsePresets("0, 2").isEmpty(), "not positive"),
        () -> assertTrue(WsiSettings.parsePresets("2, 1001").isEmpty(), "beyond 1000x"),
        () -> assertTrue(WsiSettings.parsePresets("1,2,3,4,5,6,7,8,9,10").isEmpty(), "ten"));
  }

  @Test
  void the_defaults_are_the_built_in_interaction() {
    assertAll(
        () -> assertArrayEquals(new double[] {1.25, 2.5, 5, 10, 20, 40}, WsiSettings.presets()),
        () -> assertEquals(SmoothZoomAnimator.STEP, WsiSettings.wheelStep(), 1e-9),
        () -> assertEquals(SmoothZoomAnimator.DURATION_SECONDS, WsiSettings.zoomDuration(), 1e-9),
        () -> assertTrue(WsiSettings.tileThreads() >= 2 && WsiSettings.tileThreads() <= 8),
        () -> assertTrue(WsiSettings.tileMemory() > 0));
  }
}
