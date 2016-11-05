/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.dcm4che3.data.Tag;
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
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultTreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;

public class CheckTreeModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(CheckTreeModel.class);

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
                        return d.getShortLabel();
                    }
                });
            }
        }
        for (MediaElement dicom : series.getMedias(null, null)) {
            seriesNode.add(new DefaultMutableTreeNode(dicom, false) {
                @Override
                public String toString() {
                    MediaElement m = (MediaElement) getUserObject();
                    Integer val = TagD.getTagValue(m, Tag.InstanceNumber, Integer.class);
                    StringBuilder buffer = new StringBuilder();
                    if (val != null) {
                        buffer.append("["); //$NON-NLS-1$
                        buffer.append(val);
                        buffer.append("] "); //$NON-NLS-1$
                    }
                    String sopUID = TagD.getTagValue(m, Tag.SOPInstanceUID, String.class);
                    if (sopUID != null) {
                        buffer.append(sopUID);
                    }
                    return buffer.toString();
                }
            });
        }

        List children = Collections.list(studyNode.children());
        int index = Collections.binarySearch(children, seriesNode, DicomSorter.SERIES_COMPARATOR);
        if (index < 0) {
            studyNode.insert(seriesNode, -(index + 1));
        } else {
            studyNode.insert(seriesNode, index);
        }
    }

    public static DefaultTreeModel buildModel(DicomModel dicomModel) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(DicomExplorer.ALL_PATIENTS);
        synchronized (dicomModel) {
            for (Iterator<MediaSeriesGroup> iterator =
                dicomModel.getChildren(MediaSeriesGroupNode.rootNode).iterator(); iterator.hasNext();) {
                MediaSeriesGroup pt = iterator.next();
                DefaultMutableTreeNode patientNode = new DefaultMutableTreeNode(pt, true);
                Collection<MediaSeriesGroup> studies = dicomModel.getChildren(pt);
                for (Iterator<MediaSeriesGroup> iterator2 = studies.iterator(); iterator2.hasNext();) {
                    MediaSeriesGroup study = iterator2.next();
                    DefaultMutableTreeNode studyNode = new DefaultMutableTreeNode(study, true);
                    Collection<MediaSeriesGroup> seriesList = dicomModel.getChildren(study);
                    for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                        Object item = it.next();
                        if (item instanceof Series) {
                            buildSeries(studyNode, (Series) item);
                        }
                    }
                    List children = Collections.list(patientNode.children());
                    int index = Collections.binarySearch(children, studyNode, DicomSorter.STUDY_COMPARATOR);
                    if (index < 0) {
                        patientNode.insert(studyNode, -(index + 1));
                    } else {
                        patientNode.insert(studyNode, index);
                    }
                }
                List children = Collections.list(rootNode.children());
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

        public ToolTipTreeNode(TagReadable userObject, boolean allowsChildren) {
            super(userObject, allowsChildren);
            if (userObject == null) {
                throw new IllegalArgumentException();
            }
        }

        public String getToolTipText() {
            TagReadable s = (TagReadable) getUserObject();
            Thumbnail thumb = (Thumbnail) s.getTagValue(TagW.Thumbnail);
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
