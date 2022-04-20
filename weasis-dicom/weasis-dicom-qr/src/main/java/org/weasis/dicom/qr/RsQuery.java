/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.URLParameters;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.PatientComparator;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.rs.RsQueryParams;
import org.weasis.dicom.explorer.rs.RsQueryResult;
import org.weasis.dicom.param.DicomParam;

public class RsQuery implements Callable<Boolean> {
  private static final Logger LOGGER = LoggerFactory.getLogger(RsQuery.class);

  private final DicomModel dicomModel;
  private final Properties properties;
  private final Map<String, String> queryHeaders;
  protected final List<DicomParam> queries;
  private final AuthMethod authMethod;

  public RsQuery(
      DicomModel dicomModel,
      Properties properties,
      List<DicomParam> queries,
      AuthMethod authMethod,
      Map<String, String> queryHeaders) {
    this.dicomModel = Objects.requireNonNull(dicomModel);
    this.properties = Objects.requireNonNull(properties);
    this.queries = Objects.requireNonNull(queries);
    String url = properties.getProperty(RsQueryParams.P_DICOMWEB_URL);
    if (StringUtil.hasText(url)) {
      if (url.endsWith("/")) {
        properties.setProperty(RsQueryParams.P_DICOMWEB_URL, url.substring(0, url.length() - 1));
      }
    } else {
      throw new IllegalArgumentException("DICOMWeb URL cannot be null");
    }
    this.authMethod = authMethod;
    this.queryHeaders = queryHeaders == null ? Collections.emptyMap() : queryHeaders;
  }

  public DicomModel getDicomModel() {
    return dicomModel;
  }

  public String getBaseUrl() {
    return properties.getProperty(RsQueryParams.P_DICOMWEB_URL);
  }

  @Override
  public Boolean call() throws Exception {
    StringBuilder buf = new StringBuilder(getBaseUrl());
    buf.append("/studies?"); // NON-NLS

    for (int i = 0; i < queries.size(); i++) {
      DicomParam query = queries.get(i);
      StringJoiner joiner = new StringJoiner(",");
      if (query.getValues() != null) {
        for (String v : query.getValues()) {
          if (StringUtil.hasText(v)) {
            String encode = URLEncoder.encode(v, StandardCharsets.UTF_8.toString());
            joiner.add(encode);
          }
        }
      }
      String value = joiner.toString();
      if (StringUtil.hasText(value)) {
        buf.append(TagUtils.toHexString(query.getTag()));
        buf.append("=");
        if (Tag.PatientID == query.getTag()) {
          // IssuerOfPatientID filter ( syntax like in HL7 with extension^^^root)
          int beginIndex = value.indexOf("^^^");
          String patientVal = beginIndex <= 0 ? value : value.substring(0, beginIndex);
          buf.append(patientVal);
          if (beginIndex > 0) {
            buf.append("&00100021=");
            buf.append(value.substring(beginIndex + 3));
          }
        } else {
          buf.append(value);
        }
        if (i + 1 < queries.size()) {
          buf.append("&");
        }
      }
    }
    buildQueries(buf);
    return true;
  }

  public void buildQueries(StringBuilder buf) {
    try {
      buf.append(RsQueryResult.STUDY_QUERY);
      buf.append(properties.getProperty(RsQueryParams.P_QUERY_EXT, ""));
      buf.append(properties.getProperty(RsQueryParams.P_PAGE_EXT, ""));

      LOGGER.debug(RsQueryResult.QIDO_REQUEST, buf);
      List<Attributes> studies =
          RsQueryResult.parseJSON(buf.toString(), authMethod, new URLParameters(queryHeaders));
      for (Attributes studyDataSet : studies) {
        fillSeries(studyDataSet);
      }
    } catch (Exception e) {
      LOGGER.error("QIDO-RS", e);
    }
  }

  private void fillSeries(Attributes studyDataSet) {
    String studyInstanceUID = studyDataSet.getString(Tag.StudyInstanceUID);
    if (StringUtil.hasText(studyInstanceUID)) {
      StringBuilder buf = new StringBuilder(getBaseUrl());
      buf.append("/studies/"); // NON-NLS
      buf.append(studyInstanceUID);
      buf.append("/series?includefield="); // NON-NLS
      buf.append(RsQueryResult.SERIES_QUERY);
      buf.append(properties.getProperty(RsQueryParams.P_QUERY_EXT, ""));

      try {
        LOGGER.debug(RsQueryResult.QIDO_REQUEST, buf);
        List<Attributes> series =
            RsQueryResult.parseJSON(buf.toString(), authMethod, new URLParameters(queryHeaders));
        if (!series.isEmpty()) {
          RsQuery.populateDicomModel(dicomModel, studyDataSet);
        }
      } catch (Exception e) {
        LOGGER.error("QIDO-RS all series with studyUID {}", studyInstanceUID, e);
      }
    }
  }

  static void populateDicomModel(DicomModel dicomModel, Attributes item) {
    PatientComparator patientComparator = new PatientComparator(item);
    String patientPseudoUID = patientComparator.buildPatientPseudoUID();
    MediaSeriesGroup patient =
        dicomModel.getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
    if (patient == null) {
      patient =
          new MediaSeriesGroupNode(
              TagW.PatientPseudoUID, patientPseudoUID, DicomModel.patient.tagView()) {
            @Override
            public String toString() {
              StringBuilder buf = new StringBuilder(getDisplayValue(this, Tag.PatientName));
              buf.append(" [");
              buf.append(getDisplayValue(this, Tag.PatientID));
              buf.append("] ");
              buf.append(getDisplayValue(this, Tag.PatientBirthDate));
              buf.append(" ");
              buf.append(getDisplayValue(this, Tag.PatientSex));
              return buf.toString();
            }
          };
      DicomMediaUtils.writeMetaData(patient, item);
      dicomModel.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
    }

    String studyUID = item.getString(Tag.StudyInstanceUID);
    MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, studyUID);
    if (study == null) {
      study =
          new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.tagView()) {
            @Override
            public String toString() {
              StringBuilder buf = new StringBuilder(getDisplayValue(this, Tag.StudyDescription));
              buf.append(" [");
              buf.append(getDisplayValue(this, Tag.ModalitiesInStudy));
              buf.append("] ");
              LocalDateTime studyDate = TagD.dateTime(Tag.StudyDate, Tag.StudyTime, this);
              if (studyDate != null) {
                buf.append(TagUtil.formatDateTime(studyDate));
                buf.append(" ");
              }
              buf.append(getDisplayValue(this, Tag.AccessionNumber));
              return buf.toString();
            }
          };
      DicomMediaUtils.writeMetaData(study, item);
      dicomModel.addHierarchyNode(patient, study);
    }
  }

  private static String getDisplayValue(MediaSeriesGroupNode node, int tagID) {
    TagW tag = TagD.get(tagID);
    if (tag != null) {
      Object value = node.getTagValue(tag);
      if (value != null) {
        return tag.getFormattedTagValue(value, null);
      }
    }
    return StringUtil.EMPTY_STRING;
  }
}
