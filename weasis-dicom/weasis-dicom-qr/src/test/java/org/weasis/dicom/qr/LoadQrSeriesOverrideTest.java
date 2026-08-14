/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;

/**
 * Tests that a retrieved object is filed under the patient the query returned, which is what lets a
 * resumed retrieve see the instances already loaded.
 */
class LoadQrSeriesOverrideTest {

  private static final String STUDY_UID = "1.2.840.10008.1.2.3.4";
  private static final String SERIES_UID = "1.2.840.10008.1.2.3.4.5";
  private static final String SOP_UID = "1.2.840.10008.1.2.3.4.5.6";

  /** Model built from the query answer: the archive reported this patient. */
  private static DicomModel queryModel() {
    DicomModel model = new DicomModel();
    MediaSeriesGroup patient =
        new MediaSeriesGroupNode(TagW.PatientPseudoUID, "PSEUDO", DicomModel.patient.tagView());
    patient.setTag(TagD.get(Tag.PatientID), "QUERY_ID"); // NON-NLS
    patient.setTag(TagD.get(Tag.PatientName), "QUERY^NAME"); // NON-NLS
    model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);

    MediaSeriesGroup study =
        new MediaSeriesGroupNode(TagD.getUID(TagD.Level.STUDY), STUDY_UID, null);
    model.addHierarchyNode(patient, study);

    MediaSeriesGroup series =
        new MediaSeriesGroupNode(TagD.getUID(TagD.Level.SERIES), SERIES_UID, null);
    model.addHierarchyNode(study, series);
    return model;
  }

  /** Object as the archive sends it, here with patient attributes of its own. */
  private static Path writeObject(Path dir, String patientId, String patientName) throws Exception {
    Attributes dataset = new Attributes();
    dataset.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
    dataset.setString(Tag.SOPInstanceUID, VR.UI, SOP_UID);
    dataset.setString(Tag.StudyInstanceUID, VR.UI, STUDY_UID);
    dataset.setString(Tag.SeriesInstanceUID, VR.UI, SERIES_UID);
    dataset.setString(Tag.PatientID, VR.LO, patientId);
    dataset.setString(Tag.PatientName, VR.PN, patientName);
    dataset.setString(Tag.Modality, VR.CS, "OT");

    Path file = dir.resolve("object.dcm");
    try (DicomOutputStream out = new DicomOutputStream(file.toFile())) {
      out.writeDataset(dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian), dataset);
      out.finish();
    }
    return file;
  }

  private static Attributes read(Path file) throws Exception {
    try (DicomInputStream in = new DicomInputStream(file.toFile())) {
      return in.readDataset();
    }
  }

  @Test
  void aDivergingPatientIdentityIsRealigned(@TempDir Path dir) throws Exception {
    Path file = writeObject(dir, "ARCHIVE_ID", "ARCHIVE^NAME"); // NON-NLS

    LoadQrSeries.applyIdentityOverrides(file, queryModel(), null);

    Attributes result = read(file);
    assertAll(
        () -> assertEquals("QUERY_ID", result.getString(Tag.PatientID)),
        () -> assertEquals("QUERY^NAME", result.getString(Tag.PatientName)),
        // The rest of the object must survive the rewrite
        () -> assertEquals(SOP_UID, result.getString(Tag.SOPInstanceUID)),
        () -> assertEquals(SERIES_UID, result.getString(Tag.SeriesInstanceUID)),
        () -> assertEquals(STUDY_UID, result.getString(Tag.StudyInstanceUID)),
        () -> assertEquals("OT", result.getString(Tag.Modality)));
  }

  @Test
  void aConsistentObjectIsLeftUntouched(@TempDir Path dir) throws Exception {
    Path file = writeObject(dir, "QUERY_ID", "QUERY^NAME"); // NON-NLS
    long before = file.toFile().lastModified();

    LoadQrSeries.applyIdentityOverrides(file, queryModel(), null);

    assertAll(
        () -> assertEquals(before, file.toFile().lastModified()),
        () -> assertEquals("QUERY_ID", read(file).getString(Tag.PatientID)));
  }

  @Test
  void anObjectOfAnUnknownSeriesIsLeftUntouched(@TempDir Path dir) throws Exception {
    Path file = writeObject(dir, "ARCHIVE_ID", "ARCHIVE^NAME"); // NON-NLS
    DicomModel empty = new DicomModel();

    LoadQrSeries.applyIdentityOverrides(file, empty, null);

    assertEquals("ARCHIVE_ID", read(file).getString(Tag.PatientID));
  }
}
