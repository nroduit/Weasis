/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.tile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weasis.dicom.wsi.model.SyntheticSlides.sparse;
import static org.weasis.dicom.wsi.model.SyntheticSlides.tiledFull;

import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.SlideBuilder;
import org.weasis.dicom.wsi.model.TileId;

/** The tiles planned for a viewport and their loading order. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TilePlanTest {

  /** Four levels of 16, 8, 4 and 2 tiles of 256 per side. */
  private static final Slide SLIDE =
      SlideBuilder.build(
              List.of(
                  tiledFull("L0", 4096, 4096, 256), // NON-NLS
                  tiledFull("L1", 2048, 2048, 256), // NON-NLS
                  tiledFull("L2", 1024, 1024, 256), // NON-NLS
                  tiledFull("L3", 512, 512, 256))) // NON-NLS
          .getFirst();

  private static TilePlan plan(double x, double y, double w, double h, double downsample) {
    return TilePlan.of(SLIDE, new Rectangle2D.Double(x, y, w, h), downsample, 0, 0);
  }

  @Test
  void visible_tiles_start_at_the_centre_and_the_ring_surrounds_them() {
    TilePlan plan = plan(1000, 1000, 1024, 768, 1.0);
    assertAll(
        () -> assertEquals(0, plan.level()),
        () -> assertEquals(20, plan.visible().size(), "columns 3 to 7, rows 3 to 6"),
        () -> assertEquals(TileId.of(0, 5, 5), plan.visible().getFirst()),
        () -> assertEquals(22, plan.ring().size()),
        () -> assertTrue(plan.ring().stream().noneMatch(plan.visible()::contains)),
        () -> assertEquals(10, plan.coarse().size()),
        () -> assertEquals(TileId.of(3, 0, 0), plan.coarse().getFirst(), "top level first"),
        () -> assertEquals(9, plan.coarse().stream().filter(t -> t.level() == 1).count()));
  }

  @Test
  void loading_order_is_coarse_then_visible_then_ring() {
    TilePlan plan = plan(1000, 1000, 1024, 768, 1.0);
    List<TileId> ordered = plan.ordered();
    assertAll(
        () -> assertEquals(52, ordered.size()),
        () -> assertEquals(plan.coarse(), ordered.subList(0, 10)),
        () -> assertEquals(plan.visible(), ordered.subList(10, 30)),
        () -> assertEquals(30, plan.painted().size()));
  }

  @Test
  void the_level_follows_the_downsample_and_the_top_has_no_coarse_tiles() {
    TilePlan level1 = plan(0, 0, 4096, 4096, 3.0);
    TilePlan top = plan(0, 0, 4096, 4096, 100.0);
    assertAll(
        () -> assertEquals(1, level1.level()),
        () -> assertEquals(64, level1.visible().size()),
        () -> assertEquals(20, level1.coarse().size(), "4 top tiles and 16 of level 2"),
        () -> assertEquals(3, top.level()),
        () -> assertEquals(4, top.visible().size()),
        () -> assertTrue(top.coarse().isEmpty()),
        () -> assertTrue(top.ring().isEmpty()));
  }

  @Test
  void tiles_are_clipped_to_the_slide() {
    TilePlan plan = plan(-500, -500, 800, 800, 1.0);
    assertAll(
        () -> assertEquals(4, plan.visible().size()),
        () -> assertEquals(5, plan.ring().size()),
        () -> assertEquals(TilePlan.EMPTY, plan(5000, 5000, 100, 100, 1.0)),
        () -> assertEquals(TilePlan.EMPTY, plan(0, 0, 0, 0, 1.0)));
  }

  @Test
  void zoomed_out_on_a_single_level_the_painted_tiles_are_bounded() {
    Slide single =
        SlideBuilder.build(List.of(tiledFull("S", 65536, 65536, 512))).getFirst(); // NON-NLS
    TilePlan plan = TilePlan.of(single, new Rectangle2D.Double(0, 0, 65536, 65536), 64.0, 0, 0);
    assertAll(
        () -> assertTrue(plan.truncated()),
        () -> assertEquals(256, plan.visible().size(), "64 Mpx of 512 x 512 tiles"),
        () ->
            assertTrue(
                List.of(63, 64).contains(plan.visible().getFirst().column())
                    && List.of(63, 64).contains(plan.visible().getFirst().row()),
                "one of the four tiles at the centre"),
        () -> assertTrue(plan.ring().isEmpty()),
        () -> assertFalse(plan(1000, 1000, 1024, 768, 1.0).truncated()));
  }

  @Test
  void a_coarser_level_is_shown_when_the_visible_tiles_exceed_the_bound() {
    Slide pyramid =
        SlideBuilder.build(
                List.of(
                    tiledFull("P0", 65536, 65536, 512), // NON-NLS
                    tiledFull("P1", 16384, 16384, 512))) // NON-NLS
            .getFirst();
    // A 4K view at downsample 3.9: level 0 would need 30 x 17 tiles of 512, 134 Mpx
    TilePlan plan =
        TilePlan.of(
            pyramid, new Rectangle2D.Double(20000, 20000, 3840 * 3.9, 2160 * 3.9), 3.9, 0, 0);
    assertAll(
        () -> assertEquals(1, plan.level()),
        () -> assertFalse(plan.truncated()),
        () -> assertTrue(plan.coarse().isEmpty(), "level 1 is the top"));
  }

  @Test
  void a_pan_loads_the_ring_ahead_of_the_motion_first() {
    Rectangle2D view = new Rectangle2D.Double(1000, 1000, 1024, 768);
    TilePlan right = TilePlan.of(SLIDE, view, 1.0, 0, 0, new java.awt.geom.Point2D.Double(50, 0));
    TilePlan up = TilePlan.of(SLIDE, view, 1.0, 0, 0, new java.awt.geom.Point2D.Double(0, -50));
    assertAll(
        () -> assertEquals(8, right.ring().getFirst().column(), "the column right of the view"),
        () -> assertEquals(2, up.ring().getFirst().row(), "the row above the view"),
        () -> assertEquals(right.ring().size(), plan(1000, 1000, 1024, 768, 1.0).ring().size()));
  }

  @Test
  void background_tiles_of_a_sparse_level_are_not_planned() {
    Slide slide =
        SlideBuilder.build(
                List.of(
                    sparse(
                        "S",
                        1024,
                        1024,
                        512,
                        new double[] {1, 1},
                        new double[] {513, 513}))) // NON-NLS
            .getFirst();
    TilePlan plan = TilePlan.of(slide, new Rectangle2D.Double(0, 0, 1024, 1024), 1.0, 0, 0);
    assertEquals(
        List.of(TileId.of(0, 0, 0), TileId.of(0, 1, 1)),
        plan.visible().stream().sorted((a, b) -> Integer.compare(a.column(), b.column())).toList());
  }
}
