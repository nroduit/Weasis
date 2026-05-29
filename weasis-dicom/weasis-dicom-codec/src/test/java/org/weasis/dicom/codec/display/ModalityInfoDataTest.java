/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link ModalityInfoData} — the per-modality overlay-layout container that holds four {@link
 * CornerInfoData} entries (one per corner). The modality-view XML loader uses {@code
 * getCornerInfo(corner)} to populate each corner with TagView slots; a regression in the corner
 * lookup would silently route overlays to the wrong screen corner.
 */
class ModalityInfoDataTest {

  @Test
  void constructor_createsOneCornerInfoPerCornerDisplay() {
    ModalityInfoData data = new ModalityInfoData(Modality.CT, null);

    assertAll(
        () -> assertNotNull(data.getCornerInfo()),
        () ->
            assertEquals(
                CornerDisplay.values().length,
                data.getCornerInfo().length,
                "one entry per CornerDisplay value"));
  }

  @Test
  void getModalityAndExtendModality_returnConstructorArgs() {
    ModalityInfoData data = new ModalityInfoData(Modality.MR, Modality.CT);

    assertAll(
        () -> assertSame(Modality.MR, data.getModality()),
        () -> assertSame(Modality.CT, data.getExtendModality()));
  }

  @Test
  void getExtendModality_nullWhenNotProvided() {
    ModalityInfoData data = new ModalityInfoData(Modality.CT, null);

    assertNull(data.getExtendModality());
  }

  @Test
  void getCornerInfo_byCornerReturnsMatchingEntry() {
    ModalityInfoData data = new ModalityInfoData(Modality.CT, null);

    for (CornerDisplay corner : CornerDisplay.values()) {
      assertSame(
          corner,
          data.getCornerInfo(corner).getCorner(),
          "lookup returns the entry whose corner matches " + corner);
    }
  }

  @Test
  void getCornerInfo_corners_areInCornerDisplayValuesOrder() {
    // The constructor iterates CornerDisplay.values() in order — pin that order in the array so
    // callers indexing by ordinal (rather than getCornerInfo(...)) stay correct.
    ModalityInfoData data = new ModalityInfoData(Modality.CT, null);

    CornerDisplay[] expected = CornerDisplay.values();
    for (int i = 0; i < expected.length; i++) {
      assertSame(
          expected[i],
          data.getCornerInfo()[i].getCorner(),
          "corner at index " + i + " is " + expected[i]);
    }
  }

  @Test
  void toString_includesModalityNameAndDescription() {
    String s = new ModalityInfoData(Modality.CT, null).toString();

    assertAll(
        () -> assertTrue(s.contains("CT"), s),
        () -> assertTrue(s.contains("Computed Tomography"), s));
  }

  @Test
  void toString_handlesModalityWithoutDescription() {
    // DEFAULT has a localized description in normal builds; the test must still pass without
    // assuming what that string is. Just verify the call doesn't crash and includes "DEFAULT".
    String s = new ModalityInfoData(Modality.DEFAULT, null).toString();

    assertTrue(s.contains("DEFAULT"), s);
  }
}
