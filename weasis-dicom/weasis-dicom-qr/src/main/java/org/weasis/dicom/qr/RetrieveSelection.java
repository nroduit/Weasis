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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Studies checked in the query result, each restricted to the checked series. A study mapped to an
 * empty set is retrieved as a whole, which is what happens when it is checked itself or when its
 * series were never expanded in the tree.
 */
public class RetrieveSelection {

  private final Map<String, Set<String>> seriesByStudy = new LinkedHashMap<>();

  /** Selects the whole study, dropping any series restriction recorded for it. */
  public void addStudy(String studyUid) {
    seriesByStudy.put(studyUid, Set.of());
  }

  /** Restricts the study to the given series, unless the whole study is already selected. */
  public void addSeries(String studyUid, String seriesUid) {
    Set<String> series = seriesByStudy.get(studyUid);
    if (series == null) {
      series = new LinkedHashSet<>();
      seriesByStudy.put(studyUid, series);
    } else if (series.isEmpty()) {
      return; // The whole study is already selected
    }
    series.add(seriesUid);
  }

  /** Studies to retrieve, in selection order. */
  public Set<String> getStudyUids() {
    return seriesByStudy.keySet();
  }

  /** Series to retrieve for the study, empty when the whole study is selected. */
  public Set<String> getSeriesUids(String studyUid) {
    return seriesByStudy.getOrDefault(studyUid, Set.of());
  }

  /** Tells whether the series belongs to the retrieve, the whole study counting as a match. */
  public boolean contains(String studyUid, String seriesUid) {
    Set<String> series = getSeriesUids(studyUid);
    return series.isEmpty() || series.contains(seriesUid);
  }

  public boolean isEmpty() {
    return seriesByStudy.isEmpty();
  }
}
