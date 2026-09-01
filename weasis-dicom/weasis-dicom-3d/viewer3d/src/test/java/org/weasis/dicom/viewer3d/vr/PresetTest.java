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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapType;
import org.weasis.opencv.op.lut.colormap.DomainKind;

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
        () -> assertEquals(46, presets.size()),
        () -> assertNotNull(ct),
        () -> assertEquals(Modality.CT, ct.getModality()),
        () -> assertTrue(ct.getColorMin() < ct.getColorMax()),
        () -> assertTrue(ct.getWidth() > 0 && ct.getWidth() <= Preset.MAX_TEXTURE_WIDTH),
        () -> assertTrue(ct.toColorMap().lighting() != null),
        () -> assertEquals(46, Preset.builtInMaps().stream().map(ColorMap::id).distinct().count()),
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

  @Test
  void built_in_presets_are_transfer_maps_over_their_stop_range() {
    for (ColorMap map : Preset.builtInMaps()) {
      assertAll(
          map.id(),
          () -> assertTrue(map.id().startsWith("weasis.vr."), "id prefix"),
          () -> assertEquals(ColorMapType.TRANSFER, map.type()),
          () -> assertNotEquals(DomainKind.RELATIVE, map.domain().kind(), "positions have a unit"),
          () -> assertEquals(map.firstPosition(), map.domain().min(), "domain starts at a stop"),
          () -> assertEquals(map.lastPosition(), map.domain().max(), "domain ends at a stop"),
          () -> assertTrue(map.hasAlpha(), "opacity curve"),
          () -> assertNotNull(map.lighting(), "lighting"));
      if (map.metadata().getOrDefault("source", "").startsWith("3D Slicer")) {
        assertTrue(
            map.firstPosition() >= -1024 && map.lastPosition() <= 3071, map.id() + " in HU range");
      }
    }
  }

  @Test
  void slicer_presets_keep_their_curves_once_clipped_to_the_hounsfield_range() {
    ColorMapRegistry registry = ColorMapRegistry.getInstance();
    var airways = registry.findById("weasis.vr.ct.airways").orElseThrow().sampler();
    var aorta = registry.findById("weasis.vr.ct.aaa").orElseThrow().sampler();
    Set<String> categories =
        Preset.basicPresets.stream()
            .filter(p -> p.getModality() == Modality.CT)
            .map(p -> p.toColorMap().category())
            .collect(java.util.stream.Collectors.toSet());
    assertAll(
        () -> assertEquals(0.715f, airways.sample(-1000).alpha(), 0.01f, "air stays opaque"),
        () -> assertEquals(0f, airways.sample(0).alpha(), "tissue transparent"),
        () -> assertEquals(0f, aorta.sample(100).alpha(), "below contrast"),
        () -> assertEquals(0.833f, aorta.sample(420).alpha(), 0.01f, "enhanced lumen"),
        () -> assertEquals(0.804f, aorta.sample(3071).alpha(), 0.01f, "clipped end keeps value"),
        () ->
            assertTrue(
                categories.containsAll(
                    Set.of("Bone", "Vascular", "Cardiac", "Lung & Airways", "Soft Tissue & Skin")),
                "categories: " + categories));
  }

  @Test
  void a_percent_map_is_compiled_per_volume_and_stays_equal_to_the_listed_preset() {
    DicomVolTexture mr = volume(0, 2000, null);
    Preset listed = builtIn("weasis.vr.mr.default");
    Preset ct = builtIn("weasis.vr.ct.default");
    Preset rendered = listed.forVolume(mr);
    assertAll(
        () -> assertNotSame(listed, rendered),
        () -> assertEquals(listed, rendered, "menus keep their selection"),
        () -> assertTrue(rendered.isPreview(), "released once replaced"),
        () -> assertSame(listed.toColorMap(), rendered.toColorMap(), "declared map unchanged"),
        () -> assertEquals(0, rendered.getColorMin()),
        () -> assertEquals(2000, rendered.getColorMax()),
        () -> assertTrue(listed.getColorMax() >= 4095, "listed one spans 12 bits"),
        () -> assertSame(listed, listed.forVolume(null)),
        () -> assertSame(ct, ct.forVolume(mr), "absolute HU positions do not depend on it"));
  }

  @Test
  void suv_positions_follow_the_suv_factor_of_the_series_or_the_maximum_without_one() {
    Preset hot = builtIn("weasis.vr.pt.hot");
    DicomVolTexture pet = volume(0, 40000, 0.0005);
    DicomVolTexture noSuv = volume(0, 40000, null);
    ColorMap anchored = Preset.anchoredTo(hot.toColorMap(), pet);
    assertAll(
        () -> assertEquals(24000, hot.forVolume(pet).getColorMax(), "12 SUVbw at 0.0005 per value"),
        () -> assertEquals(0f, anchored.sampler().sample(2000).alpha(), "1 SUVbw is background"),
        () -> assertTrue(anchored.sampler().sample(12000).alpha() > 0.4f, "6 SUVbw shows"),
        () -> assertEquals("BQML", anchored.domain().unit()),
        () -> assertEquals(40000, hot.forVolume(noSuv).getColorMax(), "top SUV at the maximum"));
  }

  private static Preset builtIn(String id) {
    return Preset.basicPresets.stream()
        .filter(p -> p.toColorMap().id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private static DicomVolTexture volume(double min, double max, Double suvFactor) {
    DicomVolTexture texture = mock(DicomVolTexture.class);
    when(texture.getLevelMin()).thenReturn(min);
    when(texture.getLevelMax()).thenReturn(max);
    when(texture.getPixelValueUnit()).thenReturn("BQML");
    when(texture.getSuvFactor()).thenReturn(suvFactor);
    return texture;
  }
}
