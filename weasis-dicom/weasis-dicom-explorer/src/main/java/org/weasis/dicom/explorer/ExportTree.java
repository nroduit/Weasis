/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer;

import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
        this.checkTreeModel = Objects.requireNonNull(checkTreeModel);

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

        TreeCheckingModel checkingModel = checkTreeModel.getCheckingModel();
        TreePath[] checkingPaths = checkTreeModel.getCheckingPaths();
        checkboxTree.setCheckingModel(checkingModel); // be aware that checkingPaths is cleared at this point

        // checkingModel.setCheckingMode(checkTreeModel.getCheckingModel().getCheckingMode());
        // -- checkingMode is alreadySet inDicomExport

        if (checkingPaths != null && checkingPaths.length > 0) {
            Set<TreePath> studyPathsSet = new HashSet<>();

            for (TreePath checkingPath : checkingPaths) {
                if (checkingPath.getPathCount() == 4) { // 4 stands for Series Level
                    studyPathsSet.add(checkingPath.getParentPath());
                }
            }

            if (!studyPathsSet.isEmpty()) {
                TreePath[] studyCheckingPaths = studyPathsSet.toArray(new TreePath[studyPathsSet.size()]);
                checkboxTree.setCheckingPaths(studyCheckingPaths);
            }

            List<TreePath> selectedPaths = checkTreeModel.getDefaultSelectedPaths();
            if (!selectedPaths.isEmpty()) {
                checkboxTree.setSelectionPaths(selectedPaths.toArray(new TreePath[selectedPaths.size()]));
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
            Enumeration<?> children = start.children();
            while (children.hasMoreElements()) {
                Object child = children.nextElement();
                if (child instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) child;
                    if (!dtm.isLeaf()) {
                        TreePath tp = new TreePath(dtm.getPath());
                        tree.expandPath(tp);
                        expandTree(tree, dtm, maxDeep - 1);
                    }
                }
            }
        }
    }

}
