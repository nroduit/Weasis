/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.core.bean;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultLocale;

class SeriesGroupTest {

  private static final LocalDateTime today = LocalDateTime.of(2016, 5, 12, 14, 25);
  public static final String TEST_SERIES = "test series 3"; // NON-NLS

  private static SeriesGroup s1, s2, s3;

  @BeforeAll
  static void setUp() {
    s1 = new SeriesGroup();
    s2 = new SeriesGroup(today);
    s3 = new SeriesGroup(TEST_SERIES);
  }

  @Test
  void testToString() {
    assertEquals("Other", s1.toString());
    assertEquals(
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(today), s2.toString());
    assertEquals(TEST_SERIES, s3.toString());
  }

  @Test
  void testGetters() {
    assertEquals(SeriesGroup.Type.IMAGE, s1.getType());
    assertEquals(SeriesGroup.Type.IMAGE_DATE, s2.getType());
    assertEquals(SeriesGroup.Type.IMAGE_NAME, s3.getType());
  }

  @Test
  @DefaultLocale(language = "fr", country = "CH")
  void testSort() {
    SeriesGroup s1 = new SeriesGroup(today);
    SeriesGroup s2 = new SeriesGroup();
    SeriesGroup s3 = new SeriesGroup("series3"); // NON-NLS
    assetSorted(new SeriesGroup[] {s3, s2, s1}, new SeriesGroup[] {s1, s2, s3});
    assetSorted(new SeriesGroup[] {s2, s3, s1}, new SeriesGroup[] {s1, s2, s3});

    SeriesGroup s4 = new SeriesGroup(today.minusDays(1));
    assetSorted(new SeriesGroup[] {s3, s2, s1, s4}, new SeriesGroup[] {s4, s1, s2, s3});

    SeriesGroup s5 = new SeriesGroup("series2"); // NON-NLS
    assetSorted(new SeriesGroup[] {s3, s2, s1, s4, s5}, new SeriesGroup[] {s4, s1, s2, s5, s3});

    SeriesGroup s6 = new SeriesGroup("2015");
    assetSorted(
        new SeriesGroup[] {s3, s2, s1, s4, s5, s6}, new SeriesGroup[] {s4, s1, s6, s2, s5, s3});
  }

  private void assetSorted(SeriesGroup[] input, SeriesGroup[] expected) {
    assertArrayEquals(expected, Arrays.stream(input).sorted().toArray(SeriesGroup[]::new));
  }
}
