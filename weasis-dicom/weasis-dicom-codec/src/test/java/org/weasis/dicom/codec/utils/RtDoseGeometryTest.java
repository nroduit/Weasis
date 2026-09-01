/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomMetaData;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.SimpleTaggable;
import org.weasis.dicom.codec.TagD;

@DisplayNameGeneration(ReplaceUnderscores.class)
class RtDoseGeometryTest {

  private static DicomMetaData dose(double... offsets) {
    Attributes ds = new Attributes();
    ds.setString(Tag.Modality, VR.CS, "RTDOSE");
    ds.setDouble(Tag.ImageOrientationPatient, VR.DS, 1, 0, 0, 0, 1, 0);
    ds.setDouble(Tag.ImagePositionPatient, VR.DS, -100, -200, 50);
    ds.setDouble(Tag.GridFrameOffsetVector, VR.DS, offsets);
    DicomMetaData md = mock(DicomMetaData.class);
    when(md.getDicomObject()).thenReturn(ds);
    return md;
  }

  @Test
  void relative_offsets_move_along_the_plane_normal() {
    SimpleTaggable frame = new SimpleTaggable();
    assertTrue(DicomMediaUtils.writeRtDoseGeometry(frame, dose(0, 3, 6), 2));
    assertArrayEquals(
        new double[] {-100, -200, 56},
        (double[]) frame.getTagValue(TagD.get(Tag.ImagePositionPatient)),
        1e-9);
  }

  @Test
  void absolute_offsets_are_z_positions() {
    SimpleTaggable frame = new SimpleTaggable();
    assertTrue(DicomMediaUtils.writeRtDoseGeometry(frame, dose(50, 53, 56), 1));
    assertArrayEquals(
        new double[] {-100, -200, 53},
        (double[]) frame.getTagValue(TagD.get(Tag.ImagePositionPatient)),
        1e-9);
  }

  @Test
  void other_modalities_and_bad_indexes_are_ignored() {
    SimpleTaggable frame = new SimpleTaggable();
    assertFalse(DicomMediaUtils.writeRtDoseGeometry(frame, dose(0, 3), 5));
    DicomMetaData ct = dose(0, 3);
    ct.getDicomObject().setString(Tag.Modality, VR.CS, "CT");
    assertFalse(DicomMediaUtils.writeRtDoseGeometry(frame, ct, 0));
  }
}
