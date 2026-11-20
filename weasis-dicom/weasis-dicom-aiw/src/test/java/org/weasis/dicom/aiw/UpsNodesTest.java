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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Code;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.aiw.UpsNodes.Node;
import org.weasis.dicom.explorer.DicomWebOrigin;

/** The document of the servers: what the preference page writes is what the explorer reads. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class UpsNodesTest {

  @TempDir Path folder;

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

  @Test
  void what_is_saved_is_read_back_and_a_server_left_out_is_removed() throws IOException {
    Path file = folder.resolve("upsNodes.json");
    UpsNodes.install(file);
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
    UpsNodes.install(folder.resolve("upsNodes.json"));
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
