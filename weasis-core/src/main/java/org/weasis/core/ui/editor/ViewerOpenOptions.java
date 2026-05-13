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

import javax.swing.Icon;

/**
 * Immutable, type-safe options controlling how a viewer plugin is opened and presented.
 *
 * <p>Instances are created through the fluent {@link Builder} obtained via {@link #builder()}, or
 * directly via the record constructor.
 *
 * @param placement the placement strategy, defining <em>where</em> the viewer opens (reuse
 *     existing, new tab, split, or external window); see {@link ViewerPlacement}
 * @param tabFocusPolicy the policy that controls whether a newly opened tab gets focus; see {@link
 *     TabFocusPolicy}
 * @param icon an optional icon override for the viewer tab
 * @param uid an optional unique identifier for the plugin instance
 * @param seriesCount the number of series to display; used as a hint for layout selection (default
 *     1)
 */
public record ViewerOpenOptions(
    ViewerPlacement placement,
    TabFocusPolicy tabFocusPolicy,
    Icon icon,
    String uid,
    int seriesCount) {

  private static final ViewerOpenOptions DEFAULTS =
      new ViewerOpenOptions(
          ViewerPlacement.reuseViewer(), TabFocusPolicy.foreground(), null, null, 1);

  /** Compact constructor – normalises null values. */
  public ViewerOpenOptions {
    if (placement == null) {
      placement = ViewerPlacement.reuseViewer();
    }
    if (tabFocusPolicy == null) {
      tabFocusPolicy = TabFocusPolicy.foreground();
    }
  }

  /**
   * Returns the default options: reuse existing viewer with best layout, foreground focus policy,
   * no icon/uid, single series.
   */
  public static ViewerOpenOptions defaults() {
    return DEFAULTS;
  }

  /** Returns a new {@link Builder} pre-filled with default values. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns a copy of this record with a different {@code seriesCount}. */
  public ViewerOpenOptions withSeriesCount(int count) {
    return new ViewerOpenOptions(placement, tabFocusPolicy, icon, uid, count);
  }

  /** Returns a copy of this record with a different {@link TabFocusPolicy}. */
  public ViewerOpenOptions withTabFocusPolicy(TabFocusPolicy policy) {
    return new ViewerOpenOptions(placement, policy, icon, uid, seriesCount);
  }

  /** Fluent builder for {@link ViewerOpenOptions}. */
  public static final class Builder {
    private ViewerPlacement placement = ViewerPlacement.reuseViewer();
    private TabFocusPolicy tabFocusPolicy = TabFocusPolicy.foreground();
    private Icon icon = null;
    private String uid = null;
    private int seriesCount = 1;

    private Builder() {}

    /**
     * Sets the placement strategy.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * // Reuse existing viewer (default)
     * builder.placement(ViewerPlacement.reuseViewer())
     *
     * // Force a new tab
     * builder.placement(ViewerPlacement.newTab())
     *
     * // Split beside the focused viewer
     * builder.placement(ViewerPlacement.split(SplitLayout.auto()))
     *
     * // Open on an external screen
     * builder.placement(ViewerPlacement.external(ExternalDisplay.onScreen(device)))
     * }</pre>
     *
     * @param val the placement strategy; defaults to {@link ViewerPlacement#reuseViewer()} if null
     * @see ViewerPlacement
     */
    public Builder placement(ViewerPlacement val) {
      this.placement = val;
      return this;
    }

    /**
     * Sets the tab focus policy.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * // Always bring to front (default)
     * builder.tabFocusPolicy(TabFocusPolicy.foreground())
     *
     * // Keep in background
     * builder.tabFocusPolicy(TabFocusPolicy.background())
     *
     * // Auto-decide based on loading time (default 2s threshold)
     * builder.tabFocusPolicy(TabFocusPolicy.autoByDuration())
     *
     * // Custom threshold
     * builder.tabFocusPolicy(TabFocusPolicy.autoByDuration(Duration.ofSeconds(5)))
     * }</pre>
     *
     * @param val the focus policy; defaults to {@link TabFocusPolicy#foreground()} if null
     * @see TabFocusPolicy
     */
    public Builder tabFocusPolicy(TabFocusPolicy val) {
      this.tabFocusPolicy = val;
      return this;
    }

    public Builder icon(Icon val) {
      this.icon = val;
      return this;
    }

    public Builder uid(String val) {
      this.uid = val;
      return this;
    }

    public Builder seriesCount(int val) {
      this.seriesCount = val;
      return this;
    }

    public ViewerOpenOptions build() {
      return new ViewerOpenOptions(placement, tabFocusPolicy, icon, uid, seriesCount);
    }
  }
}
