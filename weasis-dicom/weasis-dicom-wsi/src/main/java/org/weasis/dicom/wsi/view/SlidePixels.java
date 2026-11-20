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

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.Optional;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.weasis.dicom.wsi.model.PyramidLevel;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.TileId;
import org.weasis.dicom.wsi.source.TileSource;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * The stored pixels of a slide read from its tiles, for the value under the pointer and for the
 * statistics of a region: a slide is never held whole in memory, so both read the tiles they need
 * at a level of the pyramid.
 */
public final class SlidePixels {

  /** Most pixels of a region image: a larger region is read at a coarser level. */
  public static final long MAX_REGION_PIXELS = 2L << 20;

  private SlidePixels() {}

  /**
   * The pixels of a region of a slide.
   *
   * @param image the region at a level of the pyramid; the caller releases it
   * @param level the index of that level
   * @param baseToImage from the base level pixels to the pixels of the image
   */
  public record Region(PlanarImage image, int level, AffineTransform baseToImage) {}

  /** The tile of a level that holds a base level pixel, and where in it. */
  public record Location(TileId tile, int x, int y) {}

  /**
   * @param baseX a base level pixel, as is {@code baseY}
   * @return the tile holding the pixel at a level, empty out of the slide or of the levels
   */
  public static Optional<Location> locate(
      Slide slide, int level, int focalPlane, int opticalPath, double baseX, double baseY) {
    if (level < 0
        || level >= slide.levels().size()
        || baseX < 0
        || baseY < 0
        || baseX >= slide.width()
        || baseY >= slide.height()) {
      return Optional.empty();
    }
    PyramidLevel l = slide.levels().get(level);
    int x = Math.min(l.totalColumns() - 1, (int) (baseX / l.downsampleX()));
    int y = Math.min(l.totalRows() - 1, (int) (baseY / l.downsampleY()));
    int column = x / l.tileWidth();
    int row = y / l.tileHeight();
    return Optional.of(
        new Location(
            new TileId(level, column, row, focalPlane, opticalPath),
            x - column * l.tileWidth(),
            y - row * l.tileHeight()));
  }

  /**
   * The finest level at which a region of the base level holds at most {@code maxPixels}, the
   * coarsest level when none does.
   */
  public static int levelFor(Slide slide, Rectangle2D base, long maxPixels) {
    int last = slide.levels().size() - 1;
    for (int i = 0; i < last; i++) {
      PyramidLevel l = slide.levels().get(i);
      double pixels = base.getWidth() / l.downsampleX() * (base.getHeight() / l.downsampleY());
      if (pixels <= maxPixels) {
        return i;
      }
    }
    return last;
  }

  /**
   * Reads the part of the slide within a rectangle of the base level, at the finest level that
   * keeps the image within {@link #MAX_REGION_PIXELS}. The places no tile covers take the
   * background of the slide.
   *
   * @return empty when the rectangle is out of the slide
   */
  public static Optional<Region> read(
      TileSource source, Rectangle2D base, int focalPlane, int opticalPath) throws IOException {
    Slide slide = source.slide();
    Rectangle2D clipped =
        base.createIntersection(new Rectangle2D.Double(0, 0, slide.width(), slide.height()));
    if (clipped.isEmpty()) {
      return Optional.empty();
    }
    int index = levelFor(slide, clipped, MAX_REGION_PIXELS);
    PyramidLevel level = slide.levels().get(index);
    double dx = level.downsampleX();
    double dy = level.downsampleY();
    int x0 = (int) Math.floor(clipped.getMinX() / dx);
    int y0 = (int) Math.floor(clipped.getMinY() / dy);
    int x1 = Math.min(level.totalColumns(), (int) Math.ceil(clipped.getMaxX() / dx));
    int y1 = Math.min(level.totalRows(), (int) Math.ceil(clipped.getMaxY() / dy));
    Rectangle area = new Rectangle(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));

    ImageCV image = null;
    for (int row = area.y / level.tileHeight();
        row * level.tileHeight() < area.y + area.height;
        row++) {
      for (int column = area.x / level.tileWidth();
          column * level.tileWidth() < area.x + area.width;
          column++) {
        Optional<PlanarImage> tile =
            source.readTile(new TileId(index, column, row, focalPlane, opticalPath));
        if (tile.isEmpty()) {
          continue;
        }
        try {
          Mat mat = tile.get().toMat();
          if (image == null) {
            image = background(area, mat.type(), slide.background());
          }
          Rectangle part = level.tileBounds(column, row).intersection(area);
          part =
              part.intersection(
                  new Rectangle(
                      column * level.tileWidth(),
                      row * level.tileHeight(),
                      mat.width(),
                      mat.height()));
          if (part.isEmpty() || mat.type() != image.type()) {
            continue;
          }
          Mat from =
              mat.submat(
                  new Rect(
                      part.x - column * level.tileWidth(),
                      part.y - row * level.tileHeight(),
                      part.width,
                      part.height));
          Mat to =
              image.submat(new Rect(part.x - area.x, part.y - area.y, part.width, part.height));
          from.copyTo(to);
          from.release();
          to.release();
        } finally {
          tile.get().release();
        }
      }
    }
    if (image == null) {
      // Background alone: nothing was scanned there
      image = background(area, CvType.CV_8UC3, slide.background());
    }
    AffineTransform transform = AffineTransform.getTranslateInstance(-area.x, -area.y);
    transform.scale(1 / dx, 1 / dy);
    return Optional.of(new Region(image, index, transform));
  }

  // The tiles are stored blue first, as OpenCV reads them
  private static ImageCV background(Rectangle area, int type, Color color) {
    Scalar fill =
        CvType.channels(type) >= 3
            ? new Scalar(color.getBlue(), color.getGreen(), color.getRed())
            : new Scalar((color.getRed() + color.getGreen() + color.getBlue()) / 3.0);
    return new ImageCV(area.height, area.width, type, fill);
  }
}
