/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * How a view fits a cell: the viewport in logical pixels, the zoom from image pixels to logical
 * pixels and the padding that places the viewport in the cell.
 *
 * <p>A view in best fit shows its whole image: the viewport takes the aspect ratio of the image. A
 * zoomed or panned view reproduces what its canvas shows: the viewport takes the aspect ratio of
 * the canvas and the same center.
 */
public record CellFit(
    int viewportWidth,
    int viewportHeight,
    double zoom,
    double padX,
    double padY,
    boolean bestFit,
    Point2D center) {

  /**
   * What the source view showed, in image pixels: the canvas the image was displayed on ({@code
   * null} without one), its zoom ({@code <= 0} for best fit), the center offset of its view model,
   * the size of the image (after the aspect ratio correction) and its rotation in degrees.
   */
  public record SourceGeometry(
      Rectangle2D canvas,
      double zoom,
      Point2D centerOffset,
      double imageWidth,
      double imageHeight,
      int rotation) {

    public boolean isBestFit() {
      return zoom <= 0.0 || canvas == null;
    }

    /** Width of what the view showed, in image pixels. */
    public double shownWidth() {
      if (isBestFit()) {
        return isQuarterTurn() ? imageHeight : imageWidth;
      }
      return canvas.getWidth() / zoom;
    }

    /** Height of what the view showed, in image pixels. */
    public double shownHeight() {
      if (isBestFit()) {
        return isQuarterTurn() ? imageWidth : imageHeight;
      }
      return canvas.getHeight() / zoom;
    }

    private boolean isQuarterTurn() {
      return Math.floorMod(rotation, 180) == 90;
    }
  }

  /**
   * Fits {@code source} into a cell of {@code cellWidth} by {@code cellHeight} logical pixels,
   * centered in it or at its top-left corner.
   */
  public static CellFit fit(
      SourceGeometry source, double cellWidth, double cellHeight, boolean center) {
    double shownWidth = Math.max(1.0, source.shownWidth());
    double shownHeight = Math.max(1.0, source.shownHeight());
    double scale = Math.min(cellWidth / shownWidth, cellHeight / shownHeight);
    // Whole pixels, never beyond the cell
    int viewportWidth = Math.max(1, (int) Math.floor(shownWidth * scale + 1e-6));
    int viewportHeight = Math.max(1, (int) Math.floor(shownHeight * scale + 1e-6));
    double padX = center ? (cellWidth - viewportWidth) / 2.0 : 0.0;
    double padY = center ? (cellHeight - viewportHeight) / 2.0 : 0.0;
    return new CellFit(
        viewportWidth,
        viewportHeight,
        scale,
        padX,
        padY,
        source.isBestFit(),
        source.centerOffset());
  }
}
