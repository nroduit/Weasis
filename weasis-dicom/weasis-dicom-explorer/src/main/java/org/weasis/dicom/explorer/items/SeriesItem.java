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

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesImporter;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.RejectedKOSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;

/**
 * An image series of a study as the explorer lists it.
 *
 * @param series the series
 * @param study its study
 * @param seriesNumber Series Number, null when absent
 * @param splitNumber index of the sub-series when the loader split a 4D acquisition, null otherwise
 * @param imageCount images (or frames of a multi-frame) present on this machine
 * @param downloadState whether the pixel data is here, coming, or neither
 */
public record SeriesItem(
    DicomSeries series,
    MediaSeriesGroup study,
    Integer seriesNumber,
    Integer splitNumber,
    int imageCount,
    DownloadState downloadState)
    implements ExplorerItem {

  public SeriesItem {
    Objects.requireNonNull(series);
    Objects.requireNonNull(downloadState);
  }

  /** Reads the item of a series from the series itself. */
  public static SeriesItem of(DicomSeries series, MediaSeriesGroup study) {
    return new SeriesItem(
        series,
        study,
        TagD.getTagValue(series, Tag.SeriesNumber, Integer.class),
        TagD.getTagValue(series, TagD.SplitSeriesNumber, Integer.class),
        series.size(null),
        downloadStateOf(series));
  }

  /** The download state of a series, read from its loader. */
  public static DownloadState downloadStateOf(DicomSeries series) {
    SeriesImporter loader = series.getSeriesLoader();
    if (loader == null) {
      return DownloadState.COMPLETE;
    }
    return loader.isStopped() ? DownloadState.PENDING : DownloadState.DOWNLOADING;
  }

  /**
   * Instances the attached transfer announces (the series-level count of a query, or the manifest
   * list), 0 when unknown; what the thumbnail and the panel show behind the images present.
   */
  public int expectedImageCount() {
    Integer n = TagD.getTagValue(series, Tag.NumberOfSeriesRelatedInstances, Integer.class);
    return n == null ? 0 : n;
  }

  /**
   * Instances of this series that a rejection note (IOCM) rejects: taken out of the series, never
   * displayed, counted here so that the row can say so; 0 when none or when the series has no image
   * yet.
   */
  public int rejectedImageCount() {
    if (DicomModel.getPatientPseudoUID(series) == null) {
      return 0;
    }
    String uid = seriesUID();
    Set<String> rejected = new HashSet<>();
    for (RejectedKOSpecialElement note : DicomModel.getRejectionKoSpecialElements(series)) {
      rejected.addAll(note.getReferencedSOPInstanceUIDSet(uid));
    }
    return rejected.size();
  }

  /** True when the loader split the acquisition and this is one of its sub-series. */
  public boolean isSplit() {
    return splitNumber != null;
  }

  /** The handle the commands use: the subseries id of a split, else the Series Instance UID. */
  public String id() {
    return String.valueOf(series.getTagValue(TagW.SubseriesInstanceUID));
  }

  @Override
  public String seriesUID() {
    return TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
  }

  @Override
  public String label() {
    return TagD.getTagValue(series, Tag.SeriesDescription, String.class);
  }

  @Override
  public String modality() {
    return TagD.getTagValue(series, Tag.Modality, String.class);
  }

  @Override
  public LocalDateTime date() {
    return TagD.dateTime(Tag.SeriesDate, Tag.SeriesTime, series);
  }

  @Override
  public ResourceIconPath icon() {
    return null;
  }
}
