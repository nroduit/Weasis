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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.core.api.image.lut.ColorMapRegistry.Query;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;
import org.weasis.opencv.op.lut.colormap.ColorMapType;
import org.weasis.opencv.op.lut.colormap.Rgba;

/**
 * Built-in maps: those re-authoring a published table (legacy Weasis tables, Crameri's lipari and
 * navia, Moreland's cool-warm) must compile back within one level of it; the clinical classifiers
 * and overlays must keep their thresholds and transparency.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class BundledColorMapsTest {

  private static final ColorMapRegistry REGISTRY = new ColorMapRegistry(null, null);

  @ParameterizedTest
  @ValueSource(
      strings = {
        "weasis.cardiac",
        "weasis.ge-color",
        "weasis.stern",
        "weasis.t1-map",
        "weasis.t2-map",
        "weasis.cool-warm"
      })
  void stops_stay_within_one_level_of_the_reference_table(String id) throws Exception {
    ColorMap map = REGISTRY.findById(id).orElseThrow();
    JsonArray reference = reference().getJsonArray(id);
    byte[][] bgr = ColorMapCompiler.toBgr(map, reference.size());
    int worst = 0;
    for (int i = 0; i < reference.size(); i++) {
      JsonArray rgb = reference.getJsonArray(i);
      worst = Math.max(worst, Math.abs((bgr[2][i] & 0xFF) - rgb.getInt(0)));
      worst = Math.max(worst, Math.abs((bgr[1][i] & 0xFF) - rgb.getInt(1)));
      worst = Math.max(worst, Math.abs((bgr[0][i] & 0xFF) - rgb.getInt(2)));
    }
    assertTrue(worst <= 1, id + ": largest deviation " + worst);
  }

  @Test
  void lung_density_classes_are_fixed_hounsfield_steps() {
    ColorMap map = REGISTRY.findById("weasis.ct-lung-density").orElseThrow();
    var sampler = map.sampler();
    assertAll(
        () -> assertEquals("HU", map.domain().unit()),
        () -> assertEquals(sampler.sample(-1000), sampler.sample(-951), "emphysema class"),
        () -> assertEquals(sampler.sample(-950), sampler.sample(-851), "low attenuation class"),
        () -> assertEquals(sampler.sample(-850), sampler.sample(-501), "normally aerated class"),
        () -> assertEquals(Rgba.WHITE, sampler.sample(0), "non-aerated"),
        () -> assertTrue(REGISTRY.mapsFor("CT").contains(map)),
        () -> assertFalse(REGISTRY.mapsFor("MR").contains(map)));
  }

  @Test
  void calcium_is_transparent_below_the_agatston_threshold_and_stepped_above() {
    ColorMap map = REGISTRY.findById("weasis.ct-calcium").orElseThrow();
    var sampler = map.sampler();
    assertAll(
        () -> assertEquals(0f, sampler.sample(-200).alpha()),
        () -> assertEquals(0f, sampler.sample(129).alpha()),
        () -> assertEquals(1f, sampler.sample(130).alpha()),
        () -> assertEquals(sampler.sample(130), sampler.sample(199), "first density class"),
        () -> assertEquals(sampler.sample(400), sampler.sample(1000), "top density class"),
        () -> assertTrue(sampler.sample(300).red() > sampler.sample(300).green(), "red class"),
        () -> assertEquals(0f, sampler.sample(-500).alpha(), "outside low is transparent"));
  }

  @Test
  void parametric_and_cardiac_maps_are_scoped_to_their_modalities() {
    assertAll(
        () -> assertTrue(REGISTRY.mapsFor("MR").contains(map("weasis.t1-map"))),
        () -> assertFalse(REGISTRY.mapsFor("CT").contains(map("weasis.t2-map"))),
        () -> assertTrue(REGISTRY.mapsFor("NM").contains(map("weasis.cardiac"))),
        () -> assertTrue(REGISTRY.mapsFor("PT").contains(map("weasis.cardiac"))),
        () -> assertFalse(REGISTRY.mapsFor("CT").contains(map("weasis.cardiac"))),
        () -> assertTrue(REGISTRY.mapsFor("CT").contains(map("weasis.cool-warm"))),
        () -> assertEquals("Scientific", map("weasis.cool-warm").category()),
        () -> assertEquals("Classic", map("weasis.stern").category()));
  }

  @Test
  void body_composition_and_bone_density_follow_the_published_hounsfield_thresholds() {
    var body = map("weasis.ct-body-composition").sampler();
    var bone = map("weasis.ct-bone-density").sampler();
    assertAll(
        () -> assertEquals(body.sample(-190), body.sample(-30), "adipose class"),
        () -> assertEquals(body.sample(-29), body.sample(150), "muscle class"),
        () -> assertNotEquals(body.sample(-30), body.sample(-29), "fat/muscle edge"),
        () -> assertEquals(Rgba.WHITE, body.sample(151), "bone"),
        () -> assertEquals(bone.sample(80), bone.sample(109), "osteoporosis class"),
        () -> assertEquals(bone.sample(110), bone.sample(159), "indeterminate class"),
        () -> assertEquals(bone.sample(160), bone.sample(400), "normal class"),
        () -> assertTrue(bone.sample(79).red() == bone.sample(79).green(), "anatomy stays gray"),
        () -> assertTrue(REGISTRY.mapsFor("CT").contains(map("weasis.ct-bone-density"))),
        () -> assertFalse(REGISTRY.mapsFor("MR").contains(map("weasis.ct-body-composition"))));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "weasis.fmri-positive",
        "weasis.fmri-negative",
        "weasis.overlay-green",
        "weasis.overlay-magenta",
        "weasis.overlay-hot"
      })
  void overlays_are_transparent_at_the_bottom_and_opaque_at_the_top(String id) {
    ColorMap map = map(id);
    var sampler = map.sampler();
    assertAll(
        () -> assertEquals(ColorMapType.TRANSFER, map.type()),
        () -> assertEquals(0f, sampler.sample(0.0).alpha()),
        () -> assertTrue(sampler.sample(0.5).alpha() > 0f),
        () -> assertEquals(1f, sampler.sample(1.0).alpha()),
        () -> assertTrue(REGISTRY.mapsFor("MR").contains(map)),
        () ->
            assertEquals(
                id.startsWith("weasis.fmri"),
                !REGISTRY.mapsFor("CT").contains(map),
                "fMRI maps are MR only, hue overlays apply everywhere"));
  }

  @Test
  void overlay_category_groups_the_single_hue_maps() {
    assertAll(
        () -> assertEquals(3, REGISTRY.query(Query.ALL.withCategory("Overlay")).size()),
        () -> assertEquals(1f, map("weasis.overlay-green").sampler().sample(1.0).green()),
        () -> assertEquals(0f, map("weasis.overlay-green").sampler().sample(1.0).red()),
        () -> assertEquals(1f, map("weasis.overlay-magenta").sampler().sample(1.0).blue()));
  }

  private static ColorMap map(String id) {
    return REGISTRY.findById(id).orElseThrow();
  }

  private static JsonObject reference() throws Exception {
    try (InputStream in =
            BundledColorMapsTest.class.getResourceAsStream("/bundledColorMapsReference.json");
        JsonReader reader = Json.createReader(in)) {
      return reader.readObject();
    }
  }
}
