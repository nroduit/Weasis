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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.media.DicomDirReader;
import org.dcm4che3.media.DicomDirWriter;
import org.dcm4che3.media.RecordType;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Referenced File ID (0004,1500) is attacker-controlled data: whatever it holds, it must resolve
 * inside the directory owning the DICOMDIR, without breaking the tolerance real media rely on.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomDirTraversalTest {

  @TempDir Path tempDir;

  @Test
  void relative_segments_climbing_out_of_the_DICOMDIR_directory_are_rejected() throws Exception {
    Path dicomdir = createDicomDir(traversalTo("etc", "passwd"));

    assertNull(resolveInstanceFile(dicomdir));
  }

  @Test
  void separators_smuggled_inside_a_component_cannot_escape_either() throws Exception {
    Path dicomdir = createDicomDir("../../etc/passwd");

    assertNull(resolveInstanceFile(dicomdir));
  }

  @Test
  void an_absolute_component_is_rejected() throws Exception {
    Path dicomdir = createDicomDir("/etc/passwd");

    assertNull(resolveInstanceFile(dicomdir));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void a_symbolic_link_leading_out_of_the_DICOMDIR_directory_is_rejected() throws Exception {
    Path dicomdir = createDicomDir("LINK", "IMG00001");
    Path outside = Files.createDirectories(tempDir.resolve("outside"));
    Files.createFile(outside.resolve("IMG00001"));
    Files.createSymbolicLink(dicomdir.getParent().resolve("LINK"), outside);

    assertNull(resolveInstanceFile(dicomdir));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void a_symbolic_link_staying_inside_the_DICOMDIR_directory_is_tolerated() throws Exception {
    Path dicomdir = createDicomDir("LINK", "IMG00001");
    Path expected = createFileNextToDicomDir(dicomdir, "SUB", "IMG00001");
    Files.createSymbolicLink(dicomdir.getParent().resolve("LINK"), expected.getParent());

    Path resolved = resolveInstanceFile(dicomdir);

    assertNotNull(resolved);
    assertEquals(expected.toRealPath(), resolved.toRealPath());
  }

  @Test
  void a_plain_relative_file_ID_still_resolves_inside_the_DICOMDIR_directory() throws Exception {
    Path dicomdir = createDicomDir("SUB", "IMG00001");
    Path expected = createFileNextToDicomDir(dicomdir, "SUB", "IMG00001");

    assertEquals(expected, resolveInstanceFile(dicomdir));
  }

  @Test
  void a_non_conformant_separator_inside_a_component_is_tolerated() throws Exception {
    Path dicomdir = createDicomDir("SUB/IMG00001");
    Path expected = createFileNextToDicomDir(dicomdir, "SUB", "IMG00001");

    assertEquals(expected, resolveInstanceFile(dicomdir));
  }

  @Test
  void a_parent_segment_resolving_back_inside_is_tolerated() throws Exception {
    Path dicomdir = createDicomDir("SUB", "..", "IMG00001");
    Path expected = createFileNextToDicomDir(dicomdir, "IMG00001");

    assertEquals(expected, resolveInstanceFile(dicomdir));
  }

  @Test
  void a_lower_case_file_ID_still_resolves_as_on_a_mounted_cdrom() throws Exception {
    Path dicomdir = createDicomDir("SUB", "IMG00001");
    Path expected = createFileNextToDicomDir(dicomdir, "sub", "img00001");

    assertEquals(expected, resolveInstanceFile(dicomdir));
  }

  @Test
  void a_file_ID_missing_the_extension_still_resolves_to_the_single_match() throws Exception {
    Path dicomdir = createDicomDir("SUB", "IMG00001");
    Path expected = createFileNextToDicomDir(dicomdir, "SUB", "IMG00001.dcm");

    assertEquals(expected, resolveInstanceFile(dicomdir));
  }

  /** A Referenced File ID climbing out of the temp directory to an absolute target. */
  private String[] traversalTo(String... target) {
    // Number of ".." needed to reach the filesystem root from the DICOMDIR directory.
    int depth = tempDir.resolve("media").resolve("DICOM").getNameCount();
    List<String> fileID = new ArrayList<>(depth + target.length);
    for (int i = 0; i < depth; i++) {
      fileID.add("..");
    }
    fileID.addAll(List.of(target));
    return fileID.toArray(new String[0]);
  }

  private static Path createFileNextToDicomDir(Path dicomdir, String... path) throws IOException {
    Path file = dicomdir.getParent();
    for (String name : path) {
      file = file.resolve(name);
    }
    Files.createDirectories(file.getParent());
    return Files.createFile(file);
  }

  /** Writes a structurally valid DICOMDIR whose single IMAGE record carries {@code fileID}. */
  private Path createDicomDir(String... fileID) throws IOException {
    Path dir = Files.createDirectories(tempDir.resolve("media").resolve("DICOM"));
    File dicomdir = dir.resolve("DICOMDIR").toFile();
    DicomDirWriter.createEmptyDirectory(dicomdir, UIDUtils.createUID(), "WEASIS_POC", null, null);

    try (DicomDirWriter writer = DicomDirWriter.open(dicomdir)) {
      Attributes patient = newRecord(RecordType.PATIENT);
      patient.setString(Tag.PatientID, VR.LO, "TRAVERSAL_VICTIM");
      patient.setString(Tag.PatientName, VR.PN, "TRAV^VERSAL");
      writer.addRootDirectoryRecord(patient);

      Attributes study = newRecord(RecordType.STUDY);
      study.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
      writer.addLowerDirectoryRecord(patient, study);

      Attributes series = newRecord(RecordType.SERIES);
      series.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
      series.setString(Tag.Modality, VR.CS, "CT");
      writer.addLowerDirectoryRecord(study, series);

      Attributes image = newRecord(RecordType.IMAGE);
      image.setString(Tag.ReferencedSOPClassUIDInFile, VR.UI, UID.CTImageStorage);
      image.setString(Tag.ReferencedSOPInstanceUIDInFile, VR.UI, UIDUtils.createUID());
      image.setString(Tag.ReferencedTransferSyntaxUIDInFile, VR.UI, UID.ExplicitVRLittleEndian);
      image.setString(Tag.ReferencedFileID, VR.CS, fileID);
      writer.addLowerDirectoryRecord(series, image);
    }
    return dicomdir.toPath();
  }

  private static Attributes newRecord(RecordType type) {
    Attributes rec = new Attributes();
    rec.setString(Tag.DirectoryRecordType, VR.CS, type.name());
    return rec;
  }

  /** Walks the DICOMDIR the way DicomDirLoader does, then resolves the IMAGE record. */
  private static Path resolveInstanceFile(Path dicomdir) throws Exception {
    try (DicomDirReader reader = new DicomDirReader(dicomdir.toFile())) {
      Attributes record = reader.findFirstRootDirectoryRecordInUse(true);
      for (int level = 0; level < 3 && record != null; level++) {
        record = reader.findLowerDirectoryRecordInUse(record, true);
      }
      assertNotNull(record, "no IMAGE record found in the crafted DICOMDIR");
      assertEquals(RecordType.IMAGE.name(), record.getString(Tag.DirectoryRecordType));

      return new DicomDirFileResolver(dicomdir)
          .resolve(record.getStrings(Tag.ReferencedFileID))
          .orElse(null);
    }
  }
}
