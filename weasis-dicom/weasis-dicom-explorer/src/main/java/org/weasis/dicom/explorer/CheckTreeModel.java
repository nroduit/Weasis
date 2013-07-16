package org.weasis.dicom.explorer;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultTreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;

import java.util.Collection;
import java.util.Iterator;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;

public class CheckTreeModel {
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel model;
    private final TreeCheckingModel checkingModel;

    public CheckTreeModel(DicomModel dicomModel) {
        this.model = buildModel(dicomModel);
        this.rootNode = (DefaultMutableTreeNode) model.getRoot();
        this.checkingModel = new DefaultTreeCheckingModel(model);
    }

    public DefaultMutableTreeNode getRootNode() {
        return rootNode;
    }

    public DefaultTreeModel getModel() {
        return model;
    }

    public TreeCheckingModel getCheckingModel() {
        return checkingModel;
    }

    public TreePath[] getCheckingPaths() {
        return checkingModel.getCheckingPaths();
    }

    public static DefaultTreeModel buildModel(DicomModel dicomModel) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(DicomExplorer.ALL_PATIENTS);
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
                                        buffer.append("["); //$NON-NLS-1$
                                        buffer.append(val);
                                        buffer.append("] "); //$NON-NLS-1$
                                    }
                                    String desc = (String) s.getTagValue(TagW.SeriesDescription);
                                    if (desc != null) {
                                        buffer.append(desc);
                                    }
                                    return buffer.toString();
                                }
                            };
                            for (DicomImageElement dicom : series.getMedias(null, null)) {
                                seriesNode.add(new DefaultMutableTreeNode(dicom, false) {
                                    @Override
                                    public String toString() {
                                        DicomImageElement d = (DicomImageElement) getUserObject();
                                        Integer val = (Integer) d.getTagValue(TagW.InstanceNumber);
                                        StringBuffer buffer = new StringBuffer();
                                        if (val != null) {
                                            buffer.append("["); //$NON-NLS-1$
                                            buffer.append(val);
                                            buffer.append("] "); //$NON-NLS-1$
                                        }
                                        String sopUID = (String) d.getTagValue(TagW.SOPInstanceUID);
                                        if (sopUID != null) {
                                            buffer.append(sopUID);
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
                rootNode.add(patientNode);
            }
        }

        return new DefaultTreeModel(rootNode, false);
    }
}
