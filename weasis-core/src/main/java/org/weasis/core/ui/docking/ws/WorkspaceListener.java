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

import java.awt.Window;

/** Notifications from the workspace engine. */
@FunctionalInterface
public interface WorkspaceListener {

  /** A view became the visible selection of its tab group. */
  void viewSelected(WorkspaceView view, Window window);

  /** The last view of the window's workspace was removed. */
  default void workspaceEmptied(Window window) {
    // optional
  }
}
