/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws.drag;

import java.awt.Point;
import java.awt.Rectangle;
import org.weasis.core.ui.docking.ws.DockSide;

/** Pure hit-testing math of the drag overlay: which zone of a group a point falls into. */
public final class DropGeometry {

  /** Fraction of each edge that acts as a split band; the middle is the stack (tab) zone. */
  public static final double EDGE_BAND = 0.3;

  private DropGeometry() {}

  /**
   * Returns the split side for {@code point} inside {@code bounds}, or {@code null} when the point
   * falls in the center band (stacking) or outside the bounds.
   */
  public static DockSide sideAt(Rectangle bounds, Point point) {
    if (!bounds.contains(point)) {
      return null;
    }
    double relX = (point.x - bounds.x) / (double) bounds.width;
    double relY = (point.y - bounds.y) / (double) bounds.height;
    if (relX >= EDGE_BAND && relX <= 1 - EDGE_BAND && relY >= EDGE_BAND && relY <= 1 - EDGE_BAND) {
      return null;
    }
    // the nearest edge wins, measured relative to the band widths
    double left = relX;
    double right = 1 - relX;
    double top = relY;
    double bottom = 1 - relY;
    double min = Math.min(Math.min(left, right), Math.min(top, bottom));
    if (min == left) {
      return DockSide.LEFT;
    }
    if (min == right) {
      return DockSide.RIGHT;
    }
    return min == top ? DockSide.TOP : DockSide.BOTTOM;
  }

  /** Returns the drop highlight for {@code side} of {@code bounds}; the full bounds for null. */
  public static Rectangle highlightRect(Rectangle bounds, DockSide side) {
    if (side == null) {
      return new Rectangle(bounds);
    }
    int w = (int) Math.round(bounds.width * 0.5);
    int h = (int) Math.round(bounds.height * 0.5);
    return switch (side) {
      case LEFT -> new Rectangle(bounds.x, bounds.y, w, bounds.height);
      case RIGHT -> new Rectangle(bounds.x + bounds.width - w, bounds.y, w, bounds.height);
      case TOP -> new Rectangle(bounds.x, bounds.y, bounds.width, h);
      case BOTTOM -> new Rectangle(bounds.x, bounds.y + bounds.height - h, bounds.width, h);
    };
  }

  /**
   * Returns the insertion index for a drop at {@code x} given the tab rectangles of a tab strip,
   * i.e. the index of the first tab whose horizontal middle is beyond {@code x}.
   */
  public static int tabInsertionIndex(Rectangle[] tabBounds, int x) {
    for (int i = 0; i < tabBounds.length; i++) {
      if (x < tabBounds[i].x + tabBounds[i].width / 2) {
        return i;
      }
    }
    return tabBounds.length;
  }
}
