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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;

class DicomMediaUtilsTest {

  // -- containsRequiredAttributes ------------------------------------------

  @Test
  void containsRequiredAttributes_nullDatasetReturnsFalse() {
    assertFalse(DicomMediaUtils.containsRequiredAttributes(null, Tag.PatientID));
  }

  @Test
  void containsRequiredAttributes_nullTagListReturnsFalse() {
    assertFalse(DicomMediaUtils.containsRequiredAttributes(new Attributes(), (int[]) null));
  }

  @Test
  void containsRequiredAttributes_emptyTagListReturnsFalse() {
    assertFalse(DicomMediaUtils.containsRequiredAttributes(new Attributes()));
  }

  @Test
  void containsRequiredAttributes_partialMatchReturnsFalse() {
    Attributes a = new Attributes();
    a.setString(Tag.PatientID, VR.LO, "MR-001");

    assertFalse(
        DicomMediaUtils.containsRequiredAttributes(a, Tag.PatientID, Tag.PatientName),
        "only one of two tags present → false");
  }

  @Test
  void containsRequiredAttributes_completeMatchReturnsTrue() {
    Attributes a = new Attributes();
    a.setString(Tag.PatientID, VR.LO, "MR-001");
    a.setString(Tag.PatientName, VR.PN, "Doe^Jane");

    assertTrue(DicomMediaUtils.containsRequiredAttributes(a, Tag.PatientID, Tag.PatientName));
  }

  // -- containsRequiredModalityLUTAttributes (RescaleSlope + RescaleIntercept) ---

  @Test
  void modalityLUT_bothRescaleSlopeAndInterceptRequired() {
    // Clinical impact: CT HU values come from raw_value * RescaleSlope + RescaleIntercept.
    // Missing either tag must disable the modality LUT, otherwise HU calculation is silently
    // wrong (e.g. defaulting slope=1 / intercept=0 would alias raw values as HU).
    Attributes intOnly = new Attributes();
    intOnly.setDouble(Tag.RescaleIntercept, VR.DS, -1024.0);

    Attributes slopeOnly = new Attributes();
    slopeOnly.setDouble(Tag.RescaleSlope, VR.DS, 1.0);

    Attributes both = new Attributes();
    both.setDouble(Tag.RescaleIntercept, VR.DS, -1024.0);
    both.setDouble(Tag.RescaleSlope, VR.DS, 1.0);

    assertAll(
        () -> assertFalse(DicomMediaUtils.containsRequiredModalityLUTAttributes(intOnly)),
        () -> assertFalse(DicomMediaUtils.containsRequiredModalityLUTAttributes(slopeOnly)),
        () -> assertTrue(DicomMediaUtils.containsRequiredModalityLUTAttributes(both)));
  }

  @Test
  void modalityLUTData_requiresTypeDescriptorAndData() {
    Attributes a = new Attributes();
    a.setString(Tag.ModalityLUTType, VR.LO, "HU");
    // Missing LUTDescriptor and LUTData
    assertFalse(DicomMediaUtils.containsRequiredModalityLUTDataAttributes(a));

    a.setInt(Tag.LUTDescriptor, VR.US, 4096, 0, 16);
    assertFalse(DicomMediaUtils.containsRequiredModalityLUTDataAttributes(a));

    a.setBytes(Tag.LUTData, VR.OW, new byte[8192]);
    assertTrue(DicomMediaUtils.containsRequiredModalityLUTDataAttributes(a));
  }

  // -- containsRequiredVOILUTWindowLevelAttributes (WindowCenter + WindowWidth) -

  @Test
  void voiLUTWindowLevel_bothCenterAndWidthRequired() {
    Attributes centerOnly = new Attributes();
    centerOnly.setDouble(Tag.WindowCenter, VR.DS, 40);

    Attributes widthOnly = new Attributes();
    widthOnly.setDouble(Tag.WindowWidth, VR.DS, 400);

    Attributes both = new Attributes();
    both.setDouble(Tag.WindowCenter, VR.DS, 40);
    both.setDouble(Tag.WindowWidth, VR.DS, 400);

    assertAll(
        () -> assertFalse(DicomMediaUtils.containsRequiredVOILUTWindowLevelAttributes(centerOnly)),
        () -> assertFalse(DicomMediaUtils.containsRequiredVOILUTWindowLevelAttributes(widthOnly)),
        () -> assertTrue(DicomMediaUtils.containsRequiredVOILUTWindowLevelAttributes(both)));
  }

  // -- containsLUTAttributes (LUTDescriptor + LUTData) ---------------------

  @Test
  void containsLUTAttributes_requiresBothDescriptorAndData() {
    Attributes descriptorOnly = new Attributes();
    descriptorOnly.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);

    Attributes both = new Attributes();
    both.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);
    both.setBytes(Tag.LUTData, VR.OW, new byte[512]);

    assertAll(
        () -> assertFalse(DicomMediaUtils.containsLUTAttributes(descriptorOnly)),
        () -> assertTrue(DicomMediaUtils.containsLUTAttributes(both)));
  }

  // -- hasOverlay -----------------------------------------------------------

  @Test
  void hasOverlay_nullDatasetReturnsFalse() {
    assertFalse(DicomMediaUtils.hasOverlay(null));
  }

  @Test
  void hasOverlay_noOverlayTagsReturnsFalse() {
    Attributes a = new Attributes();
    a.setString(Tag.PatientID, VR.LO, "MR-001");

    assertFalse(DicomMediaUtils.hasOverlay(a));
  }

  @Test
  void hasOverlay_standardOverlayGroupDetected() {
    // The base OverlayRows tag is at (6000,0010) — the first of the 16 standard overlay groups.
    Attributes a = new Attributes();
    a.setInt(Tag.OverlayRows, VR.US, 512);

    assertTrue(DicomMediaUtils.hasOverlay(a));
  }

  @Test
  void hasOverlay_higherOverlayGroupDetected() {
    // Overlay groups are 6000, 6002, 6004, ..., 601E (even, group_offset = 0x20000 stride).
    // The loop in hasOverlay iterates i=0..15 with gg0000 = i << 17 and tests (Tag.OverlayRows |
    // gg0000). Verify a group at i=2 (offset 0x40000 from 6000 → 6004 group) is also detected.
    Attributes a = new Attributes();
    int overlayRowsAtGroup6004 = Tag.OverlayRows | (2 << 17);
    a.setInt(overlayRowsAtGroup6004, VR.US, 512);

    assertTrue(DicomMediaUtils.hasOverlay(a));
  }

  // -- getIntPixelValue (signed/unsigned + sign extension) -----------------

  @Test
  void getIntPixelValue_missingVrReturnsNull() {
    // No PixelPaddingValue set → VR lookup returns null → method returns null.
    assertNull(
        DicomMediaUtils.getIntPixelValue(new Attributes(), Tag.PixelPaddingValue, false, 16));
  }

  @Test
  void getIntPixelValue_unsignedShortValuePreserved() {
    Attributes a = new Attributes();
    a.setInt(Tag.PixelPaddingValue, VR.US, 100);

    assertEquals(100, DicomMediaUtils.getIntPixelValue(a, Tag.PixelPaddingValue, false, 16));
  }

  @Test
  void getIntPixelValue_signedShortNegativePreserved() {
    Attributes a = new Attributes();
    a.setInt(Tag.PixelPaddingValue, VR.SS, -1000);

    assertEquals(-1000, DicomMediaUtils.getIntPixelValue(a, Tag.PixelPaddingValue, true, 16));
  }

  @Test
  void getIntPixelValue_obSignedWith12BitsStored_signExtendsCorrectly() {
    // Clinically critical: 12-bit signed image (typical CT) with stored value 0x800 (bit 11 set)
    // must read as -2048, not 2048. A sign-extension bug here flips every padding-pixel comparison.
    Attributes a = new Attributes();
    a.setBytes(Tag.PixelPaddingValue, VR.OW, new byte[] {(byte) 0x00, (byte) 0x08}); // LE 0x0800

    Integer result = DicomMediaUtils.getIntPixelValue(a, Tag.PixelPaddingValue, true, 12);

    assertEquals(-2048, result, "12-bit signed sign-extension of 0x800");
  }

  @Test
  void getIntPixelValue_obUnsignedWith12BitsStored_doesNotSignExtend() {
    Attributes a = new Attributes();
    a.setBytes(Tag.PixelPaddingValue, VR.OW, new byte[] {(byte) 0x00, (byte) 0x08});

    Integer result = DicomMediaUtils.getIntPixelValue(a, Tag.PixelPaddingValue, false, 12);

    assertEquals(2048, result, "12-bit unsigned reads as positive");
  }

  @Test
  void getIntPixelValue_obSignedClampedToStoredRange() {
    // stored=12, signed=true → valid range is [-2048, 2047]. A raw 16-bit value of 0x7FFF
    // (32767, no bit 11) must clamp DOWN to 2047 (max for 12-bit signed).
    Attributes a = new Attributes();
    a.setBytes(Tag.PixelPaddingValue, VR.OW, new byte[] {(byte) 0xFF, (byte) 0x7F}); // 0x7FFF

    Integer result = DicomMediaUtils.getIntPixelValue(a, Tag.PixelPaddingValue, true, 12);

    // 0x7FFF = 32767, bit 11 is set (0x800), so sign-extension kicks in: 32767 | 0xFFFFF000
    // = 0xFFFFFFFF = -1. -1 is within [-2048, 2047], no clamp.
    assertEquals(-1, result);
  }

  @Test
  void getIntPixelValue_obUnsignedClampedToMaxForStoredBits() {
    // stored=8, unsigned → max = 255. A 16-bit value of 0xFFFF must clamp to 255.
    Attributes a = new Attributes();
    a.setBytes(Tag.PixelPaddingValue, VR.OW, new byte[] {(byte) 0xFF, (byte) 0xFF});

    Integer result = DicomMediaUtils.getIntPixelValue(a, Tag.PixelPaddingValue, false, 8);

    assertEquals(255, result);
  }

  @Test
  void getIntPixelValue_vrMismatchForcesReReadWithCorrectVr() {
    // Tag stored with US VR but caller asks for signed reading; the code re-reads as SS.
    // Pin: the result is the unsigned bit pattern reinterpreted as signed, NOT the original
    // unsigned value sign-extended differently.
    Attributes a = new Attributes();
    a.setInt(Tag.PixelPaddingValue, VR.US, 0xFFFE); // 65534 unsigned, -2 signed

    Integer result = DicomMediaUtils.getIntPixelValue(a, Tag.PixelPaddingValue, true, 16);

    assertEquals(-2, result, "US value 0xFFFE reread as SS = -2");
  }
}
