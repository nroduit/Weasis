/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SiteDocumentsTest {

  @Test
  void finds_a_regular_file_of_the_config_folder(@TempDir Path dir) throws IOException {
    Path config = Files.createDirectories(dir.resolve(SiteDocuments.FOLDER));
    Path presets = Files.writeString(config.resolve("windowPresets.json"), "[]"); // NON-NLS
    Files.createDirectories(config.resolve("colormaps.json")); // a folder, not a document

    assertAll(
        () -> assertEquals(Optional.of(presets), SiteDocuments.find(config, "windowPresets.json")),
        () -> assertTrue(SiteDocuments.find(config, "colormaps.json").isEmpty()),
        () -> assertTrue(SiteDocuments.find(config, "identityMasking.json").isEmpty()),
        () ->
            assertTrue(SiteDocuments.find(dir.resolve("absent"), "windowPresets.json").isEmpty()));
  }

  @Test
  void a_document_name_is_a_bare_file_name(@TempDir Path dir) throws IOException {
    Path config = Files.createDirectories(dir.resolve(SiteDocuments.FOLDER));
    Files.writeString(dir.resolve("outside.json"), "[]"); // NON-NLS

    assertAll(
        () -> assertTrue(SiteDocuments.find(config, "../outside.json").isEmpty()),
        () -> assertTrue(SiteDocuments.find(config, "").isEmpty()),
        () -> assertTrue(SiteDocuments.find(config, null).isEmpty()));
  }

  @Test
  void the_folder_is_config_under_the_resources_path() {
    assertEquals(SiteDocuments.FOLDER, SiteDocuments.folder().getFileName().toString());
  }

  @Test
  void reload_runs_every_hook_once_per_key_and_survives_a_failing_one() {
    java.util.List<String> calls = new java.util.ArrayList<>();
    SiteDocuments.onReload("test.a", () -> calls.add("a1")); // NON-NLS
    SiteDocuments.onReload("test.a", () -> calls.add("a2")); // replaces the first // NON-NLS
    SiteDocuments.onReload(
        "test.failing", // NON-NLS
        () -> {
          throw new IllegalStateException("boom");
        });
    SiteDocuments.onReload("test.b", () -> calls.add("b")); // NON-NLS

    SiteDocuments.reloadAll();

    assertAll(
        () -> assertEquals(java.util.List.of("a2", "b"), calls),
        () ->
            assertTrue(
                SiteDocuments.reloadHooks().containsAll(java.util.List.of("test.a", "test.b"))));
  }

  @Test
  void a_folder_document_gathers_one_entry_per_file_on_top_of_the_file(@TempDir Path dir)
      throws IOException {
    SiteDocuments.declareFolder("test.protocols.json", "test-protocols", "protocols"); // NON-NLS
    Path config = Files.createDirectories(dir.resolve(SiteDocuments.FOLDER));
    Path folder = Files.createDirectories(config.resolve("test-protocols")); // NON-NLS
    Files.writeString(
        config.resolve("test.protocols.json"),
        "{\"locked\":false,\"protocols\":[{\"id\":\"a\",\"name\":\"file\"},{\"id\":\"z\"}]}");
    Files.writeString(folder.resolve("a.json"), "{\"id\":\"a\",\"name\":\"folder\"}");
    Files.writeString(folder.resolve("ct-chest.json"), "{\"name\":\"no id\",\"version\":7}");
    Files.writeString(
        folder.resolve("group.json"),
        "{\"kind\":\"weasis.document\",\"schema\":1,\"protocols\":[{\"id\":\"g1\"}]}");
    Files.writeString(folder.resolve("future.json"), "{\"id\":\"f\",\"schema\":9}");
    Files.writeString(folder.resolve("broken.json"), "{");
    Files.writeString(folder.resolve("notes.txt"), "{}");

    JsonObject doc = SiteDocuments.read(config, "test.protocols.json").orElseThrow();
    Map<String, String> names = new LinkedHashMap<>();
    doc.getJsonArray("protocols")
        .getValuesAs(JsonObject.class)
        .forEach(o -> names.put(o.getString("id"), o.getString("name", "")));

    assertAll(
        () -> assertEquals("test.protocols", doc.getString("id")),
        () -> assertFalse(doc.getBoolean("locked")),
        () -> assertEquals(Map.of("a", "folder", "z", "", "ct-chest", "no id", "g1", ""), names),
        () -> assertEquals(List.of("a", "z", "ct-chest", "g1"), List.copyOf(names.keySet())),
        () -> assertTrue(SiteDocuments.read(config, "other.json").isEmpty()),
        () -> assertTrue(SiteDocuments.folderOf("test.protocols.json").isPresent()));
  }

  @Test
  void a_folder_document_without_its_folder_is_the_file_or_nothing(@TempDir Path dir)
      throws IOException {
    SiteDocuments.declareFolder("test.alone.json", "test-alone", "items"); // NON-NLS
    Path config = Files.createDirectories(dir.resolve(SiteDocuments.FOLDER));
    assertTrue(SiteDocuments.read(config, "test.alone.json").isEmpty());
    Files.writeString(config.resolve("test.alone.json"), "{\"items\":[{\"id\":\"x\"}]}");
    assertEquals(
        "x",
        SiteDocuments.read(config, "test.alone.json")
            .orElseThrow()
            .getJsonArray("items")
            .getJsonObject(0)
            .getString("id"));
  }
}
