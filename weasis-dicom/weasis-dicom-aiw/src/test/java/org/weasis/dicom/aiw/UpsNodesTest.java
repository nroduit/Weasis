/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.aiw;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Code;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.setting.Scope;
import org.weasis.core.api.util.DocumentStore;
import org.weasis.dicom.aiw.UpsNodes.Node;
import org.weasis.dicom.explorer.DicomWebOrigin;

/** The document of the servers: what the preference page writes is what the explorer reads. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class UpsNodesTest {

  @TempDir Path folder;
  private Path previous;
  private boolean installed;
  private Path userFolderBefore;
  private Path machineFolderBefore;
  private boolean configuredStore;

  private static Node node(String id, String url, boolean enabled) {
    return new Node(
        id,
        url,
        enabled,
        true,
        "VIEWER",
        true,
        45,
        List.of(new Code("CT-ORGAN-SEG", "99SITE", null, "Organ segmentation")));
  }

  @AfterEach
  void restoreDocument() {
    if (installed) {
      UpsNodes.restore(previous);
    }
    // DocumentStore is process-wide too: the test that installs before any read re-points it, so
    // put its folders back or later tests locate the default document in that test's temp folder.
    if (configuredStore) {
      DocumentStore.configure(prefDir(userFolderBefore), configDir(machineFolderBefore));
    }
  }

  /** The preference dir whose {@code documents} folder {@code userFolder} is. */
  private static Path prefDir(Path userFolder) {
    // configure() resolves the documents folder under prefDir; strip it to get prefDir back.
    return userFolder == null ? null : userFolder.getParent();
  }

  /** The configuration dir whose {@code machine/documents} folder {@code machineFolder} is. */
  private static Path configDir(Path machineFolder) {
    // configure() resolves machine then the documents folder under configDir; strip both.
    return machineFolder == null ? null : machineFolder.getParent().getParent();
  }

  @Test
  void what_is_saved_is_read_back_and_a_server_left_out_is_removed() throws IOException {
    Path file = folder.resolve("upsNodes.json");
    previous = UpsNodes.install(file);
    installed = true;
    UpsNodes.save(List.of(node("pacs", null, true), node("https://b/rs", "https://b/rs/", false)));

    List<Node> all = UpsNodes.all();
    Node first = all.stream().filter(n -> "pacs".equals(n.id())).findFirst().orElseThrow();
    assertAll(
        () -> assertEquals(2, all.size()),
        () -> assertTrue(Files.readString(file).contains("CT-ORGAN-SEG")),
        () -> assertEquals("VIEWER", first.requesterAet()),
        () -> assertTrue(first.requesterInPath()),
        () -> assertEquals(45, first.pollSeconds()),
        () -> assertEquals("Organ segmentation", first.catalog().getFirst().getCodeMeaning()));

    UpsNodes.save(List.of(node("pacs", null, true)));
    assertEquals(List.of("pacs"), UpsNodes.all().stream().map(Node::id).toList());
  }

  @Test
  void a_study_is_served_by_the_enabled_entry_whose_node_or_url_is_its_origin() throws IOException {
    previous = UpsNodes.install(folder.resolve("upsNodes.json"));
    installed = true;
    UpsNodes.save(
        List.of(
            node("pacs", null, true),
            node("byUrl", "https://b/rs", true),
            node("off", "https://c/rs", false)));

    assertAll(
        () ->
            assertEquals(
                "pacs",
                UpsNodes.forOrigin(new DicomWebOrigin("https://a/rs", null, "pacs"))
                    .orElseThrow()
                    .id()),
        () ->
            assertEquals(
                "byUrl",
                UpsNodes.forOrigin(new DicomWebOrigin("https://b/rs/sub", null, null))
                    .orElseThrow()
                    .id()),
        () ->
            assertTrue(
                UpsNodes.forOrigin(new DicomWebOrigin("https://c/rs", null, null)).isEmpty()),
        () ->
            assertTrue(
                UpsNodes.forOrigin(new DicomWebOrigin("https://z/rs", null, null)).isEmpty()));
  }

  @Test
  void install_is_reversible_and_reads_the_previous_document_again() throws IOException {
    Path first = folder.resolve("first.json");
    Path second = folder.resolve("second.json");
    Files.writeString(first, "{\"nodes\": [{\"id\": \"first\", \"enabled\": true}]}");
    Files.writeString(second, "{\"nodes\": [{\"id\": \"second\", \"enabled\": true}]}");

    previous = UpsNodes.install(first);
    installed = true;
    assertEquals(List.of("first"), UpsNodes.all().stream().map(Node::id).toList());
    Path beforeSecond = UpsNodes.install(second);
    assertEquals(List.of("second"), UpsNodes.all().stream().map(Node::id).toList());
    UpsNodes.restore(beforeSecond);
    assertEquals(
        List.of("first"),
        UpsNodes.all().stream().map(Node::id).toList(),
        "restore reads the document that was in force before");
  }

  @Test
  void restore_after_an_install_before_any_read_reinitializes_the_default_document()
      throws IOException {
    // NODES is a process-wide static: an earlier test that read the document has located the
    // default file already. Reset the never-read state explicitly, or install would return that
    // located file instead of null, whatever test ran first.
    UpsNodes.restore(null);

    // Point the default document at a real file: without a configured folder the lazy init has
    // nothing to locate, and a memory-only document hides the difference this test is about.
    userFolderBefore = DocumentStore.folder(Scope.USER);
    machineFolderBefore = DocumentStore.folder(Scope.MACHINE);
    configuredStore = true;
    DocumentStore.configure(folder.resolve("pref"), folder.resolve("config"));
    Path defaultFile = DocumentStore.locate(Scope.USER, UpsNodes.DOCUMENT);
    Files.createDirectories(defaultFile.getParent());
    Files.writeString(defaultFile, "{\"nodes\": [{\"id\": \"default\", \"enabled\": true}]}");

    // install before any read: the default document is not located yet, so there is no previous
    // file and restore must put the initialization state back, not only the path.
    previous = UpsNodes.install(folder.resolve("temp.json"));
    installed = true;
    assertNull(previous, "install before any read sees no previous document");
    UpsNodes.restore(previous);

    assertEquals(
        List.of("default"),
        UpsNodes.all().stream().map(Node::id).toList(),
        "restore(null) must let the next read locate the default document again");
  }

  @Test
  void the_reading_interval_never_goes_under_ten_seconds_and_zero_means_never() {
    assertAll(
        () ->
            assertEquals(
                10, new Node("a", null, true, false, null, false, 3, List.of()).pollSeconds()),
        () ->
            assertEquals(
                0, new Node("a", null, true, false, null, false, 0, List.of()).pollSeconds()),
        () ->
            assertEquals(
                "WEASIS",
                new Node("a", null, true, false, " ", false, 0, List.of()).requesterAet()));
  }
}
