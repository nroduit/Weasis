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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.service.SecretStore;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ListDocumentTest {

  private static JsonObject entry(String id) {
    return Json.createObjectBuilder().add("id", id).build();
  }

  @Test
  void writes_an_envelope_with_schema_and_updated_and_reads_it_back(@TempDir Path dir)
      throws IOException {
    Path file = dir.resolve("prefs").resolve("nodes.json");

    ListDocument.write(file, "nodes", List.of(entry("a"), entry("b")));
    ListDocument.Content content = ListDocument.read(file, "nodes");

    assertAll(
        () -> assertEquals(ListDocument.SCHEMA_VERSION, content.schema()),
        () -> assertNotNull(content.updated()),
        () ->
            assertEquals(
                List.of("a", "b"), content.entries().stream().map(o -> o.getString("id")).toList()),
        () ->
            assertTrue(
                Files.list(dir.resolve("prefs")).noneMatch(p -> p.toString().endsWith(".tmp"))));
  }

  @Test
  void reads_a_bare_array_and_refuses_a_newer_schema(@TempDir Path dir) throws IOException {
    Path bare = Files.writeString(dir.resolve("bare.json"), "[{\"id\": \"x\"}]");
    Path newer = Files.writeString(dir.resolve("newer.json"), "{\"schema\": 99, \"nodes\": []}");

    assertAll(
        () -> assertEquals(1, ListDocument.read(bare, "nodes").entries().size()),
        () -> assertThrows(IOException.class, () -> ListDocument.read(newer, "nodes")));
  }

  @Test
  void ids_are_slugged_prefixed_and_made_unique() {
    Set<String> used = Set.of("site.node.pacs-10-0-0-1-104");
    assertAll(
        () -> assertEquals("pacs-10-0-0-1-104", EntryIds.slug("PACS", "10.0.0.1", "104")),
        () -> assertEquals("entry", EntryIds.slug("", null, "  ")),
        () ->
            assertEquals(
                "site.node.pacs-10-0-0-1-104",
                EntryIds.derived(EntryIds.SITE_PREFIX, "node", "PACS@10.0.0.1:104")),
        () ->
            assertEquals(
                "site.node.pacs-10-0-0-1-104#2",
                EntryIds.unique("site.node.pacs-10-0-0-1-104", used)),
        () -> assertEquals("x", EntryIds.unique("x", used)),
        () -> assertTrue(EntryIds.isUser("user.node.x")),
        () -> assertFalse(EntryIds.isUser("site.node.x")));
  }

  @Test
  void a_legacy_document_is_migrated_once_and_kept(@TempDir Path dir) throws IOException {
    Path xml = Files.writeString(dir.resolve("nodes.xml"), "<nodes/>");
    Path json = dir.resolve("nodes.json");

    Optional<Path> first =
        LegacyMigration.migrate(xml, json, "nodes", legacy -> List.of(entry("a")), "nodes");
    Optional<Path> second =
        LegacyMigration.migrate(xml, json, "nodes", legacy -> List.of(entry("b")), "nodes");

    assertAll(
        () -> assertEquals(Optional.of(json), first),
        () -> assertTrue(second.isEmpty(), "never migrated again while the JSON exists"),
        () -> assertTrue(Files.exists(xml), "the XML is left in place"),
        () ->
            assertEquals(
                "a", ListDocument.read(json, "nodes").entries().getFirst().getString("id")),
        () ->
            assertTrue(
                LegacyMigration.migrate(
                        dir.resolve("absent.xml"),
                        dir.resolve("other.json"),
                        "nodes",
                        l -> List.of(),
                        "nodes")
                    .isEmpty()));
  }

  @Test
  void secrets_are_kept_per_entry_in_an_owner_only_file(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("prefs").resolve(SecretStore.FILE);
    SecretStore store = new SecretStore(file);

    store.put("user.auth.keycloak", "clientSecret", "s3cr3t");
    store.putAll("user.web.orthanc", Map.of("Authorization", "Bearer abc", "X-Empty", ""));
    store.put("user.auth.keycloak", "clientSecret", null);

    SecretStore reread = new SecretStore(file);
    assertAll(
        () -> assertTrue(Files.isRegularFile(file)),
        () -> assertTrue(store.get("user.auth.keycloak", "clientSecret").isEmpty()),
        () -> assertEquals(Map.of("Authorization", "Bearer abc"), reread.get("user.web.orthanc")),
        () ->
            assertEquals(
                Optional.of("Bearer abc"), reread.get("user.web.orthanc", "Authorization")),
        () -> {
          try {
            assertEquals(
                "rw-------",
                java.nio.file.attribute.PosixFilePermissions.toString(
                    Files.getPosixFilePermissions(file)));
          } catch (UnsupportedOperationException e) {
            // not a POSIX file system: the ACL path is taken
          }
        });
    reread.remove("user.web.orthanc");
    assertTrue(new SecretStore(file).get("user.web.orthanc").isEmpty());
  }
}
