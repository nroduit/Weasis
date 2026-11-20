/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weasis.dicom.wsi.model.SyntheticSlides.tiledFull;

import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.SlideBuilder;
import org.weasis.dicom.wsi.model.TileId;
import org.weasis.dicom.wsi.view.SlidePixels.Location;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SlidePixelsTest {

  /** 1024 x 1024 at the base, then 512 and 256, in tiles of 256. */
  private static Slide slide() {
    return SlideBuilder.build(
            List.of(
                tiledFull("L0", 1024, 1024, 256), // NON-NLS
                tiledFull("L1", 512, 512, 256), // NON-NLS
                tiledFull("L2", 256, 256, 256))) // NON-NLS
        .getFirst();
  }

  @Test
  void a_base_pixel_is_found_in_its_tile_of_the_base_level() {
    Location location = SlidePixels.locate(slide(), 0, 0, 0, 700, 300).orElseThrow();

    assertEquals(new TileId(0, 2, 1, 0, 0), location.tile());
    assertEquals(700 - 2 * 256, location.x());
    assertEquals(300 - 256, location.y());
  }

  @Test
  void a_base_pixel_is_found_in_a_coarser_level_at_its_downsampled_place() {
    Location location = SlidePixels.locate(slide(), 1, 0, 0, 700, 300).orElseThrow();

    assertEquals(new TileId(1, 1, 0, 0, 0), location.tile());
    assertEquals(350 - 256, location.x());
    assertEquals(150, location.y());
  }

  @Test
  void the_last_base_pixel_stays_within_the_coarsest_level() {
    Location location = SlidePixels.locate(slide(), 2, 0, 0, 1023.9, 1023.9).orElseThrow();

    assertEquals(new TileId(2, 0, 0, 0, 0), location.tile());
    assertEquals(255, location.x());
    assertEquals(255, location.y());
  }

  @Test
  void a_point_out_of_the_slide_or_a_level_the_slide_has_not_is_not_found() {
    Slide slide = slide();

    assertTrue(SlidePixels.locate(slide, 0, 0, 0, -1, 10).isEmpty());
    assertTrue(SlidePixels.locate(slide, 0, 0, 0, 10, 1024).isEmpty());
    assertTrue(SlidePixels.locate(slide, 3, 0, 0, 10, 10).isEmpty());
    assertTrue(SlidePixels.locate(slide, -1, 0, 0, 10, 10).isEmpty());
  }

  @Test
  void a_region_is_read_at_the_finest_level_that_keeps_it_within_the_budget() {
    Slide slide = slide();
    Rectangle2D region = new Rectangle2D.Double(0, 0, 800, 800);

    assertEquals(0, SlidePixels.levelFor(slide, region, 800 * 800));
    assertEquals(1, SlidePixels.levelFor(slide, region, 400 * 400));
    // Beyond the budget at every level: the coarsest one
    assertEquals(2, SlidePixels.levelFor(slide, region, 10));
  }
}
