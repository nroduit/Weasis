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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class IntervalSummaryTest {

  private static Caliper time(
      int start, int stop, String label, double duration, int beats, Double rr) {
    Caliper c = Caliper.time(start, stop, label);
    c.setResults(duration, 0.1, 1.0, beats, rr);
    return c;
  }

  @Test
  void intervals_come_from_the_labelled_calipers_and_qtc_from_qt_and_rr() {
    List<Caliper> calipers =
        List.of(
            time(0, 400, "RR", 0.8, 0, null),
            time(0, 80, "PR", 0.16, 0, null),
            time(0, 45, "QRS", 0.09, 0, null),
            time(0, 200, "QT", 0.40, 0, null),
            time(0, 210, "QT", 0.42, 0, null));
    IntervalSummary s = IntervalSummary.of(calipers, 90.0);
    assertEquals(0.8, s.rr(), 1e-9);
    assertEquals(IntervalSummary.SOURCE_CALIPER, s.rrSource());
    assertEquals(75, s.heartRate(), 1e-9);
    assertEquals(0.16, s.pr(), 1e-9);
    assertEquals(0.09, s.qrs(), 1e-9);
    assertEquals(0.41, s.qt(), 1e-9); // mean of the two QT calipers
    assertEquals(0.41 / Math.sqrt(0.8), s.qtcBazett(), 1e-9);
    assertEquals(0.41 / Math.cbrt(0.8), s.qtcFridericia(), 1e-9);
  }

  @Test
  void rr_falls_back_to_the_beats_of_a_caliper_then_to_the_estimate() {
    Caliper threeBeats = time(0, 1000, null, 2.0, 3, 0.75);
    IntervalSummary s = IntervalSummary.of(List.of(threeBeats), 90.0);
    assertEquals(0.75, s.rr(), 1e-9);
    assertEquals(IntervalSummary.SOURCE_BEATS, s.rrSource());
    assertEquals(3, s.beats());

    s = IntervalSummary.of(List.of(time(0, 10, null, 0.02, 0, null)), 90.0);
    assertEquals(60 / 90.0, s.rr(), 1e-9);
    assertEquals(IntervalSummary.SOURCE_ESTIMATE, s.rrSource());

    s = IntervalSummary.of(List.of(), null);
    assertTrue(s.isEmpty());
    assertNull(s.heartRate());
  }

  @Test
  void amplitude_and_empty_calipers_do_not_count() {
    Caliper amplitude = Caliper.amplitude(10, 0.1, 0.3, "QT");
    amplitude.setResults(null, 0.2, null, 0, null);
    Caliper empty = Caliper.time(5, 5, "QT");
    IntervalSummary s = IntervalSummary.of(List.of(amplitude, empty), null);
    assertNull(s.qt());
    assertTrue(s.isEmpty());
  }
}
