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

import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

/**
 * The place of the slide overview in the top right corner of a view, and the mapping between its
 * pixels and the base level pixels. The overview keeps the orientation of the slide, whatever the
 * rotation of the view.
 *
 * @param box the overview in view pixels
 * @param slideWidth the base level width of the slide
 * @param slideHeight the base level height of the slide
 */
public record OverviewMap(Rectangle box, double slideWidth, double slideHeight) {

  /** Inset from the corner of the view, in view pixels. */
  public static final int MARGIN = 10;

  /**
   * @param maxSide the longest side of the overview, in view pixels
   */
  public static OverviewMap of(int viewWidth, double slideWidth, double slideHeight, int maxSide) {
    double ratio = Math.min(maxSide / slideWidth, maxSide / slideHeight);
    int width = Math.max(1, (int) Math.round(slideWidth * ratio));
    int height = Math.max(1, (int) Math.round(slideHeight * ratio));
    return new OverviewMap(
        new Rectangle(viewWidth - width - MARGIN, MARGIN, width, height), slideWidth, slideHeight);
  }

  public boolean contains(Point2D view) {
    return box.contains(view);
  }

  /** The base level point under a view pixel of the overview. */
  public Point2D toModel(Point2D view) {
    return new Point2D.Double(
        (view.getX() - box.x) * slideWidth / box.width,
        (view.getY() - box.y) * slideHeight / box.height);
  }

  /** The outline, in view pixels, of the area a view shows, given its corners in base pixels. */
  public Path2D outline(Point2D... modelCorners) {
    Path2D path = new Path2D.Double();
    for (int i = 0; i < modelCorners.length; i++) {
      double x = box.x + modelCorners[i].getX() * box.width / slideWidth;
      double y = box.y + modelCorners[i].getY() * box.height / slideHeight;
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    path.closePath();
    return path;
  }
}
