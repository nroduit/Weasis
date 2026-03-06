/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileFormatFilterTest {

  @Nested
  class IsZipFileForPath {

    @Test
    void returnsFalseForNull() {
      assertFalse(FileFormatFilter.isZipFile((Path) null));
    }

    @Test
    void returnsTrueForPathEndingWithZip() {
      assertTrue(FileFormatFilter.isZipFile(Path.of("archive.zip")));
      assertTrue(FileFormatFilter.isZipFile(Path.of("a", "b", "data.zip")));
    }

    @Test
    void returnsTrueForPathEndingWithZipCaseInsensitive() {
      assertTrue(FileFormatFilter.isZipFile(Path.of("archive.ZIP")));
      assertTrue(FileFormatFilter.isZipFile(Path.of("archive.Zip")));
    }

    @Test
    void returnsFalseForPathWithoutZipExtension() {
      assertFalse(FileFormatFilter.isZipFile(Path.of("archive.dcm")));
      assertFalse(FileFormatFilter.isZipFile(Path.of("readme")));
      assertFalse(FileFormatFilter.isZipFile(Path.of("file.zipx")));
    }

    @Test
    void returnsTrueWhenFilenameIsOnlyZip() {
      assertTrue(FileFormatFilter.isZipFile(Path.of(".zip")));
    }
  }

  @Nested
  class IsZipFileForFile {

    @Test
    void returnsFalseForNull() {
      assertFalse(FileFormatFilter.isZipFile((File) null));
    }

    @Test
    void returnsFalseForDirectory(@TempDir Path tempDir) {
      assertFalse(FileFormatFilter.isZipFile(tempDir.toFile()));
    }

    @Test
    void returnsTrueForExistingFileWithZipExtension(@TempDir Path tempDir) throws IOException {
      Path zipPath = Files.createFile(tempDir.resolve("archive.zip"));
      assertTrue(FileFormatFilter.isZipFile(zipPath.toFile()));
    }

    @Test
    void returnsTrueForExistingFileWithZipExtensionCaseInsensitive(@TempDir Path tempDir)
        throws IOException {
      Path zipPath = Files.createFile(tempDir.resolve("archive.ZIP"));
      assertTrue(FileFormatFilter.isZipFile(zipPath.toFile()));
    }

    @Test
    void returnsFalseForExistingFileWithoutZipExtension(@TempDir Path tempDir)
        throws IOException {
      Path filePath = Files.createFile(tempDir.resolve("data.dcm"));
      assertFalse(FileFormatFilter.isZipFile(filePath.toFile()));
    }
  }
}
