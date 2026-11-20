/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.items;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomWebOrigin;

/**
 * What an action of a plugin applies to: a study as a whole, or some of its series. The explorer
 * builds it from what the user points at, so a plugin never reads the selection itself.
 *
 * @param model the model holding the study
 * @param study the study
 * @param series the series the action applies to, in the order of the explorer; every image series
 *     of the study when {@code wholeStudy}
 * @param wholeStudy true when the target is the study itself, not a choice of series
 */
public record ExplorerTarget(
    DicomModel model, MediaSeriesGroup study, List<DicomSeries> series, boolean wholeStudy) {

  public ExplorerTarget {
    Objects.requireNonNull(model);
    Objects.requireNonNull(study);
    series = List.copyOf(series);
  }

  /** The whole study: every image series it holds. */
  public static ExplorerTarget ofStudy(DicomModel model, MediaSeriesGroup study) {
    return new ExplorerTarget(model, study, seriesOf(model, study), true);
  }

  /**
   * The series of {@code selection} that belong to the study, empty when none does or when they are
   * all the series of the study (the whole study says it better).
   */
  public static Optional<ExplorerTarget> ofSelection(
      DicomModel model, MediaSeriesGroup study, List<DicomSeries> selection) {
    List<DicomSeries> all = seriesOf(model, study);
    List<DicomSeries> chosen = all.stream().filter(selection::contains).toList();
    if (chosen.isEmpty() || chosen.size() == all.size()) {
      return Optional.empty();
    }
    return Optional.of(new ExplorerTarget(model, study, chosen, false));
  }

  private static List<DicomSeries> seriesOf(DicomModel model, MediaSeriesGroup study) {
    return StudyContents.of(model, study).series().stream().map(SeriesItem::series).toList();
  }

  /** The patient of the study, null when the study left the model. */
  public MediaSeriesGroup patient() {
    return model.getParent(study, DicomModel.patient);
  }

  public String studyUID() {
    return TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
  }

  /** The Series Instance UIDs of the target, without duplicates (a split series counts once). */
  public List<String> seriesUIDs() {
    return series.stream()
        .map(s -> TagD.getTagValue(s, Tag.SeriesInstanceUID, String.class))
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  /** How many image series the study holds, for a label such as "2 of 9". */
  public int studySeriesCount() {
    return wholeStudy ? series.size() : seriesOf(model, study).size();
  }

  /** The DICOMweb server the study was loaded from, empty for another source. */
  public Optional<DicomWebOrigin> origin() {
    return Optional.ofNullable(DicomWebOrigin.of(model, study));
  }
}
