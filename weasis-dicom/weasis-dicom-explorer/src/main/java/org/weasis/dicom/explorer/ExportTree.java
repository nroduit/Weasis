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
package org.weasis.dicom.explorer;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;

import java.util.Enumeration;

import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public class ExportTree extends JScrollPane {

    private final CheckboxTree tree;
    private final CheckTreeModel model;

    public ExportTree(DicomModel dicomModel) {
        this(new CheckTreeModel(dicomModel));
    }

    public ExportTree(CheckTreeModel model) {
        if (model == null) {
            throw new IllegalArgumentException();
        }
        this.model = model;
        this.tree = new CheckboxTree(model.getModel());
        this.tree.setCheckingModel(model.getCheckingModel());
        this.tree.getCheckingModel().setCheckingMode(CheckingMode.PROPAGATE_PRESERVING_UNCHECK);
        expandTree(tree, model.getRootNode(), 3);
        setViewportView(tree);
    }

    public CheckboxTree getTree() {
        return tree;
    }

    public CheckTreeModel getModel() {
        return model;
    }

    public static void expandTree(JTree tree, DefaultMutableTreeNode start, int maxDeep) {
        if (maxDeep > 1) {
            for (Enumeration children = start.children(); children.hasMoreElements();) {
                DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) children.nextElement();
                if (!dtm.isLeaf()) {
                    TreePath tp = new TreePath(dtm.getPath());
                    tree.expandPath(tp);

                    expandTree(tree, dtm, maxDeep - 1);
                }
            }
        }
        return;
    }

}
