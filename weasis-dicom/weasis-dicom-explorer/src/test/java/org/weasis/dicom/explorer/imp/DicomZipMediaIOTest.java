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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.LoadLocalDicom;

/**
 * A readable directory named DICOMDIR is not a PS3.10 index. {@code canRead()} alone selects the
 * index reader and skips the recursive import.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomZipMediaIOTest {

  @TempDir Path tempDir;

  @Test
  void a_DICOMDIR_directory_is_imported_recursively() throws Exception {
    Path zip = tempDir.resolve("archive.zip");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
      out.putNextEntry(new ZipEntry("DICOMDIR/image.dcm"));
      out.write(0);
      out.closeEntry();
    }

    List<List<?>> localArgs = new ArrayList<>();
    try (MockedConstruction<LoadLocalDicom> local =
            mockConstruction(
                LoadLocalDicom.class,
                (constructed, context) -> localArgs.add(List.copyOf(context.arguments())));
        MockedConstruction<DicomDirLoader> index = mockConstruction(DicomDirLoader.class)) {
      DicomZipMediaIO.loadDicomZip(
          zip.toFile(), mock(DicomModel.class), OpeningViewer.ALL_PATIENTS, null);
      DicomModel.LOADING_EXECUTOR.submit(() -> {}).get(30, TimeUnit.SECONDS);

      assertEquals(0, index.constructed().size());
      assertEquals(1, local.constructed().size());
      List<?> args = localArgs.getFirst();
      File[] roots = (File[]) args.getFirst();
      assertEquals(Boolean.TRUE, args.get(1));
      assertTrue(Files.isRegularFile(roots[0].toPath().resolve("DICOMDIR/image.dcm")));
    }
  }
}
