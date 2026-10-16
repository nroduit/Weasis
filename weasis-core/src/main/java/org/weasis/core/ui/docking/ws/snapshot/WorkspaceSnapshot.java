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

import jakarta.json.JsonObject;
import java.awt.Rectangle;
import java.util.List;
import org.weasis.core.ui.docking.ShellState;

/**
 * Serializable state of the workspace: every window (main and floating) with its screen, bounds and
 * split tree of view descriptors, and the {@link ShellState shell} around the tree (null when
 * unknown or headless: a snapshot without it leaves the shell unchanged). The foundation for
 * saving/restoring sessions and future hanging protocols; view {@code state} content is opaque to
 * the engine.
 */
public record WorkspaceSnapshot(String version, List<WindowSnapshot> windows, ShellState shell) {

  public static final String CURRENT_VERSION = "1";

  public WorkspaceSnapshot(String version, List<WindowSnapshot> windows) {
    this(version, windows, null);
  }

  /** The same windows with that shell. */
  public WorkspaceSnapshot withShell(ShellState state) {
    return new WorkspaceSnapshot(version, windows, state);
  }

  public record WindowSnapshot(
      String screenId, Rectangle bounds, boolean main, Node root, String maximizedViewId) {}

  public sealed interface Node permits SplitNode, GroupNode {}

  /** Internal split node; {@code ratio} is the fraction given to the first child. */
  public record SplitNode(boolean vertical, double ratio, Node first, Node second)
      implements Node {}

  /** Leaf tab group with its views and selected tab. */
  public record GroupNode(List<ViewDescriptor> views, int selectedIndex) implements Node {}

  /** One view: its stable id, the type used to recreate it, and its opaque state. */
  public record ViewDescriptor(String viewId, String typeId, JsonObject state) {}
}
