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
package org.weasis.core.api.gui.util;

import java.util.Locale;
import java.util.ResourceBundle;

import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.weasis.core.api.Messages;

/**
 * <p>
 * Title: JMicroVision
 * </p>
 * 
 * <p>
 * Description: ImageJai processing and analysis
 * </p>
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
public class TreeSelection extends JTree {

    public void constructTree(DefaultTreeModel model) {
        this.setModel(model);
        this.setShowsRootHandles(true);
        this.setRootVisible(false);
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        ImageIcon plus = new ImageIcon(getClass().getResource("/icon/plusTree.png")); //$NON-NLS-1$
        ImageIcon minus = new ImageIcon(getClass().getResource("/icon/minusTree.png")); //$NON-NLS-1$
        ComponentUI ui = this.getUI();
        if (ui instanceof BasicTreeUI) {
            ((BasicTreeUI) ui).setExpandedIcon(minus);
            ((BasicTreeUI) ui).setCollapsedIcon(plus);
        }
        renderer.setOpenIcon(null);
        renderer.setClosedIcon(null);
        renderer.setLeafIcon(null);
        this.setCellRenderer(renderer);
    }

    public DefaultMutableTreeNode getRoot() {
        return (DefaultMutableTreeNode) getModel().getRoot();
    }

    public TreePath getTreePath(DefaultMutableTreeNode node) {
        ResourceBundle.getBundle("javax.help.resources.Constants", Locale.getDefault()); //$NON-NLS-1$
        return new TreePath(((DefaultTreeModel) getModel()).getPathToRoot(node));
    }

}
