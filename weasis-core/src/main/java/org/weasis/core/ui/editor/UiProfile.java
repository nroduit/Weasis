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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.ui.docking.ShellState;
import org.weasis.core.ui.docking.ShellState.ShellSide;
import org.weasis.core.ui.editor.image.MouseActionType;
import org.weasis.core.ui.editor.image.MouseButton;

/**
 * What a reading needs around the viewers: a shell template (the tool id of each slot, named by
 * role), the toolbars shown and their order, the context-menu entries, the mouse bindings and the
 * measurement profile. A section left null states nothing and leaves the workspace as it is.
 *
 * @param shell the slots per side, width and ratio always 0 (they belong to the user); a side
 *     absent is left alone, a null slot is emptied
 * @param toolbars the toolbar ids ({@code Toolbar.getToolbarId()}) shown, in that order; the others
 *     leave the row while the profile is active
 * @param menus the context-menu entries shown, by the key of their site switch without the {@code
 *     weasis.contextmenu.} prefix ({@code presets}, {@code zoom}, ...)
 * @param mouse the action of each button, as {@code MouseActionType} names them
 * @param measurementProfile the id of the measurement profile laid over the user's
 */
public record UiProfile(
    String id,
    String name,
    ShellState shell,
    List<String> toolbars,
    List<String> menus,
    Map<MouseButton, MouseActionType> mouse,
    String measurementProfile) {

  public static final String NAME = "name"; // NON-NLS
  public static final String TOOLBARS = "toolbars"; // NON-NLS
  public static final String MENUS = "menus"; // NON-NLS
  public static final String MOUSE = "mouse"; // NON-NLS
  public static final String MEASUREMENT_PROFILE = "measurementProfile"; // NON-NLS

  public UiProfile {
    Objects.requireNonNull(id);
    shell = template(shell);
    toolbars = toolbars == null ? null : List.copyOf(toolbars);
    menus = menus == null ? null : List.copyOf(menus);
    mouse = mouse == null ? null : Map.copyOf(mouse);
  }

  /** The shell sides with their slots and collapsed flag only. */
  private static ShellState template(ShellState state) {
    if (state == null) {
      return null;
    }
    return new ShellState(template(state.explorer()), template(state.tools()), state.strip());
  }

  private static ShellSide template(ShellSide side) {
    return side == null ? null : new ShellSide(side.slots(), 0, 0, side.collapsed(), side.views());
  }

  /** Whether the profile lists that toolbar, true when it states nothing about toolbars. */
  public boolean showsToolbar(String toolbarId) {
    return toolbars == null || toolbars.contains(toolbarId);
  }

  /** Whether the profile shows that entry, true when it states nothing about the menus. */
  public boolean showsMenu(String entry) {
    return menus == null || menus.contains(entry);
  }

  /** The rank of a toolbar in the profile's order, the size of the list when unlisted. */
  public int rankOf(String toolbarId) {
    int index = toolbars == null ? -1 : toolbars.indexOf(toolbarId);
    return index < 0 ? (toolbars == null ? 0 : toolbars.size()) : index;
  }

  /** This profile with another shell template, as the user shaped the slots. */
  public UiProfile withShell(ShellState template) {
    return new UiProfile(id, name, template, toolbars, menus, mouse, measurementProfile);
  }

  /** This profile with another toolbar list. */
  public UiProfile withToolbars(List<String> ids) {
    return new UiProfile(id, name, shell, ids, menus, mouse, measurementProfile);
  }

  public JsonObject toJson() {
    JsonObjectBuilder json = Json.createObjectBuilder().add(ListDocument.ID, id);
    JsonUtil.addIfPresent(json, NAME, name);
    if (shell != null) {
      json.add(ShellState.SHELL, shell.toJson());
    }
    if (toolbars != null) {
      json.add(TOOLBARS, strings(toolbars));
    }
    if (menus != null) {
      json.add(MENUS, strings(menus));
    }
    if (mouse != null) {
      JsonObjectBuilder bindings = Json.createObjectBuilder();
      mouse.forEach((button, action) -> bindings.add(button.key(), action.toString()));
      json.add(MOUSE, bindings);
    }
    JsonUtil.addIfPresent(json, MEASUREMENT_PROFILE, measurementProfile);
    return json.build();
  }

  private static JsonArrayBuilder strings(List<String> values) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    values.forEach(array::add);
    return array;
  }

  /**
   * @throws IllegalArgumentException when the entry has no id, or a mouse binding names an unknown
   *     button or action
   */
  public static UiProfile fromJson(JsonObject json) {
    String id = json.getString(ListDocument.ID, null);
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("A UI profile needs an id");
    }
    Map<MouseButton, MouseActionType> mouse = null;
    if (json.get(MOUSE) instanceof JsonObject bindings) {
      mouse = new LinkedHashMap<>();
      for (Map.Entry<String, JsonValue> e : bindings.entrySet()) {
        MouseButton button = MouseButton.of(e.getKey());
        if (button == null || !(e.getValue() instanceof JsonString action)) {
          throw new IllegalArgumentException(
              "UI profile " + id + ": unknown mouse button " + e.getKey());
        }
        mouse.put(button, MouseActionType.ofCommand(action.getString()));
      }
    }
    return new UiProfile(
        id,
        json.getString(NAME, null),
        json.get(ShellState.SHELL) instanceof JsonObject shell ? ShellState.fromJson(shell) : null,
        json.containsKey(TOOLBARS) ? new ArrayList<>(JsonUtil.getStringList(json, TOOLBARS)) : null,
        json.containsKey(MENUS) ? new ArrayList<>(JsonUtil.getStringList(json, MENUS)) : null,
        mouse,
        json.getString(MEASUREMENT_PROFILE, null));
  }
}
