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

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.weasis.dicom.wsi.model.PyramidLevel;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.TileId;

/**
 * The tiles a viewport needs, in loading order: the tiles of the top level and of the next coarser
 * level covering it (few and quick, they give a picture at once and a fallback while the finer
 * tiles load), the visible tiles of the chosen level from the centre outwards, then one ring of
 * tiles around them. Background tiles of sparse levels are left out, they have nothing to load.
 *
 * <p>The painted tiles are bounded in pixels. When the visible tiles of the level of the zoom
 * exceed the bound, a coarser level is planned instead; when even the top level does (a slide
 * without a pyramid, zoomed out), the plan keeps the centre-most tiles, plans no ring, and is
 * marked truncated so that the view fills the rest otherwise.
 *
 * @param level the index of the level chosen for the downsample, or a coarser one within the bound;
 *     -1 for an empty plan
 * @param coarse tiles of the top level, then of the next coarser level, covering the viewport;
 *     empty at the top level
 * @param visible tiles of the chosen level intersecting the viewport, centre first
 * @param ring tiles of the chosen level touching the visible ones
 * @param truncated whether tiles of the viewport were left out of the plan
 */
public record TilePlan(
    int level, List<TileId> coarse, List<TileId> visible, List<TileId> ring, boolean truncated) {

  /** Budget of the painted (pinned) tiles: 1024 tiles of 256 × 256, about 200 MB in RGB. */
  static final long MAX_PAINTED_PIXELS = 64L << 20;

  /** Share of the budget for the visible tiles, beyond which a coarser level is shown. */
  static final long MAX_VISIBLE_PIXELS = MAX_PAINTED_PIXELS * 3 / 4;

  public static final TilePlan EMPTY = new TilePlan(-1, List.of(), List.of(), List.of(), false);

  public TilePlan {
    coarse = List.copyOf(coarse);
    visible = List.copyOf(visible);
    ring = List.copyOf(ring);
  }

  /**
   * @param viewport the visible area in base level pixels
   * @param downsample base level pixels per screen pixel
   */
  public static TilePlan of(
      Slide slide, Rectangle2D viewport, double downsample, int focalPlane, int opticalPath) {
    return of(slide, viewport, downsample, focalPlane, opticalPath, null);
  }

  /**
   * @param motion how the viewport moved since the previous plan, in base level pixels: the ring
   *     then loads the tiles ahead of the pan first; null or zero for centre-out
   */
  public static TilePlan of(
      Slide slide,
      Rectangle2D viewport,
      double downsample,
      int focalPlane,
      int opticalPath,
      Point2D motion) {
    if (viewport.isEmpty() || downsample <= 0) {
      return EMPTY;
    }
    PyramidLevel level = levelFor(slide, viewport, downsample);
    Rectangle range = tileRange(level, viewport);
    if (range.isEmpty()) {
      return EMPTY;
    }
    double centreX = viewport.getCenterX() / level.downsampleX();
    double centreY = viewport.getCenterY() / level.downsampleY();
    Comparator<TileId> centreOut =
        Comparator.comparingDouble(t -> distanceSq(level, t.column(), t.row(), centreX, centreY));

    List<TileId> visible = new ArrayList<>();
    List<TileId> ring = new ArrayList<>();
    Rectangle grid = new Rectangle(0, 0, level.tilesAcross(), level.tilesDown());
    Rectangle ringRange = grid.intersection(grow(range));
    for (int row = ringRange.y; row < ringRange.y + ringRange.height; row++) {
      for (int col = ringRange.x; col < ringRange.x + ringRange.width; col++) {
        if (level.frame(col, row, focalPlane, opticalPath).isEmpty()) {
          continue;
        }
        TileId tile = new TileId(level.index(), col, row, focalPlane, opticalPath);
        (range.contains(col, row) ? visible : ring).add(tile);
      }
    }
    visible.sort(centreOut);
    boolean moving = motion != null && (motion.getX() != 0 || motion.getY() != 0);
    ring.sort(
        moving ? aheadFirst(level, centreX, centreY, motion).thenComparing(centreOut) : centreOut);

    List<TileId> coarse = new ArrayList<>();
    long budget = MAX_PAINTED_PIXELS;
    for (PyramidLevel coarser : coarserLevels(slide, level)) {
      List<TileId> tiles = tiles(coarser, tileRange(coarser, viewport), focalPlane, opticalPath);
      long pixels = tiles.size() * tilePixels(coarser);
      if (pixels <= MAX_PAINTED_PIXELS / 4) {
        coarse.addAll(tiles);
        budget -= pixels;
      }
    }
    int maxVisible = (int) Math.min(Integer.MAX_VALUE, budget / tilePixels(level));
    if (visible.size() > maxVisible) {
      return new TilePlan(level.index(), coarse, visible.subList(0, maxVisible), List.of(), true);
    }
    return new TilePlan(level.index(), coarse, visible, ring, false);
  }

  /**
   * The top level, which always gives a whole picture, then the level just above the chosen one.
   */
  private static List<PyramidLevel> coarserLevels(Slide slide, PyramidLevel level) {
    int top = slide.levels().size() - 1;
    int parent = level.index() + 1;
    if (parent > top) {
      return List.of();
    }
    if (parent == top) {
      return List.of(slide.levels().get(top));
    }
    return List.of(slide.levels().get(top), slide.levels().get(parent));
  }

  // A coarser level, slightly blurred, rather than a view left partly empty
  private static PyramidLevel levelFor(Slide slide, Rectangle2D viewport, double downsample) {
    PyramidLevel level = slide.levelForDownsample(downsample);
    while (level.index() + 1 < slide.levels().size()
        && area(tileRange(level, viewport)) * tilePixels(level) > MAX_VISIBLE_PIXELS) {
      level = slide.levels().get(level.index() + 1);
    }
    return level;
  }

  private static long area(Rectangle range) {
    return (long) range.width * range.height;
  }

  private static long tilePixels(PyramidLevel level) {
    return (long) level.tileWidth() * level.tileHeight();
  }

  /** Coarse and visible tiles: the ones painted, to keep in the cache. */
  public List<TileId> painted() {
    return Stream.concat(coarse.stream(), visible.stream()).toList();
  }

  /** Every tile of the plan, in loading order. */
  public List<TileId> ordered() {
    return Stream.of(coarse, visible, ring).flatMap(List::stream).toList();
  }

  // Tiles in the direction of the motion first: the further ahead, the sooner
  private static Comparator<TileId> aheadFirst(
      PyramidLevel level, double centreX, double centreY, Point2D motion) {
    double length = Math.hypot(motion.getX(), motion.getY());
    double ux = motion.getX() / length;
    double uy = motion.getY() / length;
    return Comparator.comparingDouble(
        t -> {
          double dx = (t.column() + 0.5) * level.tileWidth() - centreX;
          double dy = (t.row() + 0.5) * level.tileHeight() - centreY;
          return -(dx * ux + dy * uy);
        });
  }

  /** Tile indices intersecting the viewport, clipped to the grid of the level. */
  public static Rectangle tileRange(PyramidLevel level, Rectangle2D viewport) {
    double x0 = viewport.getMinX() / level.downsampleX() / level.tileWidth();
    double y0 = viewport.getMinY() / level.downsampleY() / level.tileHeight();
    double x1 = viewport.getMaxX() / level.downsampleX() / level.tileWidth();
    double y1 = viewport.getMaxY() / level.downsampleY() / level.tileHeight();
    int c0 = (int) Math.floor(x0);
    int r0 = (int) Math.floor(y0);
    Rectangle range = new Rectangle(c0, r0, (int) Math.ceil(x1) - c0, (int) Math.ceil(y1) - r0);
    return range.intersection(new Rectangle(0, 0, level.tilesAcross(), level.tilesDown()));
  }

  private static List<TileId> tiles(
      PyramidLevel level, Rectangle range, int focalPlane, int opticalPath) {
    List<TileId> tiles = new ArrayList<>();
    for (int row = range.y; row < range.y + range.height; row++) {
      for (int col = range.x; col < range.x + range.width; col++) {
        if (level.frame(col, row, focalPlane, opticalPath).isPresent()) {
          tiles.add(new TileId(level.index(), col, row, focalPlane, opticalPath));
        }
      }
    }
    return tiles;
  }

  private static Rectangle grow(Rectangle range) {
    return new Rectangle(range.x - 1, range.y - 1, range.width + 2, range.height + 2);
  }

  private static double distanceSq(
      PyramidLevel level, int column, int row, double centreX, double centreY) {
    double dx = (column + 0.5) * level.tileWidth() - centreX;
    double dy = (row + 0.5) * level.tileHeight() - centreY;
    return dx * dx + dy * dy;
  }
}
