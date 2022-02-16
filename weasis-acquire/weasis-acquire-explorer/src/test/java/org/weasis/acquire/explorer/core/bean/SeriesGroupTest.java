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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.LocalUtil;

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
    assertThat(s1.toString()).hasToString("Other"); // NON-NLS
    assertThat(s2.toString()).hasToString(LocalUtil.getDateTimeFormatter().format(today));
    assertThat(s3.toString()).hasToString(TEST_SERIES);
  }

  @Test
  void testGetters() {
    assertThat(s1.getType()).isEqualTo(SeriesGroup.Type.NONE);
    assertThat(s2.getType()).isEqualTo(SeriesGroup.Type.DATE);
    assertThat(s3.getType()).isEqualTo(SeriesGroup.Type.NAME);
  }

  @Test
  void testSort() {
    SeriesGroup s1 = new SeriesGroup();
    SeriesGroup s2 = new SeriesGroup(today);
    SeriesGroup s3 = new SeriesGroup("series3"); // NON-NLS
    assetSorted(new SeriesGroup[] {s3, s2, s1}, new SeriesGroup[] {s1, s2, s3});
    assetSorted(new SeriesGroup[] {s2, s3, s1}, new SeriesGroup[] {s1, s2, s3});

    SeriesGroup s4 = new SeriesGroup(today.minusDays(1));
    assetSorted(new SeriesGroup[] {s3, s2, s1, s4}, new SeriesGroup[] {s1, s4, s2, s3});

    SeriesGroup s5 = new SeriesGroup("series2"); // NON-NLS
    assetSorted(new SeriesGroup[] {s3, s2, s1, s4, s5}, new SeriesGroup[] {s1, s4, s2, s5, s3});

    SeriesGroup s6 = new SeriesGroup("2015");
    assetSorted(
        new SeriesGroup[] {s3, s2, s1, s4, s5, s6}, new SeriesGroup[] {s1, s4, s2, s6, s5, s3});
  }

  private void assetSorted(SeriesGroup[] input, SeriesGroup[] expected) {
    assertThat(Arrays.stream(input).sorted().toArray(SeriesGroup[]::new)).isEqualTo(expected);
  }
}
