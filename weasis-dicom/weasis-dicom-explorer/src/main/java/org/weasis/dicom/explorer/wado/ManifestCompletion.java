/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.rs.RsQueryResult;
import org.weasis.dicom.mf.HttpTag;
import org.weasis.dicom.mf.WadoParameters;

/**
 * Completes a partially populated {@code DICOM_WEB} manifest down to the series level. When an
 * arcQuery uses the {@code DICOM_WEB} query mode, the manifest may stop at the Patient or Study
 * level; the missing studies and series are queried on demand through QIDO-RS and fed back into the
 * same {@link ManifestModelBuilder} node building, so completed nodes are indistinguishable from
 * manifest ones. Each completed series is retrieved in bulk (a single series-level WADO-RS
 * request); instance-level enumeration is the archive connector's responsibility, expressed by a
 * manifest that already carries the instance level.
 *
 * <p>Queries are targeted by UID against the manifest base URL ({@code
 * {base}/studies/{study}/series}), which avoids relational archive-wide queries.
 */
final class ManifestCompletion {
  private static final Logger LOGGER = LoggerFactory.getLogger(ManifestCompletion.class);

  private static final String STUDY_FIELDS =
      "00080020,00080030,00080050,00080061,00080090,00081030,00100010,00100020,00100021,00100030,00100040,0020000D,00200010"; // NON-NLS
  private static final String SERIES_FIELDS =
      "0008103E,00080060,0020000E,00200011,00081190"; // NON-NLS

  private ManifestCompletion() {}

  /**
   * A manifest level is partial when its lower levels must be queried through DICOMweb: it has no
   * children and belongs to a {@code DICOM_WEB} (WADO-RS) arcQuery. Shared by the XML and JSON
   * parsers so both detect partial patients and studies identically.
   */
  static boolean isPartialLevel(WadoParameters wado, boolean hasChildren) {
    return !hasChildren && wado != null && wado.isWadoRS();
  }

  /** Queries the studies of a patient-level manifest and completes each of them. */
  static void completePatient(ReaderParams params, WadoParameters wado, MediaSeriesGroup patient) {
    String patientID = TagD.getTagValue(patient, Tag.PatientID, String.class);
    if (!StringUtil.hasText(patientID) || TagW.NO_VALUE.equals(patientID)) {
      LOGGER.warn("Cannot complete a patient-level DICOMweb manifest without a PatientID");
      return;
    }
    String issuer = TagD.getTagValue(patient, Tag.IssuerOfPatientID, String.class);

    StringBuilder buf = new StringBuilder(wado.getBaseURL());
    buf.append("/studies?00100020="); // NON-NLS
    buf.append(URLEncoder.encode(patientID, StandardCharsets.UTF_8));
    if (StringUtil.hasText(issuer)) {
      buf.append("&00100021=").append(URLEncoder.encode(issuer, StandardCharsets.UTF_8));
    }
    appendIncludeField(buf, STUDY_FIELDS);

    for (Attributes studyDataset : query(buf.toString(), params, wado, "patient studies")) {
      MediaSeriesGroup study = ManifestModelBuilder.buildStudy(studyDataset, params, patient);
      completeStudy(params, wado, patient, study);
    }
  }

  /** Queries the series of a study-level manifest and completes each of them. */
  static void completeStudy(
      ReaderParams params, WadoParameters wado, MediaSeriesGroup patient, MediaSeriesGroup study) {
    String studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
    if (!StringUtil.hasText(studyUID)) {
      return;
    }

    StringBuilder buf = new StringBuilder(wado.getBaseURL());
    buf.append("/studies/").append(studyUID).append("/series"); // NON-NLS
    appendIncludeField(buf, SERIES_FIELDS);

    for (Attributes seriesDataset : query(buf.toString(), params, wado, "study series")) {
      DicomSeries dicomSeries =
          ManifestModelBuilder.buildSeries(seriesDataset, params, study, wado);
      // completeSeries marks the empty DICOM_WEB series for bulk retrieve and creates the
      // LoadSeries.
      ManifestModelBuilder.completeSeries(
          params,
          patient,
          study,
          dicomSeries,
          ManifestModelBuilder.getSeriesInstanceList(dicomSeries));
    }
  }

  private static List<Attributes> query(
      String url, ReaderParams params, WadoParameters wado, String context) {
    try {
      AuthMethod authMethod = params.wadoUri == null ? null : params.wadoUri.getAuthMethod();
      LOGGER.debug("QIDO-RS completion ({}): {}", context, url);
      return RsQueryResult.parseJSON(url, authMethod, new URLParameters(queryHeaders(wado)));
    } catch (Exception e) {
      LOGGER.error("QIDO-RS completion failed for {}", context, e);
      return List.of();
    }
  }

  private static Map<String, String> queryHeaders(WadoParameters wado) {
    Map<String, String> headers = new HashMap<>();
    if (wado.getHttpTaglist() != null) {
      for (HttpTag tag : wado.getHttpTaglist()) {
        headers.put(tag.getKey(), tag.getValue());
      }
    }
    if (StringUtil.hasText(wado.getWebLogin())) {
      headers.put("Authorization", "Basic " + wado.getWebLogin()); // NON-NLS
    }
    headers.putIfAbsent("Accept", "application/dicom+json"); // NON-NLS
    return headers;
  }

  private static void appendIncludeField(StringBuilder buf, String fields) {
    buf.append(buf.indexOf("?") < 0 ? '?' : '&');
    buf.append("includefield=").append(fields); // NON-NLS
  }
}
