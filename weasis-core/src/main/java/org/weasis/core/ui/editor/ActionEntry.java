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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.Locale;
import java.util.Objects;

/**
 * One action of the workspace as the command palette and {@code ui:actions} list it: a menu entry,
 * a toolbar button or a tool panel, under a stable id, with the group it belongs to, its shortcut
 * and action command when it has them, and whether the selected view shows it now.
 *
 * @param id a stable token such as {@code menu/file/open/dicom}, {@code toolbar/viewer/zoom},
 *     {@code tool/measure}
 * @param label the text the user sees
 * @param group the menu, toolbar or kind the action comes from
 * @param shortcut readable key binding, empty when none
 * @param command the action command, empty when none
 * @param relevant whether the selected view shows the action (a hidden one still runs)
 * @param run what the action does, on the event dispatch thread
 */
public record ActionEntry(
    String id,
    String label,
    String group,
    String shortcut,
    String command,
    boolean relevant,
    Runnable run) {

  public ActionEntry {
    Objects.requireNonNull(id);
    Objects.requireNonNull(label);
    group = group == null ? "" : group;
    shortcut = shortcut == null ? "" : shortcut;
    command = command == null ? "" : command;
    Objects.requireNonNull(run);
  }

  /** A path token from a label: lower case, letters and digits kept, the rest a dash. */
  public static String slug(String text) {
    return text.toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", "-")
        .replaceAll("^-+|-+$", "");
  }

  public JsonObject toJson() {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("id", id) // NON-NLS
            .add("label", label) // NON-NLS
            .add("group", group) // NON-NLS
            .add("relevant", relevant); // NON-NLS
    if (!shortcut.isEmpty()) {
      json.add("shortcut", shortcut); // NON-NLS
    }
    if (!command.isEmpty()) {
      json.add("command", command); // NON-NLS
    }
    return json.build();
  }
}
