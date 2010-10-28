package org.weasis.base.explorer;

import java.awt.Component;
import java.io.File;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

public class TreeRenderer extends DefaultTreeCellRenderer {

    private static final long serialVersionUID = 4497513932673068084L;

    @Override
    public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean isSelected,
        final boolean isExpanded, final boolean leaf, final int row, final boolean hasFocus) {
        final Component component =
            super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded, leaf, row, hasFocus);

        if ((value != null) && (value instanceof TreeNode)) {
            final TreeNode treeNode = (TreeNode) value;

            final File selectedDir = (File) treeNode.getUserObject();

            try {
                setIcon(JIUtility.getSystemIcon(selectedDir));
                setText(treeNode.toString());
            } catch (final Exception exp) {
                // exp.printStackTrace();
            }
        }

        return component;
    }
}
