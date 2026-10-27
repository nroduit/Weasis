/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomSeries;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SubtractionServiceTest {

  @Test
  void the_stem_drops_the_contrast_marks_of_a_description() {
    assertAll(
        () -> assertEquals("braint1trans", SubtractionService.stem(named("BRAIN/T1_TRANS+C"))),
        () -> assertEquals("braint1trans", SubtractionService.stem(named("BRAIN/T1_TRANS"))),
        () -> assertEquals("t1fs", SubtractionService.stem(named("T1 FS post Gado"))),
        () -> assertEquals("t1fs", SubtractionService.stem(named("t1 fs pre"))),
        () -> assertEquals("t1tsetra", SubtractionService.stem(named("t1_tse_tra KM"))),
        () -> assertEquals("", SubtractionService.stem(named(null))));
  }

  @Test
  void the_same_sequence_without_contrast_is_proposed_before_any_other_series() {
    DicomSeries post = named("BRAIN/T1_TRANS+C", 10, "APPLIED"); // NON-NLS
    DicomSeries pdT2 = named("BRAIN/PD_T2_TRANS", 4, null); // NON-NLS
    DicomSeries flair = named("BRAIN/FLAIR_TRANS", 5, null); // NON-NLS
    DicomSeries pre = named("BRAIN/T1_TRANS", 3, null); // NON-NLS
    DicomSeries later = named("BRAIN/T1_TRANS+C", 13, "APPLIED"); // NON-NLS
    assertSame(
        pre, SubtractionService.proposeMask(post, List.of(pdT2, flair, later, pre)).orElseThrow());
    // Without the same sequence, the series without agent and the lowest number
    assertSame(pdT2, SubtractionService.proposeMask(post, List.of(flair, pdT2)).orElseThrow());
    assertTrue(SubtractionService.proposeMask(post, List.of()).isEmpty());
  }

  private static DicomSeries named(String description) {
    return named(description, 1, null);
  }

  private static DicomSeries named(String description, int number, String agent) {
    Attributes a =
        SyntheticSeries.slice("1.2.3." + number, "MR", 1, 0, 1.0, 256, SyntheticSeries.FRAME);
    if (description != null) {
      a.setString(Tag.SeriesDescription, VR.LO, description);
    }
    a.setInt(Tag.SeriesNumber, VR.IS, number);
    if (agent != null) {
      a.setString(Tag.ContrastBolusAgent, VR.LO, agent);
    }
    return SyntheticSeries.series(a);
  }
}
