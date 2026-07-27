/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import java.net.URL;
import java.util.List;
import java.util.Set;
import javax.swing.JOptionPane;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.AttributeSource;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.utils.PatientComparator;
import org.weasis.dicom.codec.utils.SeriesInstanceList;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode.WebType;
import org.weasis.dicom.mf.ArcParameters;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;

/**
 * Builds the {@link DicomModel} hierarchy from manifest attributes, independently of the source
 * format. The XML and JSON parsers drive the tree navigation and feed each node's attributes as an
 * {@link AttributeSource}.
 */
final class ManifestModelBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(ManifestModelBuilder.class);

  // Constant tag handles and keyword arrays resolved once, reused for every manifest node.
  private static final TagW PATIENT_UID = TagD.getUID(Level.PATIENT);
  private static final TagW STUDY_UID = TagD.getUID(Level.STUDY);
  private static final TagW PATIENT_ID = TagD.get(Tag.PatientID);
  private static final TagW ISSUER_OF_PATIENT_ID = TagD.get(Tag.IssuerOfPatientID);
  private static final TagW PATIENT_NAME = TagD.get(Tag.PatientName);
  private static final TagW SERIES_INSTANCE_UID = TagD.get(Tag.SeriesInstanceUID);
  private static final TagW[] PATIENT_TAGS =
      TagD.getTagFromIDs(Tag.PatientSex, Tag.PatientBirthDate, Tag.PatientBirthTime);
  private static final TagW[] STUDY_TAGS =
      TagD.getTagFromIDs(
          Tag.StudyDate, Tag.StudyTime, Tag.StudyDescription, Tag.AccessionNumber, Tag.StudyID);
  private static final TagW[] SERIES_TAGS =
      TagD.getTagFromIDs(
          Tag.Modality,
          Tag.SeriesNumber,
          Tag.SeriesDate,
          Tag.SeriesTime,
          Tag.SeriesDescription,
          Tag.ReferringPhysicianName);
  private static final String SOP_INSTANCE_UID_KEY =
      TagD.getKeywordFromTag(Tag.SOPInstanceUID, null);
  private static final String SOP_CLASS_UID_KEY = TagD.getKeywordFromTag(Tag.SOPClassUID, null);
  private static final String INSTANCE_NUMBER_KEY =
      TagD.getKeywordFromTag(Tag.InstanceNumber, null);

  private ManifestModelBuilder() {}

  static WadoParameters buildArcQueryParameters(AttributeSource source, ReaderParams params) {
    String arcID = TagUtil.getTagAttribute(source, ArcParameters.ARCHIVE_ID, "");
    String wadoURL = TagUtil.getTagAttribute(source, ArcParameters.BASE_URL, null);
    boolean onlySopUID =
        Boolean.parseBoolean(
            TagUtil.getTagAttribute(
                source, WadoParameters.WADO_ONLY_SOP_UID, Boolean.FALSE.toString()));
    String additionalParameters =
        TagUtil.getTagAttribute(source, ArcParameters.ADDITIONAL_PARAMETERS, "");
    String overrideList = TagUtil.getTagAttribute(source, ArcParameters.OVERRIDE_TAGS, null);
    String webLogin = TagUtil.getTagAttribute(source, ArcParameters.WEB_LOGIN, null);
    String queryMode = TagUtil.getTagAttribute(source, ArcParameters.QUERY_MODE, null);
    boolean wadoRs = "DICOM_WEB".equals(queryMode); // NON-NLS
    WadoParameters wadoParameters =
        new WadoParameters(
            arcID, wadoURL, onlySopUID, additionalParameters, overrideList, webLogin, wadoRs);
    params.wadoUri = getWadoUrl(wadoURL);
    params.bulkSeriesRetrieve = isBulkSeriesRetrieve(source, wadoRs);
    return wadoParameters;
  }

  /**
   * Series-level bulk-retrieve opt-in for a DICOMweb arcQuery: enabled by the {@code
   * seriesRetrieve} attribute, falling back to the {@code weasis.dicom.web.series.bulk} system
   * property when the attribute is absent. Never enabled for non-{@code DICOM_WEB} manifests.
   */
  static boolean isBulkSeriesRetrieve(AttributeSource source, boolean wadoRs) {
    if (!wadoRs) {
      return false;
    }
    String seriesRetrieve = TagUtil.getTagAttribute(source, "seriesRetrieve", null); // NON-NLS
    if (seriesRetrieve != null) {
      return Boolean.parseBoolean(seriesRetrieve);
    }
    return Boolean.getBoolean("weasis.dicom.web.series.bulk"); // NON-NLS
  }

  static void addHttpTag(AttributeSource source, WadoParameters wadoParameters) {
    String httpKey = TagUtil.getTagAttribute(source, "key", null); // NON-NLS
    String httpValue = TagUtil.getTagAttribute(source, "value", null); // NON-NLS
    wadoParameters.addHttpTag(httpKey, httpValue);
  }

  static void showViewerMessage(AttributeSource source) {
    final String title = TagUtil.getTagAttribute(source, "title", null); // NON-NLS
    final String message = TagUtil.getTagAttribute(source, "description", null);
    if (StringUtil.hasText(title) && StringUtil.hasText(message)) {
      String severity = TagUtil.getTagAttribute(source, "severity", "WARN"); // NON-NLS
      final int messageType =
          "ERROR".equals(severity)
              ? JOptionPane.ERROR_MESSAGE
              : "INFO".equals(severity)
                  ? JOptionPane.INFORMATION_MESSAGE
                  : JOptionPane.WARNING_MESSAGE;

      GuiExecutor.execute(
          () -> {
            ColorLayerUI layer =
                ColorLayerUI.createTransparentLayerUI(GuiUtils.getUICore().getBaseArea());
            JOptionPane.showMessageDialog(
                ColorLayerUI.getContentPane(layer), message, title, messageType);
            if (layer != null) {
              layer.hideUI();
            }
          });
    }
  }

  static MediaSeriesGroup buildPatient(
      AttributeSource source, ReaderParams params, WadoParameters wadoParameters) {
    // PatientID, PatientBirthDate, StudyInstanceUID, SeriesInstanceUID and SOPInstanceUID override
    // the tags located in DICOM object (because original DICOM can contain different values after
    // merging patient or study
    PatientComparator patientComparator = new PatientComparator(source);
    String patientPseudoUID = patientComparator.buildPatientPseudoUID();

    DicomModel model = params.getModel();
    MediaSeriesGroup patient =
        model.getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
    if (patient == null) {
      patient =
          new MediaSeriesGroupNode(PATIENT_UID, patientPseudoUID, DicomModel.patient.tagView());
      patient.setTag(
          PATIENT_ID, TagUtil.getTagAttribute(source, PATIENT_ID.getKeyword(), TagW.NO_VALUE));
      patient.setTag(
          PATIENT_NAME, TagUtil.getTagAttribute(source, PATIENT_NAME.getKeyword(), TagW.NO_VALUE));
      patient.setTagNoNull(
          ISSUER_OF_PATIENT_ID,
          TagUtil.getTagAttribute(source, ISSUER_OF_PATIENT_ID.getKeyword(), null));

      for (TagW tag : PATIENT_TAGS) {
        tag.readValue(source, patient);
      }

      model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
      LOGGER.info("Adding new patient: {}", patient);
    }
    return patient;
  }

  static MediaSeriesGroup buildStudy(
      AttributeSource source, ReaderParams params, MediaSeriesGroup patient) {
    return buildStudy((Object) source, params, patient);
  }

  /** Builds a study node from a QIDO-RS dataset when completing a partial DICOMweb manifest. */
  static MediaSeriesGroup buildStudy(
      Attributes dataset, ReaderParams params, MediaSeriesGroup patient) {
    return buildStudy((Object) dataset, params, patient);
  }

  private static MediaSeriesGroup buildStudy(
      Object source, ReaderParams params, MediaSeriesGroup patient) {
    DicomModel model = params.getModel();
    String studyUID = (String) STUDY_UID.getValue(source);
    MediaSeriesGroup study = model.getHierarchyNode(patient, studyUID);
    if (study == null) {
      study = new MediaSeriesGroupNode(STUDY_UID, studyUID, DicomModel.study.tagView());
      for (TagW tag : STUDY_TAGS) {
        tag.readValue(source, study);
      }

      model.addHierarchyNode(patient, study);
    }
    return study;
  }

  /** Creates or updates the series node, or returns {@code null} if it must be skipped. */
  static DicomSeries buildSeries(
      AttributeSource source,
      ReaderParams params,
      MediaSeriesGroup study,
      WadoParameters wadoParameters) {
    DicomModel model = params.getModel();
    String seriesUID = (String) SERIES_INSTANCE_UID.getValue(source);
    DicomSeries dicomSeries = (DicomSeries) model.getHierarchyNode(study, seriesUID);

    if (dicomSeries == null) {
      dicomSeries = new DicomSeries(seriesUID);
      dicomSeries.setTag(SERIES_INSTANCE_UID, seriesUID);
      dicomSeries.setTag(TagW.ExplorerModel, model);
      dicomSeries.setTag(TagW.WadoParameters, wadoParameters);

      for (TagW tag : SERIES_TAGS) {
        tag.readValue(source, dicomSeries);
      }

      dicomSeries.setTagNoNull(
          TagW.WadoTransferSyntaxUID,
          TagUtil.getTagAttribute(source, TagW.WadoTransferSyntaxUID.getKeyword(), null));
      dicomSeries.setTagNoNull(
          TagW.WadoCompressionRate,
          TagUtil.getIntegerTagAttribute(source, TagW.WadoCompressionRate.getKeyword(), null));
      dicomSeries.setTagNoNull(
          TagW.DirectDownloadThumbnail,
          TagUtil.getTagAttribute(source, TagW.DirectDownloadThumbnail.getKeyword(), null));

      model.addHierarchyNode(study, dicomSeries);
    } else {
      WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
      if (wado == null) {
        // Should not happen
        dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
      } else if (!wado.getBaseURL().equals(wadoParameters.getBaseURL())) {
        LOGGER.error("Wado parameters must be unique within a DICOM Series: {}", dicomSeries);
        return null;
      }
    }
    return dicomSeries;
  }

  /** Builds a series node from a QIDO-RS dataset when completing a partial DICOMweb manifest. */
  static DicomSeries buildSeries(
      Attributes dataset,
      ReaderParams params,
      MediaSeriesGroup study,
      WadoParameters wadoParameters) {
    DicomModel model = params.getModel();
    String seriesUID = (String) SERIES_INSTANCE_UID.getValue(dataset);
    DicomSeries dicomSeries = (DicomSeries) model.getHierarchyNode(study, seriesUID);
    if (dicomSeries == null) {
      dicomSeries = new DicomSeries(seriesUID);
      dicomSeries.setTag(SERIES_INSTANCE_UID, seriesUID);
      dicomSeries.setTag(TagW.ExplorerModel, model);
      dicomSeries.setTag(TagW.WadoParameters, wadoParameters);

      for (TagW tag : SERIES_TAGS) {
        tag.readValue(dataset, dicomSeries);
      }

      model.addHierarchyNode(study, dicomSeries);
    }
    return dicomSeries;
  }

  static SeriesInstanceList getSeriesInstanceList(DicomSeries dicomSeries) {
    SeriesInstanceList list =
        (SeriesInstanceList) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
    return list != null ? list : new SeriesInstanceList();
  }

  static void addSopInstance(AttributeSource source, SeriesInstanceList seriesInstanceList) {
    String sopInstanceUID = TagUtil.getTagAttribute(source, SOP_INSTANCE_UID_KEY, null);
    if (sopInstanceUID != null) {
      Integer frame = TagUtil.getIntegerTagAttribute(source, INSTANCE_NUMBER_KEY, null);
      SopInstance sop = seriesInstanceList.getSopInstance(sopInstanceUID, frame);
      if (sop == null) {
        String sopClassUID = TagUtil.getTagAttribute(source, SOP_CLASS_UID_KEY, null);
        sop = new SopInstance(sopInstanceUID, sopClassUID, frame);
        sop.setDirectDownloadFile(
            TagUtil.getTagAttribute(source, TagW.DirectDownloadFile.getKeyword(), null));
        seriesInstanceList.addSopInstance(sop);
      }
    }
  }

  static void completeSeries(
      ReaderParams params,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      DicomSeries dicomSeries,
      SeriesInstanceList seriesInstanceList) {
    // A DICOMweb manifest stopping at the series level is retrieved with a single series-level
    // WADO-RS multipart request when the arcQuery opts in (see isBulkSeriesRetrieve); per-instance
    // enumeration is then the archive's concern (the manifest carries the instance level
    // otherwise).
    if (seriesInstanceList.isEmpty() && params.bulkSeriesRetrieve) {
      dicomSeries.setTag(LoadSeries.SERIES_BULK_RETRIEVE, Boolean.TRUE);
    }
    dicomSeries.setTag(TagW.WadoInstanceReferenceList, seriesInstanceList);
    boolean bulkRetrieve =
        Boolean.TRUE.equals(dicomSeries.getTagValue(LoadSeries.SERIES_BULK_RETRIEVE));
    if (!seriesInstanceList.isEmpty() || bulkRetrieve) {
      DicomModel model = params.getModel();
      String seriesUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
      AuthMethod authMethod = params.wadoUri == null ? null : params.wadoUri.getAuthMethod();
      final LoadSeries loadSeries =
          new LoadSeries(
              dicomSeries,
              model,
              authMethod,
              GuiUtils.getUICore()
                  .getSystemPreferences()
                  .getIntProperty(LoadSeries.CONCURRENT_DOWNLOADS_IN_SERIES, 4),
              true,
              true);
      loadSeries.setPriority(new DownloadPriority(patient, study, dicomSeries, true));
      params.getSeriesMap().put(seriesUID, loadSeries);
    }
  }

  static void focusUniquePatient(Set<MediaSeriesGroup> patients) {
    if (patients.size() == 1) {
      final MediaSeriesGroup uniquePatient = patients.iterator().next();
      GuiExecutor.execute(
          () -> {
            List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
            synchronized (viewerPlugins) {
              for (final ViewerPlugin<?> p : viewerPlugins) {
                if (uniquePatient.equals(p.getGroupID())) {
                  p.setSelectedAndGetFocus();
                  break;
                }
              }
            }
          });
    }
  }

  static void startDownloads(ReaderParams params, WadoParameters wadoParameters) {
    for (LoadSeries loadSeries : params.getSeriesMap().values()) {
      if (!DicomModel.isHiddenModality(loadSeries.getDicomSeries())) {
        loadSeries.startDownloadImageReference(wadoParameters);
      }
    }
  }

  static DicomWebNode getWadoUrl(String url) {
    List<AbstractDicomNode> webNodes =
        AbstractDicomNode.loadDicomNodes(
            AbstractDicomNode.Type.WEB, AbstractDicomNode.UsageType.RETRIEVE, WebType.WADO);
    for (AbstractDicomNode n : webNodes) {
      if (n instanceof DicomWebNode wn) {
        URL wadoURL = wn.getUrl();
        if (wadoURL != null && wadoURL.toString().equals(url)) {
          return wn;
        }
      }
    }
    return null;
  }
}
