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

import org.weasis.core.Messages;

/**
 * The size of the overlay text, the measurement labels and the strokes on paper, as the height of
 * the overlay font in typographic points. Everything the view draws scales with it, so a size is
 * also a stroke width.
 */
public enum TextSize {
  SMALL(6.0, Messages.getString("TextSize.small")),
  NORMAL(8.0, Messages.getString("TextSize.normal")),
  LARGE(10.0, Messages.getString("TextSize.large"));

  /** Cells narrower than this print their overlay in the small size unless the user chose one. */
  public static final double SMALL_CELL_WIDTH_MM = 60.0;

  /** Logical pixels per typographic point: the view draws at 96 dpi, a point is 1/72 inch. */
  public static final double LOGICAL_PX_PER_POINT = 96.0 / 72.0;

  private final double points;
  private final String title;

  TextSize(double points, String title) {
    this.points = points;
    this.title = title;
  }

  public double points() {
    return points;
  }

  /** The size for a cell of this width when the user chose none. */
  public static TextSize defaultFor(double cellWidthMm) {
    return cellWidthMm < SMALL_CELL_WIDTH_MM ? SMALL : NORMAL;
  }

  /**
   * The factor applied to the logical pixels of the view so that its base overlay font, {@code
   * baseFontPx} high on screen, prints at this size.
   */
  public double scaleFor(double baseFontPx) {
    if (baseFontPx <= 0) {
      throw new IllegalArgumentException("Font size must be positive");
    }
    return points * LOGICAL_PX_PER_POINT / baseFontPx;
  }

  /** Height of this size on paper in millimeters. */
  public double heightMm() {
    return points / Paper.POINTS_PER_INCH * Paper.MM_PER_INCH;
  }

  @Override
  public String toString() {
    return title;
  }
}
