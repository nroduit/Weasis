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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.Tree;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.explorer.model.TreeModelNode;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.Base64;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.dicom.codec.DicomEncapDocElement;
import org.weasis.dicom.codec.DicomEncapDocSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.DicomVideoElement;
import org.weasis.dicom.codec.DicomVideoSeries;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.explorer.DicomExplorer.SeriesPane;
import org.weasis.dicom.explorer.DicomExplorer.StudyPane;
import org.weasis.dicom.explorer.wado.DicomManager;
import org.weasis.dicom.explorer.wado.DownloadManager;
import org.weasis.dicom.explorer.wado.LoadRemoteDicomManifest;
import org.weasis.dicom.explorer.wado.LoadRemoteDicomURL;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class DicomModel implements TreeModel, DataExplorerModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomModel.class);

    public static final String[] functions = { "get", "close" }; //$NON-NLS-1$ //$NON-NLS-2$
    public static final String NAME = "DICOM"; //$NON-NLS-1$
    public static final String PREFERENCE_NODE = "dicom.model"; //$NON-NLS-1$

    public static final TreeModelNode patient = new TreeModelNode(1, 0, TagW.PatientPseudoUID);
    public static final TreeModelNode study = new TreeModelNode(2, 0, TagW.StudyInstanceUID);
    public static final TreeModelNode series = new TreeModelNode(3, 0, TagW.SubseriesInstanceUID);

    public static final ArrayList<TreeModelNode> modelStrucure = new ArrayList<TreeModelNode>(5);
    static {
        modelStrucure.add(root);
        modelStrucure.add(patient);
        modelStrucure.add(study);
        modelStrucure.add(series);
    }
    public static final Executor loadingExecutor = Executors.newSingleThreadExecutor();
    private static final Collator collator = Collator.getInstance(Locale.getDefault());

    public static final Comparator PATIENT_COMPARATOR = new Comparator() {

        @Override
        public int compare(Object o1, Object o2) {
            return collator.compare(o1.toString(), o2.toString());
        }
    };

    public static final Comparator STUDY_COMPARATOR = new Comparator() {

        @Override
        public int compare(Object o1, Object o2) {
            if (o1 instanceof StudyPane && o2 instanceof StudyPane) {
                o1 = ((StudyPane) o1).dicomStudy;
                o2 = ((StudyPane) o2).dicomStudy;
            } else if (o1 instanceof DefaultMutableTreeNode && o2 instanceof DefaultMutableTreeNode) {
                o1 = ((DefaultMutableTreeNode) o1).getUserObject();
                o2 = ((DefaultMutableTreeNode) o2).getUserObject();
            }

            if (o1 instanceof MediaSeriesGroup && o2 instanceof MediaSeriesGroup) {
                MediaSeriesGroup st1 = (MediaSeriesGroup) o1;
                MediaSeriesGroup st2 = (MediaSeriesGroup) o2;
                Date date1 = (Date) st1.getTagValue(TagW.StudyDate);
                Date date2 = (Date) st2.getTagValue(TagW.StudyDate);
                // LOGGER.debug("date1: {} date2: {}", date1, date2);
                int c = -1;
                if (date1 != null && date2 != null) {
                    // Reverse chronological order. StudyDate combines DICOM StudyDate and DICOM StudyTime
                    c = date2.compareTo(date1);
                    if (c != 0) {
                        return c;
                    }
                }

                if (c == 0 || (date1 == null && date2 == null)) {
                    String d1 = (String) st1.getTagValue(TagW.StudyDescription);
                    String d2 = (String) st2.getTagValue(TagW.StudyDescription);
                    if (d1 != null && d2 != null) {
                        c = collator.compare(d1, d2);
                        if (c != 0) {
                            return c;
                        }
                    }
                    if (d1 == null && d2 != null) {
                        // Add o1 after o2
                        return 1;
                    }
                    // Add o2 after o1
                    return -1;
                } else {
                    if (date1 == null) {
                        // Add o1 after o2
                        return 1;
                    }
                    if (date2 == null) {
                        return -1;
                    }
                }
            } else {
                // Set non MediaSeriesGroup at the beginning of the list
                if (o1 instanceof MediaSeriesGroup) {
                    // Add o1 after o2
                    return 1;
                }
                if (o2 instanceof MediaSeriesGroup) {
                    return -1;
                }
            }
            return 0;
        }
    };

    public static final Comparator SERIES_COMPARATOR = new Comparator() {

        @Override
        public int compare(Object o1, Object o2) {

            if (o1 instanceof SeriesPane && o2 instanceof SeriesPane) {
                o1 = ((SeriesPane) o1).sequence;
                o2 = ((SeriesPane) o2).sequence;
            } else if (o1 instanceof DefaultMutableTreeNode && o2 instanceof DefaultMutableTreeNode) {
                o1 = ((DefaultMutableTreeNode) o1).getUserObject();
                o2 = ((DefaultMutableTreeNode) o2).getUserObject();
            }

            if (o1 instanceof MediaSeriesGroup && o2 instanceof MediaSeriesGroup) {
                MediaSeriesGroup st1 = (MediaSeriesGroup) o1;
                MediaSeriesGroup st2 = (MediaSeriesGroup) o2;

                Integer val1 = (Integer) st1.getTagValue(TagW.SeriesNumber);
                Integer val2 = (Integer) st2.getTagValue(TagW.SeriesNumber);
                int c = -1;
                if (val1 != null && val2 != null) {
                    c = val1.compareTo(val2);
                    if (c != 0) {
                        return c;
                    }
                }

                if (c == 0 || (val1 == null && val2 == null)) {
                    Date date1 = (Date) st1.getTagValue(TagW.SeriesDate);
                    Date date2 = (Date) st2.getTagValue(TagW.SeriesDate);
                    if (date1 != null && date2 != null) {
                        // Chronological order.
                        c = date1.compareTo(date2);
                        if (c != 0) {
                            return c;
                        }
                    }

                    if ((c == 0 || (date1 == null && date2 == null)) && st1 instanceof MediaSeries
                        && st2 instanceof MediaSeries) {
                        MediaElement media1 = (MediaElement) ((MediaSeries) st1).getMedia(0, null, null);
                        MediaElement media2 = (MediaElement) ((MediaSeries) st2).getMedia(0, null, null);
                        if (media1 != null && media2 != null) {

                            date1 =
                                TagW.dateTime((Date) media1.getTagValue(TagW.AcquisitionDate),
                                    (Date) media1.getTagValue(TagW.AcquisitionTime));
                            date2 =
                                TagW.dateTime((Date) media2.getTagValue(TagW.AcquisitionDate),
                                    (Date) media2.getTagValue(TagW.AcquisitionTime));
                            if (date1 == null) {
                                date1 =
                                    TagW.dateTime((Date) media1.getTagValue(TagW.ContentDate),
                                        (Date) media1.getTagValue(TagW.ContentTime));
                            }
                            if (date2 == null) {
                                date2 =
                                    TagW.dateTime((Date) media2.getTagValue(TagW.ContentDate),
                                        (Date) media2.getTagValue(TagW.ContentTime));
                            }
                            if (date1 != null && date2 != null) {
                                // Chronological order.
                                c = date1.compareTo(date2);
                                if (c != 0) {
                                    return c;
                                }
                            }
                            if (c == 0 || (date1 == null && date2 == null)) {

                                Float tag1 = (Float) media1.getTagValue(TagW.SliceLocation);
                                Float tag2 = (Float) media2.getTagValue(TagW.SliceLocation);
                                if (tag1 != null && tag2 != null) {
                                    c = tag1.compareTo(tag2);
                                    if (c != 0) {
                                        return c;
                                    }
                                }
                                if (c == 0 || (tag1 == null && tag2 == null)) {
                                    String nb1 = (String) media1.getTagValue(TagW.StackID);
                                    String nb2 = (String) media2.getTagValue(TagW.StackID);
                                    if (nb1 != null && nb2 != null) {
                                        c = nb1.compareTo(nb2);
                                        if (c != 0) {
                                            try {
                                                c = new Integer(Integer.parseInt(nb1)).compareTo(Integer.parseInt(nb2));
                                            } catch (Exception ex) {
                                            }
                                            return c;
                                        }
                                    }
                                    if (c == 0 || (nb1 == null && nb2 == null)) {
                                        return -1;
                                    }
                                    if (nb1 == null) {
                                        return 1;
                                    }
                                    return -1;
                                }
                                if (tag1 == null) {
                                    return 1;
                                }
                                return -1;
                            }
                            if (date1 == null) {
                                // Add o1 after o2
                                return 1;
                            }
                            // Add o2 after o1
                            return -1;
                        }
                        if (media2 == null) {
                            // Add o2 after o1
                            return -1;
                        }
                        return 1;
                    }
                    if (date1 == null) {
                        return 1;
                    }
                    return -1;
                }
                if (val1 == null) {
                    return 1;
                }
                return -1;
            }
            // Set non MediaSeriesGroup at the beginning of the list
            if (o1 instanceof MediaSeriesGroup) {
                // Add o1 after o2
                return 1;
            }
            if (o2 instanceof MediaSeriesGroup) {
                return -1;
            }
            return -1;
        }
    };

    private final Tree<MediaSeriesGroup> model;
    private PropertyChangeSupport propertyChange = null;
    private final TagW[] multiframeSplittingRules = new TagW[] { TagW.ImageType, TagW.SOPInstanceUID, TagW.FrameType,
        TagW.FrameAcquisitionNumber, TagW.StackID };
    private final HashMap<Modality, TagW[]> splittingRules = new HashMap<Modality, TagW[]>();

    public DicomModel() {
        model = new Tree<MediaSeriesGroup>(rootNode);
        // Preferences prefs = Activator.PREFERENCES.getDefaultPreferences();
        // if (prefs == null) {
        // } else {
        // Preferences p = prefs.node(PREFERENCE_NODE);
        // }
        splittingRules.put(Modality.Default, new TagW[] { TagW.ImageType, TagW.ContrastBolusAgent, TagW.SOPClassUID });
        splittingRules.put(Modality.CT, new TagW[] { TagW.ImageType, TagW.ContrastBolusAgent, TagW.SOPClassUID,
            TagW.ImageOrientationPlane, TagW.GantryDetectorTilt, TagW.ConvolutionKernel });
        splittingRules.put(Modality.PT, new TagW[] { TagW.ImageType, TagW.ContrastBolusAgent, TagW.SOPClassUID,
            TagW.GantryDetectorTilt, TagW.ConvolutionKernel });
        splittingRules.put(Modality.MR, new TagW[] { TagW.ImageType, TagW.ContrastBolusAgent, TagW.SOPClassUID,
            TagW.ImageOrientationPlane, TagW.ScanningSequence, TagW.SequenceVariant, TagW.ScanOptions,
            TagW.RepetitionTime, TagW.EchoTime, TagW.InversionTime, TagW.FlipAngle });

    }

    @Override
    public synchronized List<Codec> getCodecPlugins() {
        ArrayList<Codec> codecPlugins = new ArrayList<Codec>(1);
        synchronized (BundleTools.CODEC_PLUGINS) {
            for (Codec codec : BundleTools.CODEC_PLUGINS) {
                if (codec != null && codec.isMimeTypeSupported("application/dicom") && !codecPlugins.contains(codec)) { //$NON-NLS-1$
                    codecPlugins.add(codec);
                }
            }
        }
        return codecPlugins;
    }

    private static final Integer getOrientationLabelPosition(String orientationPlane) {
        if (orientationPlane == null) {
            return 0;
        }
        for (int i = 0; i < ImageOrientation.LABELS.length; i++) {
            if (ImageOrientation.LABELS[i].equals(orientationPlane)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public Collection<MediaSeriesGroup> getChildren(MediaSeriesGroup node) {
        return model.getSuccessors(node);
    }

    @Override
    public MediaSeriesGroup getHierarchyNode(MediaSeriesGroup parent, Object value) {
        if (parent != null || value != null) {
            synchronized (model) {
                for (MediaSeriesGroup node : model.getSuccessors(parent)) {
                    if (node.equals(value)) {
                        return node;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void addHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf) {
        synchronized (model) {
            model.addLeaf(root, leaf);
        }
    }

    @Override
    public void removeHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf) {
        synchronized (model) {
            Tree<MediaSeriesGroup> tree = model.getTree(root);
            if (tree != null) {
                tree.removeLeaf(leaf);
            }
        }
    }

    @Override
    public MediaSeriesGroup getParent(MediaSeriesGroup node, TreeModelNode modelNode) {
        if (null != node && modelNode != null) {
            if (node.getTagID().equals(modelNode.getTagElement())) {
                return node;
            }
            synchronized (model) {
                Tree<MediaSeriesGroup> tree = model.getTree(node);
                if (tree != null) {
                    Tree<MediaSeriesGroup> parent = null;
                    while ((parent = tree.getParent()) != null) {
                        if (parent.getHead().getTagID().equals(modelNode.getTagElement())) {
                            return parent.getHead();
                        }
                        tree = parent;
                    }
                }
            }
        }
        return null;
    }

    public void dispose() {
        synchronized (model) {
            for (Iterator<MediaSeriesGroup> iterator = this.getChildren(TreeModel.rootNode).iterator(); iterator
                .hasNext();) {
                MediaSeriesGroup pt = iterator.next();
                Collection<MediaSeriesGroup> studies = this.getChildren(pt);
                for (Iterator<MediaSeriesGroup> iterator2 = studies.iterator(); iterator2.hasNext();) {
                    MediaSeriesGroup study = iterator2.next();
                    Collection<MediaSeriesGroup> seriesList = this.getChildren(study);
                    for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                        Object item = it.next();
                        if (item instanceof Series) {
                            ((Series) item).dispose();
                        }
                    }
                }
            }
        }
        model.clear();
    }

    @Override
    public String toString() {
        return NAME;
    }

    @Override
    public List<TreeModelNode> getModelStructure() {
        return modelStrucure;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (propertyChange == null) {
            propertyChange = new PropertyChangeSupport(this);
        }
        propertyChange.addPropertyChangeListener(propertychangelistener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (propertyChange != null) {
            propertyChange.removePropertyChangeListener(propertychangelistener);
        }

    }

    @Override
    public void firePropertyChange(final ObservableEvent event) {
        if (propertyChange != null) {
            if (event == null) {
                throw new NullPointerException();
            }
            if (SwingUtilities.isEventDispatchThread()) {
                propertyChange.firePropertyChange(event);
            } else {
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        propertyChange.firePropertyChange(event);
                    }
                });
            }
        }
    }

    public void mergeSeries(List<MediaSeries<? extends MediaElement<?>>> seriesList) {
        if (seriesList != null && seriesList.size() > 1) {
            String uid = (String) seriesList.get(0).getTagValue(TagW.SeriesInstanceUID);
            boolean sameOrigin = true;
            if (uid != null) {
                for (int i = 1; i < seriesList.size(); i++) {
                    if (!uid.equals(seriesList.get(i).getTagValue(TagW.SeriesInstanceUID))) {
                        sameOrigin = false;
                        break;
                    }
                }
            }
            if (sameOrigin) {
                int min = Integer.MAX_VALUE;
                MediaSeries<? extends MediaElement<?>> base = seriesList.get(0);
                for (MediaSeries<? extends MediaElement<?>> series : seriesList) {
                    Integer splitNb = (Integer) series.getTagValue(TagW.SplitSeriesNumber);
                    if (splitNb != null && min > splitNb) {
                        min = splitNb;
                        base = series;
                    }
                }
                for (MediaSeries<? extends MediaElement<?>> series : seriesList) {
                    if (series != base) {
                        base.addAll((Collection) series.getMedias(null, null));
                        removeSeries(series);
                    }
                }
                // Force to sort the new merged media list
                List sortedMedias = base.getSortedMedias(null);
                Collections.sort(sortedMedias, SortSeriesStack.instanceNumber);
                // update observer
                this.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Replace, DicomModel.this, base,
                    base));
            }
        }
    }

    public void removeSpecialElement(DicomSpecialElement dicomSpecialElement) {
        if (dicomSpecialElement == null) {
            return;
        }

        String patientPseudoUID = (String) dicomSpecialElement.getTagValue(TagW.PatientPseudoUID);
        MediaSeriesGroup patientGroup = getHierarchyNode(TreeModel.rootNode, patientPseudoUID);

        if (patientGroup == null) {
            return;
        }

        String studyUID = (String) dicomSpecialElement.getTagValue(TagW.StudyInstanceUID);
        MediaSeriesGroup studyGroup = getHierarchyNode(patientGroup, studyUID);
        if (studyGroup == null) {
            return;
        }

        String seriesUID = (String) dicomSpecialElement.getTagValue(TagW.SeriesInstanceUID);
        Series<?> dicomSeries = (Series<?>) getHierarchyNode(studyGroup, seriesUID);
        if (dicomSeries == null) {
            return;
        }

        if (isSpecialModality(dicomSeries)) {

            @SuppressWarnings("unchecked")
            List<DicomSpecialElement> specialElementList =
                (List<DicomSpecialElement>) dicomSeries.getTagValue(TagW.DicomSpecialElementList);

            @SuppressWarnings("unchecked")
            List<DicomSpecialElement> patientSpecialElementList =
                (List<DicomSpecialElement>) patientGroup.getTagValue(TagW.DicomSpecialElementList);

            if (specialElementList == null || patientSpecialElementList == null) {
                return;
            }

            specialElementList.remove(dicomSpecialElement);

            if (patientSpecialElementList.remove(dicomSpecialElement)) {
                firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Update, this, null,
                    dicomSpecialElement));
            }

            if (specialElementList.size() == 0) {
                removeSeries(dicomSeries);
            }
        }
    }

    public void removeSeries(MediaSeriesGroup dicomSeries) {
        if (dicomSeries != null) {
            if (DownloadManager.TASKS.size() > 0) {
                if (dicomSeries instanceof DicomSeries) {
                    DownloadManager.stopDownloading((DicomSeries) dicomSeries, this);
                }
            }
            // remove first series in UI (Dicom Explorer, Viewer using this series)
            firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Remove, DicomModel.this, null,
                dicomSeries));
            // remove in the data model
            MediaSeriesGroup studyGroup = getParent(dicomSeries, DicomModel.study);
            removeHierarchyNode(studyGroup, dicomSeries);
            LOGGER.info("Remove Series: {}", dicomSeries); //$NON-NLS-1$
            dicomSeries.dispose();
        }
    }

    public void removeStudy(MediaSeriesGroup studyGroup) {
        if (studyGroup != null) {
            if (DownloadManager.TASKS.size() > 0) {
                Collection<MediaSeriesGroup> seriesList = getChildren(studyGroup);
                for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                    MediaSeriesGroup group = it.next();
                    if (group instanceof DicomSeries) {
                        DownloadManager.stopDownloading((DicomSeries) group, this);
                    }
                }
            }
            firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Remove, DicomModel.this, null,
                studyGroup));
            Collection<MediaSeriesGroup> seriesList = getChildren(studyGroup);
            for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                MediaSeriesGroup group = it.next();
                group.dispose();
            }
            MediaSeriesGroup patientGroup = getParent(studyGroup, DicomModel.patient);
            removeHierarchyNode(patientGroup, studyGroup);
            LOGGER.info("Remove Study: {}", studyGroup); //$NON-NLS-1$
        }
    }

    public void removePatient(MediaSeriesGroup patientGroup) {
        if (patientGroup != null) {
            if (DownloadManager.TASKS.size() > 0) {
                Collection<MediaSeriesGroup> studyList = getChildren(patientGroup);
                for (Iterator<MediaSeriesGroup> it = studyList.iterator(); it.hasNext();) {
                    MediaSeriesGroup studyGroup = it.next();
                    Collection<MediaSeriesGroup> seriesList = getChildren(studyGroup);
                    for (Iterator<MediaSeriesGroup> it2 = seriesList.iterator(); it2.hasNext();) {
                        MediaSeriesGroup group = it2.next();
                        if (group instanceof DicomSeries) {
                            DownloadManager.stopDownloading((DicomSeries) group, this);
                        }
                    }
                }
            }
            firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Remove, DicomModel.this, null,
                patientGroup));
            Collection<MediaSeriesGroup> studyList = getChildren(patientGroup);
            for (Iterator<MediaSeriesGroup> it = studyList.iterator(); it.hasNext();) {
                MediaSeriesGroup studyGroup = it.next();
                Collection<MediaSeriesGroup> seriesList = getChildren(studyGroup);
                for (Iterator<MediaSeriesGroup> it2 = seriesList.iterator(); it2.hasNext();) {
                    MediaSeriesGroup group = it2.next();
                    if (group instanceof DicomSeries) {
                        ((DicomSeries) group).dispose();
                    }
                }
            }
            removeHierarchyNode(rootNode, patientGroup);
            LOGGER.info("Remove Patient: {}", patientGroup); //$NON-NLS-1$
        }
    }

    /**
     * DicomSpecialElement are added at patientGroupLevel since StudyInstanceUID and SeriesInstanceUID are not relevant
     * with the CurrentRequestedProcedureEvidenceSequence which can reference any SOPInstance of any Study and Series of
     * the Patient
     * 
     * @param series
     */
    public void addSpecialModality(Series series) {

        List<DicomSpecialElement> seriesSpecialElementList =
            (List<DicomSpecialElement>) series.getTagValue(TagW.DicomSpecialElementList);
        if (seriesSpecialElementList == null || seriesSpecialElementList.size() == 0) {
            return;
        }

        MediaSeriesGroup patientGroup = getParent(series, DicomModel.patient);

        if (patientGroup == null) {
            return;
        }

        List<DicomSpecialElement> patientSpecialElementList =
            (List<DicomSpecialElement>) patientGroup.getTagValue(TagW.DicomSpecialElementList);

        if (patientSpecialElementList == null) {
            patientGroup.setTag(TagW.DicomSpecialElementList, patientSpecialElementList =
                new CopyOnWriteArrayList<DicomSpecialElement>());
        }
        for (DicomSpecialElement seriesSpecialElement : seriesSpecialElementList) {
            if (patientSpecialElementList.contains(seriesSpecialElement) == false) {
                patientSpecialElementList.add(seriesSpecialElement);
            }
        }

    }

    public static boolean isSpecialModality(Series series) {
        String modality = (series == null) ? null : (String) series.getTagValue(TagW.Modality);
        return (modality != null && ("PR".equals(modality) || "KO".equals(modality))); //$NON-NLS-1$ //$NON-NLS-2$ 
    }

    public static Collection<KOSpecialElement> getKoSpecialElements(MediaSeries<DicomImageElement> dicomSeries) {
        // Get all DicomSpecialElement at patient level
        List<DicomSpecialElement> specialElementList = getSpecialElements(dicomSeries);

        if (specialElementList != null) {
            String referencedSeriesInstanceUID = (String) dicomSeries.getTagValue(TagW.SeriesInstanceUID);
            return DicomSpecialElement.getKoSpecialElements(specialElementList, referencedSeriesInstanceUID);
        }
        return null;
    }

    public static List<PRSpecialElement> getPrSpecialElements(MediaSeries<DicomImageElement> dicomSeries,
        String sopUID, Integer frameNumber) {
        // Get all DicomSpecialElement at patient level
        List<DicomSpecialElement> specialElementList = getSpecialElements(dicomSeries);

        if (specialElementList != null) {
            String referencedSeriesInstanceUID = (String) dicomSeries.getTagValue(TagW.SeriesInstanceUID);
            return DicomSpecialElement.getPRSpecialElements(specialElementList, referencedSeriesInstanceUID, sopUID,
                frameNumber);
        }
        return null;
    }

    public static List<DicomSpecialElement> getSpecialElements(MediaSeries<DicomImageElement> dicomSeries) {
        if (dicomSeries == null) {
            return null;
        }

        DataExplorerModel model = (DataExplorerModel) dicomSeries.getTagValue(TagW.ExplorerModel);

        if (model instanceof DicomModel) {
            MediaSeriesGroup patientGroup = ((DicomModel) model).getParent(dicomSeries, DicomModel.patient);

            if (patientGroup != null) {
                return (List<DicomSpecialElement>) patientGroup.getTagValue(TagW.DicomSpecialElementList);
            }
        }
        return null;
    }

    public static <E> List<E> getSpecialElements(MediaSeriesGroup group, Class<E> clazz) {
        if (group != null && clazz != null && clazz.isAssignableFrom(clazz)) {
            List<DicomSpecialElement> kos = (List<DicomSpecialElement>) group.getTagValue(TagW.DicomSpecialElementList);
            if (kos != null) {
                List<E> list = new ArrayList<E>();
                for (DicomSpecialElement el : kos) {
                    if (clazz.isInstance(el)) {
                        list.add((E) el);
                    }
                }
                return list;
            }
        }
        return null;
    }

    public static <E> E getFirstSpecialElement(MediaSeriesGroup group, Class<E> clazz) {
        if (group != null && clazz != null && clazz.isAssignableFrom(clazz)) {
            List<DicomSpecialElement> kos = (List<DicomSpecialElement>) group.getTagValue(TagW.DicomSpecialElementList);
            if (kos != null) {
                for (DicomSpecialElement el : kos) {
                    if (clazz.isInstance(el)) {
                        return (E) el;
                    }
                }
            }
        }
        return null;
    }

    public static boolean hasSpecialElements(MediaSeriesGroup group, Class<? extends DicomSpecialElement> clazz) {
        if (group != null && clazz != null) {
            List<DicomSpecialElement> kos = (List<DicomSpecialElement>) group.getTagValue(TagW.DicomSpecialElementList);
            if (kos != null) {
                for (DicomSpecialElement el : kos) {
                    if (clazz.isInstance(el)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void openrelatedSeries(KOSpecialElement koSpecialElement, MediaSeriesGroup patient) {
        if (koSpecialElement != null && patient != null) {
            SeriesViewerFactory plugin = UIManager.getViewerFactory(DicomMediaIO.SERIES_MIMETYPE);
            if (plugin != null && !(plugin instanceof MimeSystemAppFactory)) {
                Set<String> koSet = koSpecialElement.getReferencedSeriesInstanceUIDSet();
                List<MediaSeries<? extends MediaElement<?>>> seriesList =
                    new ArrayList<MediaSeries<? extends MediaElement<?>>>();

                for (MediaSeriesGroup st : this.getChildren(patient)) {
                    for (MediaSeriesGroup s : this.getChildren(st)) {
                        if (koSet.contains(s.getTagValue(TagW.SeriesInstanceUID))) {
                            seriesList.add((MediaSeries<? extends MediaElement<?>>) s);
                        }
                    }
                }
                if (seriesList.size() > 0) {
                    String uid = UUID.randomUUID().toString();
                    Map<String, Object> props = Collections.synchronizedMap(new HashMap<String, Object>());
                    props.put(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER, false);
                    props.put(ViewerPluginBuilder.BEST_DEF_LAYOUT, false);
                    props.put(ViewerPluginBuilder.ICON,
                        new ImageIcon(getClass().getResource("/icon/16x16/key-images.png")));
                    props.put(ViewerPluginBuilder.UID, uid);
                    ViewerPluginBuilder builder = new ViewerPluginBuilder(plugin, seriesList, this, props);
                    ViewerPluginBuilder.openSequenceInPlugin(builder);
                    this.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Select, uid, null,
                        koSpecialElement));
                }
            }
        }
    }

    private void splitSeries(DicomMediaIO dicomReader, Series original, MediaElement media) {
        Series s = splitSeries(dicomReader, original);
        s.addMedia(media);
        LOGGER.info("Series splitting: {}", s); //$NON-NLS-1$
    }

    private Series splitSeries(DicomMediaIO dicomReader, Series original) {
        MediaSeriesGroup study = getParent(original, DicomModel.study);
        String seriesUID = (String) original.getTagValue(TagW.SeriesInstanceUID);
        int k = 1;
        while (true) {
            String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
            MediaSeriesGroup group = getHierarchyNode(study, uid);
            if (group == null) {
                break;
            }
            k++;
        }
        String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
        Series s = dicomReader.buildSeries(uid);
        dicomReader.writeMetaData(s);
        Object val = original.getTagValue(TagW.SplitSeriesNumber);
        if (val == null) {
            original.setTag(TagW.SplitSeriesNumber, 1);
        }
        s.setTag(TagW.SplitSeriesNumber, k + 1);
        s.setTag(TagW.ExplorerModel, this);
        addHierarchyNode(study, s);
        LOGGER.info("Series splitting: {}", s); //$NON-NLS-1$
        return s;
    }

    private void replaceSeries(DicomMediaIO dicomReader, Series original, MediaElement media) {
        MediaSeriesGroup study = getParent(original, DicomModel.study);
        String seriesUID = (String) original.getTagValue(TagW.SeriesInstanceUID);
        int k = 1;
        while (true) {
            String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
            MediaSeriesGroup group = getHierarchyNode(study, uid);
            if (group == null) {
                break;
            }
            k++;
        }
        String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
        Series s = dicomReader.buildSeries(uid);
        dicomReader.writeMetaData(s);
        Object val = original.getTagValue(TagW.SplitSeriesNumber);
        if (val == null) {
            // -1 convention to exclude this Series
            original.setTag(TagW.SplitSeriesNumber, -1);
        }
        s.setTag(TagW.SplitSeriesNumber, k);
        s.setTag(TagW.ExplorerModel, this);
        addHierarchyNode(study, s);
        s.addMedia(media);
        LOGGER.info("Replace Series: {}", s); //$NON-NLS-1$
    }

    private void rebuildSeries(DicomMediaIO dicomReader, MediaElement media) {
        String patientPseudoUID = (String) dicomReader.getTagValue(TagW.PatientPseudoUID);
        MediaSeriesGroup patient = getHierarchyNode(TreeModel.rootNode, patientPseudoUID);
        if (patient == null) {
            patient = new MediaSeriesGroupNode(TagW.PatientPseudoUID, patientPseudoUID, TagW.PatientName);
            dicomReader.writeMetaData(patient);
            addHierarchyNode(TreeModel.rootNode, patient);
            LOGGER.info(Messages.getString("LoadLocalDicom.add_pat") + patient); //$NON-NLS-1$
        }

        String studyUID = (String) dicomReader.getTagValue(TagW.StudyInstanceUID);
        MediaSeriesGroup study = getHierarchyNode(patient, studyUID);
        if (study == null) {
            study = new MediaSeriesGroupNode(TagW.StudyInstanceUID, studyUID, TagW.StudyDate);
            dicomReader.writeMetaData(study);
            addHierarchyNode(patient, study);
        }
        String seriesUID = (String) dicomReader.getTagValue(TagW.SeriesInstanceUID);
        Series dicomSeries = (Series) getHierarchyNode(study, seriesUID);

        if (dicomSeries == null) {
            dicomSeries = dicomReader.buildSeries(seriesUID);
            dicomReader.writeMetaData(dicomSeries);
            dicomSeries.setTag(TagW.ExplorerModel, this);
            addHierarchyNode(study, dicomSeries);
            LOGGER.info("Series rebuilding: {}", dicomSeries); //$NON-NLS-1$
        }
        dicomSeries.addMedia(media);

        // Load image and create thumbnail in this Thread
        Thumbnail t = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
        if (t == null) {
            t = DicomExplorer.createThumbnail(dicomSeries, this, Thumbnail.DEFAULT_SIZE);
            dicomSeries.setTag(TagW.Thumbnail, t);
        }
        firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, this, null, dicomSeries));
    }

    @Override
    public boolean applySplittingRules(Series original, MediaElement media) {
        if (media != null && media.getMediaReader() instanceof DicomMediaIO) {
            DicomMediaIO dicomReader = (DicomMediaIO) media.getMediaReader();
            String seriesUID = (String) original.getTagValue(TagW.SeriesInstanceUID);
            if (!seriesUID.equals(dicomReader.getTagValue(TagW.SeriesInstanceUID))) {
                rebuildSeries(dicomReader, media);
                return true;
            }
            if (original instanceof DicomSeries) {
                DicomSeries initialSeries = (DicomSeries) original;
                // Handle cases when the Series is created before getting the image (downloading)
                if (media instanceof DicomVideoElement || media instanceof DicomEncapDocElement) {
                    if (original.size(null) > 0) {
                        // When the series already contains elements (images), always split video and document
                        splitSeries(dicomReader, original, media);
                    } else {
                        replaceSeries(dicomReader, original, media);
                    }
                    return true;
                }
                if (media instanceof DicomSpecialElement) {
                    List<DicomSpecialElement> specialElementList =
                        (List<DicomSpecialElement>) initialSeries.getTagValue(TagW.DicomSpecialElementList);

                    if (specialElementList == null) {
                        initialSeries.setTag(TagW.DicomSpecialElementList, specialElementList =
                            new CopyOnWriteArrayList<DicomSpecialElement>());
                    } else if ("SR".equals(dicomReader.getTagValue(TagW.Modality))) {
                        // Split SR series to have only one object by series
                        Series s = splitSeries(dicomReader, initialSeries);
                        specialElementList = new CopyOnWriteArrayList<DicomSpecialElement>();
                        specialElementList.add((DicomSpecialElement) media);
                        s.setTag(TagW.DicomSpecialElementList, specialElementList);
                        return false;
                    }
                    specialElementList.add((DicomSpecialElement) media);
                    return false;
                }

                int frames = dicomReader.getMediaElementNumber();
                if (frames < 1) {
                    initialSeries.addMedia(media);
                } else {
                    Modality modality = Modality.getModality((String) initialSeries.getTagValue(TagW.Modality));

                    TagW[] rules = frames > 1 ? multiframeSplittingRules : splittingRules.get(modality);

                    if (rules == null) {
                        rules = splittingRules.get(Modality.Default);
                    }
                    // If similar add to the original series
                    if (isSimilar(rules, initialSeries, media)) {
                        initialSeries.addMedia(media);
                        return false;
                    }
                    // else try to find a similar previous split series
                    MediaSeriesGroup study = getParent(initialSeries, DicomModel.study);
                    int k = 1;
                    while (true) {
                        String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
                        MediaSeriesGroup group = getHierarchyNode(study, uid);
                        if (group instanceof DicomSeries) {
                            if (isSimilar(rules, (DicomSeries) group, media)) {
                                ((DicomSeries) group).addMedia(media);
                                return false;
                            }
                        } else {
                            break;
                        }
                        k++;
                    }
                    // no matching series exists, so split series
                    splitSeries(dicomReader, initialSeries, media);
                    return true;
                }
            } else if (original instanceof DicomVideoSeries || original instanceof DicomEncapDocSeries) {
                if (original.size(null) > 0) {
                    // Always split when it is a video or a document
                    if (media instanceof DicomVideoElement || media instanceof DicomEncapDocElement) {
                        splitSeries(dicomReader, original, media);
                        return true;
                    } else {
                        findMatchingSeriesOrsplit(original, media);
                    }
                } else {
                    original.addMedia(media);
                }
            }
        }
        return false;
    }

    private boolean findMatchingSeriesOrsplit(Series original, MediaElement media) {
        DicomMediaIO dicomReader = (DicomMediaIO) media.getMediaReader();
        int frames = dicomReader.getMediaElementNumber();
        if (frames < 1) {
            original.addMedia(media);
        } else {
            Modality modality = Modality.getModality((String) original.getTagValue(TagW.Modality));
            String seriesUID = (String) original.getTagValue(TagW.SeriesInstanceUID);
            TagW[] rules = frames > 1 ? multiframeSplittingRules : splittingRules.get(modality);

            if (rules == null) {
                rules = splittingRules.get(Modality.Default);
            }
            // If similar add to the original series
            if (isSimilar(rules, original, media)) {
                original.addMedia(media);
                return false;
            }
            // else try to find a similar previous split series
            MediaSeriesGroup study = getParent(original, DicomModel.study);
            int k = 1;
            while (true) {
                String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
                MediaSeriesGroup group = getHierarchyNode(study, uid);
                if (group instanceof Series) {
                    if (isSimilar(rules, (Series) group, media)) {
                        ((Series) group).addMedia(media);
                        return false;
                    }
                } else {
                    break;
                }
                k++;
            }
            // no matching series exists, so split series
            splitSeries(dicomReader, original, media);
            return true;
        }
        return false;
    }

    private static boolean isSimilar(TagW[] rules, Series<?> s, final MediaElement<?> media) {
        final MediaElement<?> firstMedia = s.getMedia(0, null, null);
        if (firstMedia == null) {
            // no image
            return true;
        }
        // Not similar when the instances have different classes (even when inheriting class)
        if (firstMedia.getClass() != media.getClass()) {
            return false;
        }
        for (TagW tagElement : rules) {
            Object tag = media.getTagValue(tagElement);
            Object tag2 = firstMedia.getTagValue(tagElement);
            // special case if both are null
            if (tag == null && tag2 == null) {
                continue;
            }
            if (tag != null && !tag.equals(tag2)) {
                if (TagW.ImageOrientationPlane.equals(tagElement)) {
                    String val = (String) firstMedia.getTagValue(TagW.ImageType);
                    // Exclude images that are MIP in different directions.
                    if (val != null && val.contains("PROJECTION")) {
                        continue;
                    }
                    // } else if (TagW.TemporalPositionIndex.equals(tagElement)) {
                    // // DICOM Enhanced: split only when stackID exists.
                    // String val = (String) firstMedia.getTagValue(TagW.StackID);
                    // if (val == null || val.equals(media.getTagValue(TagW.StackID))) {
                    // continue;
                    // }
                }
                return false;
            }
        }
        return true;
    }

    public void get(String[] argv) throws IOException {
        final String[] usage = { "Load DICOM files remotely or locally", "Usage: dicom:get [Options] SOURCE", //$NON-NLS-1$ //$NON-NLS-2$
            "  -l --local		Open DICOMs from local disk", //$NON-NLS-1$
            "  -r --remote       Open DICOMs from an URL", //$NON-NLS-1$
            "  -p --portable       Open DICOMs from default directories at the same level of the executable", //$NON-NLS-1$
            "  -i --iwado        Open DICOMs from an XML (GZIP, Base64) file containing UIDs", //$NON-NLS-1$
            "  -w --wado		Open DICOMs from an XML (URL) file containing UIDs", "  -? --help		show help" }; //$NON-NLS-1$ //$NON-NLS-2$

        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || (args.isEmpty() && !opt.isSet("portable"))) { //$NON-NLS-1$ //$NON-NLS-2$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {

                firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Select, DicomModel.this, null,
                    DicomModel.this));
                // start importing local dicom series list
                if (opt.isSet("local")) { //$NON-NLS-1$
                    File[] files = new File[args.size()];
                    for (int i = 0; i < files.length; i++) {
                        files[i] = new File(args.get(i));
                    }
                    loadingExecutor.execute(new LoadLocalDicom(files, true, DicomModel.this));
                } else if (opt.isSet("remote")) { //$NON-NLS-1$
                    loadingExecutor.execute(new LoadRemoteDicomURL(args.toArray(new String[args.size()]),
                        DicomModel.this));
                }
                // build WADO series list to download
                else if (opt.isSet("wado")) { //$NON-NLS-1$
                    loadingExecutor.execute(new LoadRemoteDicomManifest(args.toArray(new String[args.size()]),
                        DicomModel.this));
                } else if (opt.isSet("iwado")) { //$NON-NLS-1$
                    String[] xmlRef = args.toArray(new String[args.size()]);
                    File[] xmlFiles = new File[args.size()];
                    for (int i = 0; i < xmlFiles.length; i++) {
                        try {
                            File tempFile = File.createTempFile("wado_", ".xml", AppProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                            if (FileUtil.writeStream(new ByteArrayInputStream(Base64.decode(xmlRef[i])),
                                new FileOutputStream(tempFile)) == -1) {
                                xmlFiles[i] = tempFile;
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    loadingExecutor.execute(new LoadRemoteDicomManifest(xmlFiles, DicomModel.this));
                }
                // Get DICOM folder (by default DICOM, dicom, IHE_PDI, ihe_pdi) at the same level at the Weasis
                // executable file
                else if (opt.isSet("portable")) { //$NON-NLS-1$

                    String prop = System.getProperty("weasis.portable.dicom.directory"); //$NON-NLS-1$
                    String baseDir = System.getProperty("weasis.portable.dir"); //$NON-NLS-1$

                    if (prop != null && baseDir != null) {
                        String[] dirs = prop.split(","); //$NON-NLS-1$
                        for (int i = 0; i < dirs.length; i++) {
                            dirs[i] = dirs[i].trim().replaceAll("/", File.separator); //$NON-NLS-1$
                        }
                        File[] files = new File[dirs.length];
                        boolean notCaseSensitive = AppProperties.OPERATING_SYSTEM.startsWith("win");//$NON-NLS-1$
                        if (notCaseSensitive) {
                            Arrays.sort(dirs, String.CASE_INSENSITIVE_ORDER);
                        }
                        String last = null;
                        for (int i = 0; i < files.length; i++) {
                            if (notCaseSensitive && last != null && dirs[i].equalsIgnoreCase(last)) {
                                last = null;
                            } else {
                                last = dirs[i];
                                files[i] = new File(baseDir, dirs[i]);
                            }
                        }

                        ArrayList<LoadSeries> loadSeries = null;
                        File dcmDirFile = new File(baseDir, "DICOMDIR"); //$NON-NLS-1$
                        if (dcmDirFile.canRead()) {
                            // Copy images in cache if property weasis.portable.dicom.cache = true (default is true)
                            DicomDirLoader dirImport =
                                new DicomDirLoader(dcmDirFile, DicomModel.this, DicomManager.getInstance()
                                    .isPortableDirCache());
                            loadSeries = dirImport.readDicomDir();
                        }
                        if (loadSeries != null && loadSeries.size() > 0) {
                            loadingExecutor.execute(new LoadDicomDir(loadSeries, DicomModel.this));
                        } else {
                            loadingExecutor.execute(new LoadLocalDicom(files, true, DicomModel.this));
                        }
                    }
                }
            }
        });
    }

    public void close(String[] argv) throws IOException {
        final String[] usage = { "Remove DICOM files in Dicom Explorer", //$NON-NLS-1$
            "Usage: dicom:close [patient | study | series] [ARGS]", //$NON-NLS-1$
            "  -a --all Close all patients", //$NON-NLS-1$
            "  -p --patient <args>	Close patient, [arg] is patientUID (PatientID + Patient Birth Date, by default)", //$NON-NLS-1$
            "  -y --study <args>	Close study, [arg] is Study Instance UID", //$NON-NLS-1$
            "  -s --series <args>	Close series, [arg] is Series Instance UID", "  -? --help		show help" }; //$NON-NLS-1$ //$NON-NLS-2$
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || (args.isEmpty() && !opt.isSet("all"))) { //$NON-NLS-1$ //$NON-NLS-2$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Select, DicomModel.this, null,
                    DicomModel.this));
                // start build local dicom series list
                if (opt.isSet("patient")) { //$NON-NLS-1$
                    for (String patientUID : args) {
                        MediaSeriesGroup patientGroup = null;
                        // In Weasis, Global Identity of the patient is composed of the patientID and the birth date by
                        // default
                        // TODO handle preferences choice for patientUID
                        patientGroup = getHierarchyNode(TreeModel.rootNode, patientUID);
                        if (patientGroup == null) {
                            System.out.println("Cannot find patient: " + patientUID); //$NON-NLS-1$
                            continue;
                        } else {
                            removePatient(patientGroup);
                        }
                    }
                } else if (opt.isSet("all")) { //$NON-NLS-1$
                    for (MediaSeriesGroup patientGroup : model.getSuccessors(rootNode)) {
                        removePatient(patientGroup);
                    }
                } else if (opt.isSet("study")) { //$NON-NLS-1$
                    for (String studyUID : args) {
                        for (MediaSeriesGroup ptGroup : model.getSuccessors(rootNode)) {
                            MediaSeriesGroup stGroup = getHierarchyNode(ptGroup, studyUID);
                            if (stGroup != null) {
                                removeStudy(stGroup);
                                break;
                            }
                        }
                    }
                } else if (opt.isSet("series")) { //$NON-NLS-1$
                    for (String seriesUID : args) {
                        patientLevel: for (MediaSeriesGroup ptGroup : model.getSuccessors(rootNode)) {
                            for (MediaSeriesGroup stGroup : model.getSuccessors(ptGroup)) {
                                MediaSeriesGroup series = getHierarchyNode(stGroup, seriesUID);
                                if (series instanceof Series) {
                                    removeSeries(series);
                                    break patientLevel;
                                }
                            }
                        }
                    }
                }

            }
        });
    }

    @Override
    public TreeModelNode getTreeModelNodeForNewPlugin() {
        return patient;
    }

}
