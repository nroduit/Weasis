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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.weasis.core.api.setting.Scope;

// Changes the folders of the static store, read by the other tests that start UICore
@Isolated
@DisplayNameGeneration(ReplaceUnderscores.class)
class DocumentStoreTest {

  // Before as well: another test class may have left the static store configured
  @BeforeEach
  @AfterEach
  void unconfigure() {
    DocumentStore.configure(null, null);
  }

  @Test
  void each_scope_has_its_folder(@TempDir Path dir) {
    DocumentStore.configure(dir.resolve("pref"), dir.resolve("config")); // NON-NLS
    assertAll(
        () ->
            assertEquals(
                dir.resolve("pref/documents/windowPresets.json"), // NON-NLS
                DocumentStore.locate(Scope.USER, "windowPresets.json")), // NON-NLS
        () ->
            assertEquals(
                dir.resolve("config/machine/documents/dicomLaunchers.json"), // NON-NLS
                DocumentStore.locate(Scope.MACHINE, "dicomLaunchers.json")), // NON-NLS
        () -> assertNull(DocumentStore.folder(Scope.SITE)));
  }

  @Test
  void the_earlier_file_is_copied_once_and_left_in_place(@TempDir Path dir) throws IOException {
    Path old = Files.writeString(dir.resolve("customWindowPresets.json"), "[]"); // NON-NLS
    Path older = Files.writeString(dir.resolve("older.json"), "older"); // NON-NLS
    DocumentStore.configure(dir.resolve("pref"), dir.resolve("config")); // NON-NLS

    Path file = DocumentStore.locate(Scope.USER, "windowPresets.json", null, old, older);
    Files.writeString(file, "[{\"id\":\"user.x\"}]"); // NON-NLS
    Path again = DocumentStore.locate(Scope.USER, "windowPresets.json", old, older);

    assertAll(
        () -> assertEquals(file, again),
        () -> assertEquals("[{\"id\":\"user.x\"}]", Files.readString(again)),
        () -> assertEquals("[]", Files.readString(old)),
        () ->
            assertTrue(
                DocumentStore.declared().stream()
                    .anyMatch(
                        d -> d.name().equals("windowPresets.json") && d.scope() == Scope.USER)));
  }

  @Test
  void without_a_folder_the_earlier_place_is_kept(@TempDir Path dir) {
    Path old = dir.resolve("dicomNodes.json"); // NON-NLS
    assertAll(
        () -> assertEquals(old, DocumentStore.locate(Scope.USER, "dicomNodes.json", old)),
        () -> assertNull(DocumentStore.locate(Scope.USER, "dicomNodes.json")));
  }

  @Test
  void every_list_document_carries_kind_id_schema_and_updated(@TempDir Path dir)
      throws IOException {
    Path file = dir.resolve("dicomNodes.json"); // NON-NLS
    ListDocument.write(file, "nodes", List.of(Json.createObjectBuilder().add("id", "a").build()));
    JsonObject json = Json.createReader(new StringReader(Files.readString(file))).readObject();

    assertAll(
        () -> assertEquals(ListDocument.KIND, json.getString("kind")),
        () -> assertEquals("dicomNodes", json.getString("id")),
        () -> assertEquals(1, json.getInt("schema")),
        () -> assertTrue(json.containsKey("updated")),
        () -> assertEquals(1, ListDocument.read(file, "nodes").entries().size()),
        () -> assertFalse(Files.list(dir).anyMatch(p -> p.toString().endsWith(".tmp"))));
  }

  @Test
  void the_schema_is_read_from_schema_or_from_the_earlier_version_key() {
    JsonObject version = Json.createObjectBuilder().add("version", 1).build(); // NON-NLS
    JsonObject newer = Json.createObjectBuilder().add("schema", 2).build(); // NON-NLS
    JsonObject newerVersion = Json.createObjectBuilder().add("version", 3).build(); // NON-NLS

    assertAll(
        () -> assertEquals(1, ListDocument.checkSchema(version, 1, "test")),
        () -> assertEquals(1, ListDocument.checkSchema(JsonValueEmpty.OBJECT, 1, "test")),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> ListDocument.checkSchema(newer, 1, "test")),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> ListDocument.checkSchema(newerVersion, 1, "test")));
  }

  private static final class JsonValueEmpty {
    static final JsonObject OBJECT = Json.createObjectBuilder().build();
  }
}
