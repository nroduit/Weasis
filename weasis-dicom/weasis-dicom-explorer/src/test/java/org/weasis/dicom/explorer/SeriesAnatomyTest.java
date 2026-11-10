/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.dicom.ref.BodyPart;
import org.weasis.dicom.ref.RegionGroup;

/**
 * The anatomy the DICOM verbs answer and filter on: a Table L-1 body part whenever one is named.
 */
class SeriesAnatomyTest {

  @Test
  void aBodyPartExaminedTermAloneBecomesTheRegionOfTheTable() {
    AnatomicRegion region = DicomModelCommands.normalized(null, "CHEST"); // NON-NLS
    assertAll(
        () -> assertTrue(region.getRegion() instanceof BodyPart),
        () -> assertEquals("CHEST", region.getRegion().getLegacyCode()), // NON-NLS
        () -> assertTrue(region.isIn(RegionGroup.CHEST)),
        () ->
            assertNull(
                DicomModelCommands.normalized(null, "NOT-A-PART"),
                "an unknown term stays unknown")); // NON-NLS
  }

  @Test
  void aRetiredSnomedRtCodeResolvesToItsBodyPartAndKeepsItsLaterality() {
    Attributes dcm = new Attributes();
    Sequence sequence = dcm.newSequence(Tag.AnatomicRegionSequence, 1);
    Attributes item = new Attributes();
    item.setString(Tag.CodeValue, VR.SH, "T-04000"); // NON-NLS
    item.setString(Tag.CodingSchemeDesignator, VR.SH, "SRT"); // NON-NLS
    item.setString(Tag.CodeMeaning, VR.LO, "Breast"); // NON-NLS
    sequence.add(item);
    AnatomicRegion read = AnatomicRegion.read(dcm);
    AnatomicRegion region = DicomModelCommands.normalized(read, null);
    assertAll(
        () -> assertTrue(region.getRegion() instanceof BodyPart, String.valueOf(region)),
        () -> assertEquals("76752008", region.getRegion().getCodeValue()), // NON-NLS
        () -> assertTrue(region.isIn(RegionGroup.BREAST)),
        () ->
            assertTrue(
                DicomModelCommands.anatomySelector("BREAST").matches(region), // NON-NLS
                "the group filter sees it"),
        () ->
            assertTrue(
                DicomModelCommands.anatomySelector("SRT:T-04000").matches(region), // NON-NLS
                "the retired code names the same region"));
  }

  @Test
  void anUnknownNotationNamesTheThreeForms() {
    IllegalArgumentException wrong =
        assertThrows(
            IllegalArgumentException.class,
            () -> DicomModelCommands.anatomySelector("nope")); // NON-NLS
    assertTrue(wrong.getMessage().contains("region group"), wrong.getMessage());
  }
}
