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
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/** Colorizes a discrete SEG mask without interpolation or contour rasterization. */
final class BinaryMaskOverlay {
  private BinaryMaskOverlay() {}

  static BufferedImage colorize(
      byte[] mask, int width, int height, Color color, float opacity, boolean filled) {
    return colorize(mask, width, height, color, opacity, filled, false);
  }

  static BufferedImage colorize(
      byte[] mask,
      int width,
      int height,
      Color color,
      float opacity,
      boolean filled,
      boolean highlighted) {
    if (mask == null || width <= 0 || height <= 0 || mask.length != (long) width * height) {
      throw new IllegalArgumentException("Invalid binary mask dimensions");
    }
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    int fillAlpha = Math.clamp(Math.round(color.getAlpha() * opacity), 0, 255);
    int fillArgb = (fillAlpha << 24) | (color.getRGB() & 0xffffff);
    int outlineArgb = (color.getAlpha() << 24) | (color.getRGB() & 0xffffff);
    int highlightedArgb =
        (color.getAlpha() << 24)
            | (((color.getRed() + 255) / 2) << 16)
            | (((color.getGreen() + 255) / 2) << 8)
            | ((color.getBlue() + 255) / 2);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int i = y * width + x;
        if (mask[i] == 0) {
          continue;
        }
        if (filled && !highlighted) {
          pixels[i] = fillArgb;
        } else if (isEdge(mask, width, height, x, y)) {
          pixels[i] = highlighted ? highlightedArgb : outlineArgb;
        } else if (filled) {
          pixels[i] = fillArgb;
        }
      }
    }
    return image;
  }

  private static boolean isEdge(byte[] mask, int width, int height, int x, int y) {
    return x == 0
        || y == 0
        || x == width - 1
        || y == height - 1
        || mask[y * width + x - 1] == 0
        || mask[y * width + x + 1] == 0
        || mask[(y - 1) * width + x] == 0
        || mask[(y + 1) * width + x] == 0;
  }
}
