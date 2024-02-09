/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.seg;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

public class GroupTreeNode extends DefaultMutableTreeNode {
  private boolean selected;

  public GroupTreeNode(Object userObject) {
    this(userObject, true);
  }

  public GroupTreeNode(Object userObject, boolean allowsChildren) {
    super(userObject, allowsChildren);
    this.selected = true;
  }

  public boolean isSelected() {
    return selected;
  }

  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  public boolean isParentVisible() {
    if (!selected) {
      return false;
    }
    TreeNode node = getParent();
    while (node instanceof GroupTreeNode group) {
      if (!group.isSelected()) {
        return false;
      }
      node = node.getParent();
    }
    return true;
  }
}
