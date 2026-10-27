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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.viewer2d.subtraction.SubtractionSpec.Dsa;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DsaMaskTest {

  private static final String RUN = "1.2.3.30"; // NON-NLS

  @Test
  void an_averaged_mask_is_read_with_its_range_and_shift() {
    Attributes item = new Attributes();
    item.setString(Tag.MaskOperation, VR.CS, "AVG_SUB"); // NON-NLS
    item.setInt(Tag.MaskFrameNumbers, VR.US, 2, 3);
    item.setInt(Tag.ApplicableFrameRange, VR.US, 4, 20);
    item.setFloat(Tag.MaskSubPixelShift, VR.FL, 0.5f, -1.0f);
    DicomSeries run = run(item, "SUB"); // NON-NLS
    Dsa dsa = DsaMask.encodedMask(run).orElseThrow();
    assertAll(
        () -> assertTrue(DsaMask.isRun(run)),
        () -> assertEquals(List.of(2, 3), dsa.maskFrames()),
        () -> assertEquals(4, dsa.firstFrame()),
        () -> assertEquals(20, dsa.lastFrame()),
        () -> assertEquals(0.5, dsa.shiftRows()),
        () -> assertEquals(-1.0, dsa.shiftColumns()),
        () -> assertTrue(DsaMask.recommendsSubtraction(run)),
        () -> assertEquals(dsa, DsaMask.proposeMask(run)),
        () -> assertEquals(3, DsaMask.frameOf(run, DsaMask.frames(run).get(2))));
  }

  @Test
  void a_time_interval_mask_keeps_its_direction_and_none_is_no_mask() {
    Attributes tid = new Attributes();
    tid.setString(Tag.MaskOperation, VR.CS, "REV_TID"); // NON-NLS
    tid.setInt(Tag.TIDOffset, VR.SS, 3);
    Attributes none = new Attributes();
    none.setString(Tag.MaskOperation, VR.CS, "NONE"); // NON-NLS
    DicomSeries reverse = run(tid, "NAT"); // NON-NLS
    DicomSeries plain = run(none, "SUB"); // NON-NLS
    assertAll(
        () -> assertEquals(-3, DsaMask.encodedMask(reverse).orElseThrow().tidOffset()),
        () -> assertFalse(DsaMask.recommendsSubtraction(reverse), "NAT: shown as acquired"),
        () -> assertTrue(DsaMask.encodedMask(plain).isEmpty()),
        () -> assertFalse(DsaMask.recommendsSubtraction(plain), "SUB without a mask"),
        () -> assertEquals(Dsa.ofFrame(1), DsaMask.proposeMask(plain)),
        () -> assertFalse(DsaMask.isRun(SyntheticSeries.mr("1.2.3.31", 3, 0))));
  }

  /** A run of five XA frames whose first dataset carries the mask item and viewing mode. */
  static DicomSeries run(Attributes maskItem, String viewingMode) {
    Attributes[] frames = new Attributes[5];
    for (int i = 0; i < frames.length; i++) {
      frames[i] = SyntheticSeries.slice(RUN, "XA", i + 1, 0, 0.2, 512, null); // NON-NLS
      frames[i].remove(Tag.ImageOrientationPatient);
      frames[i].remove(Tag.ImagePositionPatient);
    }
    if (maskItem != null) {
      Sequence sequence = frames[0].newSequence(Tag.MaskSubtractionSequence, 1);
      sequence.add(maskItem);
      frames[0].setString(Tag.RecommendedViewingMode, VR.CS, viewingMode);
    }
    return SyntheticSeries.series(frames);
  }
}
