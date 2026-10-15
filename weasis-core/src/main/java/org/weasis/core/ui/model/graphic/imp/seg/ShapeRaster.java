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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Arrays;

/**
 * Binary mask of a shape in image pixel coordinates. Pixel centres have integer coordinates, as in
 * DICOM and in the OpenCV rendering of the image: pixel {@code (x, y)} belongs to the shape when
 * the point {@code (x, y)} is inside. Filling an outline that follows the pixel edges, as traced
 * from a segmentation mask, gives back exactly that mask.
 *
 * @param bounds the pixels covered by {@code mask}, in image coordinates
 * @param mask row-major values, 255 inside the shape and 0 outside
 */
public record ShapeRaster(Rectangle bounds, byte[] mask) {

  /**
   * Rasterizes {@code shape} within {@code clip}, or returns {@code null} when its bounds do not
   * overlap {@code clip}.
   */
  public static ShapeRaster of(Shape shape, Rectangle clip) {
    Rectangle2D area = shape.getBounds2D();
    int x0 = (int) Math.floor(area.getMinX());
    int y0 = (int) Math.floor(area.getMinY());
    int x1 = (int) Math.ceil(area.getMaxX());
    int y1 = (int) Math.ceil(area.getMaxY());
    Rectangle bounds = new Rectangle(x0, y0, x1 - x0 + 1, y1 - y0 + 1).intersection(clip);
    if (bounds.isEmpty()) {
      return null;
    }
    BufferedImage image =
        new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      // Pure geometry: the default normalization shifts non-integer vertices
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      // Java2D fills a raster pixel when the point half a pixel past its index is inside
      g.translate(0.5 - bounds.x, 0.5 - bounds.y);
      g.setColor(Color.WHITE);
      g.fill(shape);
    } finally {
      g.dispose();
    }
    byte[] mask = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
    return new ShapeRaster(bounds, mask);
  }

  /** Returns the number of pixels inside the shape. */
  public int countPixels() {
    int count = 0;
    for (byte b : mask) {
      if (b != 0) {
        count++;
      }
    }
    return count;
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof ShapeRaster other
            && bounds.equals(other.bounds)
            && Arrays.equals(mask, other.mask));
  }

  @Override
  public int hashCode() {
    return 31 * bounds.hashCode() + Arrays.hashCode(mask);
  }

  @Override
  public String toString() {
    return "ShapeRaster[bounds=" + bounds + ", pixels=" + countPixels() + "]";
  }
}
