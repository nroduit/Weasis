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
 * Groups a {@link SplitPosition} with a split ratio to fully describe how a new viewer is placed
 * relative to the currently focused viewer.
 *
 * <p>Use the pre-defined {@link #NONE} constant for the default tab-stacking behaviour, or the
 * convenience factory methods:
 *
 * <pre>{@code
 * SplitLayout.NONE                       // stack as a tab (default)
 * SplitLayout.of(SplitPosition.RIGHT)    // split right, equal ratio
 * SplitLayout.of(SplitPosition.LEFT, 0.3) // split left, 30 % for the new viewer
 * SplitLayout.auto()                     // framework decides, equal ratio
 * }</pre>
 *
 * @param position the split direction; {@link SplitPosition#NONE} means no split (tab stacking)
 * @param ratio the proportion of space allocated to the new viewer, between 0.0 and 1.0 (default
 *     0.5 = equal split); ignored when {@code position} is {@link SplitPosition#NONE}
 * @see ViewerOpenOptions
 */
public record SplitLayout(SplitPosition position, double ratio) {

  /** Default split ratio (equal split). */
  public static final double DEFAULT_RATIO = 0.5;

  /** No split – the viewer is stacked as a new tab. */
  public static final SplitLayout NONE = new SplitLayout(SplitPosition.NONE, DEFAULT_RATIO);

  /** Compact constructor – validates and normalises values. */
  public SplitLayout {
    if (ratio < 0.0 || ratio > 1.0) {
      throw new IllegalArgumentException("ratio must be between 0.0 and 1.0, got: " + ratio);
    }
    if (position == null) {
      position = SplitPosition.NONE;
    }
  }

  // ---------------------------------------------------------------------------
  // Factory methods
  // ---------------------------------------------------------------------------

  /**
   * Creates a split layout with the given direction and the {@linkplain #DEFAULT_RATIO default
   * ratio}.
   */
  public static SplitLayout of(SplitPosition position) {
    return new SplitLayout(position, DEFAULT_RATIO);
  }

  /** Creates a split layout with the given direction and ratio. */
  public static SplitLayout of(SplitPosition position, double ratio) {
    return new SplitLayout(position, ratio);
  }

  /**
   * Creates an automatic split layout – the framework decides the best placement, using the
   * {@linkplain #DEFAULT_RATIO default ratio}.
   */
  public static SplitLayout auto() {
    return new SplitLayout(SplitPosition.AUTO, DEFAULT_RATIO);
  }

  /**
   * Creates an automatic split layout with a custom ratio.
   *
   * @param ratio the proportion of space given to the new viewer (0.0–1.0)
   */
  public static SplitLayout auto(double ratio) {
    return new SplitLayout(SplitPosition.AUTO, ratio);
  }

  // ---------------------------------------------------------------------------
  // Query methods
  // ---------------------------------------------------------------------------

  /**
   * Returns {@code true} when this layout represents an actual split (anything other than {@link
   * SplitPosition#NONE}).
   */
  public boolean isSplit() {
    return position.isSplit();
  }

  /**
   * Converts this layout to a {@link CLocation} within the given working area.
   *
   * @param area the docking working area
   * @return the corresponding {@link CLocation}, or {@code null} for {@link SplitPosition#NONE} and
   *     {@link SplitPosition#AUTO}
   * @see SplitPosition#toLocation(CWorkingArea, double)
   */
  public CLocation toLocation(CWorkingArea area) {
    return position.toLocation(area, ratio);
  }
}
