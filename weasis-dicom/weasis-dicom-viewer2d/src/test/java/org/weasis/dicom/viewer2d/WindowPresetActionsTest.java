/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.display.WindowPreset;
import org.weasis.dicom.codec.display.WindowPreset.When;
import org.weasis.opencv.op.lut.LutShape;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WindowPresetActionsTest {

  @Test
  void the_saved_preset_carries_the_current_values_and_a_user_id() {
    WindowPreset preset =
        WindowPresetActions.buildPreset("Soft tissue", "ct", 400, 40, LutShape.SIGMOID, '5');

    assertAll(
        () -> assertEquals("user.ct.soft-tissue", preset.id()),
        () -> assertEquals("Soft tissue", preset.name()),
        () -> assertEquals(Set.of("CT"), preset.modalities()),
        () -> assertEquals(400.0, preset.window()),
        () -> assertEquals(40.0, preset.level()),
        () -> assertEquals(LutShape.SIGMOID, preset.shape()),
        () -> assertEquals('5', preset.key()),
        () -> assertEquals(When.DEFAULT, preset.when()),
        () -> assertTrue(preset.modalities().contains("CT")));
  }

  @Test
  void several_modality_codes_are_split() {
    WindowPreset preset =
        WindowPresetActions.buildPreset("Soft", "mr ct", 400, 40, LutShape.LINEAR, null);

    assertAll(
        () -> assertEquals("user.ct-mr.soft", preset.id()),
        () -> assertEquals(Set.of("CT", "MR"), preset.modalities()));
  }

  @Test
  void without_a_modality_the_preset_applies_to_every_modality() {
    WindowPreset preset =
        WindowPresetActions.buildPreset(" Wide ", "  ", 4000, 0, LutShape.LINEAR, null);

    assertAll(
        () -> assertEquals("user.wide", preset.id()),
        () -> assertEquals("Wide", preset.name()),
        () -> assertEquals(Set.of(), preset.modalities()),
        () -> assertTrue(preset.appliesTo("XA")),
        () -> assertNull(preset.key()));
  }
}
