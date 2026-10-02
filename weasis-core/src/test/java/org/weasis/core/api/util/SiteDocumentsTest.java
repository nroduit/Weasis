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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
