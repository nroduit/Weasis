/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.qr;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnailable;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSorter;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultTreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;

public class RetrieveTreeModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetrieveTreeModel.class);

    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel model;
    private final TreeCheckingModel checkingModel;
    private final List<TreePath> defaultSelectedPaths;

    public RetrieveTreeModel(DicomModel dicomModel) {
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
        DefaultMutableTreeNode seriesNode = new ToolTipSeriesNode(series, true);

        List<?> children = Collections.list(studyNode.children());
        int index = Collections.binarySearch(children, seriesNode, DicomSorter.SERIES_COMPARATOR);
        index = index < 0 ? -(index + 1) : index;
        studyNode.insert(seriesNode, index);
    }

    public static synchronized DefaultTreeModel buildModel(DicomModel dicomModel) {
        Collection<MediaSeriesGroup> patients = dicomModel.getChildren(MediaSeriesGroupNode.rootNode);
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(
            patients.isEmpty() ? Messages.getString("RetrieveTreeModel.no_pat") : DicomExplorer.ALL_PATIENTS); //$NON-NLS-1$
        for (MediaSeriesGroup pt : patients) {
            DefaultMutableTreeNode patientNode = new DefaultMutableTreeNode(pt, true);
            for (MediaSeriesGroup study : dicomModel.getChildren(pt)) {
                DefaultMutableTreeNode studyNode = new ToolTipStudyNode(study, true);
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
        return new DefaultTreeModel(rootNode, false);
    }

    static class ToolTipStudyNode extends DefaultMutableTreeNode {

        private static final long serialVersionUID = -5332455270913061130L;

        public ToolTipStudyNode(TagReadable userObject, boolean allowsChildren) {
            super(Objects.requireNonNull(userObject), allowsChildren);
        }

        public String getToolTipText() {
            TagReadable s = (TagReadable) getUserObject();
            StringBuilder toolTips = new StringBuilder();
            toolTips.append("<html>"); //$NON-NLS-1$
            s.getTagEntrySetIterator().forEachRemaining(i -> {
                TagW tag = i.getKey();
                toolTips.append("<b>"); //$NON-NLS-1$
                toolTips.append(tag.getDisplayedName());
                toolTips.append("</b>"); //$NON-NLS-1$
                toolTips.append(StringUtil.COLON_AND_SPACE);
                toolTips.append(tag.getFormattedTagValue(i.getValue(), null));
                toolTips.append("<br>"); //$NON-NLS-1$
            });
            toolTips.append("</html>"); //$NON-NLS-1$
            return toolTips.toString();
        }
    }

    static class ToolTipSeriesNode extends DefaultMutableTreeNode {

        private static final long serialVersionUID = 6815757092280682077L;

        public ToolTipSeriesNode(TagReadable userObject, boolean allowsChildren) {
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
            return buf.toString();
        }
    }
}
