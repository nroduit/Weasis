/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.util;

/**
 * 
 * <p>
 * Copyright: Copyright (c) 2002 -2005
 * </p>
 * 
 * <p>
 * Company:
 * </p>
 * 
 * @author Nicolas Roduit
 * @version 1.2.2
 */
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;

import javax.swing.JCheckBox;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import org.weasis.core.ui.Messages;

/**
 * The Class TreeLayer.
 * 
 * @author Nicolas Roduit
 */
public class TreeLayer extends JTree {

    public void constructTree(DefaultTreeModel model) {
        this.setModel(model);
        this.setCellRenderer(new CheckBoxNodeRenderer());
        // ImageIcon plus = new ImageIcon(getClass().getResource("/icon/plusTree.png"));
        // ImageIcon minus = new ImageIcon(getClass().getResource("/icon/minusTree.png"));
        // ComponentUI ui = this.getUI();
        // if (ui instanceof BasicTreeUI) {
        // ((BasicTreeUI) ui).setExpandedIcon(minus);
        // ((BasicTreeUI) ui).setCollapsedIcon(plus);
        // }
        this.setShowsRootHandles(true);
        this.setRootVisible(false);
    }

    public void addCheckedListener() {
        this.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseReleased(MouseEvent e) {
                final TreeLayer layer = (TreeLayer) e.getSource();
                TreePath path = layer.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    Object object = path.getLastPathComponent();
                    if (object instanceof CheckNode) {
                        CheckNode node = (CheckNode) object;
                        node.setSelected(!node.isSelected());
                        // page.setModified(true);
                        if (node.getChildCount() > 0) {
                            fireToChildren(node.children(), node.isSelected());
                        } else {
                            fireParentChecked(node);
                        }
                        upadateNode(node);
                    }
                }
            }
        });
    }

    public void upadateNode(CheckNode node) {
        ((DefaultTreeModel) getModel()).reload(node);
    }

    public void upadateAllNodes() {
        ((DefaultTreeModel) getModel()).reload();
    }

    public DefaultMutableTreeNode getRoot() {
        return (DefaultMutableTreeNode) getModel().getRoot();
    }

    public static void fireToChildren(Enumeration children, boolean checked) {
        while (children.hasMoreElements()) {
            CheckNode item = (CheckNode) children.nextElement();
            item.setSelected(checked);
        }
    }

    public static void fireParentChecked(CheckNode node) {
        if (node.getParent() != null && node.getParent() instanceof CheckNode) {
            CheckNode parent = (CheckNode) node.getParent();
            if (node.isSelected()) {
                parent.setSelected(true);
            } else {
                boolean needtobeChecked = true;
                Enumeration children = parent.children();
                while (children.hasMoreElements()) {
                    CheckNode item = (CheckNode) children.nextElement();
                    if (item.isSelected()) {
                        needtobeChecked = false;
                        break;
                    }
                }
                if (needtobeChecked) {
                    parent.setSelected(false);
                }
            }
        }
    }

    public static void iniParent(DefaultMutableTreeNode root) {
        Enumeration children = root.children();
        while (children.hasMoreElements()) {
            CheckNode item = (CheckNode) children.nextElement();
            Enumeration children2 = item.children();
            if (children2.hasMoreElements()) {
                item.setSelected(false);
            }
            while (children2.hasMoreElements()) {
                CheckNode item2 = (CheckNode) children2.nextElement();
                if (item2.isSelected()) {
                    item.setSelected(true);
                    break;
                }
            }
        }
    }

    static class CheckBoxNodeRenderer implements TreeCellRenderer {

        private final JCheckBox leafRenderer = new JCheckBox();

        private final DefaultTreeCellRenderer nonLeafRenderer = new DefaultTreeCellRenderer();

        Color selectionBorderColor, selectionForeground, selectionBackground, textForeground, textBackground;

        protected JCheckBox getLeafRenderer() {
            return leafRenderer;
        }

        public CheckBoxNodeRenderer() {
            Font fontValue;
            fontValue = UIManager.getFont("Tree.font"); //$NON-NLS-1$
            if (fontValue != null) {
                leafRenderer.setFont(fontValue);
            }
            Boolean booleanValue = (Boolean) UIManager.get("Tree.drawsFocusBorderAroundIcon"); //$NON-NLS-1$
            leafRenderer.setFocusPainted((booleanValue != null) && (booleanValue.booleanValue()));

            selectionBorderColor = UIManager.getColor("Tree.selectionBorderColor"); //$NON-NLS-1$
            selectionForeground = UIManager.getColor("Tree.selectionForeground"); //$NON-NLS-1$
            selectionBackground = UIManager.getColor("Tree.selectionBackground"); //$NON-NLS-1$
            textForeground = UIManager.getColor("Tree.textForeground"); //$NON-NLS-1$
            textBackground = UIManager.getColor("Tree.textBackground"); //$NON-NLS-1$
        }

        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {

            Component returnValue;
            // if (leaf) {

            String stringValue = tree.convertValueToText(value, selected, expanded, leaf, row, false);
            leafRenderer.setText(stringValue);
            leafRenderer.setSelected(false);

            leafRenderer.setEnabled(tree.isEnabled());

            if (selected) {
                leafRenderer.setForeground(selectionForeground);
                leafRenderer.setBackground(selectionBackground);
            } else {
                leafRenderer.setForeground(textForeground);
                leafRenderer.setBackground(textBackground);
            }

            if (value instanceof CheckNode) {
                CheckNode node = (CheckNode) value;
                leafRenderer.setText(node.toString());
                leafRenderer.setSelected(node.isSelected());
                returnValue = leafRenderer;
            } else {
                returnValue =
                    nonLeafRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            }
            return returnValue;
        }
    }
}
