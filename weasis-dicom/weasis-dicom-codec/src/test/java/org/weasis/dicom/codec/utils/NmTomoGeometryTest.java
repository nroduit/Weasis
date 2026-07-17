/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomMetaData;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.SimpleTaggable;
import org.weasis.dicom.codec.TagD;

class NmTomoGeometryTest {

  /** Builds an NM tomographic header whose plane geometry lives in DetectorInformationSequence. */
  private static DicomMetaData nmTomo(double spacing, int[] sliceVector) {
    Attributes a = new Attributes();
    a.setString(Tag.Modality, VR.CS, "NM");
    a.setInt(Tag.NumberOfFrames, VR.IS, sliceVector.length);
    a.setDouble(Tag.SpacingBetweenSlices, VR.DS, spacing);
    a.setInt(Tag.SliceVector, VR.US, sliceVector);
    Attributes detector = new Attributes();
    detector.setDouble(Tag.ImageOrientationPatient, VR.DS, 1, 0, 0, 0, 1, 0);
    detector.setDouble(Tag.ImagePositionPatient, VR.DS, -10, -20, 100);
    a.newSequence(Tag.DetectorInformationSequence, 1).add(detector);
    return new DicomMetaData(a, UID.ExplicitVRLittleEndian);
  }

  private static double[] ipp(DicomMetaData md, int frame) {
    SimpleTaggable taggable = new SimpleTaggable();
    assertTrue(DicomMediaUtils.writeNmTomoGeometry(taggable, md, frame));
    return (double[]) taggable.getTagValue(TagD.get(Tag.ImagePositionPatient));
  }

  @Test
  void derivesOrientationAndStacksAlongNormal() {
    DicomMetaData md = nmTomo(-4.4196, new int[] {1, 2, 3, 4});

    SimpleTaggable f0 = new SimpleTaggable();
    assertTrue(DicomMediaUtils.writeNmTomoGeometry(f0, md, 0));
    assertArrayEquals(
        new double[] {1, 0, 0, 0, 1, 0},
        (double[]) f0.getTagValue(TagD.get(Tag.ImageOrientationPatient)));

    // Axial normal is +Z; the detector position is the first slice.
    assertArrayEquals(new double[] {-10, -20, 100}, ipp(md, 0), 1e-6);
    // Frame 3 is three steps along the normal: z = 100 + (-4.4196 * 3).
    assertArrayEquals(new double[] {-10, -20, 100 - 4.4196 * 3}, ipp(md, 3), 1e-6);
  }

  @Test
  void honoursSliceVectorOrdering() {
    // Frames stored in reverse slice order: frame 0 holds slice 4.
    DicomMetaData md = nmTomo(2.0, new int[] {4, 3, 2, 1});
    assertEquals(100 + 2.0 * 3, ipp(md, 0)[2], 1e-6); // slice 4 -> offset 3
    assertEquals(100, ipp(md, 3)[2], 1e-6); // slice 1 -> offset 0
  }

  @Test
  void rejectsNonStackAndNonNm() {
    SimpleTaggable t = new SimpleTaggable();
    // Zero inter-slice spacing (gated/dynamic) is not a spatial volume.
    assertFalse(DicomMediaUtils.writeNmTomoGeometry(t, nmTomo(0.0, new int[] {1, 2}), 0));

    // Non-NM modality is ignored.
    Attributes ct = new Attributes();
    ct.setString(Tag.Modality, VR.CS, "CT");
    assertFalse(
        DicomMediaUtils.writeNmTomoGeometry(
            t, new DicomMetaData(ct, UID.ExplicitVRLittleEndian), 0));

    // NM without a Detector Information Sequence is ignored.
    Attributes nm = new Attributes();
    nm.setString(Tag.Modality, VR.CS, "NM");
    nm.setDouble(Tag.SpacingBetweenSlices, VR.DS, 2.0);
    assertFalse(
        DicomMediaUtils.writeNmTomoGeometry(
            t, new DicomMetaData(nm, UID.ExplicitVRLittleEndian), 0));
  }

  @Test
  void geometryResolvesForBuiltElement() {
    // Sanity: IPP and IOP are both present, which is what DicomImageElement.getSliceGeometry needs.
    SimpleTaggable t = new SimpleTaggable();
    DicomMediaUtils.writeNmTomoGeometry(t, nmTomo(-4.4196, new int[] {1, 2, 3, 4}), 2);
    assertNotNull(t.getTagValue(TagD.get(Tag.ImagePositionPatient)));
    assertNotNull(t.getTagValue(TagD.get(Tag.ImageOrientationPatient)));
  }
}
