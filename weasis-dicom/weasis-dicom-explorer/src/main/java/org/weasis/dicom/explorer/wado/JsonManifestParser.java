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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.utils.SeriesInstanceList;
import org.weasis.dicom.mf.ArcParameters;
import org.weasis.dicom.mf.ViewerMessage;
import org.weasis.dicom.mf.WadoParameters;

/**
 * Parses the JSON manifest produced by {@code org.weasis.dicom.mf.JsonManifestSerializer}. The
 * structure mirrors the modern (2.5) XML manifest, so node building is shared with {@link
 * XmlManifestParser} through {@link ManifestModelBuilder}.
 */
final class JsonManifestParser {

  private JsonManifestParser() {}

  static void parse(Path jsonFile, ReaderParams params) throws IOException {
    try (Reader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8);
        JsonReader jsonReader = Json.createReader(reader)) {
      JsonObject manifest = jsonReader.readObject().getJsonObject(ArcParameters.TAG_DOCUMENT_ROOT);
      if (manifest != null) {
        for (JsonObject arcQuery : children(manifest, ArcParameters.TAG_ARC_QUERY)) {
          readArcQuery(arcQuery, params);
        }
      }
    }
  }

  private static void readArcQuery(JsonObject arcQuery, ReaderParams params) {
    WadoParameters wadoParameters =
        ManifestModelBuilder.buildArcQueryParameters(new JsonAttributeSource(arcQuery), params);

    for (JsonObject tag : children(arcQuery, ArcParameters.TAG_HTTP_TAG)) {
      ManifestModelBuilder.addHttpTag(new JsonAttributeSource(tag), wadoParameters);
    }

    for (JsonObject message : children(arcQuery, ViewerMessage.TAG_DOCUMENT_MSG)) {
      ManifestModelBuilder.showViewerMessage(new JsonAttributeSource(message));
    }

    Set<MediaSeriesGroup> patients = new LinkedHashSet<>();
    for (JsonObject patient : children(arcQuery, Level.PATIENT.getTagName())) {
      patients.add(readPatient(patient, params, wadoParameters));
    }

    ManifestModelBuilder.focusUniquePatient(patients);
    ManifestModelBuilder.startDownloads(params, wadoParameters);
  }

  private static MediaSeriesGroup readPatient(
      JsonObject patient, ReaderParams params, WadoParameters wadoParameters) {
    MediaSeriesGroup patientNode =
        ManifestModelBuilder.buildPatient(new JsonAttributeSource(patient), params, wadoParameters);
    List<JsonObject> studies = children(patient, Level.STUDY.getTagName());
    if (ManifestCompletion.isPartialLevel(wadoParameters, !studies.isEmpty())) {
      ManifestCompletion.completePatient(params, wadoParameters, patientNode);
    } else {
      for (JsonObject study : studies) {
        readStudy(study, params, patientNode, wadoParameters);
      }
    }
    return patientNode;
  }

  private static void readStudy(
      JsonObject study,
      ReaderParams params,
      MediaSeriesGroup patient,
      WadoParameters wadoParameters) {
    MediaSeriesGroup studyNode =
        ManifestModelBuilder.buildStudy(new JsonAttributeSource(study), params, patient);
    List<JsonObject> series = children(study, Level.SERIES.getTagName());
    if (ManifestCompletion.isPartialLevel(wadoParameters, !series.isEmpty())) {
      ManifestCompletion.completeStudy(params, wadoParameters, patient, studyNode);
    } else {
      for (JsonObject s : series) {
        readSeries(s, params, patient, studyNode, wadoParameters);
      }
    }
  }

  private static void readSeries(
      JsonObject series,
      ReaderParams params,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      WadoParameters wadoParameters) {
    DicomSeries dicomSeries =
        ManifestModelBuilder.buildSeries(
            new JsonAttributeSource(series), params, study, wadoParameters);
    if (dicomSeries == null) {
      return;
    }

    SeriesInstanceList seriesInstanceList = ManifestModelBuilder.getSeriesInstanceList(dicomSeries);
    for (JsonObject instance : children(series, Level.INSTANCE.getTagName())) {
      ManifestModelBuilder.addSopInstance(new JsonAttributeSource(instance), seriesInstanceList);
    }
    ManifestModelBuilder.completeSeries(params, patient, study, dicomSeries, seriesInstanceList);
  }

  /** Returns the child objects held under {@code key}, tolerating a single object or an array. */
  static List<JsonObject> children(JsonObject parent, String key) {
    JsonValue value = parent.get(key);
    if (value == null) {
      return List.of();
    }
    return switch (value.getValueType()) {
      case OBJECT -> List.of(value.asJsonObject());
      case ARRAY -> {
        List<JsonObject> result = new ArrayList<>();
        for (JsonValue item : value.asJsonArray()) {
          if (item.getValueType() == JsonValue.ValueType.OBJECT) {
            result.add(item.asJsonObject());
          }
        }
        yield result;
      }
      default -> List.of();
    };
  }
}
