/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.opencv.op.lut.colormap.ColorMap;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PresetTest {

  @BeforeAll
  static void installRegistry() {
    ColorMapRegistry registry = new ColorMapRegistry(null, path -> {});
    registry.addBuiltIn(Preset.builtInMaps());
    ColorMapRegistry.useInstance(registry);
  }

  @Test
  void a_user_map_saved_under_a_built_in_id_replaces_it_and_wins_as_default() throws Exception {
    ColorMapRegistry registry = ColorMapRegistry.getInstance();
    Preset builtIn = Preset.getDefaultPreset(Modality.CT);
    ColorMap tweaked = builtIn.toColorMap().toBuilder().name("My CT").build();
    try {
      registry.saveUserMap(tweaked);
      List<Preset> all = Preset.getAllPresets();
      Preset defaultCt = Preset.getDefaultPreset(Modality.CT);
      assertAll(
          () -> assertEquals(Preset.basicPresets.size(), all.size(), "one entry per id"),
          () -> assertEquals(1, all.stream().filter(p -> p.getName().equals("My CT")).count()),
          () -> assertTrue(all.stream().noneMatch(p -> p.equals(builtIn)), "shadowed"),
          () -> assertEquals("My CT", defaultCt.getName()),
          () -> assertTrue(defaultCt.isCustom()));
    } finally {
      registry.deleteUserMap(tweaked.id());
    }
    assertEquals(builtIn, Preset.getDefaultPreset(Modality.CT), "built-in again once deleted");
  }

  @Test
  void built_in_presets_load_from_the_schema_file() {
    List<Preset> presets = Preset.basicPresets;
    Preset ct = Preset.getDefaultPreset(Modality.CT);
    assertAll(
        () -> assertEquals(25, presets.size()),
        () -> assertNotNull(ct),
        () -> assertEquals(Modality.CT, ct.getModality()),
        () -> assertTrue(ct.getColorMin() < ct.getColorMax()),
        () -> assertTrue(ct.getWidth() > 0 && ct.getWidth() <= Preset.MAX_TEXTURE_WIDTH),
        () -> assertTrue(ct.toColorMap().lighting() != null),
        () -> assertEquals(25, Preset.builtInMaps().stream().map(ColorMap::id).distinct().count()),
        () -> assertNotNull(Preset.getDefaultPreset(Modality.DEFAULT)),
        () -> assertEquals("weasis.vr.ct.default", ct.toColorMap().id()));
  }

  @Test
  void legacy_custom_file_converts_to_user_volume_maps() throws Exception {
    Path file = Path.of("src/test/resources/legacyVolumePresets.json");
    List<ColorMap> maps = LegacyVolumePresets.read(file);
    ColorMap first = maps.getFirst();
    assertAll(
        () -> assertEquals(25, maps.size()),
        () -> assertEquals("user.vr.grayscale", first.id()),
        () -> assertEquals("user.vr.ct.grayscale", maps.get(1).id()),
        () -> assertEquals(25, maps.stream().map(ColorMap::id).distinct().count(), "none lost"),
        () -> assertTrue(first.lighting() != null),
        () -> assertTrue(first.hasAlpha()),
        () -> assertEquals("Volume", first.category()),
        () -> assertEquals(0, Preset.of(first, true).getColorMin()));
  }
}
