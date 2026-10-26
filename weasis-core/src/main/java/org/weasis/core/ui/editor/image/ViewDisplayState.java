/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.awt.Component;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;

/**
 * Display state of a pane as commands report it: the resolved target, the values a display verb can
 * change, and what the pane shows. Indexes are 1-based, as everywhere in the command surface.
 */
public final class ViewDisplayState {

  /** Reported as the synchronization of a pane that is not linked to the others. */
  public static final String SYNCH_NONE = "none"; // NON-NLS

  private ViewDisplayState() {}

  /** Stable viewer type id of a viewer, or {@code null} when no factory claims it. */
  public static String viewerTypeId(SeriesViewer<?> viewer) {
    SeriesViewerFactory factory = GuiUtils.getUICore().getViewerFactory(viewer);
    return factory == null ? null : factory.getViewerTypeId();
  }

  /** Pane index of a canvas in its container, 1-based, or 0 when the canvas is not in it. */
  public static int paneIndex(ImageViewerPlugin<?> container, ViewCanvas<?> view) {
    return container.getImagePanels().indexOf(view) + 1;
  }

  /** Full display state of a pane. */
  public static JsonObject of(ImageViewerPlugin<?> container, ViewCanvas<?> view) {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("target", target(container, view)) // NON-NLS
            .add("zoom", view.getViewModel().getViewScale()) // NON-NLS
            .add("rotation", rotation(view)) // NON-NLS
            .add("flip", flip(view)) // NON-NLS
            .add(
                "pan", // NON-NLS
                Json.createObjectBuilder()
                    .add("x", view.getViewModel().getModelOffsetX())
                    .add("y", view.getViewModel().getModelOffsetY()))
            .add("grid", container.getLayoutModel().getId()) // NON-NLS
            .add("sync", sync(container, view)); // NON-NLS
    windowLevel(view).ifPresent(wl -> json.add("windowLevel", wl)); // NON-NLS
    sortKey(view).ifPresent(key -> json.add("sort", key)); // NON-NLS
    json.add("inverseStack", Boolean.TRUE.equals(view.getActionValue(ActionW.INVERSE_STACK.cmd())));
    blend(view).ifPresent(blend -> json.add("blend", blend.toJson())); // NON-NLS
    return json.build();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Optional<ViewState.Blend> blend(ViewCanvas<?> view) {
    ViewStateAdapter adapter = view.getViewStateAdapter();
    return adapter == null ? Optional.empty() : adapter.blend(view);
  }

  /**
   * The stable key of the stack sort of a pane, as the snapshots and {@code view2d:sort} name it.
   */
  private static Optional<String> sortKey(ViewCanvas<?> view) {
    return view.getActionValue(ActionW.SORT_STACK.cmd()) instanceof Comparator<?> comparator
            && view.getViewStateAdapter() != null
        ? view.getViewStateAdapter().sortKey(comparator)
        : Optional.empty();
  }

  /** What a pane shows, for the workspace listing: series, image and selection. */
  public static JsonObjectBuilder pane(
      ImageViewerPlugin<?> container, ViewCanvas<?> view, boolean selected) {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("pane", paneIndex(container, view)) // NON-NLS
            .add("selected", selected); // NON-NLS
    content(json, view);
    return json;
  }

  /** State of a whole viewer: its grid, its synchronization and what each pane shows. */
  public static JsonObject ofViewer(ImageViewerPlugin<?> container) {
    ViewCanvas<?> selected = container.getSelectedViewCanvas();
    JsonArrayBuilder panes = Json.createArrayBuilder();
    for (ViewCanvas<?> view : container.getImagePanels()) {
      panes.add(pane(container, view, view == selected));
    }
    JsonObjectBuilder target = Json.createObjectBuilder();
    if (container instanceof WorkspaceView workspaceView) {
      target.add("view", workspaceView.viewId()); // NON-NLS
    }
    String type = viewerTypeId(container);
    if (type != null) {
      target.add("viewerType", type); // NON-NLS
    }
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("target", target) // NON-NLS
            .add("grid", container.getLayoutModel().getId()) // NON-NLS
            .add("sync", sync(container, selected)) // NON-NLS
            .add("panes", panes); // NON-NLS
    if (container.supportsViewportPanes()) {
      json.add("cells", cells(container)); // NON-NLS
    }
    return json.build();
  }

  /** One entry per cell of the grid, from 1: its viewport mode and the indices of its panes. */
  private static JsonArrayBuilder cells(ImageViewerPlugin<?> container) {
    JsonArrayBuilder cells = Json.createArrayBuilder();
    List<? extends ViewCanvas<?>> all = container.getImagePanels();
    for (MigCell cell : container.getLayoutModel().getCells()) {
      Component component = container.getCellManager().getComponent(cell.position());
      if (!(component instanceof ViewportPane<?> pane)) {
        continue;
      }
      JsonArrayBuilder indices = Json.createArrayBuilder();
      pane.getAllViewCanvases().forEach(v -> indices.add(all.indexOf(v) + 1));
      JsonObjectBuilder entry =
          Json.createObjectBuilder()
              .add("cell", cell.position() + 1) // NON-NLS
              .add("mode", pane.isTiled() ? "tiled" : "stack"); // NON-NLS
      if (pane.isTiled()) {
        entry.add("cols", pane.getTiledCols()).add("rows", pane.getTiledRows()); // NON-NLS
      }
      cells.add(entry.add("panes", indices)); // NON-NLS
    }
    return cells;
  }

  /** One line for text output, in the order of the JSON body. */
  public static String text(JsonObject state) {
    JsonObject target = state.getJsonObject("target"); // NON-NLS
    StringBuilder line = new StringBuilder();
    line.append("view=")
        .append(target.getString("view", "-")) // NON-NLS
        .append(" pane=") // NON-NLS
        .append(target.getInt("pane", 0)); // NON-NLS
    JsonObject wl = state.getJsonObject("windowLevel"); // NON-NLS
    if (wl != null) {
      line.append(" window=").append(wl.getJsonNumber("window").doubleValue()); // NON-NLS
      line.append(" level=").append(wl.getJsonNumber("level").doubleValue()); // NON-NLS
      String preset = wl.getString("preset", null); // NON-NLS
      if (preset != null) {
        line.append(" preset=").append(preset); // NON-NLS
      }
    }
    if (target.containsKey("image")) { // NON-NLS
      line.append(" image=") // NON-NLS
          .append(target.getInt("image")) // NON-NLS
          .append('/')
          .append(target.getInt("imageCount")); // NON-NLS
    }
    return line.append(" zoom=") // NON-NLS
        .append(state.getJsonNumber("zoom").doubleValue()) // NON-NLS
        .append(" rotation=") // NON-NLS
        .append(state.getInt("rotation")) // NON-NLS
        .append(" flip=") // NON-NLS
        .append(state.getBoolean("flip")) // NON-NLS
        .append(" grid=") // NON-NLS
        .append(state.getString("grid")) // NON-NLS
        .append(" sync=") // NON-NLS
        .append(state.getString("sync")) // NON-NLS
        .append(state.containsKey("sort") ? " sort=" + state.getString("sort") : "") // NON-NLS
        .append(state.getBoolean("inverseStack", false) ? " inverse" : "") // NON-NLS
        .toString();
  }

  /** One line for text output of a viewer state. */
  public static String textViewer(JsonObject state) {
    JsonArray panes = state.getJsonArray("panes"); // NON-NLS
    int selected = 0;
    for (int i = 0; i < panes.size(); i++) {
      if (panes.getJsonObject(i).getBoolean("selected", false)) { // NON-NLS
        selected = panes.getJsonObject(i).getInt("pane", 0); // NON-NLS
      }
    }
    return "view=" // NON-NLS
        + state.getJsonObject("target").getString("view", "-") // NON-NLS
        + " grid=" // NON-NLS
        + state.getString("grid") // NON-NLS
        + " sync=" // NON-NLS
        + state.getString("sync") // NON-NLS
        + " panes=" // NON-NLS
        + panes.size()
        + " selected=" // NON-NLS
        + selected;
  }

  /**
   * The mode the pane is synchronized in, or {@code None}. It is off when the viewer switched the
   * synchronization off, and when this pane alone stopped following the others.
   */
  private static String sync(ImageViewerPlugin<?> container, ViewCanvas<?> view) {
    boolean on =
        container
            .getEventManager()
            .getAction(ActionW.SYNCH_MODE)
            .map(ToggleButtonListener::isSelected)
            .orElse(Boolean.TRUE);
    if (!on || view == null) {
      return SYNCH_NONE;
    }
    Object data = view.getActionValue(ActionW.SYNCH_LINK.cmd());
    boolean linked = !(data instanceof SynchData synchData) || synchData.isSynchActivated();
    return linked ? container.getSynchView().getCommand().toLowerCase(Locale.ROOT) : SYNCH_NONE;
  }

  /** The thing a result acted on: the pane, and what it shows. */
  public static JsonObjectBuilder target(ImageViewerPlugin<?> container, ViewCanvas<?> view) {
    JsonObjectBuilder json = Json.createObjectBuilder();
    if (container instanceof WorkspaceView workspaceView) {
      json.add("view", workspaceView.viewId()); // NON-NLS
    }
    json.add("pane", paneIndex(container, view)); // NON-NLS
    String type = viewerTypeId(container);
    if (type != null) {
      json.add("viewerType", type); // NON-NLS
    }
    content(json, view);
    return json;
  }

  /** The series and the image a pane shows, and the DICOM frame when the instance has several. */
  private static void content(JsonObjectBuilder json, ViewCanvas<?> view) {
    series(view).ifPresent(uid -> json.add("series", uid)); // NON-NLS
    ImageElement image = view.getImage();
    if (image != null) {
      json.add("image", view.getFrameIndex() + 1); // NON-NLS
      json.add("imageCount", imageCount(view)); // NON-NLS
      frame(image).ifPresent(frame -> json.add("frame", frame)); // NON-NLS
    }
  }

  private static Optional<Integer> frame(ImageElement image) {
    Integer frames = (Integer) image.getTagValue(TagW.get("NumberOfFrames")); // NON-NLS
    if (frames != null && frames > 1 && image.getKey() instanceof Integer index) {
      return Optional.of(index + 1);
    }
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  private static <E extends ImageElement> int imageCount(ViewCanvas<E> view) {
    MediaSeries<E> series = view.getSeries();
    return series == null
        ? 0
        : series.size((Filter<E>) view.getActionValue(ActionW.FILTERED_SERIES.cmd()));
  }

  private static Optional<String> series(ViewCanvas<?> view) {
    MediaSeries<?> series = view.getSeries();
    if (series == null) {
      return Optional.empty();
    }
    return Optional.ofNullable((String) series.getTagValue(TagW.get("SeriesInstanceUID")));
  }

  private static Optional<JsonObjectBuilder> windowLevel(ViewCanvas<?> view) {
    OpManager manager = view.getDisplayOpManager();
    Double window =
        manager.getParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd(), Double.class).orElse(null);
    Double level =
        manager.getParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd(), Double.class).orElse(null);
    if (window == null || level == null) {
      return Optional.empty();
    }
    JsonObjectBuilder json =
        Json.createObjectBuilder().add("window", window).add("level", level); // NON-NLS
    manager
        .getParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd())
        .ifPresent(preset -> json.add("preset", preset.toString())); // NON-NLS
    return Optional.of(json);
  }

  private static int rotation(ViewCanvas<?> view) {
    Object value = view.getActionValue(ActionW.ROTATION.cmd());
    return value instanceof Integer degrees ? degrees : 0;
  }

  private static boolean flip(ViewCanvas<?> view) {
    return Boolean.TRUE.equals(view.getActionValue(ActionW.FLIP.cmd()));
  }
}
