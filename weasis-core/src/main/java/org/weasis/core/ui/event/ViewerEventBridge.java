/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.event;

import java.util.List;
import java.util.Optional;
import org.weasis.core.api.command.ViewId;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventBus;
import org.weasis.core.api.event.EventType;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 * Translates the {@link SeriesViewerEvent}s an {@link ImageViewerEventManager} fires into {@code
 * view2d:} and {@code workspace:} events on the bus: the image a view shows, its window and level
 * while they are dragged (coalesced, for the UI) and once released (streamed), and its layout. The
 * payload carries the view id of {@code workspace:list} and the series id of {@code dicom:list}.
 */
public final class ViewerEventBridge {

  public static final String SERIES_CHANGED = "view2d:series.changed"; // NON-NLS
  public static final String IMAGE_CHANGED = "view2d:image.changed"; // NON-NLS
  public static final String WINDOW_LEVEL_CHANGING = "view2d:windowLevel.changing"; // NON-NLS
  public static final String WINDOW_LEVEL_CHANGED = "view2d:windowLevel.changed"; // NON-NLS
  public static final String LAYOUT_CHANGED = "workspace:layout.changed"; // NON-NLS

  private static final String SINCE = "5.0"; // NON-NLS
  private static final String VIEW = "view"; // NON-NLS
  private static final String SERIES = "series"; // NON-NLS

  private ViewerEventBridge() {}

  public static List<EventType> types() {
    return List.of(
        EventType.of(
                SERIES_CHANGED,
                SINCE,
                "A pane of a viewer shows another series, or none; whatever opened it.")
            .field(VIEW, ViewId.class, "the viewer")
            .field("pane", int.class, "pane index in the layout, from 1")
            .field(
                SERIES, String.class, "series id, as dicom:list names it; absent for an empty pane")
            .build(),
        EventType.of(IMAGE_CHANGED, SINCE, "The selected pane of a viewer shows another image.")
            .coalesced()
            .field(VIEW, ViewId.class, "the viewer")
            .field(SERIES, String.class, "series id, as dicom:list names it")
            .field("imageIndex", int.class, "position of the image in the series, from 1")
            .build(),
        EventType.of(
                WINDOW_LEVEL_CHANGING,
                SINCE,
                "Window or level of the selected pane moves under a drag; local subscribers only.")
            .coalesced()
            .field(VIEW, ViewId.class, "the viewer")
            .field("window", double.class, "window width")
            .field("level", double.class, "window center")
            .build(),
        EventType.of(WINDOW_LEVEL_CHANGED, SINCE, "Window or level of the selected pane was set.")
            .coalesced()
            .field(VIEW, ViewId.class, "the viewer")
            .field("window", double.class, "window width")
            .field("level", double.class, "window center")
            .build(),
        EventType.of(LAYOUT_CHANGED, SINCE, "A viewer changed its pane layout.")
            .field(VIEW, ViewId.class, "the viewer")
            .field("layout", String.class, "layout id")
            .build());
  }

  /** Publishes the bus event an {@link ImageViewerEventManager} event stands for, if any. */
  public static void publish(ImageViewerEventManager<?> manager, SeriesViewerEvent event) {
    Optional<String> view = viewId(event.getSeriesViewer());
    if (view.isEmpty()) {
      return;
    }
    EventBus bus = EventBus.global();
    switch (event.getEventType()) {
      case SELECT -> {
        if (event.getMediaElement() != null) {
          bus.publish(
              Event.of(IMAGE_CHANGED)
                  .coalesce(IMAGE_CHANGED + ":" + view.get())
                  .put(VIEW, view.get())
                  .put(SERIES, seriesId(event.getSeries()))
                  .put(
                      "imageIndex",
                      imageIndex(event.getSeries(), event.getMediaElement()))); // NON-NLS
        }
      }
      case WIN_LEVEL -> windowLevel(bus, manager, view.get());
      case LAYOUT -> {
        Event.Builder layout = Event.of(LAYOUT_CHANGED).put(VIEW, view.get());
        if (event.getSeriesViewer() instanceof ImageViewerPlugin<?> plugin
            && plugin.getLayoutModel() != null) {
          layout.put("layout", plugin.getLayoutModel().getId()); // NON-NLS
        }
        bus.publish(layout);
      }
      default -> {
        // selection of a pane, info toggles and LUT changes stay local to the viewer
      }
    }
  }

  /** The dragging form on every change, the final form once the sliders are released. */
  private static void windowLevel(EventBus bus, ImageViewerEventManager<?> manager, String view) {
    Optional<SliderChangeListener> window = manager.getAction(ActionW.WINDOW);
    Optional<SliderChangeListener> level = manager.getAction(ActionW.LEVEL);
    if (window.isEmpty() || level.isEmpty()) {
      return;
    }
    boolean adjusting =
        window.get().getSliderModel().getValueIsAdjusting()
            || level.get().getSliderModel().getValueIsAdjusting();
    String name = adjusting ? WINDOW_LEVEL_CHANGING : WINDOW_LEVEL_CHANGED;
    bus.publish(
        Event.of(name)
            .coalesce(name + ":" + view)
            .put(VIEW, view)
            .put("window", window.get().getRealValue()) // NON-NLS
            .put("level", level.get().getRealValue())); // NON-NLS
  }

  /**
   * A pane shows another series: the one path every opener takes (a hanging protocol, a drop, the
   * explorer, a verb), so the DICOM bridge derives the opened and closed studies from it.
   */
  public static void seriesChanged(ViewCanvas<?> pane, MediaSeries<?> series) {
    if (!(pane instanceof java.awt.Component component)) {
      return;
    }
    java.awt.Container container =
        javax.swing.SwingUtilities.getAncestorOfClass(ImageViewerPlugin.class, component);
    if (!(container instanceof ImageViewerPlugin<?> plugin)
        || !(plugin instanceof WorkspaceView view)) {
      return;
    }
    int index = plugin.getImagePanels().indexOf(pane);
    Event.Builder event = Event.of(SERIES_CHANGED).put(VIEW, view.viewId());
    if (index >= 0) {
      event.put("pane", index + 1); // NON-NLS
    }
    EventBus.global().publish(event.put(SERIES, seriesId(series)));
  }

  /** The workspace view id of a viewer, when it is one. */
  public static Optional<String> viewId(SeriesViewer<?> viewer) {
    return viewer instanceof WorkspaceView view
        ? Optional.ofNullable(view.viewId())
        : Optional.empty();
  }

  /** The handle {@code dicom:list} gives a series, which tells split sub-series apart. */
  public static String seriesId(MediaSeries<?> series) {
    if (series == null) {
      return null;
    }
    Object id = series.getTagValue(TagW.SubseriesInstanceUID);
    return id == null ? null : String.valueOf(id);
  }

  @SuppressWarnings("unchecked")
  private static Integer imageIndex(MediaSeries<?> series, MediaElement image) {
    if (series == null || image == null) {
      return null;
    }
    if (!(series instanceof Series<?> typed)) {
      return null;
    }
    try {
      int index = ((Series<MediaElement>) typed).getImageIndex(image, null, null);
      return index < 0 ? null : index + 1;
    } catch (RuntimeException e) {
      return null;
    }
  }
}
