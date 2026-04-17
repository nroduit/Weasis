/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.CWorkingArea;

/**
 * Defines how a new viewer plugin is placed relative to the focused viewer within the docking
 * framework.
 *
 * <ul>
 *   <li>{@link #NONE} – stack as a tab alongside the focused viewer (default behaviour)
 *   <li>{@link #AUTO} – automatically split beside the focused viewer; the framework picks the best
 *       side (currently falls back to {@link #RIGHT} when no existing split partner is found)
 *   <li>{@link #LEFT} – split to the left of the focused viewer
 *   <li>{@link #RIGHT} – split to the right of the focused viewer
 *   <li>{@link #TOP} – split above the focused viewer
 *   <li>{@link #BOTTOM} – split below the focused viewer
 * </ul>
 *
 * @see ViewerOpenOptions
 */
public enum SplitPosition {
  /** No split – the viewer is stacked as a new tab. */
  NONE,

  /**
   * Automatic split – the framework decides the best placement. When no existing split partner is
   * found, behaves like {@link #RIGHT}.
   */
  AUTO,

  /** Split to the left of the focused viewer. */
  LEFT,

  /** Split to the right of the focused viewer. */
  RIGHT,

  /** Split above the focused viewer. */
  TOP,

  /** Split below the focused viewer. */
  BOTTOM;

  /**
   * Returns whether this position represents an actual split (i.e. anything other than {@link
   * #NONE}).
   */
  public boolean isSplit() {
    return this != NONE;
  }

  /**
   * Converts this position to a {@link CLocation} within the given working area at the specified
   * ratio.
   *
   * @param area the docking working area
   * @param ratio the split ratio, between 0.0 and 1.0 (fraction of total space given to the new
   *     viewer)
   * @return the corresponding {@link CLocation}, or {@code null} for {@link #NONE} and {@link
   *     #AUTO}
   */
  public CLocation toLocation(CWorkingArea area, double ratio) {
    return switch (this) {
      case LEFT -> CLocation.working(area).west(ratio);
      case RIGHT -> CLocation.working(area).east(ratio);
      case TOP -> CLocation.working(area).north(ratio);
      case BOTTOM -> CLocation.working(area).south(ratio);
      default -> null;
    };
  }
}
