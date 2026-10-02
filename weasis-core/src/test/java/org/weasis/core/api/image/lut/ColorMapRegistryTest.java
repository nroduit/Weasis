/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.lut;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.image.lut.ColorMapRegistry.Origin;
import org.weasis.core.api.image.lut.ColorMapRegistry.Query;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;
import org.weasis.opencv.op.lut.colormap.Lighting;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ColorMapRegistryTest {

  @TempDir Path dir;
  private Path userFile;
  private final List<Path> remoteStores = new ArrayList<>();
  private ColorMapRegistry registry;

  @BeforeEach
  void setUp() {
    userFile = dir.resolve("prefs").resolve(ColorMapRegistry.USER_FILE);
    registry = new ColorMapRegistry(userFile, remoteStores::add);
  }

  @Test
  void built_in_maps_have_ids_categories_and_origins() {
    ColorMap pet = registry.findById("weasis.pet-suv").orElseThrow();
    assertAll(
        () -> assertTrue(registry.maps().size() >= 20),
        () -> assertEquals("PET SUV", pet.name()),
        () -> assertEquals(Origin.BUNDLED, registry.origin(pet)),
        () -> assertEquals("Clinical", pet.category()),
        () ->
            assertTrue(
                registry.categories().containsAll(List.of("Clinical", "Classic", "Scientific"))),
        () -> assertTrue(registry.maps().stream().allMatch(m -> m.id().startsWith("weasis."))),
        () ->
            assertEquals(
                registry.maps().size(),
                registry.maps().stream().map(ColorMap::id).distinct().count(),
                "ids are unique"),
        () -> assertTrue(registry.userMaps().isEmpty()));
  }

  @Test
  void queries_filter_by_modality_volume_category_origin_and_text() {
    List<ColorMap> ct = registry.mapsFor("CT");
    assertAll(
        () -> assertTrue(ct.stream().anyMatch(m -> m.id().equals("weasis.ct-tissues"))),
        () -> assertFalse(ct.stream().anyMatch(m -> m.id().equals("weasis.pet-suv"))),
        () ->
            assertTrue(
                ct.stream().anyMatch(m -> m.id().equals("weasis.viridis")),
                "general maps apply everywhere"),
        () -> assertEquals("PET SUV", registry.defaultFor("PT").orElseThrow().name()),
        () -> assertTrue(registry.defaultFor("CT").isEmpty()),
        () -> assertTrue(registry.defaultFor(null).isEmpty()),
        () -> assertEquals(7, registry.query(Query.ALL.withCategory("scientific")).size()),
        () -> assertEquals(8, registry.query(Query.ALL.withCategory("DICOM")).size()),
        () -> assertEquals(1, registry.query(Query.ALL.withText("suv")).size()),
        () -> assertTrue(registry.query(Query.ALL.withOrigins(EnumSet.of(Origin.USER))).isEmpty()),
        () -> assertSame(registry.mapsFor("CT"), registry.mapsFor("CT"), "cached until a change"));
  }

  @Test
  void compiled_byte_luts_are_cached_and_carry_their_source() {
    ColorMap map = registry.findById("weasis.dicom.hot-iron").orElseThrow();
    ByteLut lut = registry.byteLut(map);

    assertAll(
        () -> assertSame(map, lut.source()),
        () -> assertSame(lut, registry.byteLut(map)),
        () -> assertEquals((byte) 255, lut.lutTable()[2][255], "red plane at the top"),
        () -> assertEquals(registry.mapsFor("PT").size(), registry.byteLutsFor("PT").size()));
  }

  @Test
  void user_maps_shadow_by_id_and_notify_listeners() throws Exception {
    AtomicInteger notified = new AtomicInteger();
    registry.addListener(notified::incrementAndGet);
    int before = registry.revision();
    ColorMap custom =
        ColorMap.builder("My PET")
            .id("weasis.pet-suv")
            .modalities("PT")
            .defaultForModality(true)
            .domain(ColorMapDomain.absolute("SUVbw", 0, 5))
            .stop(0, Color.BLACK)
            .stop(5, Color.YELLOW)
            .build();

    registry.saveUserMap(custom);

    assertAll(
        () -> assertTrue(Files.isRegularFile(userFile)),
        () -> assertEquals(List.of(userFile), remoteStores),
        () -> assertEquals(1, notified.get()),
        () -> assertTrue(registry.revision() > before),
        () -> assertSame(custom, registry.findById("weasis.pet-suv").orElseThrow()),
        () -> assertEquals(custom, registry.defaultFor("PT").orElseThrow()),
        () -> assertEquals(Origin.USER, registry.origin(custom)),
        () -> assertTrue(registry.isUserMap(custom)),
        () ->
            assertEquals(
                1,
                registry.maps().stream().filter(m -> m.id().equals("weasis.pet-suv")).count(),
                "the built-in is shadowed"));

    assertEquals(List.of(custom), new ColorMapRegistry(userFile, null).userMaps());
    assertTrue(registry.deleteUserMap("weasis.pet-suv"));
    assertFalse(registry.deleteUserMap("weasis.pet-suv"));
    assertAll(
        () -> assertEquals(10.0, registry.findById("weasis.pet-suv").orElseThrow().domain().max()),
        () -> assertEquals(2, notified.get()));
  }

  @Test
  void contributed_volume_presets_are_kept_apart_from_2d_maps() {
    ColorMap bones =
        ColorMap.builder("Bones")
            .modalities("CT")
            .defaultForModality(true)
            .domain(ColorMapDomain.absolute("HU", -100, 2000))
            .stop(-100, Color.BLACK, 0f)
            .stop(2000, Color.WHITE, 1f)
            .lighting(Lighting.DEFAULT)
            .build();

    registry.addBuiltIn(List.of(bones));

    assertAll(
        () -> assertEquals(Origin.CONTRIBUTED, registry.origin(bones)),
        () -> assertTrue(registry.builtInMaps().contains(bones)),
        () -> assertFalse(registry.mapsFor("CT").contains(bones), "not in the 2D menu"),
        () -> assertEquals(List.of(bones), registry.volumeMapsFor("CT")),
        () -> assertTrue(registry.volumeMapsFor("PT").isEmpty()),
        () -> assertEquals(bones, registry.defaultVolumeFor("CT").orElseThrow()),
        () -> assertTrue(registry.defaultFor("CT").isEmpty(), "a volume preset is no 2D default"));
  }

  @Test
  void imported_maps_are_registered_once_hidden_ones_stay_out_of_menus() {
    ColorMap palette =
        ColorMap.builder("HOT_IRON")
            .stop(0, Color.BLACK)
            .stop(1, Color.RED)
            .hidden(true)
            .metadata(ColorMap.META_DICOM_UID, "1.2.3.4.5")
            .build();

    assertAll(
        () -> assertTrue(registry.addImported(palette)),
        () -> assertFalse(registry.addImported(palette.withName("Other name"))),
        () -> assertEquals(Origin.IMPORTED, registry.origin(palette)),
        () -> assertSame(palette, registry.findByDicomUid("1.2.3.4.5").orElseThrow()),
        () -> assertTrue(registry.findByDicomUid("9.9").isEmpty()),
        () -> assertFalse(registry.mapsFor("CT").contains(palette), "hidden"),
        () -> assertTrue(registry.query(Query.ALL.withHidden(true)).contains(palette)),
        () -> assertFalse(registry.isUserMap(palette)));
  }

  @Test
  void favorites_persist_with_the_user_maps_and_survive_a_reload() throws Exception {
    AtomicInteger notified = new AtomicInteger();
    registry.addListener(notified::incrementAndGet);
    ColorMap pet = registry.findById("weasis.pet-suv").orElseThrow();
    ColorMap hot = registry.findById("weasis.dicom.hot-iron").orElseThrow();

    registry.setFavorite(pet.id(), true);
    registry.setFavorite(hot.id(), true);
    registry.setFavorite(hot.id(), true);

    assertAll(
        () -> assertTrue(registry.isFavorite(pet)),
        () -> assertTrue(registry.isFavorite(hot)),
        () -> assertFalse(registry.isFavorite(null)),
        () -> assertEquals(List.of(pet.id(), hot.id()), registry.favorites()),
        () -> assertEquals(2, notified.get(), "a no-op toggle is not a change"),
        () -> assertEquals(2, remoteStores.size()),
        () -> assertTrue(registry.userMaps().isEmpty()));

    ColorMapRegistry reloaded = new ColorMapRegistry(userFile, null);
    assertEquals(List.of(pet.id(), hot.id()), reloaded.favorites());

    registry.setFavorite(pet.id(), false);
    assertAll(
        () -> assertFalse(registry.isFavorite(pet)),
        () -> assertEquals(List.of(hot.id()), new ColorMapRegistry(userFile, null).favorites()),
        () -> assertEquals(3, notified.get()));
  }

  @Test
  void missing_user_file_is_tolerated() {
    var absent = new ColorMapRegistry(dir.resolve("nope.json"), null);
    assertEquals(registry.maps().size(), absent.maps().size());
  }

  @Test
  void site_maps_override_built_in_ones_and_yield_to_user_maps() throws Exception {
    Path site = dir.resolve("config").resolve(ColorMapRegistry.SITE_FILE);
    Files.createDirectories(site.getParent());
    ColorMap sitePet =
        ColorMap.builder("Site PET")
            .id("weasis.pet-suv")
            .modalities("PT")
            .domain(ColorMapDomain.absolute("SUVbw", 0, 8))
            .stop(0, Color.BLACK)
            .stop(8, Color.RED)
            .build();
    ColorMap siteOnly =
        ColorMap.builder("Site only")
            .id("site.only")
            .stop(0, Color.BLACK)
            .stop(1, Color.WHITE)
            .build();
    ColorMapJson.write(site, List.of(sitePet, siteOnly), List.of());

    ColorMapRegistry withSite = new ColorMapRegistry(site, userFile, null);
    ColorMap pet = withSite.findById("weasis.pet-suv").orElseThrow();
    ColorMap only = withSite.findById("site.only").orElseThrow();
    assertAll(
        () -> assertEquals("Site PET", pet.name()),
        () -> assertEquals(Origin.SITE, withSite.origin(pet)),
        () -> assertEquals(Origin.SITE, withSite.origin(only)),
        () -> assertTrue(withSite.builtInMaps().contains(only), "site maps are read-only"),
        () -> assertFalse(withSite.isUserMap(pet)),
        () ->
            assertEquals(
                1,
                withSite.maps().stream().filter(m -> m.id().equals("weasis.pet-suv")).count(),
                "the built-in is shadowed by the site map"));

    ColorMap mine =
        ColorMap.builder("My PET")
            .id("weasis.pet-suv")
            .modalities("PT")
            .domain(ColorMapDomain.absolute("SUVbw", 0, 5))
            .stop(0, Color.BLACK)
            .stop(5, Color.YELLOW)
            .build();
    withSite.saveUserMap(mine);
    assertAll(
        () ->
            assertEquals(
                Origin.USER, withSite.origin(withSite.findById("weasis.pet-suv").orElseThrow())),
        () ->
            assertEquals(
                List.of(mine), withSite.userMaps(), "the user document holds user entries only"));
    withSite.deleteUserMap("weasis.pet-suv");
    assertEquals(Origin.SITE, withSite.origin(withSite.findById("weasis.pet-suv").orElseThrow()));

    Path absent = dir.resolve("config").resolve("missing.json");
    assertEquals(
        registry.maps().size(), new ColorMapRegistry(absent, null, null).maps().size(), "no site");
  }
}
