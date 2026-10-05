/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.seg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class BinaryMaskOverlayTest {
  @Test
  void preservesEveryPixelOfNonSquareDisconnectedMask() {
    int width = 7;
    int height = 5;
    byte[] pixels = new byte[width * height];
    int[] on = {0, 1, 7, 8, 16, 26, 27, 33};
    for (int i : on) {
      pixels[i] = (byte) 255;
    }
    BufferedImage image = BinaryMaskOverlay.colorize(pixels, width, height, Color.RED, 0.5f, true);
    assertEquals(width, image.getWidth());
    assertEquals(height, image.getHeight());
    for (int i = 0; i < pixels.length; i++) {
      assertEquals(pixels[i] != 0, (image.getRGB(i % width, i / width) >>> 24) != 0, "pixel " + i);
    }
  }

  @Test
  void outlineDoesNotFillHolesOrJoinComponents() {
    byte[] pixels = {
      -1, -1, -1, 0, -1,
      -1, -1, -1, 0, 0,
      -1, -1, -1, 0, 0
    };
    BufferedImage image = BinaryMaskOverlay.colorize(pixels, 5, 3, Color.GREEN, 1, false);
    assertEquals(0, image.getRGB(1, 1) >>> 24);
    assertEquals(255, image.getRGB(4, 0) >>> 24);
    assertEquals(0, image.getRGB(3, 0) >>> 24);
  }

  @Test
  void segGraphicPaintUsesRasterPixelsAtVoxelEdges() {
    SegContour contour = new SegContour("1", List.of(), 2);
    contour.setBinaryMask(new byte[] {-1, 0, 0, 0, 0, -1}, 3, 2);
    SegRegion<?> region = new SegRegion<>(1, "test", Color.RED);
    region.setFilled(true);
    region.setInteriorOpacity(1.0f);
    contour.setAttributes(region);
    SegGraphic graphic = contour.getSegGraphic();
    BufferedImage canvas = new BufferedImage(30, 20, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = canvas.createGraphics();
    try {
      AffineTransform pixelToScreen = new AffineTransform();
      pixelToScreen.translate(5, 5);
      pixelToScreen.scale(10, 10);
      graphic.paint(graphics, pixelToScreen);
    } finally {
      graphics.dispose();
    }
    assertEquals(0, canvas.getRGB(10, 5) >>> 24);
    assertEquals(0, canvas.getRGB(5, 10) >>> 24);
    assertEquals(255, canvas.getRGB(5, 5) >>> 24);
    assertEquals(255, canvas.getRGB(25, 15) >>> 24);
  }

  @Test
  void repaintBoundsIncludeIslandsMissingFromVectorContours() {
    SegContour contour = new SegContour("1", List.of(), 2);
    contour.setBinaryMask(new byte[] {-1, 0, 0, 0, 0, -1}, 3, 2);
    SegGraphic graphic = new SegGraphic(new Rectangle2D.Double(0, 0, 0.1, 0.1));
    graphic.setContour(contour);

    Rectangle2D bounds = graphic.getRepaintBounds(new AffineTransform());
    assertTrue(bounds.contains(2.5, 1.5));
  }
}
