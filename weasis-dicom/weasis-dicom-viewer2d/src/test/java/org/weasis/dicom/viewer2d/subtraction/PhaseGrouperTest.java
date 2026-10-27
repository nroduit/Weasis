/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.subtraction.PhaseGrouper.Phase;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.Pairing;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PhaseGrouperTest {

  private static final String UID = "1.2.3.20"; // NON-NLS

  @Test
  void a_series_without_temporal_marks_is_one_phase_of_itself() {
    DicomSeries series = SyntheticSeries.mr(UID, 4, 0);
    List<Phase> phases = PhaseGrouper.phases(series);
    assertAll(
        () -> assertEquals(1, phases.size()),
        () -> assertEquals(4, phases.getFirst().count()),
        () -> assertSame(series, phases.getFirst().series()),
        () -> assertFalse(PhaseGrouper.isDynamic(series)),
        () -> assertSame(series, PhaseGrouper.operand(series, null)));
  }

  @Test
  void the_temporal_position_identifier_splits_the_phases_in_its_order() {
    Attributes[] images = new Attributes[6];
    for (int i = 0; i < 6; i++) {
      images[i] = SyntheticSeries.slice(UID, "MR", i + 1, 2.0 * (i % 3), 1.0, 256, null); // NON-NLS
      images[i].setInt(Tag.TemporalPositionIdentifier, VR.IS, i < 3 ? 2 : 1);
    }
    DicomSeries series = SyntheticSeries.series(images);
    List<Phase> phases = PhaseGrouper.phases(series);
    assertAll(
        () -> assertEquals(2, phases.size()),
        () -> assertEquals(3, phases.get(0).count()),
        () -> assertEquals(4, instance(phases.get(0), 0)),
        () -> assertEquals(1, instance(phases.get(1), 0)),
        () -> assertTrue(PhaseGrouper.isDynamic(series)));
  }

  @Test
  void repeated_positions_give_one_phase_per_acquisition_ordered_by_time() {
    Attributes[] images = new Attributes[6];
    for (int i = 0; i < 6; i++) {
      images[i] = SyntheticSeries.slice(UID, "MR", i + 1, 2.0 * (i % 3), 1.0, 256, null); // NON-NLS
      // The later acquisition comes first in the series: the time orders the phases
      images[i].setString(Tag.AcquisitionTime, VR.TM, i < 3 ? "120500" : "120000"); // NON-NLS
    }
    DicomSeries series = SyntheticSeries.series(images);
    List<Phase> phases = PhaseGrouper.phases(series);
    assertAll(
        () -> assertEquals(2, phases.size()),
        () -> assertEquals(LocalTime.of(12, 0), phases.get(0).time()),
        () -> assertEquals(4, instance(phases.get(0), 0)),
        () -> assertEquals(LocalTime.of(12, 5), phases.get(1).time()),
        () -> assertEquals(1, instance(phases.get(1), 0)));
  }

  @Test
  void positions_acquired_an_uneven_number_of_times_are_not_phases() {
    Attributes[] images = new Attributes[5];
    for (int i = 0; i < 5; i++) {
      images[i] = SyntheticSeries.slice(UID, "MR", i + 1, 2.0 * (i % 3), 1.0, 256, null); // NON-NLS
    }
    assertEquals(1, PhaseGrouper.phases(SyntheticSeries.series(images)).size());
  }

  @Test
  void a_phase_is_a_series_that_pairs_with_another_phase_and_remembers_its_parent() {
    Attributes[] images = new Attributes[6];
    for (int i = 0; i < 6; i++) {
      images[i] = SyntheticSeries.slice(UID, "MR", i + 1, 2.0 * (i % 3), 1.0, 256, null); // NON-NLS
      images[i].setInt(Tag.TemporalPositionIdentifier, VR.IS, i < 3 ? 1 : 2);
    }
    DicomSeries series = SyntheticSeries.series(images);
    series.setTag(TagD.get(Tag.SeriesDescription), "DCE"); // NON-NLS
    PhaseGrouper.phases(series);
    var first = PhaseGrouper.operand(series, 1);
    var second = PhaseGrouper.operand(series, 2);
    assertAll(
        () -> assertInstanceOf(Pairing.class, SubtractionCompatibility.pair(second, first)),
        () -> assertEquals(UID, SubtractionCommands.uidOf(first)),
        () -> assertEquals("DCE p2", TagD.getTagValue(second, Tag.SeriesDescription, String.class)),
        () -> assertSame(series, PhaseGrouper.phaseOf(second).orElseThrow().parent()),
        () -> assertEquals(2, PhaseGrouper.phaseOf(second).orElseThrow().index()),
        () -> assertTrue(PhaseGrouper.phaseOf(series).isEmpty()),
        () -> assertSame(second, PhaseGrouper.operand(series, 2)));
  }

  private static int instance(Phase phase, int i) {
    return TagD.getTagValue(phase.images().get(i), Tag.InstanceNumber, Integer.class);
  }
}
