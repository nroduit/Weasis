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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

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
