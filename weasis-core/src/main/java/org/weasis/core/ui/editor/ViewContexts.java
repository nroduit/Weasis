/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewDisplayState;

/**
 * Builds and publishes the {@link ViewContext}: the context of the selected viewer is computed when
 * the selection changes and when the selected series of that viewer changes, with the facts of
 * every {@link ViewContextContributor}, and handed to the listeners (the tool contributions, the
 * commands). The last published context is {@link #current()}.
 */
public final class ViewContexts {

  private static final Logger LOGGER = LoggerFactory.getLogger(ViewContexts.class);

  private static final List<Consumer<ViewContext>> LISTENERS = new CopyOnWriteArrayList<>();
  private static volatile ViewContext current = ViewContext.empty();
  private static volatile Supplier<List<ViewContextContributor>> contributors =
      () -> GuiUtils.getUICore().getPluginManager().getExtensions(ViewContextContributor.class);

  private ViewContexts() {}

  /** The last published context; empty before the first selection. */
  public static ViewContext current() {
    return current;
  }

  public static void addListener(Consumer<ViewContext> listener) {
    LISTENERS.add(Objects.requireNonNull(listener));
  }

  public static void removeListener(Consumer<ViewContext> listener) {
    LISTENERS.remove(listener);
  }

  /** Replaces the contributor lookup, for tests. */
  public static void setContributors(Supplier<List<ViewContextContributor>> supplier) {
    contributors = Objects.requireNonNull(supplier);
  }

  /** The context of a viewer and the series it selects, with the facts of the contributors. */
  public static ViewContext of(SeriesViewer<?> viewer, MediaSeries<?> series) {
    if (viewer == null) {
      return ViewContext.empty();
    }
    Map<String, Object> facts = new LinkedHashMap<>();
    for (ViewContextContributor contributor : contributors.get()) {
      try {
        contributor.contribute(viewer, series, facts);
      } catch (RuntimeException e) {
        LOGGER.warn("Context contributor {} failed", contributor.getClass().getName(), e);
      }
    }
    return new ViewContext(viewer, ViewDisplayState.viewerTypeId(viewer), series, facts);
  }

  /** The context of a viewer on its selected series. */
  public static ViewContext of(SeriesViewer<?> viewer) {
    return of(viewer, selectedSeries(viewer));
  }

  /** The series the viewer selects: the one of its selected canvas for an image viewer. */
  public static MediaSeries<?> selectedSeries(SeriesViewer<?> viewer) {
    if (viewer instanceof ImageViewerPlugin<?> container) {
      ViewCanvas<?> canvas = container.getSelectedViewCanvas();
      return canvas == null ? null : canvas.getSeries();
    }
    return null;
  }

  /** Publishes a context: it becomes the current one and the listeners see it. */
  public static void publish(ViewContext context) {
    current = context == null ? ViewContext.empty() : context;
    for (Consumer<ViewContext> listener : LISTENERS) {
      try {
        listener.accept(current);
      } catch (RuntimeException e) {
        LOGGER.warn("Context listener failed", e);
      }
    }
  }

  /** Computes and publishes the context of a viewer on its selected series. */
  public static void publish(SeriesViewer<?> viewer) {
    publish(of(viewer));
  }

  /** Computes and publishes the context of a viewer on a series. */
  public static void publish(SeriesViewer<?> viewer, MediaSeries<?> series) {
    publish(of(viewer, series));
  }
}
