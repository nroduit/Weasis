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

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.explorer.pr.PrGraphicUtil;

/**
 * The Specified Displayed Area runs from the first to the last displayed pixel, from 1\1 (PS3.3
 * C.10.4), in PIXEL units where 0\0 is the corner of the first pixel.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PRDisplayedAreaTest {

  private static Rectangle2D area(int[] tlhc, int[] brhc) {
    return PRManager.getSpecifiedDisplayedArea(tlhc, brhc, null);
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
  void volume_corners_are_moved_to_the_frame() {
    // Pixel Origin Interpretation VOLUME: corners in the Total Pixel Matrix, frame at (512, 256)
    assertEquals(
        new Rectangle2D.Double(-12, 9, 100, 50),
        PRManager.getSpecifiedDisplayedArea(
            new int[] {501, 266}, new int[] {600, 315}, new Point(512, 256)));
  }

  @Test
  void missing_corner_gives_no_area() {
    assertNull(area(null, new int[] {512, 256}));
    assertNull(area(new int[] {1}, new int[] {512, 256}));
  }

  private static PRManager.TransformationContext context(
      Rectangle2D area, int rotation, boolean flip, AffineTransform pixelToImage, Point tile) {
    return new PRManager.TransformationContext(
        area.getWidth(),
        area.getHeight(),
        PrGraphicUtil.displayToPixel(area, rotation, flip),
        pixelToImage,
        tile,
        rotation,
        flip);
  }

  @Test
  void text_points_are_converted_by_their_units() {
    Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 50);
    PRManager.TransformationContext c =
        context(area, 0, false, AffineTransform.getScaleInstance(2, 1), new Point(1000, 2000));

    assertEquals(new Point2D.Double(19.5, 9.5), c.toModel(new float[] {10, 10}, "PIXEL"));
    assertEquals(new Point2D.Double(99.5, 24.5), c.toModel(new float[] {0.5f, 0.5f}, "DISPLAY"));
    assertEquals(new Point2D.Double(19.5, 9.5), c.toModel(new float[] {1010, 2010}, "MATRIX"));
    assertNull(c.toModel(null, "PIXEL"));
  }

  @Test
  void text_starts_from_the_box_corner_shown_top_left() {
    Rectangle2D box = new Rectangle2D.Double(10, 20, 40, 30);
    Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 100);
    // Without transformation: the top left corner; centered: the center; right: the top right
    assertEquals(
        new Point2D.Double(10, 20), context(area, 0, false, null, null).alignedPoint(box, 0, 0, 0));
    assertEquals(
        new Point2D.Double(30, 35),
        context(area, 0, false, null, null).alignedPoint(box, 0.5, 0.5, 0));
    assertEquals(
        new Point2D.Double(50, 20), context(area, 0, false, null, null).alignedPoint(box, 1, 0, 0));
    // Turned 90 degrees clockwise, the bottom left corner of the box is shown top left
    Point2D p = context(area, 90, false, null, null).alignedPoint(box, 0, 0, 0);
    assertEquals(10, p.getX(), 1e-9);
    assertEquals(50, p.getY(), 1e-9);
    // Flipped, the top right corner is shown top left
    Point2D q = context(area, 0, true, null, null).alignedPoint(box, 0, 0, 0);
    assertEquals(50, q.getX(), 1e-9);
    assertEquals(20, q.getY(), 1e-9);
  }

  @Test
  void turned_text_starts_from_the_box_corner_it_reads_from() {
    Rectangle2D box = new Rectangle2D.Double(10, 20, 40, 30);
    Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 100);
    PRManager.TransformationContext c = context(area, 0, false, null, null);
    // Upside down, the text starts at the bottom right corner
    assertEquals(new Point2D.Double(50, 50), c.alignedPoint(box, 0, 0, 2));
    // Turned clockwise, it reads down from the top right corner: its width runs along y
    assertEquals(new Point2D.Double(50, 35), c.alignedPoint(box, 0.5, 0, 1));
    // Turned counterclockwise, it reads up from the bottom left corner
    assertEquals(new Point2D.Double(10, 50), c.alignedPoint(box, 0, 0, 3));
  }
}
