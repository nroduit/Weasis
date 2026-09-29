/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.ColorLut;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;

@DisplayNameGeneration(ReplaceUnderscores.class)
class WindowOpTest {

  private static final ByteLut FIXED_12_BIT =
      ColorMapCompiler.toByteLut(
          ColorMap.builder("CT")
              .bits(12)
              .domain(ColorMapDomain.fixed("HU", -1000, 2000))
              .stop(-1000, Color.BLACK)
              .stop(2000, Color.WHITE)
              .build());

  @Test
  void lut_helpers_read_the_bits_and_fixed_range_of_the_map_behind_a_table() {
    assertAll(
        () -> assertEquals(12, WindowOp.outputBitsOf(FIXED_12_BIT)),
        () ->
            assertEquals(
                new WindowOp.OutputRange(-1000, 2000),
                WindowOp.outputRangeOf(FIXED_12_BIT).orElseThrow()),
        () -> assertEquals(8, WindowOp.outputBitsOf(ColorLut.GRAY.getByteLut())),
        () -> assertTrue(WindowOp.outputRangeOf(ColorLut.GRAY.getByteLut()).isEmpty()),
        () -> assertEquals(8, WindowOp.outputBitsOf(null)));
  }

  @Test
  void a_presentation_state_palette_sets_the_index_width_and_range() {
    WindowOp op = new WindowOp();
    op.setParam(WindowOp.P_OUTPUT_BITS, 12);
    op.setParam(WindowOp.P_OUTPUT_RANGE, new WindowOp.OutputRange(-1000, 2000));

    op.handleImageOpEvent(
        new ImageOpEvent(
            ImageOpEvent.OpEvent.APPLY_PR,
            null,
            null,
            Map.of(PseudoColorOp.P_LUT, ColorLut.GRAY.getByteLut())));

    assertAll(
        () -> assertEquals(8, op.getParam(WindowOp.P_OUTPUT_BITS)),
        () -> assertNull(op.getParam(WindowOp.P_OUTPUT_RANGE)));
  }

  @Test
  void a_presentation_state_without_palette_keeps_the_current_width() {
    WindowOp op = new WindowOp();
    op.setParam(WindowOp.P_OUTPUT_BITS, 12);

    op.handleImageOpEvent(new ImageOpEvent(ImageOpEvent.OpEvent.APPLY_PR, null, null, Map.of()));

    assertEquals(12, op.getParam(WindowOp.P_OUTPUT_BITS));
  }
}
