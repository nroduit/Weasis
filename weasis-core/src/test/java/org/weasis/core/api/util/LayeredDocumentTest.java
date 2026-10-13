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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.util.LayeredEntries.Origin;

@DisplayNameGeneration(ReplaceUnderscores.class)
class LayeredDocumentTest {
  private static final String NAME = "hp.protocols.json"; // NON-NLS
  private static final String ENTRIES = "protocols"; // NON-NLS

  private static JsonObject entry(String id, String name) {
    return Json.createObjectBuilder().add("id", id).add("name", name).build(); // NON-NLS
  }

  private static Optional<JsonObject> site(boolean wholeLocked, JsonObject... entries) {
    var array = Json.createArrayBuilder();
    for (JsonObject e : entries) {
      array.add(e);
    }
    return Optional.of(
        Json.createObjectBuilder()
            .add(LayeredDocument.LOCKED, wholeLocked)
            .add(ENTRIES, array)
            .build());
  }

  private static Map<String, String> view(LayeredDocument doc) {
    Map<String, String> map = new TreeMap<>();
    doc.entries()
        .forEach(e -> map.put(e.id(), e.json().getString("name") + "@" + e.origin().name()));
    return map;
  }

  @Test
  void the_user_entries_go_on_top_of_the_site_ones_except_a_locked_one(@TempDir Path dir)
      throws IOException {
    LayeredDocument doc = new LayeredDocument(NAME, ENTRIES);
    Path file = dir.resolve(NAME);
    doc.setBuiltIn(List.of(entry("b", "built-in")));
    doc.configure(
        site(
            false,
            entry("s", "site"),
            Json.createObjectBuilder(entry("l", "site")).add("locked", true).build()),
        file);
    doc.put(entry("s", "mine"));
    doc.put(entry("u", "mine"));

    assertAll(
        () ->
            assertEquals(
                Map.of(
                    "b", "built-in@BUILT_IN",
                    "l", "site@SITE",
                    "s", "mine@USER",
                    "u", "mine@USER"),
                view(doc)),
        () -> assertThrows(IllegalStateException.class, () -> doc.put(entry("l", "mine"))),
        () -> assertTrue(doc.isLocked("l")),
        () -> assertEquals(Optional.of(Origin.SITE), doc.below("s")),
        () ->
            assertEquals(
                ListDocument.KIND,
                JsonUtil.readObject(Files.readString(file)).getString(ListDocument.KIND_KEY)),
        () -> assertEquals(2, doc.userEntries().size()));
  }

  @Test
  void removing_a_user_entry_brings_the_site_one_back_and_survives_a_reload(@TempDir Path dir)
      throws IOException {
    LayeredDocument doc = new LayeredDocument(NAME, ENTRIES);
    Path file = dir.resolve(NAME);
    doc.configure(site(false, entry("s", "site")), file);
    doc.put(entry("s", "mine"));
    doc.put(entry("u", "mine"));
    assertTrue(doc.remove("s"));

    LayeredDocument again = new LayeredDocument(NAME, ENTRIES);
    again.configure(site(false, entry("s", "site")), file);
    assertAll(
        () -> assertEquals(Map.of("s", "site@SITE", "u", "mine@USER"), view(again)),
        () -> assertFalse(again.remove("absent")));
  }

  @Test
  void a_hidden_user_entry_hides_the_site_one_from_the_list() throws IOException {
    LayeredDocument doc = new LayeredDocument(NAME, ENTRIES);
    doc.configure(site(false, entry("s", "site"), entry("t", "site")), null);
    doc.put(Json.createObjectBuilder(entry("s", "mine")).add("hidden", true).build());

    assertAll(
        () -> assertEquals(Map.of("t", "site@SITE"), view(doc)),
        () -> assertTrue(doc.ids().contains("s")));
  }

  @Test
  void a_site_locking_the_whole_document_ignores_the_user_layer(@TempDir Path dir)
      throws IOException {
    Path file = dir.resolve(NAME);
    ListDocument.write(file, ENTRIES, List.of(entry("u", "mine")));
    LayeredDocument doc = new LayeredDocument(NAME, ENTRIES);
    doc.configure(site(true, entry("s", "site")), file);

    assertAll(
        () -> assertEquals(Map.of("s", "site@SITE"), view(doc)),
        () -> assertTrue(doc.isLocked()),
        () -> assertThrows(IllegalStateException.class, () -> doc.put(entry("v", "mine"))));
  }

  @Test
  void a_user_file_of_a_newer_schema_is_read_only(@TempDir Path dir) throws IOException {
    Path file = dir.resolve(NAME);
    String newer = "{\"schema\":2,\"protocols\":[{\"id\":\"u\",\"name\":\"mine\"}]}";
    Files.writeString(file, newer);
    LayeredDocument doc = new LayeredDocument(NAME, ENTRIES);
    doc.configure(Optional.empty(), file);

    assertAll(
        () -> assertTrue(doc.isReadOnly()),
        () -> assertThrows(IllegalStateException.class, () -> doc.put(entry("v", "mine"))),
        () -> assertEquals(newer, Files.readString(file)));
  }

  @Test
  void listeners_hear_every_change() throws IOException {
    LayeredDocument doc = new LayeredDocument(NAME, ENTRIES);
    AtomicInteger calls = new AtomicInteger();
    doc.addListener(calls::incrementAndGet);
    doc.configure(Optional.empty(), null);
    doc.put(entry("u", "mine"));
    doc.remove("u");
    doc.reload(site(false, entry("s", "site")));

    assertAll(
        () -> assertEquals(4, calls.get()),
        () -> assertEquals(Map.of("s", "site@SITE"), view(doc)),
        () -> assertThrows(IllegalArgumentException.class, () -> doc.put(entry(" ", "x"))));
  }
}
