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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.swing.JOptionPane;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import org.dcm4che3.data.Tag;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.DicomSpecialElementFactory;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;

public abstract class LoadDicom extends ExplorerTask<Boolean, String> {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(LoadDicom.class);

  protected final AtomicInteger errors = new AtomicInteger(0);

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
    if (openingStrategy.isFullImportSession()) {
      dicomModel.firePropertyChange(
          new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, dicomModel, null, this));
    }
  }

  @Override
  protected void done() {
    openingStrategy.reset();
    if (openingStrategy.isFullImportSession()) {
      dicomModel.firePropertyChange(
          new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
      LOGGER.info("End of loading DICOM locally");
      int nbErrors = errors.get();
      if (nbErrors > 0) {
        JOptionPane.showMessageDialog(
            GuiUtils.getUICore().getApplicationWindow(),
            getErrorPanel(nbErrors),
            Messages.getString("DicomImport.imp_dicom"),
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private JTextPane getErrorPanel(int nbErrors) {
    String message = Messages.getString("dicom.file.cannot.be.read").formatted(nbErrors);
    String logOutput = Messages.getString("check.log.output");
    String html =
        """
      <P>
      %s<br>
      <a href="%s">%s</a>
      </P>
    """
            .formatted(
                message,
                GuiUtils.getUICore().getSystemPreferences().getProperty("weasis.help.online")
                    + "logging",
                logOutput);
    JTextPane jTextPane1 = GuiUtils.getPanelWithHyperlink(html);
    jTextPane1.setBorder(new EmptyBorder(5, 5, 15, 5));
    return jTextPane1;
  }

  protected DicomSeries buildDicomStructure(DicomMediaIO dicomReader) {
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
    DicomSeries dicomSeries = (DicomSeries) dicomModel.getHierarchyNode(study, seriesUID);
    try {
      if (dicomSeries == null) {
        dicomSeries = dicomReader.buildSeries(seriesUID);
        dicomSeries.setTag(TagW.ExplorerModel, dicomModel);
        dicomSeries.setTag(TagW.stepNDimensions, null);
        if (editableDicom) {
          dicomSeries.setTag(TagW.ObjectToSave, Boolean.TRUE);
        }
        dicomReader.writeMetaData(dicomSeries);
        dicomModel.addHierarchyNode(study, dicomSeries);
        getDicomImageElements(dicomReader, dicomSeries, editableDicom);

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

        DicomImageElement[] medias = getDicomImageElements(dicomReader, dicomSeries, editableDicom);
        if (medias != null && medias.length > 0) {
          // Refresh the number of images on the thumbnail
          Thumbnail t = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
          if (t != null) {
            GuiExecutor.execute(t::repaint);
          }
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
    } catch (Exception e) {
      LOGGER.error("Build DICOM hierarchy", e);
    }
    return dicomSeries;
  }

  private DicomImageElement[] getDicomImageElements(
      DicomMediaIO dicomReader, DicomSeries series, boolean editableDicom) {
    Function<DicomSpecialElementFactory, DicomSpecialElement> buildSpecialElement =
        factory -> factory.buildDicomSpecialElement(dicomReader);

    DicomMediaIO.ResultContainer result = dicomReader.getMediaElement(buildSpecialElement);
    DicomImageElement[] medias = result.getImage();
    if (medias != null) {
      for (DicomImageElement media : medias) {
        dicomModel.applySplittingRules(series, media);
        if (editableDicom) {
          media.setTag(TagW.ObjectToSave, Boolean.TRUE);
        }
      }
      if (medias.length > 0) {
        // Handle multi-frame DICOM
        series.setFileSize(series.getFileSize() + medias[0].getLength());
      }
    }
    if (result.getSpecialElement() != null) {
      DicomSpecialElement media = result.getSpecialElement();
      dicomModel.applySplittingRules(series, media);
      series.setFileSize(series.getFileSize() + media.getLength());
      dicomModel.firePropertyChange(
          new ObservableEvent(ObservableEvent.BasicAction.UPDATE, dicomModel, null, media));
    }
    return medias;
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
