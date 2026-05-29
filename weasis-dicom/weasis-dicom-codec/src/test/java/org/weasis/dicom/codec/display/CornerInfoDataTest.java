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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagView;

/**
 * Tests {@link CornerInfoData} — the per-corner overlay-layout carrier. Each corner gets {@code
 * ELEMENT_NUMBER = 7} TagView slots. A regression in the size constant or in extend-modality
 * cloning would silently truncate, duplicate, or alias overlay rows across modalities.
 */
class CornerInfoDataTest {

  @Test
  void elementNumberConstantIsSeven() {
    // Pin the constant — the modality-view XML loader (ModalityView.readCorner) checks indices
    // against 1..7 and would silently ignore extra slots if this changed.
    assertEquals(7, CornerInfoData.ELEMENT_NUMBER);
  }

  @Test
  void constructor_nullExtendModality_givesFreshSevenSlotArray() {
    CornerInfoData data = new CornerInfoData(CornerDisplay.TOP_LEFT, null);

    assertAll(
        () -> assertSame(CornerDisplay.TOP_LEFT, data.getCorner()),
        () -> assertNotNull(data.getInfos()),
        () -> assertEquals(7, data.getInfos().length, "fresh array has 7 null slots"),
        () -> {
          // Every slot starts null.
          for (TagView slot : data.getInfos()) {
            assertNull(slot);
          }
        });
  }

  @Test
  void constructor_extendModalityNotInRegistry_givesFreshArray() {
    // No ModalityView XML loaded for DEFAULT in test classpath (or it loads with all-null
    // slots) — either way, the constructor must NOT throw and must return a 7-slot array.
    CornerInfoData data = new CornerInfoData(CornerDisplay.BOTTOM_RIGHT, Modality.RTSEGANN);

    assertAll(
        () -> assertSame(CornerDisplay.BOTTOM_RIGHT, data.getCorner()),
        () -> assertNotNull(data.getInfos()),
        () -> assertEquals(7, data.getInfos().length));
  }

  @Test
  void constructor_doesNotShareArrayBetweenInstances() {
    // Two corners with the same identifier must NOT share the underlying TagView array;
    // otherwise mutating one corner's layout would mutate the other.
    CornerInfoData a = new CornerInfoData(CornerDisplay.TOP_LEFT, null);
    CornerInfoData b = new CornerInfoData(CornerDisplay.TOP_LEFT, null);

    assertNotSame(a.getInfos(), b.getInfos());
  }

  @Test
  void toString_delegatesToCorner() {
    CornerInfoData data = new CornerInfoData(CornerDisplay.TOP_LEFT, null);

    assertEquals(CornerDisplay.TOP_LEFT.toString(), data.toString());
  }
}
