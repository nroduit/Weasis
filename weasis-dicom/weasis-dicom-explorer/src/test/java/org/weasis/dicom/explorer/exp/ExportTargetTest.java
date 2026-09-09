/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.exp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.media.DicomDirWriter;
import org.dcm4che3.media.RecordType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ExportTargetTest {

  @TempDir Path tempDir;

  @Test
  void file_IDs_are_the_path_components_below_the_export_root() {
    File root = tempDir.resolve("export").toFile();
    try (ExportTarget target = new ExportTarget(root)) {
      File file = new File(new File(new File(root, "DICOM"), "abc"), "def");

      assertArrayEquals(new String[] {"DICOM", "abc", "def"}, target.toFileIDs(file));
    }
  }

  @Test
  void a_directory_is_created_once_and_later_calls_are_free() throws IOException {
    File root = tempDir.resolve("export").toFile();
    try (ExportTarget target = new ExportTarget(root)) {
      File series = new File(new File(root, "study"), "series");

      target.ensureDirectory(series);
      Files.delete(series.toPath());
      target.ensureDirectory(series);

      assertFalse(Files.exists(series.toPath()), "second call must not touch the file system");
    }
  }

  @Test
  void a_local_destination_is_written_in_place() throws IOException {
    File root = tempDir.resolve("export").toFile();
    try (ExportTarget target = new ExportTarget(root)) {
      target.ensureDirectory(root);
      File destination = new File(root, "instance");
      AtomicInteger written = new AtomicInteger();

      Boolean ok =
          target.write(
              destination,
              file -> {
                try {
                  Files.writeString(file.toPath(), "DICM", StandardCharsets.US_ASCII);
                  written.incrementAndGet();
                  return true;
                } catch (IOException e) {
                  return false;
                }
              },
              Boolean::booleanValue);

      assertTrue(ok);
      assertEquals(1, written.get());
      assertEquals("DICM", Files.readString(destination.toPath()));
      assertFalse(target.isStaged());
    }
  }

  @Test
  void the_DICOMDIR_of_a_local_export_is_written_in_the_export_root() throws IOException {
    File root = tempDir.resolve("export").toFile();
    try (ExportTarget target = new ExportTarget(root)) {
      target.ensureDirectory(root);
      DicomDirWriter writer = target.openDicomDir();
      Attributes patient = new Attributes();
      patient.setString(Tag.DirectoryRecordType, VR.CS, RecordType.PATIENT.name());
      patient.setString(Tag.PatientID, VR.LO, "P1");
      writer.addRootDirectoryRecord(patient);
      target.closeDicomDir(writer);

      assertTrue(Files.size(root.toPath().resolve("DICOMDIR")) > 0);
    }
  }

  @Test
  void a_missing_writer_is_ignored_on_close() throws IOException {
    try (ExportTarget target = new ExportTarget(tempDir.toFile())) {
      target.closeDicomDir(null);
      assertEquals(tempDir.toFile(), target.root());
    }
  }
}
