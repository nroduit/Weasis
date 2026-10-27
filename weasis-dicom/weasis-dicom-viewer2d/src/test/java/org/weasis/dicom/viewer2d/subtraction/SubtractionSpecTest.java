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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.viewer2d.subtraction.SubtractionSpec.Dsa;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SubtractionSpecTest {

  @Test
  void a_spec_is_written_and_read_back_as_the_same_series() {
    SubtractionSpec spec = new SubtractionSpec("1.2.3", "1.2.3", 2, 1, 0.8, 10, true, true);
    SubtractionSpec back = SubtractionSpec.fromJson(spec.toJson().build());
    SubtractionSpec whole =
        SubtractionSpec.fromJson(SubtractionSpec.of("1.2.3", "1.2.4").toJson().build());
    assertAll(
        () -> assertEquals(spec, back),
        () -> assertEquals(spec.derivedSeriesUid(), back.derivedSeriesUid()),
        () -> assertNull(whole.phase()),
        () -> assertNull(whole.maskPhase()),
        () -> assertEquals(SubtractionSpec.of("1.2.3", "1.2.4"), whole));
  }

  @Test
  void the_phases_and_the_options_are_part_of_the_identity() {
    SubtractionSpec plain = SubtractionSpec.of("1.2.3", "1.2.4");
    assertAll(
        () -> assertNotEquals(plain.derivedSeriesUid(), plain.withPhase(2).derivedSeriesUid()),
        () ->
            assertNotEquals(
                plain.derivedSeriesUid(),
                new SubtractionSpec("1.2.3", "1.2.4", 0.5, 0, false, false).derivedSeriesUid()),
        () ->
            assertEquals(
                plain.derivedSeriesUid(), SubtractionSpec.of("1.2.3", "1.2.4").derivedSeriesUid()),
        () ->
            assertNotEquals(
                plain.derivedInstanceUid("1.2.3.1"), plain.derivedInstanceUid("1.2.3.2")));
  }

  @Test
  void a_dsa_mask_names_frames_of_the_run_and_travels_in_the_json() {
    Dsa averaged = new Dsa(List.of(2, 3, 4), null, 2, 40, 0.5, -0.25);
    SubtractionSpec spec = SubtractionSpec.ofRun("1.2.3", averaged, 0.8);
    SubtractionSpec back = SubtractionSpec.fromJson(spec.toJson().build());
    SubtractionSpec tid =
        SubtractionSpec.ofRun("1.2.3", new Dsa(List.of(), -2, null, null, 0, 0), 1);
    assertAll(
        () -> assertEquals(spec, back),
        () -> assertEquals("1.2.3", back.maskUid()),
        () -> assertEquals("2-4", back.dsa().label()),
        () -> assertEquals("1,3", Dsa.ofFrame(1).withFrames(List.of(3, 1)).label()),
        () -> assertEquals("REV_TID 2", tid.dsa().label()),
        () -> assertTrue(back.dsa().applies(2) && back.dsa().applies(40)),
        () -> assertFalse(back.dsa().applies(1) || back.dsa().applies(41)),
        () -> assertTrue(back.dsa().isShifted()),
        () ->
            assertNotEquals(
                spec.derivedSeriesUid(), spec.withDsa(averaged.withShift(0, 0)).derivedSeriesUid()),
        () -> assertNotEquals(spec.derivedSeriesUid(), tid.derivedSeriesUid()),
        () -> assertTrue(spec.derivationDescription().contains("mask frames 2-4")),
        () -> assertTrue(spec.derivationDescription().contains("shifted")),
        () ->
            assertEquals(
                spec,
                SubtractionSpec.fromJson(
                    SubtractionSpec.ofRun("1.2.3", averaged, 0.8).toJson().build())));
  }

  @Test
  void a_dsa_mask_is_frames_or_an_offset_of_the_run_itself() {
    assertAll(
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> new Dsa(List.of(), null, null, null, 0, 0)),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> new Dsa(List.of(1), 2, null, null, 0, 0)),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> new Dsa(List.of(), 0, null, null, 0, 0)),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> new Dsa(List.of(0), null, null, null, 0, 0)),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> new Dsa(List.of(1), null, 5, 2, 0, 0)),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () ->
                    new SubtractionSpec(
                        "1.2.3", "1.2.4", null, null, 1, 0, false, false, Dsa.ofFrame(1))),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () ->
                    new SubtractionSpec(
                        "1.2.3", "1.2.3", 2, null, 1, 0, false, false, Dsa.ofFrame(1))));
  }

  @Test
  void a_series_minus_itself_needs_two_different_phases() {
    assertAll(
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> SubtractionSpec.of("1.2.3", "1.2.3")),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new SubtractionSpec("1.2.3", "1.2.3", 1, 1, 1, 0, false, false)),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new SubtractionSpec("1.2.3", "1.2.4", 0, null, 1, 0, false, false)),
        () ->
            assertEquals(
                2, new SubtractionSpec("1.2.3", "1.2.3", 2, 1, 1, 0, false, false).phase()));
  }
}
