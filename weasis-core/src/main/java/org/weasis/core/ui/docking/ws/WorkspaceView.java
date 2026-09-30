/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

/**
 * A view hosted in a {@link Workspace} tab group. The component reference is the identity: views
 * are never registered, docking is the only act.
 */
public interface WorkspaceView {

  /** Stable identifier used by workspace snapshots and commands. */
  String viewId();

  JComponent component();

  String tabTitle();

  default Icon tabIcon() {
    return null;
  }

  default String tabTooltip() {
    return null;
  }

  default boolean isClosable() {
    return true;
  }

  /** Whether the view can be detached into its own window. */
  default boolean isDetachable() {
    return true;
  }

  /**
   * Invoked when the user closes the tab; the implementation disposes the view. Returning {@code
   * false} vetoes the close.
   */
  default boolean requestClose() {
    return true;
  }

  /** Adds the entries of the contextual menu of the tab. */
  default void populateTabMenu(JPopupMenu menu) {
    // no entries by default
  }

  /** Stable view type used to recreate the view when restoring a snapshot. */
  default String typeId() {
    return getClass().getName();
  }

  /** Content references and view state for workspace snapshots; opaque to the engine. */
  default JsonObject saveState() {
    return JsonValue.EMPTY_JSON_OBJECT;
  }
}
