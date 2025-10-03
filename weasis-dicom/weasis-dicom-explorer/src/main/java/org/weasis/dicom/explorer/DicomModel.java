/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import com.formdev.flatlaf.util.SystemInfo;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import javax.swing.SwingUtilities;
import org.apache.felix.service.command.CommandProcessor;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.PrDicomObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.ObservableEvent.BasicAction;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.Tree;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.explorer.model.TreeModelNode;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.AbstractKOSpecialElement;
import org.weasis.dicom.codec.DicomEncapDocElement;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.DicomVideoElement;
import org.weasis.dicom.codec.FilesExtractor;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.HiddenSpecialElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.RejectedKOSpecialElement;
import org.weasis.dicom.codec.SegSpecialElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.SpecialElementReferences;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.utils.SplittingModalityRules;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Rule;
import org.weasis.dicom.codec.utils.SplittingRules;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.rs.RsQueryParams;
import org.weasis.dicom.explorer.wado.DicomManager;
import org.weasis.dicom.explorer.wado.DownloadManager;
import org.weasis.dicom.explorer.wado.LoadRemoteDicomManifest;
import org.weasis.dicom.explorer.wado.LoadRemoteDicomURL;
import org.weasis.dicom.explorer.wado.LoadSeries;

@org.osgi.service.component.annotations.Component(
    property = {
      CommandProcessor.COMMAND_SCOPE + "=dicom",
      CommandProcessor.COMMAND_FUNCTION + "=get",
      CommandProcessor.COMMAND_FUNCTION + "=rs",
      CommandProcessor.COMMAND_FUNCTION + "=close"
    },
    service = DicomModel.class)
public class DicomModel implements TreeModel, DataExplorerModel {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomModel.class);

  public static final String NAME = "DICOM";
  public static final String PREFERENCE_NODE = "dicom.model";

  public static final TreeModelNode patient =
      new TreeModelNode(
          1,
          0,
          TagW.PatientPseudoUID,
          new TagView(TagD.getTagFromIDs(Tag.PatientName, Tag.PatientID)));
  public static final TreeModelNode study =
      new TreeModelNode(
          2,
          0,
          TagD.get(Tag.StudyInstanceUID),
          new TagView(
              TagD.getTagFromIDs(
                  Tag.StudyDate, Tag.AccessionNumber, Tag.StudyID, Tag.StudyDescription)));
  public static final TreeModelNode series =
      new TreeModelNode(
          3,
          0,
          TagW.SubseriesInstanceUID,
          new TagView(TagD.getTagFromIDs(Tag.SeriesDescription, Tag.SeriesNumber, Tag.SeriesTime)));
  public static final ExecutorService LOADING_EXECUTOR =
      ThreadUtil.newSingleThreadExecutor("DicomModelLoader");

  private static final List<TreeModelNode> modelStructure =
      Arrays.asList(TreeModelNode.ROOT, patient, study, series);

  private final Tree<MediaSeriesGroup> model;
  private PropertyChangeSupport propertyChange = null;
  private final SplittingRules splittingRules;

  public DicomModel() {
    model = new Tree<>(MediaSeriesGroupNode.rootNode);
    splittingRules = new SplittingRules();
  }

  @Override
  public List<Codec<MediaElement>> getCodecPlugins() {
    ArrayList<Codec<MediaElement>> codecPlugins = new ArrayList<>(1);
    List<Codec<MediaElement>> codecs = GuiUtils.getUICore().getCodecPlugins();
    synchronized (codecs) {
      for (Codec<MediaElement> codec : codecs) {
        if (codec != null
            && !"JDK ImageIO".equals(codec.getCodecName()) // NON-NLS
            && codec.isMimeTypeSupported(DicomMediaIO.DICOM_MIMETYPE)
            && !codecPlugins.contains(codec)) {
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

  public List<MediaSeriesGroup> getAllSeriesNodes(MediaSeriesGroup parent, Object valueID) {
    if (parent != null || valueID != null) {
      List<MediaSeriesGroup> list = new ArrayList<>();
      for (MediaSeriesGroup node : getChildren(parent)) {
        String seriesInstanceUID = TagD.getTagValue(node, Tag.SeriesInstanceUID, String.class);
        if (Objects.equals(seriesInstanceUID, valueID)) {
          list.add(node);
        }
      }
      return list;
    }
    return Collections.emptyList();
  }

  public void mergePatientUID(
      String oldPatientUID, String newPatientUID, PluginOpeningStrategy openingStrategy) {
    MediaSeriesGroup pt = getHierarchyNode(MediaSeriesGroupNode.rootNode, oldPatientUID);
    MediaSeriesGroup pt2 = getHierarchyNode(MediaSeriesGroupNode.rootNode, newPatientUID);

    if (pt == null || Objects.equals(pt, pt2)) {
      return;
    }
    if (pt2 == null) {
      pt2 =
          new MediaSeriesGroupNode(
              TagD.getUID(Level.PATIENT), newPatientUID, DicomModel.patient.tagView());
      Iterator<Entry<TagW, Object>> iter = pt.getTagEntrySetIterator();
      while (iter.hasNext()) {
        Entry<TagW, Object> e = iter.next();
        pt2.setTag(e.getKey(), e.getValue());
      }
      addHierarchyNode(MediaSeriesGroupNode.rootNode, pt2);
    }
    Collection<MediaSeriesGroup> studies = getChildren(pt);
    Map<MediaSeriesGroup, Collection<MediaSeriesGroup>> studyMap = new HashMap<>();
    for (MediaSeriesGroup st : studies) {
      studyMap.put(st, getChildren(st));
    }
    firePropertyChange(new ObservableEvent(BasicAction.REMOVE, DicomModel.this, null, pt));
    openingStrategy.removePatient(pt);

    for (Entry<MediaSeriesGroup, Collection<MediaSeriesGroup>> stEntry : studyMap.entrySet()) {
      MediaSeriesGroup st = stEntry.getKey();
      removeHierarchyNode(pt, st);
      addHierarchyNode(pt2, st);
      for (MediaSeriesGroup s : stEntry.getValue()) {
        addHierarchyNode(st, s);
        firePropertyChange(new ObservableEvent(BasicAction.ADD, DicomModel.this, null, s));
        if (s instanceof DicomSeries dicomSeries) {
          openingStrategy.openViewerPlugin(pt2, DicomModel.this, dicomSeries);
        }
      }
    }
    removeHierarchyNode(MediaSeriesGroupNode.rootNode, pt);
  }

  public void mergeStudyUID(String oldStudyUID, String studyUID) {
    MediaSeriesGroup studyGroup = getStudyNode(oldStudyUID);
    MediaSeriesGroup studyGroup2 = getStudyNode(studyUID);

    if (studyGroup == null || Objects.equals(studyGroup, studyGroup2)) {
      return;
    }

    MediaSeriesGroup patientGroup = getParent(studyGroup, DicomModel.patient);
    if (studyGroup2 == null) {
      studyGroup2 =
          new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.tagView());
      Iterator<Entry<TagW, Object>> iter = studyGroup.getTagEntrySetIterator();
      while (iter.hasNext()) {
        Entry<TagW, Object> e = iter.next();
        studyGroup2.setTag(e.getKey(), e.getValue());
      }
      addHierarchyNode(patientGroup, studyGroup2);
    }

    Collection<MediaSeriesGroup> seriesGroups = getChildren(studyGroup);
    for (MediaSeriesGroup s : seriesGroups) {
      removeHierarchyNode(studyGroup, s);
      addHierarchyNode(studyGroup2, s);
      firePropertyChange(new ObservableEvent(BasicAction.ADD, DicomModel.this, null, s));
    }
    firePropertyChange(new ObservableEvent(BasicAction.REMOVE, DicomModel.this, null, studyGroup));
    removeHierarchyNode(patientGroup, studyGroup);
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
      TagW matchTagID = modelNode.tagElement();
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
      String uid = TagD.getTagValue(seriesList.getFirst(), Tag.SeriesInstanceUID, String.class);
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
        MediaSeries<? extends MediaElement> base = seriesList.getFirst();
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
        sortedMedias.sort(SortSeriesStack.instanceNumber);
        if (base instanceof DicomSeries dicomSeries) {
          List<DicomImageElement> imageList =
              dicomSeries.copyOfMedias(null, SortSeriesStack.slicePosition);
          int samplingRate = LoadLocalDicom.calculateSamplingRateFor4d(imageList);
          base.setTag(TagW.stepNDimensions, samplingRate); // update stepNDimensions tag
        }
        // filter observer
        this.firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.REPLACE, DicomModel.this, base, base));
      }
    }
  }

  public void removeHiddenSpecialElement(HiddenSpecialElement element) {
    if (element == null) {
      return;
    }

    String patientPseudoUID = (String) element.getTagValue(TagW.PatientPseudoUID);
    MediaSeriesGroup patientGroup =
        getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);

    if (patientGroup == null) {
      return;
    }

    String studyUID = TagD.getTagValue(element, Tag.StudyInstanceUID, String.class);
    MediaSeriesGroup studyGroup = getHierarchyNode(patientGroup, studyUID);
    if (studyGroup == null) {
      return;
    }

    String seriesUID = TagD.getTagValue(element, Tag.SeriesInstanceUID, String.class);
    Series<?> dicomSeries = (Series<?>) getHierarchyNode(studyGroup, seriesUID);
    if (dicomSeries == null) {
      return;
    }

    if (isHiddenModality(dicomSeries)) {
      Set<HiddenSpecialElement> specialElementList =
          HiddenSeriesManager.getInstance().series2Elements.get(seriesUID);
      if (specialElementList == null) {
        return;
      }

      if (specialElementList.remove(element)) {
        firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.UPDATE, this, null, element));
        element.dispose();
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
          new ObservableEvent(
              ObservableEvent.BasicAction.REMOVE, DicomModel.this, null, dicomSeries));
      // remove in the data model
      MediaSeriesGroup studyGroup = getParent(dicomSeries, DicomModel.study);
      removeHierarchyNode(studyGroup, dicomSeries);
      LOGGER.info("Remove Series (no dispose): {}", dicomSeries);
    }
  }

  public void removeSeries(MediaSeriesGroup seriesGroup) {
    if (seriesGroup != null) {
      if (!DownloadManager.getTasks().isEmpty() && seriesGroup instanceof DicomSeries dicomSeries) {
        DownloadManager.stopDownloading(dicomSeries, this);
      }
      // remove first series in UI (Dicom Explorer, Viewer using this series)
      firePropertyChange(
          new ObservableEvent(
              ObservableEvent.BasicAction.REMOVE, DicomModel.this, null, seriesGroup));
      // remove in the data model
      MediaSeriesGroup studyGroup = getParent(seriesGroup, DicomModel.study);
      removeHierarchyNode(studyGroup, seriesGroup);
      seriesGroup.dispose();
      LOGGER.info("Remove Series: {}", seriesGroup);
    }
  }

  public void removeStudy(MediaSeriesGroup studyGroup) {
    if (studyGroup != null) {
      if (!DownloadManager.getTasks().isEmpty()) {
        for (MediaSeriesGroup group : getChildren(studyGroup)) {
          if (group instanceof DicomSeries dicomSeries) {
            DownloadManager.stopDownloading(dicomSeries, this);
          }
        }
      }
      firePropertyChange(
          new ObservableEvent(
              ObservableEvent.BasicAction.REMOVE, DicomModel.this, null, studyGroup));
      for (MediaSeriesGroup group : getChildren(studyGroup)) {
        group.dispose();
      }
      MediaSeriesGroup patientGroup = getParent(studyGroup, DicomModel.patient);
      removeHierarchyNode(patientGroup, studyGroup);
      LOGGER.info("Remove Study: {}", studyGroup);
    }
  }

  public void removePatient(MediaSeriesGroup patientGroup) {
    if (patientGroup != null) {
      if (!DownloadManager.getTasks().isEmpty()) {
        for (MediaSeriesGroup studyGroup : getChildren(patientGroup)) {
          for (MediaSeriesGroup group : getChildren(studyGroup)) {
            if (group instanceof DicomSeries dicomSeries) {
              DownloadManager.stopDownloading(dicomSeries, this);
            }
          }
        }
      }
      firePropertyChange(
          new ObservableEvent(
              ObservableEvent.BasicAction.REMOVE, DicomModel.this, null, patientGroup));
      for (MediaSeriesGroup studyGroup : getChildren(patientGroup)) {
        for (MediaSeriesGroup group : getChildren(studyGroup)) {
          group.dispose();
        }
      }
      removeHierarchyNode(MediaSeriesGroupNode.rootNode, patientGroup);
      LOGGER.info("Remove Patient: {}", patientGroup);
    }
  }

  public static boolean isHiddenModality(MediaSeries<?> series) {
    String modality =
        (series == null) ? null : TagD.getTagValue(series, Tag.Modality, String.class);
    return DicomMediaIO.isHiddenModality(modality);
  }

  public static Collection<KOSpecialElement> getEditableKoSpecialElements(
      MediaSeriesGroup patient) {
    List<KOSpecialElement> list =
        HiddenSeriesManager.getHiddenElementsFromPatient(KOSpecialElement.class, patient);
    if (!list.isEmpty()) {
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

  public static Collection<KOSpecialElement> getKoSpecialElements(
      MediaSeries<DicomImageElement> dicomSeries) {
    String patientPseudoUID = getPatientPseudoUID(dicomSeries);
    List<KOSpecialElement> specialElementList =
        HiddenSeriesManager.getHiddenElementsFromPatient(KOSpecialElement.class, patientPseudoUID);

    String referencedSeriesInstanceUID =
        TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
    return AbstractKOSpecialElement.getKoSpecialElements(
        specialElementList, referencedSeriesInstanceUID);
  }

  public static Collection<RejectedKOSpecialElement> getRejectionKoSpecialElements(
      MediaSeries<DicomImageElement> dicomSeries) {
    String patientPseudoUID = getPatientPseudoUID(dicomSeries);
    List<RejectedKOSpecialElement> specialElementList =
        HiddenSeriesManager.getHiddenElementsFromPatient(
            RejectedKOSpecialElement.class, patientPseudoUID);

    String referencedSeriesInstanceUID =
        TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
    return AbstractKOSpecialElement.getRejectionKoSpecialElements(
        specialElementList, referencedSeriesInstanceUID);
  }

  public static String getPatientPseudoUID(MediaSeries<DicomImageElement> dicomSeries) {
    if (dicomSeries == null) {
      return null;
    }
    DicomImageElement img = dicomSeries.getMedia(MEDIA_POSITION.FIRST, null, null);
    if (img == null) {
      return null;
    }
    return (String) img.getTagValue(TagW.PatientPseudoUID);
  }

  public static RejectedKOSpecialElement getRejectionKoSpecialElement(
      MediaSeries<DicomImageElement> dicomSeries, String sopUID, Integer dicomFrameNumber) {
    String patientPseudoUID = getPatientPseudoUID(dicomSeries);
    List<RejectedKOSpecialElement> specialElementList =
        HiddenSeriesManager.getHiddenElementsFromPatient(
            RejectedKOSpecialElement.class, patientPseudoUID);

    String referencedSeriesInstanceUID =
        TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
    return AbstractKOSpecialElement.getRejectionKoSpecialElement(
        specialElementList, referencedSeriesInstanceUID, sopUID, dicomFrameNumber);
  }

  public static List<PRSpecialElement> getPrSpecialElements(
      MediaSeries<DicomImageElement> dicomSeries, DicomImageElement img) {
    String patientPseudoUID = getPatientPseudoUID(dicomSeries);
    List<PRSpecialElement> specialElementList =
        HiddenSeriesManager.getHiddenElementsFromPatient(PRSpecialElement.class, patientPseudoUID);
    if (!specialElementList.isEmpty()) {
      String referencedSeriesInstanceUID =
          TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
      String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
      if (Objects.equals(seriesUID, referencedSeriesInstanceUID)) {
        return PRSpecialElement.getPRSpecialElements(specialElementList, img);
      }
    }
    return Collections.emptyList();
  }

  public static <E> List<E> getSpecialElements(MediaSeriesGroup series, Class<E> clazz) {
    if (series != null && clazz != null && clazz.isAssignableFrom(clazz)) {
      List<DicomSpecialElement> elements =
          (List<DicomSpecialElement>) series.getTagValue(TagW.DicomSpecialElementList);
      if (elements != null) {
        List<E> list = new ArrayList<>();
        for (DicomSpecialElement el : elements) {
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
      List<DicomSpecialElement> sps =
          (List<DicomSpecialElement>) group.getTagValue(TagW.DicomSpecialElementList);
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

  public void openRelatedSeries(KOSpecialElement koSpecialElement, MediaSeriesGroup patient) {
    if (koSpecialElement != null && patient != null) {
      SeriesViewerFactory plugin =
          GuiUtils.getUICore().getViewerFactory(DicomMediaIO.SERIES_MIMETYPE);
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
          Map<String, Object> props = createViewerKeyImagePluginProperties();
          ViewerPluginBuilder builder = new ViewerPluginBuilder(plugin, seriesList, this, props);
          ViewerPluginBuilder.openSequenceInPlugin(builder);
          this.firePropertyChange(
              new ObservableEvent(
                  ObservableEvent.BasicAction.SELECT,
                  props.get(ViewerPluginBuilder.UID),
                  null,
                  koSpecialElement));
        }
      }
    }
  }

  public Map<String, Object> createViewerKeyImagePluginProperties() {
    String uid = UUID.randomUUID().toString();
    Map<String, Object> props = Collections.synchronizedMap(new HashMap<>());
    props.put(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER, false);
    props.put(ViewerPluginBuilder.BEST_DEF_LAYOUT, false);
    props.put(ViewerPluginBuilder.ICON, ResourceUtil.getIcon(OtherIcon.KEY_IMAGE));
    props.put(ViewerPluginBuilder.UID, uid);
    return props;
  }

  private void splitSeries(DicomMediaIO mediaIo, DicomSeries original, DicomImageElement media) {
    splitSeries(mediaIo, original).addMedia(media);
  }

  private void replaceSeries(DicomMediaIO mediaIO, DicomSeries original, DicomImageElement media) {
    replaceSeries(mediaIO, original).addMedia(media);
  }

  DicomSeries splitSeries(DicomMediaIO dicomReader, DicomSeries original) {
    return createSplitOrReplaceSeries(dicomReader, original, true);
  }

  DicomSeries replaceSeries(DicomMediaIO io, DicomSeries orig) {
    return createSplitOrReplaceSeries(io, orig, false);
  }

  private DicomSeries createSplitOrReplaceSeries(
      DicomMediaIO dicomReader, DicomSeries original, boolean isSplit) {
    MediaSeriesGroup study = getParent(original, DicomModel.study);
    String uid = TagD.getTagValue(original, Tag.SeriesInstanceUID, String.class);
    int idx = nextIndex(study, uid);
    String newUid = "#" + idx + "." + uid;
    DicomSeries s = dicomReader.buildSeries(newUid);
    dicomReader.writeMetaData(s);
    if (original.getTagValue(TagW.SplitSeriesNumber) == null) {
      original.setTag(TagW.SplitSeriesNumber, isSplit ? 1 : -1);
    }
    s.setTag(TagW.SplitSeriesNumber, isSplit ? idx + 1 : idx);
    s.setTag(TagW.ExplorerModel, this);
    s.setTag(TagW.WadoParameters, original.getTagValue(TagW.WadoParameters));
    addHierarchyNode(study, s);
    LOGGER.info("{} of the series: {}", isSplit ? "Splitting" : "Replacement", s);
    return s;
  }

  private int nextIndex(MediaSeriesGroup study, String uid) {
    int i = 1;
    while (getHierarchyNode(study, "#" + i + "." + uid) != null) {
      i++;
    }
    return i;
  }

  private void rebuildSeries(DicomMediaIO dicomReader, DicomImageElement media) {
    String studyUID = TagD.getTagValue(dicomReader, Tag.StudyInstanceUID, String.class);
    String patientPseudoUID = (String) dicomReader.getTagValue(TagW.PatientPseudoUID);
    MediaSeriesGroup pt = getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
    if (pt == null) {
      MediaSeriesGroup st = getStudyNode(studyUID);
      if (st == null) {
        pt =
            new MediaSeriesGroupNode(
                TagW.PatientPseudoUID, patientPseudoUID, DicomModel.patient.tagView());
        dicomReader.writeMetaData(pt);
        addHierarchyNode(MediaSeriesGroupNode.rootNode, pt);
        LOGGER.info("Adding patient: {}", pt);
      } else {
        pt = getParent(st, DicomModel.patient);
        LOGGER.warn(
            "DICOM patient attributes are inconsistent! Name or ID is different within an exam.");
      }
    }

    MediaSeriesGroup st = getHierarchyNode(pt, studyUID);
    if (st == null) {
      st =
          new MediaSeriesGroupNode(
              TagD.get(Tag.StudyInstanceUID), studyUID, DicomModel.study.tagView());
      dicomReader.writeMetaData(st);
      addHierarchyNode(pt, st);
    }

    String seriesUID = TagD.getTagValue(dicomReader, Tag.SeriesInstanceUID, String.class);
    DicomSeries dicomSeries = (DicomSeries) getHierarchyNode(st, seriesUID);

    if (dicomSeries == null) {
      dicomSeries = dicomReader.buildSeries(seriesUID);
      dicomReader.writeMetaData(dicomSeries);
      dicomSeries.setTag(TagW.ExplorerModel, this);
      addHierarchyNode(st, dicomSeries);
      LOGGER.info("Series rebuilding: {}", dicomSeries);
    }
    dicomSeries.addMedia(media);

    buildThumbnail(dicomSeries);
  }

  /**
   * Post-processing for all series in the model. This method is called after loading all series to
   * ensure that the right thumbnails are built and any necessary processing is done.
   */
  public void allSeriesPostProcessing() {
    for (MediaSeriesGroup pt : getChildren(MediaSeriesGroupNode.rootNode)) {
      for (MediaSeriesGroup st : getChildren(pt)) {
        for (MediaSeriesGroup item : getChildren(st)) {
          Integer step = (Integer) item.getTagValue(TagW.stepNDimensions);
          if (step == null || step < 1) {
            if (item instanceof DicomSeries dicomSeries) {
              int imageCount = dicomSeries.size(null);
              if (imageCount == 0) {
                continue;
              }
              if (!DicomModel.isHiddenModality(dicomSeries)) {
                boolean split = LoadLocalDicom.seriesPostProcessing(dicomSeries, this);
                if (!split) {
                  buildThumbnail(dicomSeries);
                }

                if (dicomSeries.isSuitableFor3d()) {
                  firePropertyChange(
                      new ObservableEvent(
                          ObservableEvent.BasicAction.UPDATE,
                          dicomSeries,
                          null,
                          new SeriesEvent(SeriesEvent.Action.UPDATE, dicomSeries, null)));
                }
              }
            }
          }
        }
      }
    }
  }

  public void buildThumbnail(Series<?> dicomSeries) {
    // Load image and create a thumbnail in this Thread
    SeriesThumbnail t = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
    if (t == null) {
      int thumbnailSize =
          GuiUtils.getUICore()
              .getSystemPreferences()
              .getIntProperty(Thumbnail.KEY_SIZE, Thumbnail.DEFAULT_SIZE);
      t = DicomExplorer.createThumbnail(dicomSeries, this, thumbnailSize);
      dicomSeries.setTag(TagW.Thumbnail, t);
      firePropertyChange(new ObservableEvent(BasicAction.ADD, this, null, dicomSeries));
    }
  }

  public void applySplittingRules(DicomSeries original, DicomSpecialElement media) {
    if (media != null && media.getMediaReader() instanceof DicomMediaIO dicomReader) {
      String seriesUID = TagD.getTagValue(original, Tag.SeriesInstanceUID, String.class);
      splitSpecialElement(media, original, seriesUID, dicomReader);
    }
  }

  public void applySplittingRules(DicomSeries original, DicomImageElement media) {
    if (media != null && media.getMediaReader() instanceof DicomMediaIO dicomReader) {
      String seriesUID = TagD.getTagValue(original, Tag.SeriesInstanceUID, String.class);
      if (seriesUID == null) {
        throw new IllegalArgumentException("Series UID cannot be null");
      }

      if (!seriesUID.equals(TagD.getTagValue(dicomReader, Tag.SeriesInstanceUID))) {
        rebuildSeries(dicomReader, media);
        return;
      }

      if (original instanceof FilesExtractor) {
        handleFilesExtractorSeries(original, media, dicomReader);
      } else {
        handleRegularSeries(original, media, dicomReader, seriesUID);
      }
    }
  }

  private void handleFilesExtractorSeries(
      DicomSeries original, DicomImageElement media, DicomMediaIO dicomReader) {
    if (original.size(null) > 0) {
      // Always split when it is a video or an encapsulated document
      if (media instanceof DicomVideoElement || media instanceof DicomEncapDocElement) {
        splitSeries(dicomReader, original, media);
      } else {
        findMatchingSeriesOrSplit(original, media);
      }
    } else {
      original.addMedia(media);
    }
  }

  private void handleRegularSeries(
      DicomSeries original, DicomImageElement media, DicomMediaIO dicomReader, String seriesUID) {
    // Handle cases when the Series is created before getting the image (downloading)
    if (media instanceof DicomVideoElement || media instanceof DicomEncapDocElement) {
      if (original.size(null) > 0) {
        // When the series already contains elements (images), always split video and document
        splitSeries(dicomReader, original, media);
      } else {
        replaceSeries(dicomReader, original, media);
      }
      return;
    }

    int frames = dicomReader.getMediaElementNumber();
    if (frames < 1) {
      original.addMedia(media);
    } else {
      List<Rule> rules = buildRules(original, frames);
      // If similar, add to the original series
      if (isSimilar(rules, original, media)
          || "seg/dicom".equals(original.getMimeType())) { // NON-NLS
        original.addMedia(media);
        return;
      }
      findSimilarOrSplit(original, media, dicomReader, seriesUID, rules);
    }
  }

  private void findSimilarOrSplit(
      DicomSeries original,
      DicomImageElement media,
      DicomMediaIO dicomReader,
      String seriesUID,
      List<Rule> rules) {
    MediaSeriesGroup study = getParent(original, DicomModel.study);
    int k = 1;
    while (true) {
      String uid = "#" + k + "." + seriesUID;
      MediaSeriesGroup group = getHierarchyNode(study, uid);
      if (group instanceof DicomSeries dicomSeries) {
        if (isSimilar(rules, dicomSeries, media)) {
          dicomSeries.addMedia(media);
          return;
        }
      } else {
        break;
      }
      k++;
    }
    // No matching series exists, so split series
    splitSeries(dicomReader, original, media);
  }

  private List<Rule> buildRules(DicomSeries initialSeries, int frames) {
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
    return rules;
  }

  private void splitSpecialElement(
      DicomSpecialElement specialElement,
      DicomSeries originalSeries,
      String seriesUID,
      DicomMediaIO dicomReader) {

    String rMime = dicomReader.getMimeType();
    if (specialElement instanceof HiddenSpecialElement hiddenElement) {
      handleHiddenSpecialElement(hiddenElement, seriesUID, originalSeries);
    } else {
      handleRegularSpecialElement(specialElement, originalSeries, rMime, dicomReader);
    }
  }

  private void handleHiddenSpecialElement(
      HiddenSpecialElement hiddenElement, String seriesUID, DicomSeries originalSeries) {
    // Initialize references
    if (hiddenElement instanceof SpecialElementReferences references) {
      references.initReferences(seriesUID);
    }

    // Process-specific element types
    if (hiddenElement instanceof SegSpecialElement seg) {
      List<DicomSeries> refSeriesList =
          seg.getRefMap().keySet().stream()
              .map(this::getSeriesNode)
              .filter(series -> series instanceof DicomSeries)
              .map(series -> (DicomSeries) series)
              .toList();
      seg.initContours(originalSeries, refSeriesList);
    } else if (hiddenElement instanceof PRSpecialElement pr) {
      PrDicomObject prDicomObject = pr.getPrDicomObject();
      if (StringUtil.hasText(seriesUID) && prDicomObject != null) {
        Attributes prAttributes = prDicomObject.getDicomObject();
        HiddenSeriesManager.getInstance().extractReferencedSeries(prAttributes, seriesUID);
      }
    } else if (hiddenElement instanceof KOSpecialElement ko) {
      if (StringUtil.hasText(seriesUID)) {
        Set<String> referencedSeriesUIDs = ko.getReferencedSeriesInstanceUIDSet();
        if (referencedSeriesUIDs != null) {
          for (String referenceKey : referencedSeriesUIDs) {
            if (StringUtil.hasText(referenceKey)) {
              HiddenSeriesManager.getInstance()
                  .reference2Series
                  .computeIfAbsent(referenceKey, _ -> new CopyOnWriteArraySet<>())
                  .add(seriesUID);
            }
          }
        }
      }
    }

    // Register hidden element
    synchronized (this) {
      Map<String, Set<HiddenSpecialElement>> mapSeries =
          HiddenSeriesManager.getInstance().series2Elements;
      mapSeries.computeIfAbsent(seriesUID, _ -> new CopyOnWriteArraySet<>()).add(hiddenElement);

      String patientPseudoUID = (String) hiddenElement.getTagValue(TagW.PatientPseudoUID);
      if (patientPseudoUID != null) {
        Set<String> patients =
            HiddenSeriesManager.getInstance()
                .patient2Series
                .computeIfAbsent(patientPseudoUID, _ -> new CopyOnWriteArraySet<>());
        patients.add(seriesUID);
      }
    }
  }

  private void handleRegularSpecialElement(
      DicomSpecialElement specialElement,
      DicomSeries originalSeries,
      String rMime,
      DicomMediaIO dicomReader) {

    List<DicomSpecialElement> specialElementList =
        (List<DicomSpecialElement>) originalSeries.getTagValue(TagW.DicomSpecialElementList);

    if (specialElementList == null) {
      specialElementList = new CopyOnWriteArrayList<>();
      originalSeries.setTag(TagW.DicomSpecialElementList, specialElementList);
    } else if ("sr/dicom".equals(rMime) || "wf/dicom".equals(rMime)) { // NON-NLS
      // Split SR series to have only one object by series
      Series<?> s = splitSeries(dicomReader, originalSeries);
      specialElementList = new CopyOnWriteArrayList<>();
      s.setTag(TagW.DicomSpecialElementList, specialElementList);
    }

    specialElementList.add(specialElement);
  }

  private void findMatchingSeriesOrSplit(DicomSeries original, DicomImageElement media) {
    DicomMediaIO dicomReader = (DicomMediaIO) media.getMediaReader();
    int frames = dicomReader.getMediaElementNumber();
    if (frames < 1) {
      original.addMedia(media);
    } else {
      String seriesUID = TagD.getTagValue(original, Tag.SeriesInstanceUID, String.class);
      List<Rule> rules = buildRules(original, frames);
      // If similar, add to the original series
      if (isSimilar(rules, original, media)) {
        original.addMedia(media);
        return;
      }

      // Try to find a similar previous split series or split the series
      findSimilarOrSplit(original, media, dicomReader, seriesUID, rules);
    }
  }

  private static boolean hasSameConcatenationUID(
      MediaElement firstMedia, final MediaElement media) {
    if (firstMedia instanceof DicomImageElement && media instanceof DicomImageElement) {
      String firstConcatenationUID =
          TagD.getTagValue(firstMedia, Tag.ConcatenationUID, String.class);
      if (firstConcatenationUID != null) {
        String concatenationUID = TagD.getTagValue(media, Tag.ConcatenationUID, String.class);
        return Objects.equals(firstConcatenationUID, concatenationUID);
      }
    }
    return false;
  }

  private static boolean isSimilar(List<Rule> list, Series<?> s, final MediaElement media) {
    final MediaElement firstMedia = s.getMedia(0, null, null);
    if (firstMedia == null || hasSameConcatenationUID(firstMedia, media)) {
      // No image or has the same concatenation UID
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
    final String[] usage = {
      "Load DICOM files remotely or locally", // NON-NLS
      "Usage: dicom:get ([-l PATH]... [-w URI]... [-r URI]... [-p] [-i DATA]... [-z URI]...)", // NON-NLS
      "PATH is either a directory(recursive) or a file", // NON-NLS
      "  -l --local=PATH   open DICOMs from local disk", // NON-NLS
      "  -r --remote=URI   open DICOMs from an URI", // NON-NLS
      "  -w --wado=URI     open DICOMs from an XML manifest", // NON-NLS
      "  -z --zip=URI      open DICOM ZIP from an URI", // NON-NLS
      "  -p --portable     open DICOMs from configured directories at the same level of the executable", // NON-NLS
      "  -i --iwado=DATA   open DICOMs from an XML manifest (GZIP-Base64)", // NON-NLS
      "  -? --help         show help" // NON-NLS
    };

    final Option opt = Options.compile(usage).parse(argv);
    final List<String> largs = opt.getList("local"); // NON-NLS
    final List<String> rargs = opt.getList("remote"); // NON-NLS
    final List<String> zargs = opt.getList("zip");
    final List<String> iargs = opt.getList("iwado"); // NON-NLS
    final List<String> wargs = opt.getList("wado"); // NON-NLS

    if (opt.isSet("help") // NON-NLS
        || (largs.isEmpty()
            && rargs.isEmpty()
            && iargs.isEmpty()
            && wargs.isEmpty()
            && zargs.isEmpty()
            && !opt.isSet("portable"))) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          firePropertyChange(
              new ObservableEvent(
                  ObservableEvent.BasicAction.SELECT, DicomModel.this, null, DicomModel.this));
          getCommand(opt, largs, rargs, iargs, wargs, zargs);
        });
  }

  private void getCommand(
      Option opt,
      List<String> largs,
      List<String> rargs,
      List<String> iargs,
      List<String> wargs,
      List<String> zargs) {
    // start importing local dicom series list
    if (opt.isSet("local")) { // NON-NLS
      File[] files = new File[largs.size()];
      for (int i = 0; i < files.length; i++) {
        files[i] = new File(largs.get(i));
      }
      OpeningViewer openingViewer =
          OpeningViewer.getOpeningViewerByLocalKey(LocalImport.LAST_OPEN_VIEWER_MODE);
      LOADING_EXECUTOR.execute(new LoadLocalDicom(files, true, DicomModel.this, openingViewer));
    }

    if (opt.isSet("remote")) { // NON-NLS
      LOADING_EXECUTOR.execute(
          new LoadRemoteDicomURL(rargs.toArray(new String[0]), DicomModel.this));
    }

    // build WADO series list to download
    if (opt.isSet("wado")) { // NON-NLS
      LOADING_EXECUTOR.execute(new LoadRemoteDicomManifest(wargs, DicomModel.this));
    }

    if (opt.isSet("zip")) {
      for (String zip : zargs) {
        DicomZipImport.loadDicomZip(zip, DicomModel.this);
      }
    }

    if (opt.isSet("iwado")) { // NON-NLS
      List<String> xmlFiles = new ArrayList<>(iargs.size());
      for (String iarg : iargs) {
        try {
          File tempFile =
              File.createTempFile("wado_", ".xml", AppProperties.APP_TEMP_DIR); // NON-NLS
          if (GzipManager.gzipUncompressToFile(Base64.getDecoder().decode(iarg), tempFile)) {
            xmlFiles.add(tempFile.getPath());
          }

        } catch (Exception e) {
          LOGGER.info("ungzip manifest", e);
        }
      }
      LOADING_EXECUTOR.execute(new LoadRemoteDicomManifest(xmlFiles, DicomModel.this));
    }

    // Get DICOM folder (by default DICOM, dicom, IHE_PDI, ihe_pdi) at the same level at the Weasis
    // executable file
    if (opt.isSet("portable")) { // NON-NLS

      String prop = System.getProperty("weasis.portable.dicom.directory");
      String baseDir = System.getProperty("weasis.portable.dir");

      if (prop != null && baseDir != null) {
        String[] dirs = prop.split(",");
        for (int i = 0; i < dirs.length; i++) {
          dirs[i] = dirs[i].trim().replace("/", File.separator);
        }
        File[] files = new File[dirs.length];
        boolean notCaseSensitive = SystemInfo.isWindows;
        if (notCaseSensitive) {
          Arrays.sort(dirs, String.CASE_INSENSITIVE_ORDER);
        }
        String last = null;
        for (int i = 0; i < files.length; i++) {
          if (notCaseSensitive && dirs[i].equalsIgnoreCase(last)) {
            last = null;
          } else {
            last = dirs[i];
            files[i] = new File(baseDir, dirs[i]);
          }
        }

        List<LoadSeries> loadSeries = null;
        File dcmDirFile = new File(baseDir, "DICOMDIR");
        if (dcmDirFile.canRead()) {
          // Copy images in cache if property weasis.portable.dicom.cache = true (default is true)
          DicomDirLoader dirImport =
              new DicomDirLoader(
                  dcmDirFile, DicomModel.this, DicomManager.getInstance().isPortableDirCache());
          loadSeries = dirImport.readDicomDir();
        }
        if (loadSeries != null && !loadSeries.isEmpty()) {
          OpeningViewer openingViewer =
              OpeningViewer.getOpeningViewerByLocalKey(DicomDirImport.LAST_DICOMDIR_OPEN_MODE);
          LOADING_EXECUTOR.execute(new LoadDicomDir(loadSeries, DicomModel.this, openingViewer));
        } else {
          OpeningViewer openingViewer =
              OpeningViewer.getOpeningViewerByLocalKey(LocalImport.LAST_OPEN_VIEWER_MODE);
          LOADING_EXECUTOR.execute(new LoadLocalDicom(files, true, DicomModel.this, openingViewer));
        }
      }
    }
  }

  public void rs(String[] argv) throws IOException {
    final String[] usage = {
      "Load DICOM files from DICOMWeb API (QIDO/WADO-RS)", // NON-NLS
      "Usage: dicom:rs -u URL -r QUERYPARAMS... [-H HEADER]... [--query-header HEADER]... [--retrieve-header HEADER]... [--query-ext EXT] [--retrieve-ext EXT] [--accept-ext EXT]", // NON-NLS
      "  -u --url=URL               URL of the DICOMWeb service", // NON-NLS
      "  -r --request=QUERYPARAMS   Query params of the URL, see weasis-pacs-connector", // NON-NLS
      "  -H --header=HEADER         Pass custom header(s) to all the requests", // NON-NLS
      "  --query-header=HEADER      Pass custom header(s) to the query requests (QIDO)", // NON-NLS
      "  --retrieve-header=HEADER   Pass custom header(s) to the retrieve requests (WADO)", // NON-NLS
      "  --query-ext=EXT            Additional parameters for Query URL (QIDO)", // NON-NLS
      "  --retrieve-ext=EXT         Additional parameters for Retrieve URL (WADO)", // NON-NLS
      "  --accept-ext=EXT           Additional parameters for DICOM multipart/related Accept header of the retrieve URL (WADO). Default value is: transfer-syntax=*", // NON-NLS
      "  --auth-uid=UID             UID of the Weasis authentication method", // NON-NLS
      "  --oidc-iss=UID             Issuer Identifier for OpenID Connect Authentication Request", // NON-NLS
      "  --oidc-login=UID           Identifier the End-User might use to log in (OpenID Connect)", // NON-NLS
      "  --show-whole-study         when downloading a series, show all the other series (ready for download) from the same study", // NON-NLS
      "  -? --help                  show help" // NON-NLS
    };

    final Option opt = Options.compile(usage).parse(argv);
    final String rsUrl = opt.get("url"); // NON-NLS
    final List<String> pargs = opt.getList("request"); // NON-NLS

    if (opt.isSet("help") || rsUrl.isEmpty() || (pargs.isEmpty())) { // NON-NLS
      opt.usage();
      return;
    }

    Properties props = new Properties();
    props.setProperty(RsQueryParams.P_DICOMWEB_URL, rsUrl);
    String queryExt = opt.get("query-ext"); // NON-NLS
    if (StringUtil.hasText(queryExt)) {
      props.setProperty(RsQueryParams.P_QUERY_EXT, queryExt);
    }
    String retrieveExt = opt.get("retrieve-ext"); // NON-NLS
    if (StringUtil.hasText(retrieveExt)) {
      props.setProperty(RsQueryParams.P_RETRIEVE_EXT, retrieveExt);
    }

    String acceptExt = opt.get("accept-ext"); // NON-NLS
    if (!StringUtil.hasText(acceptExt)) {
      acceptExt = "transfer-syntax=*"; // NON-NLS
    }
    props.setProperty(RsQueryParams.P_ACCEPT_EXT, acceptExt);

    if (opt.isSet("show-whole-study")) { // NON-NLS
      props.setProperty(RsQueryParams.P_SHOW_WHOLE_STUDY, Boolean.TRUE.toString());
    }

    String authUID = opt.get("auth-uid"); // NON-NLS
    if (StringUtil.hasText(authUID)) {
      props.setProperty(RsQueryParams.P_AUTH_UID, authUID);
    }
    String oidcIssuer = opt.get("oidc-iss"); // NON-NLS
    if (StringUtil.hasText(oidcIssuer)) {
      props.setProperty(RsQueryParams.P_OIDC_ISSUER, oidcIssuer);
    }
    String oidcLogin = opt.get("oidc-login"); // NON-NLS
    if (StringUtil.hasText(oidcLogin)) {
      props.setProperty(RsQueryParams.P_OIDC_USER, oidcLogin);
    }

    GuiExecutor.execute(
        () -> {
          firePropertyChange(
              new ObservableEvent(
                  ObservableEvent.BasicAction.SELECT, DicomModel.this, null, DicomModel.this));
          for (String query : pargs) {
            List<String> common = opt.getList("header"); // NON-NLS
            List<String> q = opt.getList("query-header"); // NON-NLS
            q.addAll(common);
            List<String> r = opt.getList("retrieve-header"); // NON-NLS
            r.addAll(common);
            RsQueryParams rsquery =
                new RsQueryParams(
                    DicomModel.this,
                    props,
                    RsQueryParams.getQueryMap(query),
                    RsQueryParams.getHeaders(q),
                    RsQueryParams.getHeaders(r));
            LOADING_EXECUTOR.execute(rsquery);
          }
        });
  }

  public void close(String[] argv) throws IOException {
    final String[] usage = {
      "Close DICOM files", // NON-NLS
      "Usage: dicom:close  (-a | ([-y UID]... [-s UID]...))", // NON-NLS
      "  -a --all           close all the patients", // NON-NLS
      "  -p --patient=ID    close a patient from its patient ID", // NON-NLS
      "  -y --study=UID     close a study, UID is Study Instance UID", // NON-NLS
      "  -s --series=UID    close a series, UID is Series Instance UID", // NON-NLS
      "  -? --help          show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> pargs = opt.getList("patient"); // NON-NLS
    final List<String> yargs = opt.getList("study"); // NON-NLS
    final List<String> sargs = opt.getList("series"); // NON-NLS

    if (opt.isSet("help") // NON-NLS
        || (pargs.isEmpty()
            && yargs.isEmpty()
            && sargs.isEmpty()
            && !opt.isSet("all"))) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          firePropertyChange(
              new ObservableEvent(
                  ObservableEvent.BasicAction.SELECT, DicomModel.this, null, DicomModel.this));
          closeCommand(opt, pargs, yargs, sargs);
        });
  }

  private void closeCommand(
      Option opt, List<String> pargs, List<String> yargs, List<String> sargs) {
    if (opt.isSet("all")) { // NON-NLS
      for (MediaSeriesGroup patientGroup : model.getSuccessors(MediaSeriesGroupNode.rootNode)) {
        removePatient(patientGroup);
      }
    } else {
      if (opt.isSet("patient")) { // NON-NLS
        for (MediaSeriesGroup patientGroup : model.getSuccessors(MediaSeriesGroupNode.rootNode)) {
          String patientID = TagD.getTagValue(patientGroup, Tag.PatientID, String.class);
          if (pargs.contains(patientID)) {
            removePatient(patientGroup);
          }
        }
      }
      if (opt.isSet("study")) { // NON-NLS
        for (String studyUID : yargs) {
          removeStudy(getStudyNode(studyUID));
        }
      }
      if (opt.isSet("series")) { // NON-NLS
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
          String uid = "#" + k + "." + seriesUID;
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
