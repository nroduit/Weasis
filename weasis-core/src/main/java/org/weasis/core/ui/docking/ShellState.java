/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.weasis.core.ui.docking.SidePanel.Role;

/**
 * The shell around the workspace tree as a session or a UI profile records it: the two sides by
 * role ({@code explorer} leads, {@code tools} trails, whatever the locale), each with the tool ids
 * of its slots from top to bottom, its width, its slot ratio and whether the user collapsed it; and
 * the tool of the central strip. Tool ids are the stable {@link DockableTool#getToolId() ids}, so a
 * state captured on one viewer type applies to another and stays valid in any language.
 *
 * <pre>
 * "shell": {"explorer": {"slots": ["dicom-explorer", null], "width": 300, "ratio": 0.6,
 *                        "collapsed": false},
 *           "tools": {"slots": ["display", "measure"], "width": 320, "ratio": 0.5,
 *                     "collapsed": false},
 *           "strip": "mini"}
 * </pre>
 */
public record ShellState(ShellSide explorer, ShellSide tools, String strip) {

  public static final String SHELL = "shell"; // NON-NLS
  private static final String SLOTS = "slots"; // NON-NLS
  private static final String WIDTH = "width"; // NON-NLS
  private static final String RATIO = "ratio"; // NON-NLS
  private static final String COLLAPSED = "collapsed"; // NON-NLS
  private static final String STRIP = "strip"; // NON-NLS

  /**
   * One side: the tool id of each slot from top to bottom ({@code null} for an empty slot; a list,
   * so a third slot would not change the schema), the content width in pixels (0 = unchanged), the
   * share of the top slot (0 = unchanged) and the collapsed flag.
   */
  public record ShellSide(List<String> slots, int width, double ratio, boolean collapsed) {

    public ShellSide {
      // Collections.unmodifiableList keeps null entries, List.copyOf would refuse them
      slots = Collections.unmodifiableList(new ArrayList<>(slots == null ? List.of() : slots));
    }

    /** The tool id of a slot, or null when the slot is empty or beyond the list. */
    public String slot(Slot slot) {
      int i = slot.ordinal();
      return i < slots.size() ? slots.get(i) : null;
    }

    public JsonObject toJson() {
      JsonArrayBuilder ids = Json.createArrayBuilder();
      for (String id : slots) {
        if (id == null) {
          ids.addNull();
        } else {
          ids.add(id);
        }
      }
      return Json.createObjectBuilder()
          .add(SLOTS, ids)
          .add(WIDTH, width)
          .add(RATIO, ratio)
          .add(COLLAPSED, collapsed)
          .build();
    }

    public static ShellSide fromJson(JsonObject json) {
      List<String> ids = new ArrayList<>();
      JsonArray array = json.getJsonArray(SLOTS);
      if (array != null) {
        for (JsonValue value : array) {
          ids.add(
              value.getValueType() == JsonValue.ValueType.STRING
                  ? ((JsonString) value).getString()
                  : null);
        }
      }
      return new ShellSide(
          ids,
          json.getInt(WIDTH, 0),
          json.containsKey(RATIO) ? json.getJsonNumber(RATIO).doubleValue() : 0,
          json.getBoolean(COLLAPSED, false));
    }
  }

  public ShellSide side(Role role) {
    return role == Role.EXPLORER ? explorer : tools;
  }

  public JsonObject toJson() {
    JsonObjectBuilder json = Json.createObjectBuilder();
    if (explorer != null) {
      json.add(Role.EXPLORER.key(), explorer.toJson());
    }
    if (tools != null) {
      json.add(Role.TOOLS.key(), tools.toJson());
    }
    if (strip != null) {
      json.add(STRIP, strip);
    }
    return json.build();
  }

  public static ShellState fromJson(JsonObject json) {
    Objects.requireNonNull(json);
    JsonObject explorer = json.getJsonObject(Role.EXPLORER.key());
    JsonObject tools = json.getJsonObject(Role.TOOLS.key());
    return new ShellState(
        explorer == null ? null : ShellSide.fromJson(explorer),
        tools == null ? null : ShellSide.fromJson(tools),
        json.getString(STRIP, null));
  }

  /** The current state of a shell; null when the application runs without one (headless). */
  public static ShellState capture(Shell shell) {
    if (shell == null) {
      return null;
    }
    return new ShellState(
        shell.explorer().captureState(), shell.tools().captureState(), shell.strip().toolId());
  }

  /**
   * Applies a state to a shell: each side present in the state gets its slots, width, ratio and
   * collapsed flag; a strip tool that is not the current one is skipped.
   *
   * @return the tool ids the shell does not offer, skipped; empty when everything applied
   */
  public static List<String> apply(ShellState state, Shell shell) {
    List<String> skipped = new ArrayList<>();
    if (state == null || shell == null) {
      return skipped;
    }
    if (state.explorer() != null) {
      skipped.addAll(shell.explorer().applyState(state.explorer()));
    }
    if (state.tools() != null) {
      skipped.addAll(shell.tools().applyState(state.tools()));
    }
    String current = shell.strip().toolId();
    if (state.strip() == null) {
      shell.strip().hideCurrent();
    } else if (!state.strip().equals(current)) {
      skipped.add(state.strip());
    }
    return skipped;
  }
}
