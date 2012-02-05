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

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;

import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;

public class ExportTree extends JScrollPane {

    private final CheckboxTree tree = new CheckboxTree();
    private final DicomModel dicomModel;

    public ExportTree(DicomModel dicomModel) {
        this.dicomModel = dicomModel;
        iniTree();

    }

    public void iniTree() {
        tree.getCheckingModel().setCheckingMode(CheckingMode.PROPAGATE_PRESERVING_UNCHECK);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(DicomExplorer.ALL_PATIENTS);

        synchronized (dicomModel) {
            for (Iterator<MediaSeriesGroup> iterator = dicomModel.getChildren(TreeModel.rootNode).iterator(); iterator
                .hasNext();) {
                MediaSeriesGroup pt = iterator.next();
                DefaultMutableTreeNode patientNode = new DefaultMutableTreeNode(pt, true);
                Collection<MediaSeriesGroup> studies = dicomModel.getChildren(pt);
                for (Iterator<MediaSeriesGroup> iterator2 = studies.iterator(); iterator2.hasNext();) {
                    MediaSeriesGroup study = iterator2.next();
                    DefaultMutableTreeNode studyNode = new DefaultMutableTreeNode(study, true);
                    Collection<MediaSeriesGroup> seriesList = dicomModel.getChildren(study);
                    for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                        Object item = it.next();
                        if (item instanceof DicomSeries) {
                            DicomSeries series = (DicomSeries) item;
                            DefaultMutableTreeNode seriesNode = new DefaultMutableTreeNode(series, true) {
                                @Override
                                public String toString() {
                                    DicomSeries s = (DicomSeries) getUserObject();
                                    Integer val = (Integer) s.getTagValue(TagW.SeriesNumber);
                                    StringBuffer buffer = new StringBuffer();
                                    if (val != null) {
                                        buffer.append("[");
                                        buffer.append(val);
                                        buffer.append("] ");
                                    }
                                    String desc = (String) s.getTagValue(TagW.SeriesDescription);
                                    if (desc != null) {
                                        buffer.append(desc);
                                    }
                                    return buffer.toString();
                                }
                            };
                            for (DicomImageElement dicom : series.getMedias()) {
                                seriesNode.add(new DefaultMutableTreeNode(dicom, false) {
                                    @Override
                                    public String toString() {
                                        DicomImageElement d = (DicomImageElement) getUserObject();
                                        Integer val = (Integer) d.getTagValue(TagW.InstanceNumber);
                                        StringBuffer buffer = new StringBuffer();
                                        if (val != null) {
                                            buffer.append("[");
                                            buffer.append(val);
                                            buffer.append("] ");
                                        }
                                        String desc = (String) d.getTagValue(TagW.SOPInstanceUID);
                                        if (desc != null) {
                                            buffer.append(desc);
                                        }
                                        return buffer.toString();
                                    }
                                });
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
        tree.setModel(model);
        expandTree(tree, root, 3);
        setViewportView(tree);
    }

    public CheckboxTree getTree() {
        return tree;
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
