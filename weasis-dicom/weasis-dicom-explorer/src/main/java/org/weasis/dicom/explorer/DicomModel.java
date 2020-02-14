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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import org.apache.felix.service.command.CommandProcessor;
import org.dcm4che3.data.Tag;
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
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.ThreadUtil;
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
import org.weasis.dicom.codec.RejectedKOSpecialElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.utils.SplittingModalityRules;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Rule;
import org.weasis.dicom.codec.utils.SplittingRules;
import org.weasis.dicom.explorer.rs.RsQueryParams;
import org.weasis.dicom.explorer.wado.DicomManager;
import org.weasis.dicom.explorer.wado.DownloadManager;
import org.weasis.dicom.explorer.wado.LoadRemoteDicomManifest;
import org.weasis.dicom.explorer.wado.LoadRemoteDicomURL;
import org.weasis.dicom.explorer.wado.LoadSeries;

@org.osgi.service.component.annotations.Component(immediate = false, property = {
    CommandProcessor.COMMAND_SCOPE + "=dicom", CommandProcessor.COMMAND_FUNCTION + "=get",
    CommandProcessor.COMMAND_FUNCTION + "=rs",
    CommandProcessor.COMMAND_FUNCTION + "=close" }, service = DicomModel.class)
public class DicomModel implements TreeModel, DataExplorerModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomModel.class);

    public static final String NAME = "DICOM"; //$NON-NLS-1$
    public static final String PREFERENCE_NODE = "dicom.model"; //$NON-NLS-1$

    public static final TreeModelNode patient =
        new TreeModelNode(1, 0, TagW.PatientPseudoUID, new TagView(TagD.getTagFromIDs(Tag.PatientName, Tag.PatientID)));
    public static final TreeModelNode study = new TreeModelNode(2, 0, TagD.get(Tag.StudyInstanceUID),
        new TagView(TagD.getTagFromIDs(Tag.StudyDate, Tag.AccessionNumber, Tag.StudyID, Tag.StudyDescription)));
    public static final TreeModelNode series = new TreeModelNode(3, 0, TagW.SubseriesInstanceUID,
        new TagView(TagD.getTagFromIDs(Tag.SeriesDescription, Tag.SeriesNumber, Tag.SeriesTime)));
    public static final ExecutorService LOADING_EXECUTOR = ThreadUtil.buildNewSingleThreadExecutor("Dicom Model"); //$NON-NLS-1$

    private static final List<TreeModelNode> modelStructure = Arrays.asList(TreeModelNode.ROOT, patient, study, series);

    private final Tree<MediaSeriesGroup> model;
    private PropertyChangeSupport propertyChange = null;
    private final SplittingRules splittingRules;

    public DicomModel() {
        model = new Tree<>(MediaSeriesGroupNode.rootNode);
        splittingRules = new SplittingRules();
    }

    @Override
    public List<Codec> getCodecPlugins() {
        ArrayList<Codec> codecPlugins = new ArrayList<>(1);
        synchronized (BundleTools.CODEC_PLUGINS) {
            for (Codec codec : BundleTools.CODEC_PLUGINS) {
                if (codec != null && !"Sun java imageio".equals(codec.getCodecName()) //$NON-NLS-1$
                    && codec.isMimeTypeSupported(DicomMediaIO.DICOM_MIMETYPE) && !codecPlugins.contains(codec)) {
                    codecPlugins.add(codec);
                }
            }
        }
        return codecPlugins;
    }

    @Override
    public Collection<MediaSeriesGroup> getChildren(MediaSeriesGroup node) {
        return model.getSuccessors(node);
    }

    @Override
    public MediaSeriesGroup getHierarchyNode(MediaSeriesGroup parent, Object valueID) {
        if (parent != null || valueID != null) {
            for (MediaSeriesGroup node : getChildren(parent)) {
                if (node.matchIdValue(valueID)) {
                    return node;
                }
            }
        }
        return null;
    }

    public void mergePatientUID(String oldPatientUID, String newPatientUID) {
        MediaSeriesGroup pt = getHierarchyNode(MediaSeriesGroupNode.rootNode, oldPatientUID);
        MediaSeriesGroup pt2 = getHierarchyNode(MediaSeriesGroupNode.rootNode, newPatientUID);

        if (pt == null || Objects.equals(pt, pt2)) {
            return;
        }
        if (pt2 == null) {
            pt.addMergeIdValue(newPatientUID);
        } else {
            Collection<MediaSeriesGroup> studies = getChildren(pt);
            Map<MediaSeriesGroup, Collection<MediaSeriesGroup>> studyMap = new HashMap<>();
            for (MediaSeriesGroup st : studies) {
                studyMap.put(st, getChildren(st));
            }

            removeHierarchyNode(MediaSeriesGroupNode.rootNode, pt);

            for (Entry<MediaSeriesGroup, Collection<MediaSeriesGroup>> stEntry : studyMap.entrySet()) {
                MediaSeriesGroup st = stEntry.getKey();
                addHierarchyNode(pt, st);
                for (MediaSeriesGroup s : stEntry.getValue()) {
                    addHierarchyNode(st, s);
                }
            }
            firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.UDPATE_PARENT, DicomModel.this, pt, pt2));
        }
    }

    public MediaSeriesGroup getStudyNode(String studyUID) {
        Objects.requireNonNull(studyUID);
        for (MediaSeriesGroup pt : getChildren(MediaSeriesGroupNode.rootNode)) {
            for (MediaSeriesGroup st : getChildren(pt)) {
                if (st.matchIdValue(studyUID)) {
                    return st;
                }
            }
        }
        return null;
    }

    public MediaSeriesGroup getSeriesNode(String seriesUID) {
        Objects.requireNonNull(seriesUID);
        for (MediaSeriesGroup pt : getChildren(MediaSeriesGroupNode.rootNode)) {
            for (MediaSeriesGroup st : getChildren(pt)) {
                for (MediaSeriesGroup item : getChildren(st)) {
                    if (item.matchIdValue(seriesUID)) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void addHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf) {
        model.addLeaf(root, leaf);
    }

    @Override
    public void removeHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf) {
        Tree<MediaSeriesGroup> tree = model.getTree(root);
        if (tree != null) {
            tree.removeLeaf(leaf);
        }
    }

    @Override
    public MediaSeriesGroup getParent(MediaSeriesGroup node, TreeModelNode modelNode) {
        if (node != null && modelNode != null) {
            TagW matchTagID = modelNode.getTagElement();
            if (node.getTagID().equals(matchTagID)) {
                return node;
            }
            Tree<MediaSeriesGroup> tree = model.getTree(node);
            if (tree != null) {
                Tree<MediaSeriesGroup> parent;
                while ((parent = tree.getParent()) != null) {
                    if (parent.getHead().getTagID().equals(matchTagID)) {
                        return parent.getHead();
                    }
                    tree = parent;
                }
            }
        }
        return null;
    }

    public void dispose() {
        removeAllPropertyChangeListener();

        for (MediaSeriesGroup pt : getChildren(MediaSeriesGroupNode.rootNode)) {
            for (MediaSeriesGroup st : getChildren(pt)) {
                for (MediaSeriesGroup item : getChildren(st)) {
                    item.dispose();
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
        return modelStructure;
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

    public void removeAllPropertyChangeListener() {
        if (propertyChange != null) {
            for (PropertyChangeListener listener : propertyChange.getPropertyChangeListeners()) {
                propertyChange.removePropertyChangeListener(listener);
            }
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
                SwingUtilities.invokeLater(() -> propertyChange.firePropertyChange(event));
            }
        }
    }

    public void mergeSeries(List<MediaSeries<? extends MediaElement>> seriesList) {
        if (seriesList != null && seriesList.size() > 1) {
            String uid = TagD.getTagValue(seriesList.get(0), Tag.SeriesInstanceUID, String.class);
            boolean sameOrigin = true;
            if (uid != null) {
                for (int i = 1; i < seriesList.size(); i++) {
                    if (!uid.equals(TagD.getTagValue(seriesList.get(i), Tag.SeriesInstanceUID))) {
                        sameOrigin = false;
                        break;
                    }
                }
            }
            if (sameOrigin) {
                int min = Integer.MAX_VALUE;
                MediaSeries<? extends MediaElement> base = seriesList.get(0);
                for (MediaSeries<? extends MediaElement> s : seriesList) {
                    Integer splitNb = (Integer) s.getTagValue(TagW.SplitSeriesNumber);
                    if (splitNb != null && min > splitNb) {
                        min = splitNb;
                        base = s;
                    }
                }
                for (MediaSeries<? extends MediaElement> s : seriesList) {
                    if (s != base) {
                        base.addAll((Collection) s.getMedias(null, null));
                        removeSeriesWithoutDisposingMedias(s);
                    }
                }
                // Force to sort the new merged media list
                List sortedMedias = base.getSortedMedias(null);
                Collections.sort(sortedMedias, SortSeriesStack.instanceNumber);
                // update observer
                this.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.REPLACE, DicomModel.this, base, base));
            }
        }
    }

    public void removeSpecialElement(DicomSpecialElement dicomSpecialElement) {
        if (dicomSpecialElement == null) {
            return;
        }

        String patientPseudoUID = (String) dicomSpecialElement.getTagValue(TagW.PatientPseudoUID);
        MediaSeriesGroup patientGroup = getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);

        if (patientGroup == null) {
            return;
        }

        String studyUID = TagD.getTagValue(dicomSpecialElement, Tag.StudyInstanceUID, String.class);
        MediaSeriesGroup studyGroup = getHierarchyNode(patientGroup, studyUID);
        if (studyGroup == null) {
            return;
        }

        String seriesUID = TagD.getTagValue(dicomSpecialElement, Tag.SeriesInstanceUID, String.class);
        Series<?> dicomSeries = (Series<?>) getHierarchyNode(studyGroup, seriesUID);
        if (dicomSeries == null) {
            return;
        }

        if (isSpecialModality(dicomSeries)) {
            List<DicomSpecialElement> specialElementList =
                (List<DicomSpecialElement>) dicomSeries.getTagValue(TagW.DicomSpecialElementList);

            List<DicomSpecialElement> patientSpecialElementList =
                (List<DicomSpecialElement>) patientGroup.getTagValue(TagW.DicomSpecialElementList);

            if (specialElementList == null || patientSpecialElementList == null) {
                return;
            }

            specialElementList.remove(dicomSpecialElement);

            if (patientSpecialElementList.remove(dicomSpecialElement)) {
                firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.UPDATE, this, null, dicomSpecialElement));
            }

            if (specialElementList.isEmpty()) {
                removeSeries(dicomSeries);
            }
        }
    }

    public void removeSeriesWithoutDisposingMedias(MediaSeriesGroup dicomSeries) {
        if (dicomSeries != null) {
            // remove first series in UI (Dicom Explorer, Viewer using this series)
            firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.REMOVE, DicomModel.this, null, dicomSeries));
            // remove in the data model
            MediaSeriesGroup studyGroup = getParent(dicomSeries, DicomModel.study);
            removeHierarchyNode(studyGroup, dicomSeries);
            LOGGER.info("Remove Series (no dispose): {}", dicomSeries); //$NON-NLS-1$
        }
    }

    public void removeSeries(MediaSeriesGroup dicomSeries) {
        if (dicomSeries != null) {
            if (!DownloadManager.TASKS.isEmpty() && dicomSeries instanceof DicomSeries) {
                DownloadManager.stopDownloading((DicomSeries) dicomSeries, this);
            }
            // remove first series in UI (Dicom Explorer, Viewer using this series)
            firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.REMOVE, DicomModel.this, null, dicomSeries));
            // remove in the data model
            MediaSeriesGroup studyGroup = getParent(dicomSeries, DicomModel.study);
            removeHierarchyNode(studyGroup, dicomSeries);
            dicomSeries.dispose();
            LOGGER.info("Remove Series: {}", dicomSeries); //$NON-NLS-1$
        }
    }

    public void removeStudy(MediaSeriesGroup studyGroup) {
        if (studyGroup != null) {
            if (!DownloadManager.TASKS.isEmpty()) {
                for (MediaSeriesGroup group : getChildren(studyGroup)) {
                    if (group instanceof DicomSeries) {
                        DownloadManager.stopDownloading((DicomSeries) group, this);
                    }
                }
            }
            firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.REMOVE, DicomModel.this, null, studyGroup));
            for (MediaSeriesGroup group : getChildren(studyGroup)) {
                group.dispose();
            }
            MediaSeriesGroup patientGroup = getParent(studyGroup, DicomModel.patient);
            removeHierarchyNode(patientGroup, studyGroup);
            LOGGER.info("Remove Study: {}", studyGroup); //$NON-NLS-1$
        }
    }

    public void removePatient(MediaSeriesGroup patientGroup) {
        if (patientGroup != null) {
            if (!DownloadManager.TASKS.isEmpty()) {
                for (MediaSeriesGroup studyGroup : getChildren(patientGroup)) {
                    for (MediaSeriesGroup group : getChildren(studyGroup)) {
                        if (group instanceof DicomSeries) {
                            DownloadManager.stopDownloading((DicomSeries) group, this);
                        }
                    }
                }
            }
            firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.REMOVE, DicomModel.this, null, patientGroup));
            for (MediaSeriesGroup studyGroup : getChildren(patientGroup)) {
                for (MediaSeriesGroup group : getChildren(studyGroup)) {
                    group.dispose();
                }
            }

            List<DicomSpecialElement> sps =
                (List<DicomSpecialElement>) patientGroup.getTagValue(TagW.DicomSpecialElementList);
            if (sps != null) {
                for (DicomSpecialElement d : sps) {
                    d.dispose();
                }
            }
            removeHierarchyNode(MediaSeriesGroupNode.rootNode, patientGroup);
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
        if (seriesSpecialElementList == null || seriesSpecialElementList.isEmpty()) {
            return;
        }

        MediaSeriesGroup patientGroup = getParent(series, DicomModel.patient);

        if (patientGroup == null) {
            return;
        }

        List<DicomSpecialElement> patientSpecialElementList =
            (List<DicomSpecialElement>) patientGroup.getTagValue(TagW.DicomSpecialElementList);

        if (patientSpecialElementList == null) {
            patientSpecialElementList = new CopyOnWriteArrayList<>();
            patientGroup.setTag(TagW.DicomSpecialElementList, patientSpecialElementList);
        }
        for (DicomSpecialElement seriesSpecialElement : seriesSpecialElementList) {
            if (!patientSpecialElementList.contains(seriesSpecialElement)) {
                patientSpecialElementList.add(seriesSpecialElement);
            }
        }

    }

    public static boolean isSpecialModality(MediaSeries<?> series) {
        String modality = (series == null) ? null : TagD.getTagValue(series, Tag.Modality, String.class);
        return modality != null && ("PR".equals(modality) || "KO".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static Collection<KOSpecialElement> getEditableKoSpecialElements(MediaSeriesGroup group) {
        List<KOSpecialElement> list = getSpecialElements(group, KOSpecialElement.class);
        if (list != null && !list.isEmpty()) {
            for (int i = list.size() - 1; i >= 0; i--) {
                KOSpecialElement koElement = list.get(i);
                if (!koElement.getMediaReader().isEditableDicom()) {
                    list.remove(i);
                }
            }
            return list;
        }
        return Collections.emptyList();
    }

    public static Collection<KOSpecialElement> getKoSpecialElements(MediaSeries<DicomImageElement> dicomSeries) {
        // Get all DicomSpecialElement at patient level
        List<DicomSpecialElement> specialElementList = getSpecialElements(dicomSeries);

        if (specialElementList != null) {
            String referencedSeriesInstanceUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
            return DicomSpecialElement.getKoSpecialElements(specialElementList, referencedSeriesInstanceUID);
        }
        return Collections.emptyList();
    }

    public static Collection<RejectedKOSpecialElement> getRejectionKoSpecialElements(
        MediaSeries<DicomImageElement> dicomSeries) {
        // Get all DicomSpecialElement at patient level
        List<DicomSpecialElement> specialElementList = getSpecialElements(dicomSeries);

        if (specialElementList != null) {
            String referencedSeriesInstanceUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
            return DicomSpecialElement.getRejectionKoSpecialElements(specialElementList, referencedSeriesInstanceUID);
        }
        return Collections.emptyList();
    }

    public static RejectedKOSpecialElement getRejectionKoSpecialElement(MediaSeries<DicomImageElement> dicomSeries,
        String sopUID, Integer dicomFrameNumber) {
        // Get all DicomSpecialElement at patient level
        List<DicomSpecialElement> specialElementList = getSpecialElements(dicomSeries);

        if (specialElementList != null) {
            String referencedSeriesInstanceUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
            return DicomSpecialElement.getRejectionKoSpecialElement(specialElementList, referencedSeriesInstanceUID,
                sopUID, dicomFrameNumber);
        }
        return null;
    }

    public static List<PRSpecialElement> getPrSpecialElements(MediaSeries<DicomImageElement> dicomSeries, String sopUID,
        Integer dicomFrameNumber) {
        // Get all DicomSpecialElement at patient level
        List<DicomSpecialElement> specialElementList = getSpecialElements(dicomSeries);

        if (!specialElementList.isEmpty()) {
            String referencedSeriesInstanceUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
            return DicomSpecialElement.getPRSpecialElements(specialElementList, referencedSeriesInstanceUID, sopUID,
                dicomFrameNumber);
        }
        return Collections.emptyList();
    }

    public static List<DicomSpecialElement> getSpecialElements(MediaSeries<DicomImageElement> dicomSeries) {
        if (dicomSeries == null) {
            return Collections.emptyList();
        }

        List<DicomSpecialElement> list = null;
        DataExplorerModel model = (DataExplorerModel) dicomSeries.getTagValue(TagW.ExplorerModel);

        if (model instanceof DicomModel) {
            MediaSeriesGroup patientGroup = ((DicomModel) model).getParent(dicomSeries, DicomModel.patient);

            if (patientGroup != null) {
                list = (List<DicomSpecialElement>) patientGroup.getTagValue(TagW.DicomSpecialElementList);
            }
        }
        return list == null ? Collections.emptyList() : list;
    }

    public static <E> List<E> getSpecialElements(MediaSeriesGroup group, Class<E> clazz) {
        if (group != null && clazz != null && clazz.isAssignableFrom(clazz)) {
            List<DicomSpecialElement> kos = (List<DicomSpecialElement>) group.getTagValue(TagW.DicomSpecialElementList);
            if (kos != null) {
                List<E> list = new ArrayList<>();
                for (DicomSpecialElement el : kos) {
                    if (clazz.isInstance(el)) {
                        list.add((E) el);
                    }
                }
                return list;
            }
        }
        return Collections.emptyList();
    }

    public static <E> E getFirstSpecialElement(MediaSeriesGroup group, Class<E> clazz) {
        if (group != null && clazz != null && clazz.isAssignableFrom(clazz)) {
            List<DicomSpecialElement> sps = (List<DicomSpecialElement>) group.getTagValue(TagW.DicomSpecialElementList);
            if (sps != null) {
                for (DicomSpecialElement el : sps) {
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
                List<MediaSeries<MediaElement>> seriesList = new ArrayList<>();

                for (MediaSeriesGroup st : this.getChildren(patient)) {
                    for (MediaSeriesGroup s : this.getChildren(st)) {
                        if (koSet.contains(TagD.getTagValue(s, Tag.SeriesInstanceUID))) {
                            seriesList.add((MediaSeries<MediaElement>) s);
                        }
                    }
                }
                if (!seriesList.isEmpty()) {
                    String uid = UUID.randomUUID().toString();
                    Map<String, Object> props = Collections.synchronizedMap(new HashMap<String, Object>());
                    props.put(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER, false);
                    props.put(ViewerPluginBuilder.BEST_DEF_LAYOUT, false);
                    props.put(ViewerPluginBuilder.ICON,
                        new ImageIcon(getClass().getResource("/icon/16x16/key-images.png"))); //$NON-NLS-1$
                    props.put(ViewerPluginBuilder.UID, uid);
                    ViewerPluginBuilder builder = new ViewerPluginBuilder(plugin, seriesList, this, props);
                    ViewerPluginBuilder.openSequenceInPlugin(builder);
                    this.firePropertyChange(
                        new ObservableEvent(ObservableEvent.BasicAction.SELECT, uid, null, koSpecialElement));
                }
            }
        }
    }

    private void splitSeries(DicomMediaIO dicomReader, Series original, MediaElement media) {
        Series s = splitSeries(dicomReader, original);
        s.addMedia(media);
    }

    private Series splitSeries(DicomMediaIO dicomReader, Series original) {
        MediaSeriesGroup st = getParent(original, DicomModel.study);
        String seriesUID = TagD.getTagValue(original, Tag.SeriesInstanceUID, String.class);
        int k = 1;
        while (true) {
            String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
            MediaSeriesGroup group = getHierarchyNode(st, uid);
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
        s.setTag(TagW.WadoParameters, original.getTagValue(TagW.WadoParameters));
        addHierarchyNode(st, s);
        LOGGER.info("Series splitting: {}", s); //$NON-NLS-1$
        return s;
    }

    private void replaceSeries(DicomMediaIO dicomReader, Series original, MediaElement media) {
        MediaSeriesGroup st = getParent(original, DicomModel.study);
        String seriesUID = TagD.getTagValue(original, Tag.SeriesInstanceUID, String.class);

        int k = 1;
        while (true) {
            String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
            MediaSeriesGroup group = getHierarchyNode(st, uid);
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
        s.setTag(TagW.WadoParameters, original.getTagValue(TagW.WadoParameters));
        addHierarchyNode(st, s);
        s.addMedia(media);
        LOGGER.info("Replace Series: {}", s); //$NON-NLS-1$
    }

    private void rebuildSeries(DicomMediaIO dicomReader, MediaElement media) {
        String studyUID = TagD.getTagValue(dicomReader, Tag.StudyInstanceUID, String.class);
        String patientPseudoUID = (String) dicomReader.getTagValue(TagW.PatientPseudoUID);
        MediaSeriesGroup pt = getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
        if (pt == null) {
            MediaSeriesGroup st = getStudyNode(studyUID);
            if (st == null) {
                pt = new MediaSeriesGroupNode(TagW.PatientPseudoUID, patientPseudoUID, DicomModel.patient.getTagView());
                dicomReader.writeMetaData(pt);
                addHierarchyNode(MediaSeriesGroupNode.rootNode, pt);
                LOGGER.info("Adding patient: {}", pt); //$NON-NLS-1$
            } else {
                pt = getParent(st, DicomModel.patient);
                LOGGER.warn("DICOM patient attributes are inconsistent! Name or ID is different within an exam."); //$NON-NLS-1$
            }
        }

        MediaSeriesGroup st = getHierarchyNode(pt, studyUID);
        if (st == null) {
            st = new MediaSeriesGroupNode(TagD.get(Tag.StudyInstanceUID), studyUID, DicomModel.study.getTagView());
            dicomReader.writeMetaData(st);
            addHierarchyNode(pt, st);
        }

        String seriesUID = TagD.getTagValue(dicomReader, Tag.SeriesInstanceUID, String.class);
        Series dicomSeries = (Series) getHierarchyNode(st, seriesUID);

        if (dicomSeries == null) {
            dicomSeries = dicomReader.buildSeries(seriesUID);
            dicomReader.writeMetaData(dicomSeries);
            dicomSeries.setTag(TagW.ExplorerModel, this);
            addHierarchyNode(st, dicomSeries);
            LOGGER.info("Series rebuilding: {}", dicomSeries); //$NON-NLS-1$
        }
        dicomSeries.addMedia(media);

        // Load image and create thumbnail in this Thread
        Thumbnail t = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
        if (t == null) {
            t = DicomExplorer.createThumbnail(dicomSeries, this, Thumbnail.DEFAULT_SIZE);
            dicomSeries.setTag(TagW.Thumbnail, t);
            Optional.ofNullable(t).ifPresent(Thumbnail::repaint);
        }
        firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.ADD, this, null, dicomSeries));
    }

    @Override
    public boolean applySplittingRules(Series original, MediaElement media) {
        if (media != null && media.getMediaReader() instanceof DicomMediaIO) {
            DicomMediaIO dicomReader = (DicomMediaIO) media.getMediaReader();
            String seriesUID = TagD.getTagValue(original, Tag.SeriesInstanceUID, String.class);
            if (!seriesUID.equals(TagD.getTagValue(dicomReader, Tag.SeriesInstanceUID))) {
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
                    String rMime = dicomReader.getMimeType();
                    if (specialElementList == null) {
                        specialElementList = new CopyOnWriteArrayList<>();
                        initialSeries.setTag(TagW.DicomSpecialElementList, specialElementList);
                    } else if ("sr/dicom".equals(rMime) || "wf/dicom".equals(rMime)) { //$NON-NLS-1$ //$NON-NLS-2$
                        // Split SR series to have only one object by series
                        Series s = splitSeries(dicomReader, initialSeries);
                        specialElementList = new CopyOnWriteArrayList<>();
                        specialElementList.add((DicomSpecialElement) media);
                        s.setTag(TagW.DicomSpecialElementList, specialElementList);
                        return false;
                    }
                    specialElementList.add((DicomSpecialElement) media);
                    return false;
                }

                int frames = dicomReader.getMediaElementNumber();
                if (frames < 1) {
                    initialSeries.addMedia((DicomImageElement) media);
                } else {
                    Modality modality =
                        Modality.getModality(TagD.getTagValue(initialSeries, Tag.Modality, String.class));

                    SplittingModalityRules splitRules =
                        splittingRules.getSplittingModalityRules(modality, Modality.DEFAULT);
                    List<Rule> rules;
                    if (splitRules == null) {
                        rules = Collections.emptyList();
                    } else {
                        rules = frames > 1 ? splitRules.getMultiFrameRules() : splitRules.getSingleFrameRules();
                    }
                    // If similar add to the original series
                    if (isSimilar(rules, initialSeries, media)) {
                        initialSeries.addMedia((DicomImageElement) media);
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
                                ((DicomSeries) group).addMedia((DicomImageElement) media);
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
                    // Always split when it is a video or a encapsulated document
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
            String seriesUID = TagD.getTagValue(original, Tag.SeriesInstanceUID, String.class);

            Modality modality = Modality.getModality(TagD.getTagValue(original, Tag.Modality, String.class));
            SplittingModalityRules splitRules = splittingRules.getSplittingModalityRules(modality, Modality.DEFAULT);
            List<Rule> rules;
            if (splitRules == null) {
                rules = Collections.emptyList();
            } else {
                rules = frames > 1 ? splitRules.getMultiFrameRules() : splitRules.getSingleFrameRules();
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

    private static boolean isSimilar(List<Rule> list, Series<?> s, final MediaElement media) {
        final MediaElement firstMedia = s.getMedia(0, null, null);
        if (firstMedia == null) {
            // no image
            return true;
        }
        // Not similar when the instances have different classes (even when inheriting class)
        if (firstMedia.getClass() != media.getClass()) {
            return false;
        }

        for (Rule rule : list) {
            if (!rule.isTagValueMatching(firstMedia, media)) {
                return false;
            }
        }
        return true;
    }

    public void get(String[] argv) throws IOException {
        final String[] usage = { "Load DICOM files remotely or locally", //$NON-NLS-1$
            "Usage: dicom:get ([-l PATH]... [-w URI]... [-r URI]... [-p] [-i DATA]... [-z URI]...)", //$NON-NLS-1$
            "PATH is either a directory(recursive) or a file", "  -l --local=PATH   open DICOMs from local disk", //$NON-NLS-1$ //$NON-NLS-2$
            "  -r --remote=URI   open DICOMs from an URI", //$NON-NLS-1$
            "  -w --wado=URI     open DICOMs from an XML manifest", "  -z --zip=URI      open DICOM ZIP from an URI",  //$NON-NLS-1$//$NON-NLS-2$
            "  -p --portable     open DICOMs from configured directories at the same level of the executable", //$NON-NLS-1$
            "  -i --iwado=DATA   open DICOMs from an XML manifest (GZIP-Base64)", //$NON-NLS-1$
            "  -? --help         show help" }; //$NON-NLS-1$

        final Option opt = Options.compile(usage).parse(argv);
        final List<String> largs = opt.getList("local"); //$NON-NLS-1$
        final List<String> rargs = opt.getList("remote"); //$NON-NLS-1$
        final List<String> zargs = opt.getList("zip"); //$NON-NLS-1$
        final List<String> iargs = opt.getList("iwado"); //$NON-NLS-1$
        final List<String> wargs = opt.getList("wado"); //$NON-NLS-1$

        if (opt.isSet("help") //$NON-NLS-1$
            || (largs.isEmpty() && rargs.isEmpty() && iargs.isEmpty() && wargs.isEmpty() && zargs.isEmpty()
                && !opt.isSet("portable"))) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> {
            firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.SELECT, DicomModel.this, null, DicomModel.this));
            getCommand(opt, largs, rargs, iargs, wargs, zargs);
        });
    }

    private void getCommand(Option opt, List<String> largs, List<String> rargs, List<String> iargs, List<String> wargs,
        List<String> zargs) {
        // start importing local dicom series list
        if (opt.isSet("local")) { //$NON-NLS-1$
            File[] files = new File[largs.size()];
            for (int i = 0; i < files.length; i++) {
                files[i] = new File(largs.get(i));
            }
            LOADING_EXECUTOR.execute(new LoadLocalDicom(files, true, DicomModel.this));
        }

        if (opt.isSet("remote")) { //$NON-NLS-1$
            LOADING_EXECUTOR.execute(new LoadRemoteDicomURL(rargs.toArray(new String[rargs.size()]), DicomModel.this));
        }

        // build WADO series list to download
        if (opt.isSet("wado")) { //$NON-NLS-1$
            LOADING_EXECUTOR.execute(new LoadRemoteDicomManifest(wargs, DicomModel.this));
        }

        if (opt.isSet("zip")) { //$NON-NLS-1$
            for (String zip : zargs) {
                DicomZipImport.loadDicomZip(zip, DicomModel.this);
            }
        }

        if (opt.isSet("iwado")) { //$NON-NLS-1$
            List<String> xmlFiles = new ArrayList<>(iargs.size());
            for (int i = 0; i < iargs.size(); i++) {
                try {
                    File tempFile = File.createTempFile("wado_", ".xml", AppProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                    if (GzipManager.gzipUncompressToFile(Base64.getDecoder().decode(iargs.get(i)), tempFile)) {
                        xmlFiles.add(tempFile.getPath());
                    }

                } catch (Exception e) {
                    LOGGER.info("ungzip manifest", e); //$NON-NLS-1$
                }
            }
            LOADING_EXECUTOR.execute(new LoadRemoteDicomManifest(xmlFiles, DicomModel.this));
        }

        // Get DICOM folder (by default DICOM, dicom, IHE_PDI, ihe_pdi) at the same level at the Weasis
        // executable file
        if (opt.isSet("portable")) { //$NON-NLS-1$

            String prop = System.getProperty("weasis.portable.dicom.directory"); //$NON-NLS-1$
            String baseDir = System.getProperty("weasis.portable.dir"); //$NON-NLS-1$

            if (prop != null && baseDir != null) {
                String[] dirs = prop.split(","); //$NON-NLS-1$
                for (int i = 0; i < dirs.length; i++) {
                    dirs[i] = dirs[i].trim().replace("/", File.separator); //$NON-NLS-1$
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

                List<LoadSeries> loadSeries = null;
                File dcmDirFile = new File(baseDir, "DICOMDIR"); //$NON-NLS-1$
                if (dcmDirFile.canRead()) {
                    // Copy images in cache if property weasis.portable.dicom.cache = true (default is true)
                    DicomDirLoader dirImport = new DicomDirLoader(dcmDirFile, DicomModel.this,
                        DicomManager.getInstance().isPortableDirCache());
                    loadSeries = dirImport.readDicomDir();
                }
                if (loadSeries != null && !loadSeries.isEmpty()) {
                    LOADING_EXECUTOR.execute(new LoadDicomDir(loadSeries, DicomModel.this));
                } else {
                    LOADING_EXECUTOR.execute(new LoadLocalDicom(files, true, DicomModel.this));
                }
            }
        }
    }

    public void rs(String[] argv) throws IOException {
        final String[] usage = { "Load DICOM files from DICOMWeb API (QIDO/WADO-RS)", //$NON-NLS-1$
            "Usage: dicom:rs -u URL -r QUERYPARAMS... [-H HEADER]... [--query-header HEADER]... [--retrieve-header HEADER]... [--query-ext EXT] [--retrieve-ext EXT] [--accept-ext EXT]", //$NON-NLS-1$
            "  -u --url=URL               URL of the DICOMWeb service", //$NON-NLS-1$
            "  -r --request=QUERYPARAMS   Query params of the URL, see weasis-pacs-connector", //$NON-NLS-1$
            "  -H --header=HEADER         Pass custom header(s) to all the requests", //$NON-NLS-1$
            "  --query-header=HEADER      Pass custom header(s) to the query requests (QIDO)", //$NON-NLS-1$
            "  --retrieve-header=HEADER   Pass custom header(s) to the retrieve requests (WADO)", //$NON-NLS-1$
            "  --query-ext=EXT            Additionnal parameters for Query URL (QIDO)", //$NON-NLS-1$
            "  --retrieve-ext=EXT         Additionnal parameters for Retrieve URL (WADO)", //$NON-NLS-1$
            "  --accept-ext=EXT           Additionnal parameters for DICOM multipart/related Accept header of the retrieve URL (WADO). Default value is: transfer-syntax=*", //$NON-NLS-1$
            "  --show-whole-study         when downloading a series, show all the other series (ready for download) from the same study", //$NON-NLS-1$
            "  -? --help                  show help" }; //$NON-NLS-1$

        final Option opt = Options.compile(usage).parse(argv);
        final String rsUrl = opt.get("url"); //$NON-NLS-1$
        final List<String> pargs = opt.getList("request"); //$NON-NLS-1$

        if (opt.isSet("help") || rsUrl.isEmpty() //$NON-NLS-1$
            || (pargs.isEmpty())) {
            opt.usage();
            return;
        }

        Properties props = new Properties();
        props.setProperty(RsQueryParams.P_DICOMWEB_URL, rsUrl);
        String queryExt = opt.get("query-ext"); //$NON-NLS-1$
        if (StringUtil.hasText(queryExt)) {
            props.setProperty(RsQueryParams.P_QUERY_EXT, queryExt);
        }
        String retrieveExt = opt.get("retrieve-ext"); //$NON-NLS-1$
        if (StringUtil.hasText(retrieveExt)) {
            props.setProperty(RsQueryParams.P_RETRIEVE_EXT, retrieveExt);
        }

        String acceptExt = opt.get("accept-ext"); //$NON-NLS-1$
        if (!StringUtil.hasText(acceptExt)) {
            acceptExt = "transfer-syntax=*"; //$NON-NLS-1$
        }
        props.setProperty(RsQueryParams.P_ACCEPT_EXT, acceptExt);
        
        if (opt.isSet("show-whole-study")) { //$NON-NLS-1$
            props.setProperty(RsQueryParams.P_SHOW_WHOLE_STUDY, Boolean.TRUE.toString());
        }

        GuiExecutor.instance().execute(() -> {
            firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.SELECT, DicomModel.this, null, DicomModel.this));
            for (String query : pargs) {
                List<String> common = opt.getList("header"); //$NON-NLS-1$
                List<String> q = opt.getList("query-header"); //$NON-NLS-1$
                q.addAll(common);
                List<String> r = opt.getList("retrieve-header"); //$NON-NLS-1$
                r.addAll(common);
                RsQueryParams rsquery = new RsQueryParams(DicomModel.this, props, RsQueryParams.getQueryMap(query),
                    RsQueryParams.getHeaders(q), RsQueryParams.getHeaders(r));
                LOADING_EXECUTOR.execute(rsquery);
            }
        });

    }

    public void close(String[] argv) throws IOException {
        final String[] usage = { "Close DICOM files", //$NON-NLS-1$
            "Usage: dicom:close  (-a | ([-y UID]... [-s UID]...))", //$NON-NLS-1$
            "  -a --all           close all the patients", //$NON-NLS-1$
            "  -y --study=UID     close a study, UID is Study Instance UID", //$NON-NLS-1$
            "  -s --series=UID    close a series, UID is Series Instance UID", "  -? --help          show help" }; //$NON-NLS-1$ //$NON-NLS-2$
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> yargs = opt.getList("study"); //$NON-NLS-1$
        final List<String> sargs = opt.getList("series"); //$NON-NLS-1$

        if (opt.isSet("help") || (yargs.isEmpty() && sargs.isEmpty() && !opt.isSet("all"))) { //$NON-NLS-1$ //$NON-NLS-2$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> {
            firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.SELECT, DicomModel.this, null, DicomModel.this));
            closeCommand(opt, yargs, sargs);
        });
    }

    private void closeCommand(Option opt, List<String> yargs, List<String> sargs) {
        if (opt.isSet("all")) { //$NON-NLS-1$
            for (MediaSeriesGroup patientGroup : model.getSuccessors(MediaSeriesGroupNode.rootNode)) {
                removePatient(patientGroup);
            }
        } else {
            if (opt.isSet("study")) { //$NON-NLS-1$
                for (String studyUID : yargs) {
                    removeStudy(getStudyNode(studyUID));
                }
            }
            if (opt.isSet("series")) { //$NON-NLS-1$
                for (String seriesUID : sargs) {
                    findSeriesToRemove(seriesUID);
                }
            }
        }
    }

    private void findSeriesToRemove(String seriesUID) {
        for (MediaSeriesGroup ptGroup : model.getSuccessors(MediaSeriesGroupNode.rootNode)) {
            for (MediaSeriesGroup stGroup : model.getSuccessors(ptGroup)) {
                // Remove all the split series
                int k = 1;
                while (true) {
                    String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
                    MediaSeriesGroup group = getHierarchyNode(stGroup, uid);
                    if (group != null) {
                        removeSeries(group);
                    } else {
                        break;
                    }
                    k++;
                }

                MediaSeriesGroup seGroup = getHierarchyNode(stGroup, seriesUID);
                if (seGroup != null) {
                    removeSeries(seGroup);
                    return;
                }
            }
        }
    }

    @Override
    public TreeModelNode getTreeModelNodeForNewPlugin() {
        return patient;
    }

}
