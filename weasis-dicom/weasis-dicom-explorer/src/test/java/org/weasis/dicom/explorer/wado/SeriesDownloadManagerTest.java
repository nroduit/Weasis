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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDate;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;

/**
 * Tests the manifest tag overrides applied to every downloaded instance by {@link
 * SeriesDownloadManager#applyOverrides(Attributes, int[], MediaSeriesGroup, MediaSeriesGroup)}.
 */
class SeriesDownloadManagerTest {

  private static final String STUDY_UID = "1.2.840.10008.1.2.3.4";

  private static MediaSeriesGroup patientNode() {
    MediaSeriesGroup patient =
        new MediaSeriesGroupNode(TagW.PatientPseudoUID, "PSEUDO_UID", null); // NON-NLS
    patient.setTag(TagD.get(Tag.PatientID), "MERGED_ID"); // NON-NLS
    patient.setTag(TagD.get(Tag.PatientName), "MERGED^NAME"); // NON-NLS
    patient.setTag(TagD.get(Tag.PatientBirthDate), LocalDate.of(1980, 2, 15));
    return patient;
  }

  private static MediaSeriesGroup studyNode() {
    MediaSeriesGroup study =
        new MediaSeriesGroupNode(TagD.getUID(TagD.Level.STUDY), STUDY_UID, null);
    study.setTag(TagD.get(Tag.AccessionNumber), "MERGED_ACC"); // NON-NLS
    study.setTag(TagD.get(Tag.StudyDescription), "Merged study"); // NON-NLS
    return study;
  }

  private static Attributes dataset() {
    Attributes dataset = new Attributes();
    dataset.setString(Tag.PatientID, VR.LO, "ARCHIVE_ID"); // NON-NLS
    dataset.setString(Tag.PatientName, VR.PN, "ARCHIVE^NAME"); // NON-NLS
    dataset.setString(Tag.PatientBirthDate, VR.DA, "19700101");
    dataset.setString(Tag.StudyInstanceUID, VR.UI, "9.9.9.9");
    dataset.setString(Tag.AccessionNumber, VR.SH, "ARCHIVE_ACC"); // NON-NLS
    dataset.setString(Tag.StudyDescription, VR.LO, "Archive study"); // NON-NLS
    return dataset;
  }

  @Test
  void overridesPatientAndStudyValues() {
    Attributes dataset = dataset();
    SeriesDownloadManager.applyOverrides(
        dataset,
        new int[] {
          Tag.PatientID, Tag.PatientName, Tag.PatientBirthDate, Tag.AccessionNumber,
        },
        patientNode(),
        studyNode());

    assertAll(
        () -> assertEquals("MERGED_ID", dataset.getString(Tag.PatientID)),
        () -> assertEquals("MERGED^NAME", dataset.getString(Tag.PatientName)),
        () -> assertEquals("19800215", dataset.getString(Tag.PatientBirthDate)),
        () -> assertEquals("MERGED_ACC", dataset.getString(Tag.AccessionNumber)),
        // Not listed: left as received from the archive.
        () -> assertEquals("Archive study", dataset.getString(Tag.StudyDescription)));
  }

  @Test
  void neverOverridesStudyInstanceUID() {
    Attributes dataset = dataset();
    SeriesDownloadManager.applyOverrides(
        dataset, new int[] {Tag.StudyInstanceUID, Tag.PatientID}, patientNode(), studyNode());

    assertAll(
        () -> assertEquals("9.9.9.9", dataset.getString(Tag.StudyInstanceUID)),
        // The rest of the list is still applied.
        () -> assertEquals("MERGED_ID", dataset.getString(Tag.PatientID)));
  }

  @Test
  void patientValueWinsOverStudyValue() {
    MediaSeriesGroup study = studyNode();
    study.setTag(TagD.get(Tag.PatientID), "STUDY_ID"); // NON-NLS

    Attributes dataset = dataset();
    SeriesDownloadManager.applyOverrides(dataset, new int[] {Tag.PatientID}, patientNode(), study);

    assertEquals("MERGED_ID", dataset.getString(Tag.PatientID));
  }

  @Test
  void keepsValueWhenTagIsUnknownOrNull() {
    MediaSeriesGroup patient = patientNode();
    patient.setTag(TagD.get(Tag.PatientSex), null);

    Attributes dataset = dataset();
    dataset.setString(Tag.PatientSex, VR.CS, "M");
    SeriesDownloadManager.applyOverrides(
        dataset,
        // StationName is in neither node, PatientSex has a null value in the patient node.
        new int[] {Tag.StationName, Tag.PatientSex},
        patient,
        studyNode());

    assertAll(
        () -> assertEquals("M", dataset.getString(Tag.PatientSex)),
        () -> assertFalse(dataset.contains(Tag.StationName)));
  }

  @Test
  void acceptsMissingListOrNodes() {
    Attributes dataset = dataset();
    assertAll(
        () ->
            assertDoesNotThrow(
                () ->
                    SeriesDownloadManager.applyOverrides(
                        dataset, null, patientNode(), studyNode())),
        () ->
            assertDoesNotThrow(
                () ->
                    SeriesDownloadManager.applyOverrides(
                        dataset, new int[] {Tag.PatientID, Tag.AccessionNumber}, null, null)),
        () -> assertEquals("ARCHIVE_ID", dataset.getString(Tag.PatientID)),
        // A missing patient node falls back to the study node.
        () ->
            assertDoesNotThrow(
                () ->
                    SeriesDownloadManager.applyOverrides(
                        dataset, new int[] {Tag.AccessionNumber}, null, studyNode())),
        () -> assertEquals("MERGED_ACC", dataset.getString(Tag.AccessionNumber)));
  }
}
