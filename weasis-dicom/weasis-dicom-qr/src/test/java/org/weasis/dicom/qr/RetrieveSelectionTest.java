/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests how the checked tree paths translate into studies and series to retrieve. */
class RetrieveSelectionTest {

  private static final String STUDY_1 = "1.2.3.1";
  private static final String STUDY_2 = "1.2.3.2";
  private static final String SERIES_1 = "1.2.3.1.1";
  private static final String SERIES_2 = "1.2.3.1.2";

  @Test
  void emptySelectionRetrievesNothing() {
    assertTrue(new RetrieveSelection().isEmpty());
  }

  @Test
  void aCheckedStudyRetrievesAllItsSeries() {
    RetrieveSelection selection = new RetrieveSelection();
    selection.addStudy(STUDY_1);

    assertAll(
        () -> assertFalse(selection.isEmpty()),
        () -> assertEquals(Set.of(STUDY_1), selection.getStudyUids()),
        () -> assertTrue(selection.getSeriesUids(STUDY_1).isEmpty()),
        () -> assertTrue(selection.contains(STUDY_1, "any.series.uid")));
  }

  @Test
  void checkedSeriesRestrictTheStudy() {
    RetrieveSelection selection = new RetrieveSelection();
    selection.addSeries(STUDY_1, SERIES_1);
    selection.addSeries(STUDY_1, SERIES_2);

    assertAll(
        () -> assertEquals(Set.of(STUDY_1), selection.getStudyUids()),
        () -> assertEquals(Set.of(SERIES_1, SERIES_2), selection.getSeriesUids(STUDY_1)),
        () -> assertTrue(selection.contains(STUDY_1, SERIES_1)),
        () -> assertFalse(selection.contains(STUDY_1, "1.2.3.1.9")));
  }

  @Test
  void aCheckedStudyWinsOverItsSeriesWhateverTheOrder() {
    RetrieveSelection studyFirst = new RetrieveSelection();
    studyFirst.addStudy(STUDY_1);
    studyFirst.addSeries(STUDY_1, SERIES_1);

    RetrieveSelection seriesFirst = new RetrieveSelection();
    seriesFirst.addSeries(STUDY_1, SERIES_1);
    seriesFirst.addStudy(STUDY_1);

    assertAll(
        () -> assertTrue(studyFirst.getSeriesUids(STUDY_1).isEmpty()),
        () -> assertTrue(studyFirst.contains(STUDY_1, "other.series")),
        () -> assertTrue(seriesFirst.getSeriesUids(STUDY_1).isEmpty()),
        () -> assertTrue(seriesFirst.contains(STUDY_1, "other.series")));
  }

  @Test
  void studiesKeepTheirSelectionOrderAndStayIndependent() {
    RetrieveSelection selection = new RetrieveSelection();
    selection.addSeries(STUDY_1, SERIES_1);
    selection.addStudy(STUDY_2);

    assertAll(
        () -> assertEquals(List.of(STUDY_1, STUDY_2), List.copyOf(selection.getStudyUids())),
        () -> assertEquals(Set.of(SERIES_1), selection.getSeriesUids(STUDY_1)),
        () -> assertTrue(selection.getSeriesUids(STUDY_2).isEmpty()),
        () -> assertFalse(selection.contains(STUDY_1, SERIES_2)),
        () -> assertTrue(selection.contains(STUDY_2, SERIES_2)));
  }

  @Test
  void anUnknownStudyMatchesNothing() {
    RetrieveSelection selection = new RetrieveSelection();
    selection.addSeries(STUDY_1, SERIES_1);

    // No restriction recorded, so the study would be retrieved whole if it were selected
    assertTrue(selection.getSeriesUids(STUDY_2).isEmpty());
  }
}
