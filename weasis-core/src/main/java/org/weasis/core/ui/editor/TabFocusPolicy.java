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

import java.time.Duration;
import java.time.Instant;

/**
 * Defines the focus policy when a new viewer tab is opened.
 *
 * <p>This sealed hierarchy allows the framework to decide whether a newly opened viewer tab should
 * steal focus from the current tab:
 *
 * <ul>
 *   <li>{@link Foreground} – always give focus to the new tab (the default behaviour).
 *   <li>{@link Background} – never give focus; the user's current view stays undisturbed.
 *   <li>{@link AutoByDuration} – give focus only if the time elapsed between when loading started
 *       (recorded via {@link #startLoadingTimestamp()}) and when the tab is ready to display is
 *       shorter than a configurable {@link Duration} threshold. When the loading takes longer than
 *       the threshold, the tab opens in the background so the user is not interrupted while working
 *       on another series.
 * </ul>
 *
 * @see ViewerOpenOptions
 */
public sealed interface TabFocusPolicy {

  /** Default threshold for {@link AutoByDuration}: 3 seconds. */
  Duration DEFAULT_AUTO_THRESHOLD = Duration.ofSeconds(3);

  // ---------------------------------------------------------------------------
  // Strategies
  // ---------------------------------------------------------------------------

  /**
   * Always give focus to the new tab.
   *
   * <p>This is the traditional behaviour: the last tab opened is immediately selected and
   * displayed.
   */
  record Foreground() implements TabFocusPolicy {}

  /**
   * Never give focus to the new tab.
   *
   * <p>The tab is created but stays behind the currently focused viewer. This is useful when
   * multiple series are loaded in batch and the user should remain on their current view.
   */
  record Background() implements TabFocusPolicy {}

  /**
   * Adaptively decide whether to give focus, based on loading duration.
   *
   * <p>When the tab becomes ready, the elapsed time since {@code loadStartedAt} is compared against
   * {@code threshold}:
   *
   * <ul>
   *   <li>If elapsed &le; threshold &rarr; the tab is brought to front (quick load, user likely
   *       expects it).
   *   <li>If elapsed &gt; threshold &rarr; the tab stays in the background (slow load, user has
   *       moved on to other work).
   * </ul>
   *
   * @param threshold the maximum acceptable loading duration before the tab is relegated to the
   *     background
   * @param loadStartedAt the instant when the loading request was initiated; if {@code null},
   *     {@link Instant#now()} is used at evaluation time (effectively behaving like {@link
   *     Foreground})
   */
  record AutoByDuration(Duration threshold, Instant loadStartedAt) implements TabFocusPolicy {

    /** Creates an auto policy with the default threshold and the current time as start. */
    public AutoByDuration() {
      this(DEFAULT_AUTO_THRESHOLD, Instant.now());
    }

    /**
     * Creates an auto policy with the given threshold and the current time as start.
     *
     * @param threshold the maximum acceptable loading duration
     */
    public AutoByDuration(Duration threshold) {
      this(threshold, Instant.now());
    }

    public AutoByDuration {
      if (threshold == null) {
        threshold = DEFAULT_AUTO_THRESHOLD;
      }
      if (loadStartedAt == null) {
        loadStartedAt = Instant.now();
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Factory methods
  // ---------------------------------------------------------------------------

  /** Always bring the new tab to front (default). */
  static Foreground foreground() {
    return new Foreground();
  }

  /** Keep the new tab in the background. */
  static Background background() {
    return new Background();
  }

  /** Auto-decide with the {@linkplain #DEFAULT_AUTO_THRESHOLD default threshold}. */
  static AutoByDuration autoByDuration() {
    return new AutoByDuration();
  }

  /**
   * Auto-decide with a custom threshold.
   *
   * @param threshold the maximum acceptable loading duration
   */
  static AutoByDuration autoByDuration(Duration threshold) {
    return new AutoByDuration(threshold);
  }

  /**
   * Auto-decide with a custom threshold and explicit start timestamp.
   *
   * @param threshold the maximum acceptable loading duration
   * @param loadStartedAt the instant when loading was initiated
   */
  static AutoByDuration autoByDuration(Duration threshold, Instant loadStartedAt) {
    return new AutoByDuration(threshold, loadStartedAt);
  }

  // ---------------------------------------------------------------------------
  // Query helpers
  // ---------------------------------------------------------------------------

  /**
   * Returns the loading start timestamp relevant for duration-based evaluation, or {@code null} if
   * not applicable.
   */
  default Instant startLoadingTimestamp() {
    if (this instanceof AutoByDuration auto) {
      return auto.loadStartedAt();
    }
    return null;
  }

  /**
   * Evaluates whether the new tab should receive focus <em>right now</em>.
   *
   * <p>Call this method when the viewer is ready to be displayed:
   *
   * <ul>
   *   <li>{@link Foreground} &rarr; always {@code true}
   *   <li>{@link Background} &rarr; always {@code false}
   *   <li>{@link AutoByDuration} &rarr; {@code true} only if the elapsed time since loadStartedAt
   *       is within the threshold
   * </ul>
   *
   * @return {@code true} if the tab should receive focus
   */
  default boolean shouldBringToFront() {
    return switch (this) {
      case Foreground _ -> true;
      case Background _ -> false;
      case AutoByDuration auto -> {
        Duration elapsed = Duration.between(auto.loadStartedAt(), Instant.now());
        yield elapsed.compareTo(auto.threshold()) <= 0;
      }
    };
  }
}
