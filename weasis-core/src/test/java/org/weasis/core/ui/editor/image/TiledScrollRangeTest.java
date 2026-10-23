/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class TiledScrollRangeTest {

  @Test
  void the_last_position_puts_the_last_image_on_the_last_tile() {
    // 173 images on 2x2 tiles: position 170 shows images 170 to 173
    int max = ImageViewerEventManager.tiledScrollMax(173, 4);
    assertEquals(170, max);
    assertEquals(173, max + 4 - 1);
  }

  @Test
  void a_series_filling_the_tiles_exactly_does_not_scroll() {
    assertEquals(1, ImageViewerEventManager.tiledScrollMax(4, 4));
  }

  @Test
  void a_series_shorter_than_the_tiles_keeps_one_position() {
    assertEquals(1, ImageViewerEventManager.tiledScrollMax(2, 4));
    assertEquals(1, ImageViewerEventManager.tiledScrollMax(0, 4));
  }
}
