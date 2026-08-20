/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr.lut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.InputStream;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.JsonUtil;

class VolumePresetTest {

  @Test
  void bundledPresetsQuoteTheirNumbersAndBooleans() throws Exception {
    List<VolumePreset> presets;
    try (InputStream in = VolumePreset.class.getResourceAsStream("/volumePresets.json")) {
      assertNotNull(in, "Missing the bundled volumePresets.json");
      presets =
          JsonUtil.objects(JsonUtil.readArray(in)).stream().map(VolumePreset::fromJson).toList();
    }
    assertFalse(presets.isEmpty());

    VolumePreset grayscale = presets.getFirst();
    assertEquals("Grayscale", grayscale.getName());
    assertEquals("ALL", grayscale.getModality());
    assertTrue(grayscale.isDefaultElement());
    assertTrue(grayscale.isShade());
    assertEquals(10f, grayscale.getSpecularPower());

    PresetPoint first = grayscale.getGroups().getFirst().getPoints()[0];
    assertEquals(0, first.getIntensity());
    assertEquals(0f, first.getOpacity());
    assertEquals(0.1f, first.getAmbient());
    assertEquals(0.9f, first.getDiffuse());

    // Every preset must be usable: at least one group, each with at least one point.
    for (VolumePreset preset : presets) {
      assertFalse(preset.getGroups().isEmpty(), preset.getName());
      preset.getGroups().forEach(g -> assertTrue(g.getPoints().length > 0, preset.getName()));
    }
  }

  @Test
  void presetSurvivesAJsonRoundTrip() {
    VolumePreset preset = new VolumePreset();
    preset.setName("Custom");
    preset.setModality("CT");
    preset.setDefaultElement(true);
    preset.setShade(false);
    preset.setSpecularPower(12.5f);
    preset.setGroups(
        new PresetGroup[] {
          new PresetGroup(
              "Default",
              new PresetPoint[] {new PresetPoint(-1024, 0.1f, 1f, null, 0f, null, 0.9f, null)})
        });

    VolumePreset copy = VolumePreset.fromJson(preset.toJson());
    assertEquals("Custom", copy.getName());
    assertEquals("CT", copy.getModality());
    assertTrue(copy.isDefaultElement());
    assertFalse(copy.isShade());
    assertEquals(12.5f, copy.getSpecularPower());

    PresetPoint point = copy.getGroups().getFirst().getPoints()[0];
    assertEquals(-1024, point.getIntensity());
    assertEquals(0.1f, point.getOpacity());
    assertEquals(1f, point.getRed());
    assertNull(point.getGreen());
    assertEquals(0f, point.getBlue());
  }

  /** A float must keep its own decimal form, not the expansion of its double value. */
  @Test
  void floatsAreWrittenWithoutDoubleNoise() {
    String json = new PresetPoint(0, 0.1f, null, null, null, null, null, null).toJson().toString();
    assertTrue(json.contains("\"opacity\":0.1"), json);
    assertFalse(json.contains("0.100000"), json);
  }

  @Test
  void unquotedNumbersAreAlsoRead() {
    JsonObject json =
        parse(
            """
        {"intensity":300,"opacity":0.5,"red":1,"ambient":0.2}""");
    PresetPoint point = PresetPoint.fromJson(json);
    assertEquals(300, point.getIntensity());
    assertEquals(0.5f, point.getOpacity());
    assertEquals(1f, point.getRed());
    assertEquals(0.2f, point.getAmbient());
    assertNull(point.getDiffuse());
  }

  private static JsonObject parse(String json) {
    try (JsonReader reader = Json.createReader(new StringReader(json))) {
      return reader.readObject();
    }
  }
}
