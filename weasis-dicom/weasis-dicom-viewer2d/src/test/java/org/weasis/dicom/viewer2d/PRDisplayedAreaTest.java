/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.geom.Rectangle2D;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * The Specified Displayed Area runs from the first to the last displayed pixel, from 1\1 (PS3.3
 * C.10.4), in PIXEL units where 0\0 is the corner of the first pixel.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PRDisplayedAreaTest {

  private static Rectangle2D area(int[] tlhc, int[] brhc) {
    return PRManager.getSpecifiedDisplayedArea(tlhc, brhc, 1.0, 1.0);
  }

  @Test
  void whole_image_is_the_whole_pixel_matrix() {
    assertEquals(
        new Rectangle2D.Double(0, 0, 512, 256), area(new int[] {1, 1}, new int[] {512, 256}));
  }

  @Test
  void first_and_last_pixels_are_displayed() {
    // Columns 11 to 20 and rows 31 to 40: ten pixels each way
    assertEquals(
        new Rectangle2D.Double(10, 30, 10, 10), area(new int[] {11, 31}, new int[] {20, 40}));
  }

  @Test
  void corners_swapped_by_the_rotation_give_the_same_area() {
    // With 90 degrees, the top left after rotation is the first column of the last row
    assertEquals(
        new Rectangle2D.Double(0, 0, 512, 256), area(new int[] {1, 256}, new int[] {512, 1}));
  }

  @Test
  void area_may_extend_beyond_the_image() {
    assertEquals(
        new Rectangle2D.Double(-10, -1, 532, 258), area(new int[] {-9, 0}, new int[] {522, 257}));
  }

  @Test
  void non_square_pixels_follow_the_resampled_image() {
    assertEquals(
        new Rectangle2D.Double(0, 0, 1024, 256),
        PRManager.getSpecifiedDisplayedArea(new int[] {1, 1}, new int[] {512, 256}, 2.0, 1.0));
  }

  @Test
  void missing_corner_gives_no_area() {
    assertNull(area(null, new int[] {512, 256}));
    assertNull(area(new int[] {1}, new int[] {512, 256}));
  }
}
