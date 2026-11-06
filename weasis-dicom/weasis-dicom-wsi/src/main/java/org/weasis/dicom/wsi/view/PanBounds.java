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

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Keeps a slide in view: on each axis the view shows the whole slide when it is smaller than half
 * the view, and otherwise at least half the view is covered by the slide. A pan may go beyond while
 * the mouse holds it; the view comes back once released.
 */
public final class PanBounds {

  /** The share of the view the slide covers at least, unless the slide is smaller. */
  static final double VISIBLE = 0.5;

  private static final double EPSILON = 0.5;

  private PanBounds() {}

  /**
   * @param slide the bounds of the slide in view pixels
   * @param view the view, in the same space
   * @return the pan, in view pixels, that brings back the slide in view; zero when it is
   */
  public static Point2D correction(Rectangle2D slide, Rectangle2D view) {
    return new Point2D.Double(
        axis(slide.getMinX() - view.getMinX(), slide.getMaxX() - view.getMinX(), view.getWidth()),
        axis(slide.getMinY() - view.getMinY(), slide.getMaxY() - view.getMinY(), view.getHeight()));
  }

  // The slide spans [a, b] on an axis where the view spans [0, extent]
  static double axis(double a, double b, double extent) {
    double required = Math.min(b - a, extent * VISIBLE);
    double overlap = Math.min(b, extent) - Math.max(a, 0);
    if (overlap >= required - EPSILON) {
      return 0;
    }
    // Away on the left: its right end comes to the required width, and the mirror on the right
    return a + b < extent ? required - b : extent - required - a;
  }

  public static boolean isZero(Point2D correction) {
    return correction.getX() == 0 && correction.getY() == 0;
  }
}
