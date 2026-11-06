/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.sr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.model.utils.bean.QuantityKind;
import org.weasis.dicom.explorer.sr.MeasurementReport.ImageRef;
import org.weasis.dicom.explorer.sr.MeasurementReport.Region;

@DisplayNameGeneration(ReplaceUnderscores.class)
class MeasurementReportTest {

  private static Attributes report() {
    Attributes source = new Attributes();
    source.setString(Tag.PatientName, VR.PN, "DOE^JANE"); // NON-NLS
    source.setString(Tag.PatientID, VR.LO, "P1"); // NON-NLS
    source.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3"); // NON-NLS
    source.setString(Tag.SOPInstanceUID, VR.UI, "9.9.9"); // NON-NLS
    ImageRef image =
        new ImageRef(
            "1.2.3",
            "1.2.3.4",
            UID.VLWholeSlideMicroscopyImageStorage,
            "1.2.3.4.5",
            null,
            true); // NON-NLS
    Region line =
        new Region(
            "line 1", // NON-NLS
            "line", // NON-NLS
            "POLYLINE", // NON-NLS
            new double[] {10.5, 20.5, 110.5, 20.5},
            List.of(
                MeasurementReport.value(
                        QuantityKind.LENGTH, "measure.length", 49.57, Unit.MICROMETER)
                    .orElseThrow())); // NON-NLS
    Region area =
        new Region(
            "polygon 2", // NON-NLS
            "polygon", // NON-NLS
            "POLYLINE", // NON-NLS
            new double[] {0.5, 0.5, 10.5, 0.5, 10.5, 10.5, 0.5, 0.5},
            List.of(
                MeasurementReport.value(QuantityKind.AREA, "measure.area", 1234.5, Unit.MICROMETER)
                    .orElseThrow(), // NON-NLS
                MeasurementReport.value(
                        QuantityKind.LENGTH, "measure.perimeter", 99.0, Unit.MICROMETER)
                    .orElseThrow())); // NON-NLS
    return MeasurementReport.build(
        source, image, List.of(line, area), "Slide measurements"); // NON-NLS
  }

  @Test
  void the_report_follows_tid_1500_with_one_planar_group_per_region() {
    Attributes sr = report();
    Sequence content = sr.getSequence(Tag.ContentSequence);
    Attributes measurements = content.get(content.size() - 1);
    Sequence groups = measurements.getSequence(Tag.ContentSequence);
    Attributes first = groups.getFirst();
    Attributes scoord = first.getSequence(Tag.ContentSequence).get(2);
    Attributes imageRef =
        scoord
            .getSequence(Tag.ContentSequence)
            .getFirst()
            .getNestedDataset(Tag.ReferencedSOPSequence);
    Attributes num = first.getSequence(Tag.ContentSequence).get(3);
    Attributes measured = num.getNestedDataset(Tag.MeasuredValueSequence);
    assertAll(
        () -> assertEquals(UID.ComprehensiveSRStorage, sr.getString(Tag.SOPClassUID)),
        () -> assertEquals("DOE^JANE", sr.getString(Tag.PatientName)), // NON-NLS
        () -> assertEquals("1.2.3", sr.getString(Tag.StudyInstanceUID)), // NON-NLS
        () ->
            assertEquals(
                "126000",
                sr.getNestedDataset(Tag.ConceptNameCodeSequence)
                    .getString(Tag.CodeValue)), // NON-NLS
        () ->
            assertEquals(
                "1500",
                sr.getNestedDataset(Tag.ContentTemplateSequence)
                    .getString(Tag.TemplateIdentifier)), // NON-NLS
        () -> assertEquals(2, groups.size()),
        () ->
            assertEquals(
                "1410",
                first
                    .getNestedDataset(Tag.ContentTemplateSequence)
                    .getString(Tag.TemplateIdentifier)), // NON-NLS
        () -> assertEquals("POLYLINE", scoord.getString(Tag.GraphicType)), // NON-NLS
        () ->
            assertArrayEquals(
                new float[] {10.5f, 20.5f, 110.5f, 20.5f}, scoord.getFloats(Tag.GraphicData)),
        () ->
            assertEquals("1.2.3.4.5", imageRef.getString(Tag.ReferencedSOPInstanceUID)), // NON-NLS
        () -> assertEquals("VOLUME", imageRef.getString(Tag.PixelOriginInterpretation)), // NON-NLS
        () ->
            assertEquals(
                "410668003",
                num.getNestedDataset(Tag.ConceptNameCodeSequence)
                    .getString(Tag.CodeValue)), // NON-NLS
        () -> assertEquals(49.57, measured.getDouble(Tag.NumericValue, 0), 1e-9),
        () ->
            assertEquals(
                "um",
                measured
                    .getNestedDataset(Tag.MeasurementUnitsCodeSequence)
                    .getString(Tag.CodeValue)), // NON-NLS
        () ->
            assertEquals(
                "1.2.3.4",
                sr.getNestedDataset(Tag.CurrentRequestedProcedureEvidenceSequence)
                    .getNestedDataset(Tag.ReferencedSeriesSequence)
                    .getString(Tag.SeriesInstanceUID))); // NON-NLS
  }

  @Test
  void values_get_a_concept_by_kind_and_key() {
    assertAll(
        () ->
            assertEquals(
                "42798000",
                MeasurementReport.value(QuantityKind.AREA, "measure.area", 1, Unit.MILLIMETER)
                    .orElseThrow()
                    .concept()
                    .getCodeValue()), // NON-NLS
        () ->
            assertEquals(
                "mm2",
                MeasurementReport.value(QuantityKind.AREA, "measure.area", 1, Unit.MILLIMETER)
                    .orElseThrow()
                    .ucum()), // NON-NLS
        () ->
            assertEquals(
                "131191004",
                MeasurementReport.value(
                        QuantityKind.LENGTH, "measure.perimeter", 1, Unit.MICROMETER)
                    .orElseThrow()
                    .concept()
                    .getCodeValue()), // NON-NLS
        () ->
            assertEquals(
                "81827009",
                MeasurementReport.value(QuantityKind.LENGTH, "measure.diameter", 1, Unit.MICROMETER)
                    .orElseThrow()
                    .concept()
                    .getCodeValue()), // NON-NLS
        () ->
            assertTrue(
                MeasurementReport.value(QuantityKind.ANGLE, "measure.angle", 30, Unit.MICROMETER)
                    .isEmpty()), // NON-NLS
        () ->
            assertTrue(
                MeasurementReport.value(QuantityKind.LENGTH, "measure.dx", 30, Unit.MICROMETER)
                    .isEmpty()), // NON-NLS
        () ->
            assertTrue(
                MeasurementReport.value(QuantityKind.LENGTH, "ombb.length", 30, Unit.MICROMETER)
                    .isEmpty()), // NON-NLS
        () ->
            assertTrue(
                MeasurementReport.value(
                        QuantityKind.LENGTH, "measure.length", Double.NaN, Unit.MICROMETER)
                    .isEmpty())); // NON-NLS
  }

  @Test
  void the_file_reads_back(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("report.dcm"); // NON-NLS
    Attributes sr = report();
    MeasurementReport.write(sr, file);
    try (DicomInputStream in = new DicomInputStream(file.toFile())) {
      Attributes fmi = in.readFileMetaInformation();
      Attributes read = in.readDataset();
      assertAll(
          () -> assertEquals(UID.ExplicitVRLittleEndian, fmi.getString(Tag.TransferSyntaxUID)),
          () -> assertEquals(sr.getString(Tag.SOPInstanceUID), read.getString(Tag.SOPInstanceUID)),
          () -> assertEquals("SR", read.getString(Tag.Modality))); // NON-NLS
    }
  }
}
