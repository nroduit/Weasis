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

import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.weasis.dicom.explorer.CheckTreeModel.ToolTipTreeNode;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;

public class ExportTree extends JScrollPane {

    private final CheckboxTree checkboxTree;
    private final CheckTreeModel checkTreeModel;

    public ExportTree(DicomModel dicomModel) {
        this(new CheckTreeModel(dicomModel));
    }

    public ExportTree(final CheckTreeModel checkTreeModel) {
        if (checkTreeModel == null) {
            throw new IllegalArgumentException();
        }

        this.checkTreeModel = checkTreeModel;

        checkboxTree = new CheckboxTree(checkTreeModel.getModel()) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                if (getRowForLocation(evt.getX(), evt.getY()) == -1) {
                    return null;
                }
                TreePath curPath = getPathForLocation(evt.getX(), evt.getY());
                if (curPath != null) {
                    Object object = curPath.getLastPathComponent();
                    if (object instanceof ToolTipTreeNode) {
                        return ((ToolTipTreeNode) object).getToolTipText();
                    }
                }
                return null;
            }
        };

        // Register tooltips
        checkboxTree.setToolTipText(""); //$NON-NLS-1$

        /**
         * At this point checking Paths are supposed to be binded at Series Level but depending on the CheckingMode it
         * may also contains parents treeNode paths.<br>
         * For medical use recommendation is to default select the whole series related to studies to be analyzed
         */

        // TreeCheckingModel checkingModel = checkTreeModel.getCheckingModel();
        // checkboxTree.setCheckingModel(checkingModel);
        // !! DONT REUSE THE CHECKING MODEL LIKE ABOVE SINCE IT IS CLEARED AT INIT !!

        TreeCheckingModel checkingModel = checkboxTree.getCheckingModel();
        checkingModel.setCheckingMode(checkTreeModel.getCheckingModel().getCheckingMode());

        TreePath[] checkingPaths = checkTreeModel.getCheckingPaths();

        if (checkingPaths != null && checkingPaths.length > 0) {
            Set<TreePath> seriesPathsSet = new HashSet<TreePath>(checkingPaths.length);
            Set<TreePath> studyPathsSet = new HashSet<TreePath>();

            for (TreePath checkingPath : checkingPaths) {
                if (checkingPath.getPathCount() == 4) { // 4 stands for Series Level
                    seriesPathsSet.add(checkingPath);
                    studyPathsSet.add(checkingPath.getParentPath());
                }
            }

            if (studyPathsSet.isEmpty() == false) {
                TreePath[] studyCheckingPaths = studyPathsSet.toArray(new TreePath[studyPathsSet.size()]);
                checkboxTree.setCheckingPaths(studyCheckingPaths);
            }

            if (seriesPathsSet.isEmpty() == false) {
                TreePath[] seriesSelectionPaths = seriesPathsSet.toArray(new TreePath[seriesPathsSet.size()]);
                checkboxTree.setSelectionPaths(seriesSelectionPaths);
            }
        }

        expandTree(checkboxTree, checkTreeModel.getRootNode(), 2); // 2 stands for Study Level
        setViewportView(checkboxTree);
    }

    public CheckboxTree getTree() {
        return checkboxTree;
    }

    public CheckTreeModel getModel() {
        return checkTreeModel;
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
