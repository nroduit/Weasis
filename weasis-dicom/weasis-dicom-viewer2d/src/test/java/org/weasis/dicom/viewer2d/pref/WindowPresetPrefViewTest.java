/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.pref;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.codec.display.WindowPreset;
import org.weasis.dicom.codec.display.WindowPreset.Domain;
import org.weasis.dicom.codec.display.WindowPreset.DomainKind;
import org.weasis.dicom.codec.display.WindowPreset.When;
import org.weasis.dicom.codec.display.WindowPresetRegistry;
import org.weasis.opencv.op.lut.LutShape;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WindowPresetPrefViewTest {

  private static final WindowPreset USER_PRESET =
      new WindowPreset(
          "user.mr.bright",
          "Bright",
          Set.of("MR"),
          null,
          List.of(),
          false,
          60,
          45,
          new Domain(DomainKind.PERCENT, null, WindowPreset.REFERENCE_SERIES),
          LutShape.SIGMOID,
          '5',
          new When(12, true, Set.of("HEAD")),
          true);

  @Test
  void a_user_preset_shown_and_read_back_is_unchanged(@TempDir Path dir) throws IOException {
    WindowPresetPrefView page = page(dir, USER_PRESET);

    page.selectPreset(USER_PRESET.id());

    assertAll(
        () -> assertTrue(page.editorEnabled()),
        () -> assertEquals(USER_PRESET, page.editorPreset()));
  }

  @Test
  void a_built_in_preset_is_read_only_and_never_written(@TempDir Path dir) throws IOException {
    WindowPresetRegistry registry = registry(dir, USER_PRESET);
    WindowPresetPrefView page = new WindowPresetPrefView(registry);

    page.selectPreset("weasis.ct.lung");
    page.typeName("Poumon");
    page.commitEditor();
    page.closeAdditionalWindow();

    assertAll(
        () -> assertFalse(page.editorEnabled()),
        () -> assertEquals("Lung", page.selected().name()),
        () -> assertEquals("Lung", registry.find("weasis.ct.lung").orElseThrow().name()),
        () -> assertEquals(List.of(USER_PRESET), registry.userPresets()));
  }

  @Test
  void an_edit_of_a_user_preset_is_written_on_apply(@TempDir Path dir) throws IOException {
    WindowPresetRegistry registry = registry(dir, USER_PRESET);
    WindowPresetPrefView page = new WindowPresetPrefView(registry);

    page.selectPreset(USER_PRESET.id());
    page.typeName("Clair");
    page.commitEditor();
    assertEquals("Bright", registry.find(USER_PRESET.id()).orElseThrow().name(), "deferred");

    page.closeAdditionalWindow();

    assertEquals("Clair", registry.find(USER_PRESET.id()).orElseThrow().name());
  }

  @Test
  void codes_are_typed_with_any_separator() {
    assertEquals(Set.of("CT", "MR", "PT"), WindowPreset.parseCodes(" ct; mr ,CT  pt,"));
    assertEquals(Set.of(), WindowPreset.parseCodes("  "));
    assertEquals("t2-bright", WindowPresetPrefView.slug(" T2  bright! "));
  }

  private static WindowPresetPrefView page(Path dir, WindowPreset user) throws IOException {
    return new WindowPresetPrefView(registry(dir, user));
  }

  private static WindowPresetRegistry registry(Path dir, WindowPreset user) throws IOException {
    Path userFile = dir.resolve(WindowPresetRegistry.USER_FILE);
    Files.createDirectories(dir);
    WindowPresetRegistry registry = new WindowPresetRegistry(WindowPresetRegistry.loadBuiltIn());
    registry.configure(null, userFile);
    registry.saveUser(user);
    return registry;
  }
}
