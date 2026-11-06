/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.ann;

import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import org.weasis.dicom.wsi.ann.BulkAnnotations.Group;

/**
 * The items of a group on a grid over the slide, in base level pixels: what a viewport shows is
 * found by its cells, and a cap stops the search, so that a group of millions of items costs one
 * pass to build and a bounded paint afterwards.
 */
public final class AnnotationIndex {

  /** Cells along each axis of the grid. */
  static final int CELLS = 128;

  private final Group group;
  private final float[] bounds;
  private final double cellWidth;
  private final double cellHeight;
  private final int[] cellStart;
  private final int[] cellItems;
  private final int[] seen;
  private int generation;

  private AnnotationIndex(
      Group group,
      float[] bounds,
      double cellWidth,
      double cellHeight,
      int[] cellStart,
      int[] cellItems) {
    this.group = group;
    this.bounds = bounds;
    this.cellWidth = cellWidth;
    this.cellHeight = cellHeight;
    this.cellStart = cellStart;
    this.cellItems = cellItems;
    this.seen = new int[group.count()];
  }

  public Group group() {
    return group;
  }

  /** The bounds of an item in base level pixels: minX, minY, maxX, maxY. */
  public float[] bounds(int item, float[] out) {
    System.arraycopy(bounds, 4 * item, out, 0, 4);
    return out;
  }

  /**
   * Builds the index of a group over a slide of the given size, off the event dispatch thread: one
   * pass over the points.
   */
  public static AnnotationIndex build(Group group, int slideWidth, int slideHeight) {
    int count = group.count();
    float[] bounds = new float[4 * count];
    double cellWidth = Math.max(1.0, slideWidth / (double) CELLS);
    double cellHeight = Math.max(1.0, slideHeight / (double) CELLS);
    int[] cellCount = new int[CELLS * CELLS + 1];
    int[] range = new int[4];
    for (int item = 0; item < count; item++) {
      itemBounds(group, item, bounds);
      cells(bounds, item, cellWidth, cellHeight, range);
      for (int row = range[1]; row <= range[3]; row++) {
        for (int col = range[0]; col <= range[2]; col++) {
          cellCount[row * CELLS + col + 1]++;
        }
      }
    }
    int[] cellStart = new int[CELLS * CELLS + 1];
    for (int i = 1; i < cellStart.length; i++) {
      cellStart[i] = cellStart[i - 1] + cellCount[i];
    }
    int[] fill = Arrays.copyOf(cellStart, cellStart.length);
    int[] cellItems = new int[cellStart[CELLS * CELLS]];
    for (int item = 0; item < count; item++) {
      cells(bounds, item, cellWidth, cellHeight, range);
      for (int row = range[1]; row <= range[3]; row++) {
        for (int col = range[0]; col <= range[2]; col++) {
          cellItems[fill[row * CELLS + col]++] = item;
        }
      }
    }
    return new AnnotationIndex(group, bounds, cellWidth, cellHeight, cellStart, cellItems);
  }

  private static void itemBounds(Group group, int item, float[] bounds) {
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for (int p = group.start(item); p < group.end(item); p++) {
      double x = group.points().x(p) * group.scaleX();
      double y = group.points().y(p) * group.scaleY();
      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      maxX = Math.max(maxX, x);
      maxY = Math.max(maxY, y);
    }
    if (minX > maxX) {
      minX = minY = maxX = maxY = 0;
    }
    bounds[4 * item] = (float) minX;
    bounds[4 * item + 1] = (float) minY;
    bounds[4 * item + 2] = (float) maxX;
    bounds[4 * item + 3] = (float) maxY;
  }

  private static void cells(
      float[] bounds, int item, double cellWidth, double cellHeight, int[] range) {
    range[0] = cell(bounds[4 * item] / cellWidth);
    range[1] = cell(bounds[4 * item + 1] / cellHeight);
    range[2] = cell(bounds[4 * item + 2] / cellWidth);
    range[3] = cell(bounds[4 * item + 3] / cellHeight);
  }

  private static int cell(double value) {
    return (int) Math.clamp(Math.floor(value), 0, CELLS - 1);
  }

  /**
   * The items whose bounds meet the viewport, at most {@code limit} of them, in base level pixels.
   * Beyond the limit every k-th item is taken, so that the whole viewport keeps a texture of the
   * group instead of its first rows. Not thread-safe: one query at a time, on the painting thread.
   */
  public int[] query(Rectangle2D viewport, int limit) {
    int c0 = cell(viewport.getMinX() / cellWidth);
    int r0 = cell(viewport.getMinY() / cellHeight);
    int c1 = cell(viewport.getMaxX() / cellWidth);
    int r1 = cell(viewport.getMaxY() / cellHeight);
    float[] v = {
      (float) viewport.getMinX(),
      (float) viewport.getMinY(),
      (float) viewport.getMaxX(),
      (float) viewport.getMaxY()
    };
    int matching = collect(r0, r1, c0, c1, v, null, 1);
    int stride = Math.max(1, Math.ceilDiv(matching, Math.max(1, limit)));
    int[] found = new int[Math.min(limit, matching)];
    int n = collect(r0, r1, c0, c1, v, found, stride);
    return n == found.length ? found : Arrays.copyOf(found, n);
  }

  // One pass over the cells of the viewport: counts the distinct items meeting it, or stores every
  // stride-th of them into found
  private int collect(int r0, int r1, int c0, int c1, float[] v, int[] found, int stride) {
    generation++;
    int matching = 0;
    int n = 0;
    for (int row = r0; row <= r1; row++) {
      for (int i = cellStart[row * CELLS + c0]; i < cellStart[row * CELLS + c1 + 1]; i++) {
        int item = cellItems[i];
        if (seen[item] == generation) {
          continue;
        }
        seen[item] = generation;
        int b = 4 * item;
        if (bounds[b + 2] < v[0]
            || bounds[b] > v[2]
            || bounds[b + 3] < v[1]
            || bounds[b + 1] > v[3]) {
          continue;
        }
        if (found != null && matching % stride == 0) {
          if (n >= found.length) {
            return n;
          }
          found[n++] = item;
        }
        matching++;
      }
    }
    return found == null ? matching : n;
  }
}
