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

import jakarta.json.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

// Changes the folders of the static store, read by the other tests that start UICore
@Isolated
class DocumentVersionsTest {

  @AfterEach
  void unconfigure() {
    DocumentVersions.clear();
    DocumentStore.configure(null, null);
  }

  private static void write(Path file, String value) throws IOException {
    ListDocument.writeAtomic(
        file, Json.createObjectBuilder().add("value", value).build()); // NON-NLS
  }

  @Test
  void theVersionBeforeTheFirstWriteAfterACheckpointComesBackOnRestore(@TempDir Path dir)
      throws IOException {
    DocumentStore.configure(dir.resolve("pref"), dir.resolve("config"));
    Path user = DocumentStore.folder(org.weasis.core.api.setting.Scope.USER);
    Path presets = user.resolve("presets.json");
    Path nodes = user.resolve("nodes.json");
    Path site = dir.resolve("site").resolve("presets.json");
    write(presets, "v1");

    DocumentVersions.open("cp1");
    write(presets, "v2");
    write(presets, "v3");
    Instant between = Instant.now();
    DocumentVersions.open("cp2");
    write(nodes, "n1");
    write(site, "site");

    assertAll(
        "one version per document per checkpoint, user and machine documents only",
        () ->
            assertEquals(List.of("nodes.json", "presets.json"), DocumentVersions.documents("cp1")),
        () -> assertEquals(List.of("nodes.json"), DocumentVersions.documents("cp2")),
        () ->
            assertEquals(List.of("presets.json"), DocumentVersions.documentsBefore("cp1", between)),
        () -> assertEquals(List.of(), DocumentVersions.documents("unknown")),
        () ->
            assertTrue(
                new String(DocumentVersions.versions("cp1").getFirst().previous()).contains("v1"),
                "the version kept is the one before the first write"));

    DocumentVersions.Restored second = DocumentVersions.restore("cp2");
    assertAll(
        "a rollback to the newer checkpoint removes the document created since",
        () -> assertEquals(List.of("nodes.json"), second.documents()),
        () -> assertTrue(second.problems().isEmpty()),
        () -> assertFalse(Files.exists(nodes)),
        () -> assertTrue(Files.readString(presets).contains("v3")));

    DocumentVersions.Restored first = DocumentVersions.restore("cp1");
    assertAll(
        "a rollback to the older one puts the presets back and restores nothing twice",
        () -> assertEquals(List.of("presets.json", "nodes.json"), first.documents()),
        () -> assertTrue(Files.readString(presets).contains("v1")),
        () -> assertFalse(Files.exists(nodes)),
        () -> assertTrue(Files.readString(site).contains("site"), "site documents are never kept"),
        () -> assertEquals(List.of(), DocumentVersions.documents("cp1")),
        () -> assertTrue(DocumentVersions.restore("cp1").documents().isEmpty()));
  }

  @Test
  void aRegistryReReadsItsDocumentWhenARollbackPutsItBack(@TempDir Path dir) throws IOException {
    DocumentStore.configure(dir.resolve("pref"), null);
    Path user = DocumentStore.folder(org.weasis.core.api.setting.Scope.USER);
    LayeredDocument document = new LayeredDocument("things.json", "things"); // NON-NLS
    document.configure(java.util.Optional.empty(), user.resolve("things.json"));
    document.put(Json.createObjectBuilder().add("id", "one").build()); // NON-NLS
    DocumentVersions.open("cp");
    document.put(Json.createObjectBuilder().add("id", "two").build()); // NON-NLS
    assertEquals(2, document.entries().size());
    DocumentVersions.restore("cp");
    assertAll(
        () ->
            assertEquals(1, document.entries().size(), "the registry re-read the earlier version"),
        () -> assertEquals("one", document.entries().getFirst().id()));
  }

  @Test
  void aClosedCheckpointKeepsNothingAndTheImportBackupFollowsIt(@TempDir Path dir)
      throws IOException {
    DocumentStore.configure(dir.resolve("pref"), null);
    Path user = DocumentStore.folder(org.weasis.core.api.setting.Scope.USER);
    DocumentVersions.open("cp");
    DocumentVersions.importBackup("cp", dir.resolve("backup.zip"));
    DocumentVersions.importBackup("ghost", dir.resolve("other.zip"));
    write(user.resolve("a.json"), "a");
    assertAll(
        () -> assertEquals(List.of("cp"), DocumentVersions.open()),
        () ->
            assertEquals(
                dir.resolve("backup.zip"), DocumentVersions.importBackup("cp").orElseThrow()),
        () ->
            assertTrue(DocumentVersions.importBackup("ghost").isEmpty(), "an unknown checkpoint"));
    DocumentVersions.close("cp");
    write(user.resolve("b.json"), "b");
    assertAll(
        () -> assertEquals(List.of(), DocumentVersions.documents("cp")),
        () -> assertTrue(DocumentVersions.importBackup("cp").isEmpty()),
        () -> assertEquals(List.of(), DocumentVersions.open()));
  }
}
