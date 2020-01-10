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

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.media.data.Thumbnailable;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultTreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;

public class CheckTreeModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(CheckTreeModel.class);

    public static final TagW SourceSeriesForPR = new TagW("SourceSeriesForPR", TagType.OBJECT); //$NON-NLS-1$

    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel model;
    private final TreeCheckingModel checkingModel;
    private final List<TreePath> defaultSelectedPaths;

    public CheckTreeModel(DicomModel dicomModel) {
        this.model = buildModel(dicomModel);
        this.rootNode = (DefaultMutableTreeNode) model.getRoot();
        this.checkingModel = new DefaultTreeCheckingModel(model);
        this.defaultSelectedPaths = Collections.synchronizedList(new ArrayList<TreePath>());
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

    public void setDefaultSelectionPaths(List<TreePath> selectedPaths) {
        defaultSelectedPaths.clear();
        defaultSelectedPaths.addAll(selectedPaths);
    }

    public List<TreePath> getDefaultSelectedPaths() {
        return defaultSelectedPaths;
    }

    private static void buildSeries(DefaultMutableTreeNode studyNode, Series<?> series) {
        DefaultMutableTreeNode seriesNode = new ToolTipTreeNode(series, true);
        List<DicomSpecialElement> specialElements =
            (List<DicomSpecialElement>) series.getTagValue(TagW.DicomSpecialElementList);
        if (specialElements != null) {
            for (DicomSpecialElement specialElement : specialElements) {
                seriesNode.add(new DefaultMutableTreeNode(specialElement, false) {
                    @Override
                    public String toString() {
                        DicomSpecialElement d = (DicomSpecialElement) getUserObject();
                        StringBuilder buf = new StringBuilder();
                        boolean newElement = LangUtil.getNULLtoFalse((Boolean) d.getTagValue(TagW.ObjectToSave));
                        if (newElement) {
                            buf.append("<html>"); //$NON-NLS-1$
                            buf.append("<font color='orange'><b>NEW </b></font>"); //$NON-NLS-1$
                        }
                        buf.append(d.getShortLabel());
                        if (newElement) {
                            buf.append("</html>"); //$NON-NLS-1$
                        }
                        return buf.toString();
                    }
                });
            }
        }

        boolean hasGraphics = false;
        synchronized (series) { // NOSONAR lock object is the list for iterating its elements safely
            for (MediaElement dicom : series.getMedias(null, null)) {
                seriesNode.add(new DefaultMutableTreeNode(dicom, false) {
                    @Override
                    public String toString() {
                        MediaElement m = (MediaElement) getUserObject();
                        Integer val = TagD.getTagValue(m, Tag.InstanceNumber, Integer.class);
                        StringBuilder buf = new StringBuilder();
                        if (val != null) {
                            buf.append("["); //$NON-NLS-1$
                            buf.append(val);
                            buf.append("] "); //$NON-NLS-1$
                        }
                        String sopUID = TagD.getTagValue(m, Tag.SOPInstanceUID, String.class);
                        if (sopUID != null) {
                            buf.append(sopUID);
                        }
                        return buf.toString();
                    }
                });

                if (!hasGraphics) {
                    GraphicModel grModel = (GraphicModel) dicom.getTagValue(TagW.PresentationModel);
                    hasGraphics = grModel != null && grModel.hasSerializableGraphics();
                }
            }
        }

        List<?> children = Collections.list(studyNode.children());
        int index = Collections.binarySearch(children, seriesNode, DicomSorter.SERIES_COMPARATOR);
        index = index < 0 ? -(index + 1) : index;
        studyNode.insert(seriesNode, index);

        if (hasGraphics) {
            String seriesInstanceUID = UIDUtils.createUID();
            Series<?> prSeries = new DicomSeries(seriesInstanceUID);
            prSeries.setTag(TagD.get(Tag.SeriesNumber), TagD.getTagValue(series, Tag.SeriesNumber, Integer.class));
            prSeries.setTag(TagD.get(Tag.Modality), "PR"); //$NON-NLS-1$
            prSeries.setTag(TagD.get(Tag.SeriesInstanceUID), seriesInstanceUID);
            prSeries.setTag(TagW.ObjectToSave, Boolean.TRUE);
            prSeries.setTag(SourceSeriesForPR, series);
            prSeries.setTag(TagD.get(Tag.SeriesDescription),
                Optional.ofNullable(TagD.getTagValue(series, Tag.SeriesDescription, String.class)).orElse("") //$NON-NLS-1$
                    + " [GRAPHICS]"); //$NON-NLS-1$
            DefaultMutableTreeNode prVirtualNode = new ToolTipTreeNode(prSeries, false);
            studyNode.insert(prVirtualNode, index + 1);
        }
    }

    public static DefaultTreeModel buildModel(DicomModel dicomModel) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(DicomExplorer.ALL_PATIENTS);
        synchronized (dicomModel) { // NOSONAR lock object is the list for iterating its elements safely
            for (MediaSeriesGroup pt : dicomModel.getChildren(MediaSeriesGroupNode.rootNode)) {
                DefaultMutableTreeNode patientNode = new DefaultMutableTreeNode(pt, true);
                for (MediaSeriesGroup study : dicomModel.getChildren(pt)) {
                    DefaultMutableTreeNode studyNode = new DefaultMutableTreeNode(study, true);
                    for (MediaSeriesGroup item : dicomModel.getChildren(study)) {
                        if (item instanceof Series) {
                            buildSeries(studyNode, (Series<?>) item);
                        }
                    }
                    List<?> children = Collections.list(patientNode.children());
                    int index = Collections.binarySearch(children, studyNode, DicomSorter.STUDY_COMPARATOR);
                    if (index < 0) {
                        patientNode.insert(studyNode, -(index + 1));
                    } else {
                        patientNode.insert(studyNode, index);
                    }
                }
                List<?> children = Collections.list(rootNode.children());
                int index = Collections.binarySearch(children, patientNode, DicomSorter.PATIENT_COMPARATOR);
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

        private static final long serialVersionUID = 6815757092280682077L;

        public ToolTipTreeNode(TagReadable userObject, boolean allowsChildren) {
            super(Objects.requireNonNull(userObject), allowsChildren);
        }

        public String getToolTipText() {
            TagReadable s = (TagReadable) getUserObject();
            Thumbnailable thumb = (Thumbnailable) s.getTagValue(TagW.Thumbnail);
            if (thumb != null) {
                try {
                    File path = thumb.getThumbnailPath();
                    if (path != null) {
                        URL url = path.toURI().toURL();
                        if (url != null) {
                            StringBuilder buf = new StringBuilder();
                            buf.append("<html>"); //$NON-NLS-1$
                            buf.append("<img src=\""); //$NON-NLS-1$
                            buf.append(url.toString());
                            buf.append("\"><br>"); //$NON-NLS-1$
                            LocalDateTime date = TagD.dateTime(Tag.SeriesDate, Tag.SeriesTime, s);
                            if (date != null) {
                                buf.append(TagUtil.formatDateTime(date));
                            }
                            buf.append("</html>"); //$NON-NLS-1$
                            return buf.toString();
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Display tooltip", e); //$NON-NLS-1$
                }
            }
            return null;
        }

        @Override
        public String toString() {
            MediaSeries<?> s = (MediaSeries<?>) getUserObject();
            StringBuilder buf = new StringBuilder();
            boolean newElement = LangUtil.getNULLtoFalse((Boolean) s.getTagValue(TagW.ObjectToSave));
            if (newElement) {
                buf.append("<html>"); //$NON-NLS-1$
                buf.append("<font color='orange'><b>NEW </b></font>"); //$NON-NLS-1$
            }
            Integer val = TagD.getTagValue(s, Tag.SeriesNumber, Integer.class);
            if (val != null) {
                buf.append("["); //$NON-NLS-1$
                buf.append(val);
                buf.append("] "); //$NON-NLS-1$
            }
            String modality = TagD.getTagValue(s, Tag.Modality, String.class);
            if (modality != null) {
                buf.append(modality);
                buf.append(" "); //$NON-NLS-1$
            }
            String desc = TagD.getTagValue(s, Tag.SeriesDescription, String.class);
            if (desc != null) {
                buf.append(desc);
            }
            buf.append(" -- (").append(getChildCount()).append(" instances)"); //$NON-NLS-1$ //$NON-NLS-2$
            if (newElement) {
                buf.append("</html>"); //$NON-NLS-1$
            }
            return buf.toString();
        }
    }
}
