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

/**
 * Defines the placement strategy for a new viewer plugin. This is a sealed hierarchy so that the
 * consumer code can exhaustively switch on the placement type.
 *
 * <p>The four strategies are mutually exclusive:
 *
 * <ul>
 *   <li>{@link ReuseViewer} – try to reuse an existing viewer matching the same patient/study
 *       entry; if none is found, create a new tab. This is the <em>default</em> strategy.
 *   <li>{@link NewTab} – always create a new tab without looking for existing viewers.
 *   <li>{@link Split} – create a new tab placed in a directional split beside the focused viewer.
 *   <li>{@link External} – open the viewer in an external (detached) window on a given display.
 * </ul>
 *
 * @see ViewerOpenOptions
 */
public sealed interface ViewerPlacement {

  // ---------------------------------------------------------------------------
  // Strategies
  // ---------------------------------------------------------------------------

  /**
   * Reuse an existing viewer when a matching entry (patient/study) is already open. If no match is
   * found, a new viewer tab is created.
   *
   * @param bestDefaultLayout when {@code true}, the best default layout is selected automatically
   *     and old series may be removed when reusing an existing viewer
   * @param openInSelection when {@code true}, a single series is added into the currently
   *     <em>selected</em> view rather than creating a new viewer — even across groups for non-DICOM
   *     series
   */
  record ReuseViewer(boolean bestDefaultLayout, boolean openInSelection)
      implements ViewerPlacement {

    /** Reuse with best layout, no add-to-selection (the default behaviour). */
    public static final ReuseViewer DEFAULT = new ReuseViewer(true, false);
  }

  /**
   * Always create a new viewer tab. Does not compare existing entries.
   *
   * @param bestDefaultLayout when {@code true}, the best default layout is selected automatically
   */
  record NewTab(boolean bestDefaultLayout) implements ViewerPlacement {

    /** New tab without automatic best layout. */
    public static final NewTab DEFAULT = new NewTab(false);
  }

  /**
   * Create a new viewer in a directional split beside the focused viewer.
   *
   * @param splitLayout defines the split direction and ratio
   */
  record Split(SplitLayout splitLayout) implements ViewerPlacement {

    public Split {
      if (splitLayout == null) {
        throw new IllegalArgumentException("splitLayout must not be null");
      }
    }
  }

  /**
   * Open the viewer in an external (detached) window on the given display.
   *
   * @param externalDisplay describes the target screen
   */
  record External(ExternalDisplay externalDisplay) implements ViewerPlacement {

    public External {
      if (externalDisplay == null) {
        throw new IllegalArgumentException("externalDisplay must not be null");
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Factory methods for convenience
  // ---------------------------------------------------------------------------

  /** Reuse an existing viewer with best layout and no add-to-selection (default). */
  static ReuseViewer reuseViewer() {
    return ReuseViewer.DEFAULT;
  }

  /** Reuse an existing viewer with explicit flags. */
  static ReuseViewer reuseViewer(boolean bestDefaultLayout, boolean openInSelection) {
    return new ReuseViewer(bestDefaultLayout, openInSelection);
  }

  /** Always create a new tab (no automatic layout). */
  static NewTab newTab() {
    return NewTab.DEFAULT;
  }

  /** Always create a new tab with explicit layout preference. */
  static NewTab newTab(boolean bestDefaultLayout) {
    return new NewTab(bestDefaultLayout);
  }

  /** Create a new viewer in a split beside the focused one. */
  static Split split(SplitLayout splitLayout) {
    return new Split(splitLayout);
  }

  /** Open on an external display. */
  static External external(ExternalDisplay externalDisplay) {
    return new External(externalDisplay);
  }
}
