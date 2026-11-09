/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weasis.dicom.codec.hp.TestSeries.ctSlice;
import static org.weasis.dicom.codec.hp.TestSeries.series;

import jakarta.json.JsonObject;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.provenance.Provenance;
import org.weasis.core.api.provenance.Provenance.Kind;
import org.weasis.dicom.codec.hp.FeatureSelector.Key;

/** Every derived feature says where it came from: a header, a table, a heuristic, a computation. */
class SeriesFeatureProvenanceTest {

  private final SeriesFeatureExtractor extractor =
      new SeriesFeatureExtractor(new DerivationTables(), InterestProvider.NONE);

  @Test
  void a_ct_stack_names_the_header_the_table_and_the_computation_behind_each_feature() {
    Attributes first = ctSlice(1, 0);
    first.setString(Tag.SeriesDescription, VR.LO, "Thorax  5mm soft"); // NON-NLS
    first.setString(Tag.ContrastBolusAgent, VR.LO, "OMNIPAQUE 350"); // NON-NLS
    first.setString(Tag.ProtocolName, VR.LO, "CT chest portal venous"); // NON-NLS
    SeriesFeatures f =
        extractor.of(series(first, ctSlice(2, 2.5), ctSlice(3, 5.0), ctSlice(4, 7.5)));

    Provenance role = f.provenanceOf(Key.ROLE.id());
    Provenance orientation = f.provenanceOf(Key.ORIENTATION.id());
    Provenance family = f.provenanceOf(Key.CT_KERNEL_FAMILY.id());
    Provenance contrast = f.provenanceOf(Key.CONTRAST.id());
    Provenance phase = f.provenanceOf(Key.CONTRAST_PHASE.id());
    Provenance spacing = f.provenanceOf(Key.SPACING.id());
    JsonObject json = f.provenanceJson();
    assertAll(
        () -> assertEquals(Kind.HEADER, role.kind()),
        () -> assertEquals("(0008,0008)", role.source()),
        () -> assertEquals(Kind.HEADER, orientation.kind()),
        () -> assertEquals("(0020,0037)", orientation.source()),
        () -> assertEquals(Kind.HEADER, f.provenanceOf(Key.CT_KERNEL.id()).kind()),
        () -> assertEquals(Kind.TABLE, family.kind()),
        () -> assertEquals(DerivationTables.KERNELS, family.source()),
        () -> assertEquals(Kind.HEADER, contrast.kind(), "the Contrast/Bolus module says so"),
        () -> assertEquals(Kind.HEURISTIC, phase.kind(), "the phase is read in the description"),
        () -> assertEquals(Kind.COMPUTED, spacing.kind()),
        () -> assertEquals(1, spacing.from().size()),
        () -> assertEquals(Kind.HEADER, f.provenanceOf(Key.SLICE_THICKNESS.id()).kind()),
        () ->
            assertEquals(
                Kind.HEADER,
                f.provenanceOf(Key.TEMPORAL.id()).kind(),
                "a static stack says which header has no temporal positions"),
        () -> assertNull(f.provenanceOf(Key.MODALITY.id()), "a raw attribute has no provenance"),
        () -> assertEquals("header", json.getJsonObject("orientation").getString("kind")),
        () -> assertEquals(Kind.HEADER, f.sourced(Key.ROLE).provenance().kind()),
        () -> assertEquals("ORIGINAL", f.sourced(Key.ROLE).value()));
  }

  @Test
  void a_contrast_read_in_the_description_is_a_heuristic_and_a_dose_report_too() {
    Attributes byName = ctSlice(1, 0);
    byName.setString(Tag.SeriesDescription, VR.LO, "Abdomen C+ arterial"); // NON-NLS
    SeriesFeatures named = extractor.of(series(byName));

    Attributes dose = ctSlice(1, 0);
    dose.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.7");
    dose.setString(Tag.ImageType, VR.CS, "DERIVED", "SECONDARY"); // NON-NLS
    dose.setString(Tag.SeriesDescription, VR.LO, "Dose report"); // NON-NLS
    SeriesFeatures report = extractor.of(series(dose));

    assertAll(
        () -> assertEquals(Kind.HEURISTIC, named.provenanceOf(Key.CONTRAST.id()).kind()),
        () -> assertEquals("contrast.description", named.provenanceOf(Key.CONTRAST.id()).source()),
        () -> assertEquals(SeriesFeatures.Role.DOSE_REPORT, report.role()),
        () -> assertEquals(Kind.HEURISTIC, report.provenanceOf(Key.ROLE.id()).kind()),
        () -> assertTrue(report.provenanceOf(Key.ROLE.id()).detail().contains("DOSE")));
  }
}
