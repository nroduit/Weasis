/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.explorer;

import java.awt.Component;
import java.nio.file.Path;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreeRenderer extends DefaultTreeCellRenderer {
  private static final Logger LOGGER = LoggerFactory.getLogger(TreeRenderer.class);

  @Override
  public Component getTreeCellRendererComponent(
      final JTree tree,
      final Object value,
      final boolean isSelected,
      final boolean isExpanded,
      final boolean leaf,
      final int row,
      final boolean hasFocus) {
    final Component component =
        super.getTreeCellRendererComponent(
            tree, value, isSelected, isExpanded, leaf, row, hasFocus);

    if (value instanceof TreeNode treeNode) {
      final Path selectedDir = treeNode.getNodePath();
      try {
        setIcon(JIUtility.getSystemIcon(selectedDir.toFile()));
        setText(treeNode.toString());
      } catch (Exception e) {
        LOGGER.error("", e);
      }
    }
    return component;
  }
}
