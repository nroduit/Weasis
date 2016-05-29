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

        if (value != null && value instanceof TreeNode) {
            final TreeNode treeNode = (TreeNode) value;
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
