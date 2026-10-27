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
import org.weasis.core.api.command.GraphicId;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventBus;
import org.weasis.core.api.event.EventType;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;

/**
 * Publishes the {@code graphic:} events of a graphic model: a graphic added or removed, a graphic
 * whose shape or label changed (coalesced per graphic), and the selection. The payload carries the
 * graphic id the {@code graphics} verbs use and the id of the model, which is that of its view's
 * series.
 */
public final class GraphicEventBridge {

  public static final String ADDED = "graphic:added"; // NON-NLS
  public static final String REMOVED = "graphic:removed"; // NON-NLS
  public static final String CHANGED = "graphic:changed"; // NON-NLS
  public static final String SELECTION_CHANGED = "graphic:selection.changed"; // NON-NLS

  private static final String SINCE = "5.0"; // NON-NLS
  private static final String GRAPHIC = "graphic"; // NON-NLS
  private static final String MODEL = "model"; // NON-NLS

  private GraphicEventBridge() {}

  public static List<EventType> types() {
    return List.of(
        EventType.of(ADDED, SINCE, "A graphic was drawn or loaded.")
            .field(GRAPHIC, GraphicId.class, "graphic id")
            .field("tool", String.class, "the kind of graphic, as its tool names it")
            .field("layer", String.class, "layer type")
            .field(MODEL, String.class, "id of the graphic model")
            .build(),
        EventType.of(REMOVED, SINCE, "A graphic was deleted.")
            .field(GRAPHIC, GraphicId.class, "graphic id")
            .field(MODEL, String.class, "id of the graphic model")
            .build(),
        EventType.of(CHANGED, SINCE, "A graphic moved, was resized or relabelled.")
            .coalesced()
            .field(GRAPHIC, GraphicId.class, "graphic id")
            .field("view", String.class, "the viewer, when the model is shown")
            .build(),
        EventType.of(SELECTION_CHANGED, SINCE, "The selected graphics of a view changed.")
            .field("graphics", "array", "the selected graphic ids")
            .field("view", String.class, "the viewer, when the model is shown")
            .build());
  }

  public static void added(GraphicModel model, Graphic graphic) {
    EventBus.global()
        .publish(
            Event.of(ADDED)
                .put(GRAPHIC, graphic.getUuid())
                .put("tool", graphic.getUIName()) // NON-NLS
                .put("layer", graphic.getLayerType()) // NON-NLS
                .put(MODEL, model.getUuid()));
  }

  public static void removed(GraphicModel model, Graphic graphic) {
    EventBus.global()
        .publish(Event.of(REMOVED).put(GRAPHIC, graphic.getUuid()).put(MODEL, model.getUuid()));
  }

  public static void changed(String viewId, Graphic graphic) {
    EventBus.global()
        .publish(
            Event.of(CHANGED)
                .coalesce(CHANGED + ":" + graphic.getUuid())
                .put(GRAPHIC, graphic.getUuid())
                .put("view", viewId)); // NON-NLS
  }

  public static void selectionChanged(String viewId, List<Graphic> selected) {
    EventBus.global()
        .publish(
            Event.of(SELECTION_CHANGED)
                .put("graphics", selected.stream().map(Graphic::getUuid).toList()) // NON-NLS
                .put("view", viewId)); // NON-NLS
  }
}
