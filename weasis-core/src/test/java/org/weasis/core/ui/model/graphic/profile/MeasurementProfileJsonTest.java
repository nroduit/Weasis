/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile.Defaults;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.MeasureFormat;
import org.weasis.core.ui.model.utils.bean.Measurement;

@DisplayNameGeneration(ReplaceUnderscores.class)
class MeasurementProfileJsonTest {

  @Test
  void a_profile_survives_a_round_trip() {
    MeasurementProfile profile =
        new MeasurementProfile(
            "chest", // NON-NLS
            "Chest", // NON-NLS
            List.of("CR", "DX"), // NON-NLS
            List.of("weasis.line", "weasis.parallel"), // NON-NLS
            new Defaults(new Color(255, 0, 0, 128), 2, true, 0.5f, true, 3),
            Map.of("weasis.line", List.of("length")), // NON-NLS
            List.of("stats.mean"), // NON-NLS
            false);
    StringWriter writer = new StringWriter();
    MeasurementProfileJson.write(writer, List.of(profile));
    List<MeasurementProfile> read =
        MeasurementProfileJson.read(
            new ByteArrayInputStream(writer.toString().getBytes(StandardCharsets.UTF_8)));
    assertEquals(1, read.size());
    MeasurementProfile back = read.getFirst();
    assertAll(
        () -> assertEquals(profile.id(), back.id()),
        () -> assertEquals(profile.name(), back.name()),
        () -> assertEquals(profile.modalities(), back.modalities()),
        () -> assertEquals(profile.tools(), back.tools()),
        () -> assertEquals(profile.defaults(), back.defaults()),
        () -> assertEquals(profile.labels(), back.labels()),
        () -> assertEquals(profile.statistics(), back.statistics()),
        () -> assertFalse(back.builtIn()),
        () -> assertTrue(writer.toString().contains("\"version\": 1")));
  }

  private static Integer decimalsOf(String defaults) {
    String json =
        "{\"version\":1,\"profiles\":[{\"id\":\"p\",\"defaults\":" // NON-NLS
            + defaults
            + "}]}";
    return MeasurementProfileJson.read(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
        .getFirst()
        .defaults()
        .decimals();
  }

  @Test
  void decimals_are_a_number_or_auto_and_stay_unset_otherwise() {
    MeasurementProfile auto =
        new MeasurementProfile(
            "p", // NON-NLS
            null,
            List.of(),
            null,
            new Defaults(null, null, null, null, null, MeasureFormat.AUTO),
            Map.of(),
            null,
            false);
    StringWriter writer = new StringWriter();
    MeasurementProfileJson.write(writer, List.of(auto));
    assertAll(
        () -> assertTrue(writer.toString().contains("\"decimals\": \"auto\"")), // NON-NLS
        () -> assertEquals(MeasureFormat.AUTO, decimalsOf("{\"decimals\":\"auto\"}")), // NON-NLS
        () -> assertEquals(2, decimalsOf("{\"decimals\":2}")), // NON-NLS
        () -> assertEquals(MeasureFormat.MAX_DECIMALS, decimalsOf("{\"decimals\":12}")), // NON-NLS
        () -> assertNull(decimalsOf("{\"decimals\":\"many\"}")), // NON-NLS
        () -> assertNull(decimalsOf("{\"fill\":true}"))); // NON-NLS
  }

  @Test
  void absent_sections_mean_keep_the_current_settings() {
    String json =
        "{\"version\":1,\"profiles\":[{\"id\":\"p\",\"modalities\":[\"us\"]}]}"; // NON-NLS
    MeasurementProfile p =
        MeasurementProfileJson.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
            .getFirst();
    assertAll(
        () -> assertEquals("p", p.name()),
        () -> assertNull(p.tools()),
        () -> assertNull(p.defaults()),
        () -> assertNull(p.statistics()),
        () -> assertTrue(p.labels().isEmpty()),
        () -> assertTrue(p.matches("US")),
        () -> assertFalse(p.matches("CT")));
  }

  @Test
  void the_bundled_document_has_a_default_and_modality_profiles() {
    List<MeasurementProfile> profiles = MeasurementProfileRegistry.loadBuiltIn();
    assertAll(
        () -> assertEquals(4, profiles.size()),
        () -> assertTrue(profiles.stream().anyMatch(MeasurementProfile::isDefault)),
        () -> assertTrue(profiles.stream().anyMatch(p -> p.matches("CT"))),
        () -> assertTrue(profiles.stream().anyMatch(p -> p.matches("DX"))),
        () -> assertTrue(profiles.stream().anyMatch(p -> p.matches("US"))),
        () -> assertFalse(profiles.stream().anyMatch(p -> p.matches("MG"))));
  }

  @Test
  void the_bundled_profiles_only_name_tools_measurements_and_statistics_that_exist() {
    GraphicRegistry registry = new GraphicRegistry(property -> false);
    List<String> statistics =
        Arrays.stream(ImageStatistics.ALL_MEASUREMENTS).map(Measurement::getKey).toList();
    List<String> unknown = new ArrayList<>();
    for (MeasurementProfile profile : MeasurementProfileRegistry.loadBuiltIn()) {
      List<String> tools = profile.tools() == null ? List.of() : profile.tools();
      for (String tool : tools) {
        if (registry.descriptor(tool).isEmpty()) {
          unknown.add(profile.id() + ": tool " + tool);
        }
      }
      profile
          .labels()
          .forEach(
              (tool, keys) -> {
                List<String> known =
                    registry
                        .prototype(tool)
                        .map(g -> g.getMeasurementList().stream().map(Measurement::getKey).toList())
                        .orElse(List.of());
                keys.stream()
                    .filter(k -> !known.contains(k))
                    .forEach(k -> unknown.add(profile.id() + ": label " + tool + "/" + k));
              });
      if (profile.statistics() != null) {
        profile.statistics().stream()
            .filter(k -> !statistics.contains(k))
            .forEach(k -> unknown.add(profile.id() + ": statistic " + k));
      }
    }
    assertEquals(List.of(), unknown);
  }

  @Test
  void ids_derive_from_names() {
    assertAll(
        () -> assertEquals("chest-x-ray", MeasurementProfile.idFromName(" Chest X-Ray ")),
        () -> assertEquals("profile", MeasurementProfile.idFromName("***")));
  }
}
