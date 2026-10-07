/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link ManifestFiles#localFile}. The URI of a Windows file has a drive letter after a
 * leading slash ({@code /C:/...}), which is not a valid path on that platform.
 */
class ManifestFilesTest {

  @Test
  void localXmlManifestResolvesToItsFile(@TempDir Path dir) throws IOException {
    Path folder = Files.createDirectory(dir.resolve("my manifests"));
    Path manifest = Files.writeString(folder.resolve("wado_1.xml"), "<manifest/>");

    assertEquals(Optional.of(manifest), ManifestFiles.localFile(manifest.toUri()));
  }

  @Test
  void localFileWithAnotherExtensionIsNotParsedInPlace(@TempDir Path dir) throws IOException {
    Path file = Files.writeString(dir.resolve("wado_1.manifest"), "<manifest/>");

    assertTrue(ManifestFiles.localFile(file.toUri()).isEmpty());
  }

  @Test
  void remoteManifestIsNotLocal() {
    assertTrue(ManifestFiles.localFile(URI.create("https://pacs.example.org/wado.xml")).isEmpty());
  }
}
