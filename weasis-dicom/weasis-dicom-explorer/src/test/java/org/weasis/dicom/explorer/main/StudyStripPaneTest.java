/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.explorer.main.StudyStripPane.Interval;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StudyStripPaneTest {

  private static final LocalDate START = LocalDate.of(2020, 1, 15);

  @Test
  void interval_takes_the_most_telling_unit() {
    assertAll(
        () -> assertEquals(new Interval(0, 0, 'd'), Interval.between(START, START)),
        () -> assertEquals(new Interval(44, 44, 'd'), Interval.between(START, START.plusDays(44))),
        () -> assertEquals('m', Interval.between(START, START.plusDays(45)).unit()),
        () -> assertEquals(23, Interval.between(START, START.plusMonths(23)).count()),
        () -> assertEquals(new Interval(731, 2, 'y'), Interval.between(START, START.plusYears(2))),
        () -> assertEquals(9, Interval.between(START, START.plusYears(9).plusMonths(11)).count()));
  }

  @Test
  void gap_grows_with_time_on_a_compressed_scale() {
    int sameDay = StudyStripPane.gapForDays(0);
    int week = StudyStripPane.gapForDays(7);
    int month = StudyStripPane.gapForDays(30);
    int year = StudyStripPane.gapForDays(365);
    int decade = StudyStripPane.gapForDays(3650);
    assertAll(
        () -> assertEquals(6, sameDay),
        () -> assertTrue(sameDay < week && week < month && month < year && year < decade),
        // ten times the time is far less than ten times the room
        () -> assertTrue(decade < 2 * year),
        () -> assertEquals(6, StudyStripPane.gapForDays(-3)));
  }
}
