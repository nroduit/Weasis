/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.weasis.core.api.command.CommandOrigin;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.GroupNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.Node;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.SplitNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.ViewDescriptor;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.WindowSnapshot;

/**
 * One record of the restorable state of the display, taken at a transaction boundary: the workspace
 * (windows, tab groups, views with their state, the shell), the graphics of every image a pane
 * shows, the studies loaded, the selected view and the active hanging. Data only, no component and
 * no live model: a few kilobytes that the {@link CheckpointService} puts back with the restorers
 * the sessions already use.
 *
 * <p>Not captured, and said so by {@link #NOT_CAPTURED}: what left the application (sends, exports,
 * prints, files written), the settings that are not documents, the download cache, the graphics of
 * images no pane shows, the scroll positions and dividers outside the shell state.
 *
 * @param id the checkpoint id, the instant it was taken
 * @param seq the event sequence number at the boundary
 * @param label what the checkpoint precedes, for the user
 * @param origin who asked for the work it precedes
 * @param workspace the layout, the view states and the shell
 * @param graphics the serialized graphic model per displayed image, keyed by {@link #imageKey}
 * @param studies the Study Instance UIDs loaded in the model
 * @param selectedView the id of the selected view, or null
 * @param protocol the protocol hung on the selected viewer, or null
 * @param step the step of that protocol, from 1
 * @param bindings display set number to series id of that step
 * @param derived the series a plugin derived, to build them again before a rollback
 * @param automatic true for a boundary the application took by itself (a script, a remote batch, a
 *     hanging, a restore), false for one the user or the caller asked for by name
 */
public record Checkpoint(
    String id,
    long seq,
    Instant at,
    String label,
    CommandOrigin origin,
    WorkspaceSnapshot workspace,
    Map<String, String> graphics,
    Set<String> studies,
    String selectedView,
    String protocol,
    int step,
    Map<Integer, String> bindings,
    List<Session.Derived> derived,
    boolean automatic) {

  /** What a checkpoint does not hold, stated to the caller of every rollback. */
  public static final List<String> NOT_CAPTURED =
      List.of(
          "what left the application: sends, exports, prints and files written",
          "the settings that are not documents",
          "the graphics of the images no pane shows",
          "scroll positions and dividers outside the shell state");

  public Checkpoint {
    Objects.requireNonNull(id);
    Objects.requireNonNull(at);
    Objects.requireNonNull(workspace);
    graphics = graphics == null ? Map.of() : Map.copyOf(graphics);
    studies = studies == null ? Set.of() : Set.copyOf(studies);
    bindings = bindings == null ? Map.of() : Map.copyOf(bindings);
    derived = derived == null ? List.of() : List.copyOf(derived);
  }

  /** The key of an image in {@link #graphics}: its SOP Instance UID and its key in the series. */
  public static String imageKey(String sopInstanceUid, Object key) {
    return sopInstanceUid + "#" + key;
  }

  /** True when the other checkpoint shows the same display: nothing to keep twice. */
  public boolean sameDisplayAs(Checkpoint other) {
    return other != null
        && workspace.equals(other.workspace)
        && graphics.equals(other.graphics)
        && studies.equals(other.studies)
        && Objects.equals(selectedView, other.selectedView);
  }

  /** The views of the workspace by id, in layout order. */
  public Map<String, ViewDescriptor> views() {
    return viewsOf(workspace);
  }

  static Map<String, ViewDescriptor> viewsOf(WorkspaceSnapshot snapshot) {
    Map<String, ViewDescriptor> views = new LinkedHashMap<>();
    for (WindowSnapshot window : snapshot.windows()) {
      collect(window.root(), views);
    }
    return views;
  }

  private static void collect(Node node, Map<String, ViewDescriptor> views) {
    if (node instanceof SplitNode split) {
      collect(split.first(), views);
      collect(split.second(), views);
    } else if (node instanceof GroupNode group) {
      for (ViewDescriptor view : group.views()) {
        views.put(view.viewId(), view);
      }
    }
  }

  /** The series ids the views of the workspace show. */
  public Set<String> series() {
    return new LinkedHashSet<>(StateCommands.seriesOf(workspace));
  }

  /** The session this checkpoint promotes to, with its sources resolved by the caller. */
  Session toSession(String sessionLabel, boolean pin, List<Session.Source> sources) {
    Instant now = Instant.now();
    return new Session(
        now.toString(),
        sessionLabel,
        pin,
        now,
        now,
        sources,
        protocol,
        step,
        bindings,
        workspace,
        derived);
  }

  /** The summary a verb prints: identity, label, what it holds, never the content. */
  public JsonObjectBuilder summary() {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("checkpoint", id) // NON-NLS
            .add("seq", seq) // NON-NLS
            .add("at", at.toString()); // NON-NLS
    if (label != null) {
      json.add("label", label); // NON-NLS
    }
    if (origin != null) {
      json.add("origin", origin.name().toLowerCase()); // NON-NLS
    }
    json.add("views", views().size()) // NON-NLS
        .add("graphics", graphics.size()) // NON-NLS
        .add("studies", studies.size()); // NON-NLS
    if (selectedView != null) {
      json.add("selectedView", selectedView); // NON-NLS
    }
    if (protocol != null) {
      json.add("protocol", protocol).add("step", step); // NON-NLS
    }
    json.add("automatic", automatic); // NON-NLS
    return json;
  }
}
