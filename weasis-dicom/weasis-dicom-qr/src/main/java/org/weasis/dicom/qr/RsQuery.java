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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Callable;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.PatientComparator;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.rs.RsQueryParams;
import org.weasis.dicom.explorer.rs.RsQueryResult;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.web.HttpWarning;
import org.weasis.dicom.web.QidoQuery;
import org.weasis.dicom.web.SearchResponse;

public class RsQuery implements Callable<Boolean> {
  private static final Logger LOGGER = LoggerFactory.getLogger(RsQuery.class);

  private final DicomModel dicomModel;
  private final Properties properties;
  private final Map<String, String> queryHeaders;
  protected final List<DicomParam> queries;
  private final AuthMethod authMethod;
  private boolean fuzzyMatching;
  private volatile List<HttpWarning> warnings = List.of();

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
      throw new IllegalArgumentException("DICOMweb URL cannot be null");
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

  /** Requests fuzzy matching of Person Name values (PS3.18 §8.3.4.2). */
  public void setFuzzyMatching(boolean fuzzyMatching) {
    this.fuzzyMatching = fuzzyMatching;
  }

  /** The warnings of the origin server on the last query, e.g. a literal matching fallback. */
  public List<HttpWarning> getWarnings() {
    return warnings;
  }

  @Override
  public Boolean call() throws Exception {
    buildQueries(queryString(queries, fuzzyMatching));
    return true;
  }

  /** The QIDO-RS matching keys of the parameters that have a value. */
  static String queryString(List<DicomParam> queries, boolean fuzzyMatching) {
    QidoQuery query = new QidoQuery().fuzzyMatching(fuzzyMatching);
    for (DicomParam param : queries) {
      String[] values = param.getValues();
      if (values == null || Arrays.stream(values).noneMatch(StringUtil::hasText)) {
        continue;
      }
      if (Tag.PatientID == param.getTag()) {
        // IssuerOfPatientID filter (syntax like in HL7 with extension^^^root)
        String value = values[0];
        int beginIndex = value.indexOf("^^^");
        if (beginIndex > 0) {
          query.match(Tag.PatientID, value.substring(0, beginIndex));
          query.match(Tag.IssuerOfPatientID, value.substring(beginIndex + 3));
          continue;
        }
      }
      query.match(param.getTag(), values);
    }
    return query.toQueryString();
  }

  private void buildQueries(String queryString) {
    StringBuilder buf = new StringBuilder(getBaseUrl());
    buf.append("/studies?"); // NON-NLS
    buf.append(queryString);
    String includeFields = RsQueryResult.STUDY_QUERY;
    buf.append(queryString.isEmpty() ? includeFields.substring(1) : includeFields);
    buf.append(properties.getProperty(RsQueryParams.P_QUERY_EXT, ""));
    buf.append(properties.getProperty(RsQueryParams.P_PAGE_EXT, ""));
    try {
      LOGGER.debug(RsQueryResult.QIDO_REQUEST, buf);
      SearchResponse response =
          RsQueryResult.search(buf.toString(), authMethod, new URLParameters(queryHeaders));
      warnings = HttpWarning.parse(response.warnings());
      for (Attributes studyDataSet : response.matches()) {
        fillSeries(studyDataSet);
      }
    } catch (Exception e) {
      LOGGER.error("QIDO-RS", e);
    }
  }

  private void fillSeries(Attributes studyDataSet) {
    String studyUID = studyDataSet.getString(Tag.StudyInstanceUID);
    if (StringUtil.hasText(studyUID)) {
      //      StringBuilder buf = new StringBuilder(getBaseUrl());
      //      buf.append("/studies/"); // NON-NLS
      //      buf.append(studyInstanceUID);
      //      buf.append("/series?includefield="); // NON-NLS
      //      buf.append(RsQueryResult.SERIES_QUERY);
      //      buf.append(properties.getProperty(RsQueryParams.P_QUERY_EXT, ""));
      //
      //      try {
      //        LOGGER.debug(RsQueryResult.QIDO_REQUEST, buf);
      //        List<Attributes> series =
      //            RsQueryResult.parseJSON(buf.toString(), authMethod, new
      // URLParameters(queryHeaders));
      //        if (!series.isEmpty()) {
      RsQuery.populateDicomModel(dicomModel, studyDataSet);
      //        }
      //      } catch (Exception e) {
      //        LOGGER.error("QIDO-RS all series with studyInstanceUID {}", studyUID , e);
      //      }
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
              return getDisplayValue(this, Tag.PatientName)
                  + " ["
                  + getDisplayValue(this, Tag.PatientID)
                  + "] "
                  + getDisplayValue(this, Tag.PatientBirthDate)
                  + " "
                  + getDisplayValue(this, Tag.PatientSex);
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
        String format = tag.addGMTOffset(null, node);
        return tag.getFormattedTagValue(value, format);
      }
    }
    return StringUtil.EMPTY_STRING;
  }
}
