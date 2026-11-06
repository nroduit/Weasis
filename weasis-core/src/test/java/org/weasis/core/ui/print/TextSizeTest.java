/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class TextSizeTest {

  @Test
  void default_is_small_under_60_mm_and_normal_above() {
    assertAll(
        () -> assertEquals(TextSize.SMALL, TextSize.defaultFor(59.9)),
        () -> assertEquals(TextSize.NORMAL, TextSize.defaultFor(60)),
        () -> assertEquals(TextSize.NORMAL, TextSize.defaultFor(285)));
  }

  @Test
  void scale_prints_the_base_font_at_the_chosen_points() {
    // A 12 px base font on a 96 dpi screen is 9 pt: NORMAL (8 pt) shrinks it by 8/9
    assertAll(
        () -> assertEquals(8.0 / 9.0, TextSize.NORMAL.scaleFor(12), 1e-9),
        () -> assertEquals(1.0, TextSize.LARGE.scaleFor(10 * 96.0 / 72.0), 1e-9),
        () -> assertEquals(0.5, TextSize.SMALL.scaleFor(16), 1e-9),
        () -> assertThrows(IllegalArgumentException.class, () -> TextSize.SMALL.scaleFor(0)));
  }

  @Test
  void height_on_paper() {
    assertAll(
        () -> assertEquals(8 / 72.0 * 25.4, TextSize.NORMAL.heightMm(), 1e-9),
        () -> assertEquals(6, TextSize.SMALL.points()),
        () -> assertEquals(10, TextSize.LARGE.points()));
  }
}
