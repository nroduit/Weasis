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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;
import org.weasis.opencv.op.lut.colormap.ColorMapType;
import org.weasis.opencv.op.lut.colormap.ColorStop;
import org.weasis.opencv.op.lut.colormap.DomainKind;
import org.weasis.opencv.op.lut.colormap.GradientOpacity;
import org.weasis.opencv.op.lut.colormap.Interpolation;
import org.weasis.opencv.op.lut.colormap.InterpolationSpace;
import org.weasis.opencv.op.lut.colormap.Lighting;
import org.weasis.opencv.op.lut.colormap.Material;
import org.weasis.opencv.op.lut.colormap.OutsideColors;
import org.weasis.opencv.op.lut.colormap.Rgba;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ColorMapJsonTest {

  private static final ColorMap FULL =
      ColorMap.builder("PET SUV")
          .id("test.pet-suv")
          .category("Clinical")
          .tags("pet", "suv")
          .hidden(true)
          .type(ColorMapType.TRANSFER)
          .modalities("PT", "NM")
          .defaultForModality(true)
          .domain(ColorMapDomain.absolute("SUVbw", 0, 10))
          .bits(12)
          .space(InterpolationSpace.LAB)
          .interpolation(Interpolation.STEP)
          .stop(new ColorStop(0, Color.BLACK, 0f, new Material(0.1f, 0.8f, 0.3f), "Background"))
          .alphaStop(2.5, 1f)
          .stop(10, Color.RED)
          .outside(new OutsideColors(Rgba.TRANSPARENT, new Rgba(1f, 1f, 1f, 128 / 255f), null))
          .lighting(new Lighting(false, 20f, GradientOpacity.edgeEmphasis(0.5f)))
          .metadata(ColorMap.META_DICOM_UID, "1.2.840.10008.1.5.1")
          .metadata(ColorMap.META_DICOM_LABEL, "HOT_IRON")
          .build();

  @Test
  void round_trip_keeps_every_field() {
    JsonObject json = ColorMapJson.toJson(FULL);
    assertEquals(FULL, ColorMapJson.fromJson(json));
  }

  @Test
  void json_uses_the_documented_field_names() {
    JsonObject json = ColorMapJson.toJson(FULL);
    JsonObject domain = json.getJsonObject("domain");
    JsonObject first = json.getJsonArray("stops").getJsonObject(0);

    assertAll(
        () -> assertEquals("test.pet-suv", json.getString("id")),
        () -> assertEquals("Clinical", json.getString("category")),
        () -> assertEquals(List.of("pet", "suv"), JsonUtil.getStringList(json, "tags")),
        () -> assertTrue(json.getBoolean("hidden")),
        () -> assertEquals("transfer", json.getString("type")),
        () -> assertEquals(List.of("NM", "PT"), JsonUtil.getStringList(json, "modality")),
        () -> assertTrue(json.getBoolean("default")),
        () -> assertEquals("absolute", domain.getString("kind")),
        () -> assertEquals("SUVbw", domain.getString("unit")),
        () -> assertEquals(12, json.getInt("bits")),
        () -> assertEquals("lab", json.getString("space")),
        () -> assertEquals("step", json.getString("interpolation")),
        () -> assertEquals("#000000", first.getString("color")),
        () -> assertEquals("Background", first.getString("group")),
        () -> assertEquals("transparent", json.getJsonObject("outside").getString("low")),
        () -> assertEquals("#ffffff80", json.getJsonObject("outside").getString("high")),
        () -> assertFalse(json.getJsonObject("outside").containsKey("nan")),
        () -> assertFalse(json.getJsonObject("lighting").getBoolean("shade")),
        () ->
            assertEquals(2, json.getJsonObject("lighting").getJsonArray("gradientOpacity").size()),
        () ->
            assertEquals(
                "HOT_IRON", json.getJsonObject("metadata").getString("dicom.contentLabel")));
  }

  @Test
  void minimal_document_gets_defaults() {
    String text =
        """
        { "name": "Ramp", "stops": [ { "pos": 0, "color": "#000" }, { "pos": 1, "color": "#fff" } ] }
        """;
    ColorMap map = read(text).getFirst();

    assertAll(
        () -> assertEquals("Ramp", map.name()),
        () -> assertEquals(ColorMapType.SEQUENTIAL, map.type()),
        () -> assertEquals(ColorMapDomain.RELATIVE, map.domain()),
        () -> assertEquals(8, map.bits()),
        () -> assertEquals(InterpolationSpace.RGB, map.space()),
        () -> assertEquals(Interpolation.LINEAR, map.interpolation()),
        () -> assertEquals(OutsideColors.CLAMP, map.outside()),
        () -> assertNull(map.lighting()),
        () -> assertEquals(Color.WHITE, map.stops().get(1).color()));
  }

  @Test
  void arrays_and_lenient_values_are_accepted() {
    String text =
        """
        [
          { "name": "A", "type": "Qualitative", "bits": "12", "default": "true",
            "domain": { "kind": "PERCENT", "reference": "max", "min": "0", "max": "100" },
            "stops": [ { "pos": "50", "alpha": "0.5", "ambient": 0.3 } ] },
          { "name": "B", "stops": [ { "pos": 0, "color": "#ff000080" } ] }
        ]
        """;
    List<ColorMap> maps = read(text);
    ColorMap a = maps.get(0);
    ColorStop stop = a.stops().getFirst();

    assertAll(
        () -> assertEquals(2, maps.size()),
        () -> assertEquals(ColorMapType.QUALITATIVE, a.type()),
        () -> assertEquals(12, a.bits()),
        () -> assertTrue(a.defaultForModality()),
        () -> assertEquals(DomainKind.PERCENT, a.domain().kind()),
        () -> assertEquals(100.0, a.domain().max()),
        () -> assertEquals(50.0, stop.position()),
        () -> assertEquals(0.5f, stop.alpha()),
        () -> assertNull(stop.color()),
        () -> assertEquals(new Material(0.3f, 0.9f, 0.2f), stop.material()),
        () -> assertEquals(new Color(255, 0, 0, 128), maps.get(1).stops().getFirst().color()));
  }

  @Test
  void invalid_documents_are_rejected() {
    assertAll(
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> read("{ \"stops\": [ { \"pos\": 0, \"color\": \"#000\" } ] }")),
        () -> assertThrows(IllegalArgumentException.class, () -> read("{ \"name\": \"x\" }")),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> read("{ \"name\": \"x\", \"stops\": [ { \"color\": \"#000\" } ] }")),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> read("{ \"name\": \"x\", \"type\": \"rainbow\", \"stops\": [] }")),
        () -> assertThrows(IllegalArgumentException.class, () -> ColorMapJson.parseColor("#12")),
        () -> assertThrows(IllegalArgumentException.class, () -> ColorMapJson.parseColor("red")));
  }

  @Test
  void colors_format_and_parse() {
    assertAll(
        () -> assertEquals("transparent", ColorMapJson.format(Rgba.TRANSPARENT)),
        () -> assertEquals("#ff0000", ColorMapJson.format(Rgba.of(Color.RED))),
        () -> assertEquals("#0000ff80", ColorMapJson.format(new Rgba(0f, 0f, 1f, 0.5f))),
        () -> assertEquals(Rgba.of(Color.RED), ColorMapJson.parseColor("#F00")),
        () -> assertEquals(Rgba.of(Color.RED), ColorMapJson.parseColor("ff0000")),
        () -> assertEquals(Rgba.TRANSPARENT, ColorMapJson.parseColor("Transparent")),
        () -> assertEquals(0.5f, ColorMapJson.parseColor("#00000080").alpha(), 0.01f));
  }

  @Test
  void file_round_trip(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("maps.json");
    ColorMap plain = ColorMap.builder("Plain").stop(0, Color.BLACK).stop(1, Color.WHITE).build();

    ColorMapJson.write(file, List.of(FULL, plain));

    assertEquals(List.of(FULL, plain), ColorMapJson.readAll(file));
  }

  @Test
  void envelope_carries_the_favorites_only_when_there_are_some(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("maps.json");
    ColorMapJson.write(file, List.of(FULL), List.of("weasis.hot-iron", "user.mine"));
    ColorMapJson.Document document = ColorMapJson.readDocument(file);

    assertAll(
        () -> assertEquals(List.of(FULL), document.maps()),
        () -> assertEquals(List.of("weasis.hot-iron", "user.mine"), document.favorites()),
        () -> assertEquals(List.of(FULL), ColorMapJson.readAll(file)),
        () -> assertFalse(ColorMapJson.toEnvelope(List.of(FULL)).containsKey("favorites")),
        () ->
            assertTrue(
                ColorMapJson.readDocument(
                        new ByteArrayInputStream(
                            ("[" + ColorMapJson.toJson(FULL) + "]")
                                .getBytes(StandardCharsets.UTF_8)))
                    .favorites()
                    .isEmpty()));
  }

  private static List<ColorMap> read(String text) {
    return ColorMapJson.readAll(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void envelope_carries_the_schema_and_older_forms_still_read() {
    JsonObject envelope = ColorMapJson.toEnvelope(List.of(FULL));
    assertEquals(ColorMapJson.SCHEMA_VERSION, envelope.getInt("schema"));

    assertAll(
        () -> assertEquals(List.of(FULL), read(envelope.toString())),
        () -> assertEquals(List.of(FULL), read("[" + ColorMapJson.toJson(FULL) + "]")),
        () ->
            assertEquals(
                "ramp",
                read("{ \"name\": \"Ramp\", \"stops\": [ { \"pos\": 0, \"color\": \"#000\" } ] }")
                    .getFirst()
                    .id()),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> read("{ \"schema\": 99, \"maps\": [] }")),
        () -> assertTrue(read("{ \"schema\": 1, \"maps\": [] }").isEmpty()));
  }
}
