/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws.snapshot;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.GroupNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.Node;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.SplitNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.ViewDescriptor;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.WindowSnapshot;

/** JSON serialization of {@link WorkspaceSnapshot}. */
public final class WorkspaceSnapshotIO {

  private WorkspaceSnapshotIO() {}

  public static JsonObject toJson(WorkspaceSnapshot snapshot) {
    JsonArrayBuilder windows = Json.createArrayBuilder();
    for (WindowSnapshot window : snapshot.windows()) {
      JsonObjectBuilder w = Json.createObjectBuilder();
      if (window.screenId() != null) {
        w.add("screenId", window.screenId());
      }
      if (window.bounds() != null) {
        w.add("bounds", toJson(window.bounds()));
      }
      w.add("main", window.main());
      if (window.maximizedViewId() != null) {
        w.add("maximizedViewId", window.maximizedViewId());
      }
      w.add("root", toJson(window.root()));
      windows.add(w);
    }
    return Json.createObjectBuilder()
        .add("version", snapshot.version())
        .add("windows", windows)
        .build();
  }

  private static JsonObject toJson(Rectangle r) {
    return Json.createObjectBuilder()
        .add("x", r.x)
        .add("y", r.y)
        .add("width", r.width)
        .add("height", r.height)
        .build();
  }

  private static JsonObject toJson(Node node) {
    return switch (node) {
      case SplitNode(boolean vertical, double ratio, Node first, Node second) ->
          Json.createObjectBuilder()
              .add("type", "split")
              .add("vertical", vertical)
              .add("ratio", ratio)
              .add("first", toJson(first))
              .add("second", toJson(second))
              .build();
      case GroupNode(List<ViewDescriptor> views, int selectedIndex) -> {
        JsonArrayBuilder array = Json.createArrayBuilder();
        for (ViewDescriptor view : views) {
          array.add(
              Json.createObjectBuilder()
                  .add("id", view.viewId())
                  .add("type", view.typeId())
                  .add("state", view.state() == null ? JsonValue.EMPTY_JSON_OBJECT : view.state()));
        }
        yield Json.createObjectBuilder()
            .add("type", "group")
            .add("selected", selectedIndex)
            .add("views", array)
            .build();
      }
    };
  }

  public static WorkspaceSnapshot fromJson(JsonObject json) {
    List<WindowSnapshot> windows =
        json.getJsonArray("windows").stream()
            .map(JsonValue::asJsonObject)
            .map(WorkspaceSnapshotIO::windowFromJson)
            .toList();
    return new WorkspaceSnapshot(
        json.getString("version", WorkspaceSnapshot.CURRENT_VERSION), windows);
  }

  private static WindowSnapshot windowFromJson(JsonObject w) {
    Rectangle bounds = null;
    JsonObject b = w.getJsonObject("bounds");
    if (b != null) {
      bounds =
          new Rectangle(
              b.getInt("x", 0), b.getInt("y", 0), b.getInt("width", 0), b.getInt("height", 0));
    }
    return new WindowSnapshot(
        w.getString("screenId", null),
        bounds,
        w.getBoolean("main", true),
        nodeFromJson(w.getJsonObject("root")),
        w.getString("maximizedViewId", null));
  }

  private static Node nodeFromJson(JsonObject node) {
    if ("split".equals(node.getString("type"))) { // NON-NLS
      return new SplitNode(
          node.getBoolean("vertical", false),
          node.getJsonNumber("ratio").doubleValue(),
          nodeFromJson(node.getJsonObject("first")),
          nodeFromJson(node.getJsonObject("second")));
    }
    List<ViewDescriptor> views =
        node.getJsonArray("views").stream()
            .map(JsonValue::asJsonObject)
            .map(
                v ->
                    new ViewDescriptor(
                        v.getString("id"), v.getString("type"), v.getJsonObject("state")))
            .toList();
    return new GroupNode(views, node.getInt("selected", 0));
  }

  public static void write(WorkspaceSnapshot snapshot, Path path) throws IOException {
    try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        JsonWriter jsonWriter = Json.createWriter(writer)) {
      jsonWriter.writeObject(toJson(snapshot));
    }
  }

  public static WorkspaceSnapshot read(Path path) throws IOException {
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        JsonReader jsonReader = Json.createReader(reader)) {
      return fromJson(jsonReader.readObject());
    }
  }
}
