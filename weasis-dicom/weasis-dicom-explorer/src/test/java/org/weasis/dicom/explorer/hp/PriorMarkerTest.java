/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.codec.hp.HangingPlan.Match;
import org.weasis.dicom.codec.hp.WeasisExtensions.ViewerType;

class PriorMarkerTest {

  private static MediaSeriesGroup study(String uid, LocalDate date) {
    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    when(study.getTagValue(TagD.get(Tag.StudyInstanceUID))).thenReturn(uid);
    when(study.getTagValue(TagD.get(Tag.StudyDate))).thenReturn(date);
    return study;
  }

  private static CellPlan cell(String study, int prior) {
    return new CellPlan(
        2,
        7,
        "Prior RCC",
        10,
        "1.2.9",
        Match.EXACT,
        1,
        ViewerType.VIEWER_2D,
        List.of(),
        List.of(),
        study,
        prior);
  }

  static final MediaSeriesGroup CURRENT = study("1.1", LocalDate.of(2007, 1, 19)); // NON-NLS
  static final MediaSeriesGroup PRIOR = study("1.2", LocalDate.of(2006, 1, 18)); // NON-NLS

  @Test
  void aCellShowingAnOlderStudyIsMarkedWithTheRankOfThePlan() {
    PriorMarker marker = PriorMarker.of(CURRENT, PRIOR, cell("1.2", 1)).orElseThrow(); // NON-NLS
    assertAll(
        () -> assertEquals(1, marker.rank()),
        () -> assertEquals(LocalDate.of(2006, 1, 18), marker.date()),
        () -> assertEquals(new PriorMarker.Interval(1, 'y'), marker.interval()));
  }

  @Test
  void aSeriesTheUserPutInACellIsMarkedWithoutRank() {
    CellPlan corrected =
        new CellPlan(
            0,
            1,
            "RCC",
            1,
            "1.2.9",
            Match.CORRECTED,
            0,
            ViewerType.VIEWER_2D,
            List.of(),
            List.of());
    PriorMarker marker = PriorMarker.of(CURRENT, PRIOR, corrected).orElseThrow();
    assertEquals(0, marker.rank());
  }

  @Test
  void theCurrentStudyALaterOneOrAnUndatedOneIsNotMarked() {
    MediaSeriesGroup later = study("1.4", LocalDate.of(2008, 1, 20)); // NON-NLS
    MediaSeriesGroup undated = study("1.5", null); // NON-NLS
    assertAll(
        () -> assertTrue(PriorMarker.of(CURRENT, CURRENT, null).isEmpty()),
        () -> assertTrue(PriorMarker.of(CURRENT, later, null).isEmpty()),
        () -> assertTrue(PriorMarker.of(CURRENT, undated, null).isEmpty()),
        () -> assertTrue(PriorMarker.of(null, PRIOR, null).isEmpty()));
  }

  @Test
  void theIntervalUsesItsLargestUnit() {
    MediaSeriesGroup months = study("1.6", LocalDate.of(2006, 8, 1)); // NON-NLS
    MediaSeriesGroup days = study("1.7", LocalDate.of(2007, 1, 2)); // NON-NLS
    assertAll(
        () ->
            assertEquals(
                new PriorMarker.Interval(5, 'm'),
                PriorMarker.of(CURRENT, months, null).orElseThrow().interval()),
        () ->
            assertEquals(
                new PriorMarker.Interval(17, 'd'),
                PriorMarker.of(CURRENT, days, null).orElseThrow().interval()));
  }
}
