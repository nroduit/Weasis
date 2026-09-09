/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomDirImportTest {

  @TempDir Path media;

  @Test
  void the_DICOMDIR_at_the_root_of_the_media_is_found() throws Exception {
    Path dicomdir = Files.createFile(media.resolve("DICOMDIR"));

    assertEquals(dicomdir, DicomDirImport.findDicomDir(media));
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void a_lower_case_DICOMDIR_as_on_a_cdrom_mounted_on_linux_is_found() throws Exception {
    Path dicomdir = Files.createFile(media.resolve("dicomdir"));

    assertEquals(dicomdir, DicomDirImport.findDicomDir(media));
  }

  @Test
  void a_media_without_DICOMDIR_yields_nothing() throws Exception {
    Files.createFile(media.resolve("README.TXT"));

    assertNull(DicomDirImport.findDicomDir(media));
  }
}
