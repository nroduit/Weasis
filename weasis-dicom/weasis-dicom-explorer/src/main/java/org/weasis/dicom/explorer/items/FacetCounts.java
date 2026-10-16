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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.explorer.DicomModel;

/**
 * How many image series of a patient each value of a filter facet keeps: what a filter menu or an
 * agent can choose from before setting the facets. Built over {@link StudyContents}, so the hidden
 * series are left out and each value is counted the way the filter tests it.
 *
 * @param total the image series of the patient
 * @param modalities series per modality, in alphabetical order
 * @param objectKinds series referenced by at least one object of the kind
 * @param interestAny series with at least one image of interest
 * @param interestSources series with an image of interest pointed at by the source
 * @param downloadStates series per download state
 */
public record FacetCounts(
    int total,
    Map<String, Integer> modalities,
    Map<ObjectKind, Integer> objectKinds,
    int interestAny,
    Map<InterestSource, Integer> interestSources,
    Map<DownloadState, Integer> downloadStates) {

  public static final FacetCounts EMPTY =
      new FacetCounts(0, Map.of(), Map.of(), 0, Map.of(), Map.of());

  public FacetCounts {
    modalities = Collections.unmodifiableMap(new TreeMap<>(modalities));
    objectKinds = unmodifiable(ObjectKind.class, objectKinds);
    interestSources = unmodifiable(InterestSource.class, interestSources);
    downloadStates = unmodifiable(DownloadState.class, downloadStates);
  }

  /** Counts the facet values over the image series of the patient; empty without a patient. */
  public static FacetCounts of(DicomModel model, MediaSeriesGroup patient) {
    if (model == null || patient == null) {
      return EMPTY;
    }
    int total = 0;
    int interestAny = 0;
    Map<String, Integer> modalities = new TreeMap<>();
    Map<ObjectKind, Integer> kinds = new EnumMap<>(ObjectKind.class);
    Map<InterestSource, Integer> sources = new EnumMap<>(InterestSource.class);
    Map<DownloadState, Integer> states = new EnumMap<>(DownloadState.class);
    for (MediaSeriesGroup study : StudyContents.studiesOf(model, patient)) {
      for (SeriesItem item : StudyContents.of(model, study).series()) {
        total++;
        if (StringUtil.hasText(item.modality())) {
          modalities.merge(item.modality(), 1, Integer::sum);
        }
        for (ObjectKind kind : StudyContents.kindsReferencing(item.seriesUID())) {
          kinds.merge(kind, 1, Integer::sum);
        }
        Interest interest = Interest.of(item.series());
        if (!interest.isEmpty()) {
          interestAny++;
          for (InterestSource source : interest.sources()) {
            sources.merge(source, 1, Integer::sum);
          }
        }
        states.merge(item.downloadState(), 1, Integer::sum);
      }
    }
    return new FacetCounts(total, modalities, kinds, interestAny, sources, states);
  }

  public boolean isEmpty() {
    return total == 0;
  }

  /** True when some series are not downloaded or being downloaded. */
  public boolean hasTransfers() {
    return downloadStates.getOrDefault(DownloadState.PENDING, 0) > 0
        || downloadStates.getOrDefault(DownloadState.DOWNLOADING, 0) > 0;
  }

  private static <K extends Enum<K>> Map<K, Integer> unmodifiable(
      Class<K> type, Map<K, Integer> map) {
    Map<K, Integer> copy = new EnumMap<>(type);
    copy.putAll(map);
    return Collections.unmodifiableMap(copy);
  }
}
