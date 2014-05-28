package org.weasis.dicom.explorer;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultTreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;

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

                            DefaultMutableTreeNode seriesNode = new ToolTipTreeNode(series, true);
                            List<DicomSpecialElement> specialElements =
                                (List<DicomSpecialElement>) series.getTagValue(TagW.DicomSpecialElementList);
                            if (specialElements != null) {
                                for (DicomSpecialElement specialElement : specialElements) {
                                    seriesNode.add(new DefaultMutableTreeNode(specialElement, false) {
                                        @Override
                                        public String toString() {
                                            DicomSpecialElement d = (DicomSpecialElement) getUserObject();
                                            return d.getShortLabel();
                                        }
                                    });
                                }
                            }
                            for (DicomImageElement dicom : series.getMedias(null, null)) {
                                seriesNode.add(new DefaultMutableTreeNode(dicom, false) {
                                    @Override
                                    public String toString() {
                                        DicomImageElement d = (DicomImageElement) getUserObject();
                                        Integer val = (Integer) d.getTagValue(TagW.InstanceNumber);
                                        StringBuilder buffer = new StringBuilder();
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

                            List children = Collections.list(studyNode.children());
                            int index = Collections.binarySearch(children, seriesNode, DicomModel.SERIES_COMPARATOR);
                            if (index < 0) {
                                studyNode.insert(seriesNode, -(index + 1));
                            } else {
                                studyNode.insert(seriesNode, index);
                            }
                        }
                    }
                    List children = Collections.list(patientNode.children());
                    int index = Collections.binarySearch(children, studyNode, DicomModel.STUDY_COMPARATOR);
                    if (index < 0) {
                        patientNode.insert(studyNode, -(index + 1));
                    } else {
                        patientNode.insert(studyNode, index);
                    }
                }
                List children = Collections.list(rootNode.children());
                int index = Collections.binarySearch(children, patientNode, DicomModel.PATIENT_COMPARATOR);
                if (index < 0) {
                    rootNode.insert(patientNode, -(index + 1));
                } else {
                    rootNode.insert(patientNode, index);
                }
            }
        }
        return new DefaultTreeModel(rootNode, false);
    }

    static class ToolTipTreeNode extends DefaultMutableTreeNode {

        public ToolTipTreeNode(MediaSeries<?> userObject, boolean allowsChildren) {
            super(userObject, allowsChildren);
            if (userObject == null) {
                throw new IllegalArgumentException();
            }
        }

        public String getToolTipText() {
            MediaSeries<?> s = (MediaSeries<?>) getUserObject();
            Thumbnail thumb = (Thumbnail) s.getTagValue(TagW.Thumbnail);
            URL url = null;
            if (thumb != null) {
                try {
                    File path = thumb.getThumbnailPath();
                    if (path != null) {
                        url = path.toURI().toURL();
                        if (url != null) {
                            StringBuilder buf = new StringBuilder();
                            buf.append("<html>");
                            buf.append("<img src=\"");
                            buf.append(url.toString());
                            buf.append("\"><br>");
                            Date date = (Date) s.getTagValue(TagW.SeriesDate);
                            if (date != null) {
                                buf.append(TagW.formatDateTime(date));
                            }
                            buf.append("</html>");
                            return buf.toString();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

        @Override
        public String toString() {
            MediaSeries<?> s = (MediaSeries<?>) getUserObject();
            StringBuilder buf = new StringBuilder();
            Integer val = (Integer) s.getTagValue(TagW.SeriesNumber);
            if (val != null) {
                buf.append("["); //$NON-NLS-1$
                buf.append(val);
                buf.append("] "); //$NON-NLS-1$
            }
            String modality = (String) s.getTagValue(TagW.Modality);
            if (modality != null) {
                buf.append(modality);
                buf.append(" "); //$NON-NLS-1$
            }
            String desc = (String) s.getTagValue(TagW.SeriesDescription);
            if (desc != null) {
                buf.append(desc);
            }
            return buf.toString();
        }
    }
}
