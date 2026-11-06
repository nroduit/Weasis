/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class WaveComparisonTest {

  @Test
  void the_previous_ecg_is_the_latest_one_acquired_before_the_current() {
    List<String> others = List.of("20160916120000", "20150101080000", "20161124093000");
    assertEquals("20160916120000", WaveComparison.previousOf(others, "20161101000000", s -> s));
    assertEquals("20161124093000", WaveComparison.previousOf(others, "20170101000000", s -> s));
  }

  @Test
  void without_an_earlier_ecg_the_latest_other_one_is_taken() {
    List<String> others = List.of("20160916120000", "20161124093000");
    assertEquals("20161124093000", WaveComparison.previousOf(others, "20140101000000", s -> s));
    assertNull(WaveComparison.previousOf(List.<String>of(), "20140101000000", s -> s));
  }
}
