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

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;

import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.util.CheckNode;
import org.weasis.core.ui.util.TreeLayer;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;

public class ExportTree extends JScrollPane {

    private final JCheckBox applyAllViews = new JCheckBox("Select All", true);
    private final TreeLayer tree = new TreeLayer();
    private final DicomModel dicomModel;
    private CheckNode image;
    private CheckNode dicomInfo;
    private CheckNode drawings;

    public ExportTree(DicomModel dicomModel) {
        this.dicomModel = dicomModel;
        jbInit();

    }

    private void jbInit() {
        iniTree();
    }

    public void iniTree() {

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root"); //$NON-NLS-1$

        synchronized (dicomModel) {
            for (Iterator<MediaSeriesGroup> iterator = dicomModel.getChildren(TreeModel.rootNode).iterator(); iterator
                .hasNext();) {
                MediaSeriesGroup pt = iterator.next();
                CheckNode patientNode = new CheckNode(pt, true, false, true);
                Collection<MediaSeriesGroup> studies = dicomModel.getChildren(pt);
                for (Iterator<MediaSeriesGroup> iterator2 = studies.iterator(); iterator2.hasNext();) {
                    MediaSeriesGroup study = iterator2.next();
                    CheckNode studyNode = new CheckNode(study, true, true, true);
                    Collection<MediaSeriesGroup> seriesList = dicomModel.getChildren(study);
                    for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                        Object item = it.next();
                        if (item instanceof DicomSeries) {
                            DicomSeries series = (DicomSeries) item;
                            CheckNode seriesNode = new CheckNode(series, true, true, false);
                            for (DicomImageElement dicom : series.getMedias()) {
                                // writeFile(dicom);
                            }
                            studyNode.add(seriesNode);
                        }
                    }
                    patientNode.add(studyNode);
                }
                root.add(patientNode);
            }
        }

        DefaultTreeModel model = new DefaultTreeModel(root, false);
        tree.constructTree(model);
        tree.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseReleased(MouseEvent e) {
                TreeLayer layer = (TreeLayer) e.getSource();
                TreePath path = layer.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    Object node = path.getLastPathComponent();
                    if (node instanceof CheckNode) {
                        CheckNode checkNode = (CheckNode) node;
                        checkNode.setSelected(!checkNode.isSelected());
                        if (checkNode.isUpdateChildren() && checkNode.getChildCount() > 0) {
                            TreeLayer.fireToChildren(checkNode.children(), checkNode.isSelected());
                        }
                        if (checkNode.isUpdateParent()) {
                            TreeLayer.fireParentChecked(checkNode);
                        }
                        tree.upadateNode(checkNode);
                        changeLayerSelection(checkNode);
                    }
                }
            }
        });

        expandTree(tree, root);
        setViewportView(tree);
    }

    public void changeLayerSelection(CheckNode userObject) {

    }

    public void expandAllTree() {
        tree.expandRow(4);
    }

    private static void expandTree(JTree tree, DefaultMutableTreeNode start) {
        for (Enumeration children = start.children(); children.hasMoreElements();) {
            DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) children.nextElement();
            if (!dtm.isLeaf()) {
                //
                TreePath tp = new TreePath(dtm.getPath());
                tree.expandPath(tp);
                //
                expandTree(tree, dtm);
            }
        }
        return;
    }

}
