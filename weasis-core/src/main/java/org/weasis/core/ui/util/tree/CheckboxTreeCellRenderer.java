/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util.tree;

import javax.swing.tree.TreeCellRenderer;

/** A tree cell renderer whose checkbox area can be hit-tested by {@link CheckboxTree}. */
public interface CheckboxTreeCellRenderer extends TreeCellRenderer {

  /**
   * Returns whether the given coordinates, relative to the node bounds, hit the checkbox. Used by
   * {@link CheckboxTree} to decide between toggling the checking and selecting the node.
   */
  boolean isOnHotspot(int x, int y);
}
