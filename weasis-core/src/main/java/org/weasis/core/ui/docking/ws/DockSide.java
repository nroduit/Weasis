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

import javax.swing.JSplitPane;

/** Side of an anchor view where a workspace split places a new tab group. */
public enum DockSide {
  TOP,
  BOTTOM,
  LEFT,
  RIGHT;

  int splitOrientation() {
    return this == LEFT || this == RIGHT ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT;
  }

  /** Whether the new group takes the first (left/top) slot of the split. */
  boolean newGroupFirst() {
    return this == LEFT || this == TOP;
  }
}
