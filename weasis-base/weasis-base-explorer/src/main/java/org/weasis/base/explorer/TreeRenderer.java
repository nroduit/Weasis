/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.base.explorer;

import java.awt.Component;
import java.nio.file.Path;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
public class TreeRenderer extends DefaultTreeCellRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TreeRenderer.class);

    @Override
    public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean isSelected,
        final boolean isExpanded, final boolean leaf, final int row, final boolean hasFocus) {
        final Component component =
            super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded, leaf, row, hasFocus);

        if (value instanceof TreeNode) {
            final TreeNode treeNode = (TreeNode) value;
            final Path selectedDir = treeNode.getNodePath();

            try {
                setIcon(JIUtility.getSystemIcon(selectedDir.toFile()));
                setText(treeNode.toString());
            } catch (Exception e) {
                LOGGER.error("", e); //$NON-NLS-1$
            }
        }

        return component;
    }
}
