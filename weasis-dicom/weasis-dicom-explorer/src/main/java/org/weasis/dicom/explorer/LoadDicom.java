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

import java.util.Arrays;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.service.BundleTools;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;

public abstract class LoadDicom extends ExplorerTask<Boolean, String> {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(LoadDicom.class);

  protected final DicomModel dicomModel;

  protected final PluginOpeningStrategy openingStrategy;

  protected LoadDicom(
      DataExplorerModel explorerModel, boolean interruptible, OpeningViewer openingMode) {
    this(explorerModel, interruptible, new PluginOpeningStrategy(openingMode));
  }

  protected LoadDicom(
      DataExplorerModel explorerModel,
      boolean interruptible,
      PluginOpeningStrategy openingStrategy) {
    super(Messages.getString("DicomExplorer.loading"), interruptible);
    if (!(explorerModel instanceof DicomModel)) {
      throw new IllegalArgumentException("invalid DICOM model");
    }
    this.dicomModel = (DicomModel) explorerModel;
    this.openingStrategy =
        Optional.ofNullable(openingStrategy)
            .orElseGet(() -> new PluginOpeningStrategy(OpeningViewer.ONE_PATIENT));
  }

  protected void startLoadingEvent() {
    if (!openingStrategy.isResetVeto()) {
      dicomModel.firePropertyChange(
          new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, dicomModel, null, this));
    }
  }

  @Override
  protected void done() {
    openingStrategy.reset();
    if (!openingStrategy.isResetVeto()) {
      dicomModel.firePropertyChange(
          new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
      LOGGER.info("End of loading DICOM locally");
    }
  }

  protected SeriesThumbnail buildDicomStructure(DicomMediaIO dicomReader) {
    SeriesThumbnail thumb = null;
    String studyUID = (String) dicomReader.getTagValue(TagD.getUID(Level.STUDY));
    String patientPseudoUID = (String) dicomReader.getTagValue(TagD.getUID(Level.PATIENT));
    MediaSeriesGroup patient =
        dicomModel.getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
    if (patient == null) {
      MediaSeriesGroup study = dicomModel.getStudyNode(studyUID);
      if (study == null) {
        patient =
            new MediaSeriesGroupNode(
                TagW.PatientPseudoUID, patientPseudoUID, DicomModel.patient.tagView());
        dicomReader.writeMetaData(patient);
        dicomModel.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
        LOGGER.info("Adding patient: {}", patient);
      } else {
        patient = dicomModel.getParent(study, DicomModel.patient);
        LOGGER.warn(
            "DICOM patient attributes are inconsistent! Name or ID is different within an exam.");
      }
    }

    MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, studyUID);
    if (study == null) {
      study =
          new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.tagView());
      dicomReader.writeMetaData(study);
      dicomModel.addHierarchyNode(patient, study);
    }

    boolean editableDicom = dicomReader.isEditableDicom();
    String seriesUID = (String) dicomReader.getTagValue(TagD.get(Tag.SeriesInstanceUID));
    Series<?> dicomSeries = (Series<?>) dicomModel.getHierarchyNode(study, seriesUID);
    try {
      if (dicomSeries == null) {
        dicomSeries = dicomReader.buildSeries(seriesUID);
        dicomSeries.setTag(TagW.ExplorerModel, dicomModel);
        if (editableDicom) {
          dicomSeries.setTag(TagW.ObjectToSave, Boolean.TRUE);
        }
        dicomReader.writeMetaData(dicomSeries);
        dicomModel.addHierarchyNode(study, dicomSeries);
        MediaElement[] medias = dicomReader.getMediaElement();
        if (medias != null) {
          for (MediaElement media : medias) {
            dicomModel.applySplittingRules(dicomSeries, media);
            if (editableDicom) {
              media.setTag(TagW.ObjectToSave, Boolean.TRUE);
            }
          }
          if (medias.length > 0) {
            dicomSeries.setFileSize(dicomSeries.getFileSize() + medias[0].getLength());
          }
        }

        // Load image and create thumbnail in this Thread
        SeriesThumbnail t = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
        if (t == null) {
          int thumbnailSize =
              BundleTools.SYSTEM_PREFERENCES.getIntProperty(
                  Thumbnail.KEY_SIZE, Thumbnail.DEFAULT_SIZE);
          t = DicomExplorer.createThumbnail(dicomSeries, dicomModel, thumbnailSize);
          dicomSeries.setTag(TagW.Thumbnail, t);
          if (t != null) {
            GuiExecutor.instance().execute(t::repaint);
          }
        }

        if (DicomModel.isSpecialModality(dicomSeries)) {
          dicomModel.addSpecialModality(dicomSeries);
          Arrays.stream(medias)
              .filter(DicomSpecialElement.class::isInstance)
              .map(DicomSpecialElement.class::cast)
              .findFirst()
              .ifPresent(
                  d ->
                      dicomModel.firePropertyChange(
                          new ObservableEvent(
                              ObservableEvent.BasicAction.UPDATE, dicomModel, null, d)));
        } else {
          dicomModel.firePropertyChange(
              new ObservableEvent(ObservableEvent.BasicAction.ADD, dicomModel, null, dicomSeries));
        }

        // After the thumbnail is sent to interface, it will be return to be rebuilt later
        thumb = t;

        Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
        if (splitNb != null) {
          dicomModel.firePropertyChange(
              new ObservableEvent(
                  ObservableEvent.BasicAction.UPDATE, dicomModel, null, dicomSeries));
        }
        openingStrategy.openViewerPlugin(patient, dicomModel, dicomSeries);
      } else {
        // Test if SOPInstanceUID already exists
        if (isSOPInstanceUIDExist(
            study, dicomSeries, TagD.getTagValue(dicomReader, Tag.SOPInstanceUID, String.class))) {
          openingStrategy.openViewerPlugin(patient, dicomModel, dicomSeries);
          return null;
        }
        MediaElement[] medias = dicomReader.getMediaElement();
        if (medias != null) {
          for (MediaElement media : medias) {
            dicomModel.applySplittingRules(dicomSeries, media);
            if (editableDicom) {
              media.setTag(TagW.ObjectToSave, Boolean.TRUE);
            }
          }
          if (medias.length > 0) {
            dicomSeries.setFileSize(dicomSeries.getFileSize() + medias[0].getLength());
            // Refresh the number of images on the thumbnail
            Thumbnail t = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
            if (t != null) {
              GuiExecutor.instance().execute(t::repaint);
            }
          }

          if (DicomModel.isSpecialModality(dicomSeries)) {
            dicomModel.addSpecialModality(dicomSeries);
            Arrays.stream(medias)
                .filter(DicomSpecialElement.class::isInstance)
                .map(DicomSpecialElement.class::cast)
                .findFirst()
                .ifPresent(
                    d ->
                        dicomModel.firePropertyChange(
                            new ObservableEvent(
                                ObservableEvent.BasicAction.UPDATE, dicomModel, null, d)));
          }

          // If Split series or special DICOM element update the explorer view and View2DContainer
          Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
          if (splitNb != null) {
            dicomModel.firePropertyChange(
                new ObservableEvent(
                    ObservableEvent.BasicAction.UPDATE, dicomModel, null, dicomSeries));
          }
          openingStrategy.openViewerPlugin(patient, dicomModel, dicomSeries);
        }
      }
    } catch (Exception e) {
      LOGGER.error("Build DICOM hierarchy", e);
    }
    return thumb;
  }

  protected boolean isSOPInstanceUIDExist(
      MediaSeriesGroup study, Series<?> dicomSeries, Object sopUID) {
    TagW sopTag = TagD.getUID(Level.INSTANCE);
    if (dicomSeries.hasMediaContains(sopTag, sopUID)) {
      return true;
    }
    Object splitNb = dicomSeries.getTagValue(TagW.SplitSeriesNumber);
    if (splitNb != null && study != null) {
      String uid = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
      if (uid != null) {
        for (MediaSeriesGroup group : dicomModel.getChildren(study)) {
          if (dicomSeries != group
              && group instanceof Series<?> s
              && uid.equals(TagD.getTagValue(group, Tag.SeriesInstanceUID))
              && s.hasMediaContains(sopTag, sopUID)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
