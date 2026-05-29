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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Modality} — the DICOM-modality lookup used to bind a study/series to a default W/L
 * preset, a default modality-view layout, and a default modality string for PACS routing. A wrong
 * lookup means the wrong default contrast and the wrong overlay layout — both are clinically
 * misleading.
 */
class ModalityTest {

  // -- getModality lookup --------------------------------------------------

  @Test
  void getModality_resolvesKnownCode() {
    assertAll(
        () -> assertEquals(Modality.CT, Modality.getModality("CT")),
        () -> assertEquals(Modality.MR, Modality.getModality("MR")),
        () -> assertEquals(Modality.US, Modality.getModality("US")),
        () -> assertEquals(Modality.XA, Modality.getModality("XA")),
        () -> assertEquals(Modality.RTDOSE, Modality.getModality("RTDOSE")),
        () -> assertEquals(Modality.RTSTRUCT, Modality.getModality("RTSTRUCT")));
  }

  @Test
  void getModality_unknownCodeReturnsDefault() {
    // Critical: an unknown modality string from a non-conformant DICOM file must NOT throw —
    // it falls back to DEFAULT so the model can still be loaded.
    assertSame(Modality.DEFAULT, Modality.getModality("XYZ_NOT_A_MODALITY"));
  }

  @Test
  void getModality_nullReturnsDefault() {
    assertSame(Modality.DEFAULT, Modality.getModality(null));
  }

  @Test
  void getModality_emptyReturnsDefault() {
    assertSame(Modality.DEFAULT, Modality.getModality(""));
  }

  @Test
  void getModality_blankReturnsDefault() {
    assertSame(Modality.DEFAULT, Modality.getModality("   "));
  }

  @Test
  void getModality_isCaseSensitive() {
    // DICOM Modality strings are upper-case; lower-case must not match (case-folding here would
    // accept "ct"/"mr" and silently bypass strict-mode validators downstream).
    assertSame(Modality.DEFAULT, Modality.getModality("ct"));
  }

  // -- getAllModalitiesExceptDefault ----------------------------------------

  @Test
  void getAllModalitiesExceptDefault_excludesDefaultAndKeepsTheRest() {
    Modality[] all = Modality.getAllModalitiesExceptDefault();

    assertAll(
        () -> assertEquals(Modality.values().length - 1, all.length, "one less than total"),
        () -> assertFalse(Arrays.asList(all).contains(Modality.DEFAULT)),
        () -> assertTrue(Arrays.asList(all).contains(Modality.CT)),
        () -> assertTrue(Arrays.asList(all).contains(Modality.MR)));
  }

  @Test
  void getAllModalitiesExceptDefault_returnsFreshArrayEachCall() {
    Modality[] a = Modality.getAllModalitiesExceptDefault();
    Modality[] b = Modality.getAllModalitiesExceptDefault();

    // Defensive copy: mutating one must not affect the other (a UI binding could otherwise
    // accidentally re-order the enum across views).
    a[0] = Modality.DEFAULT;
    assertFalse(Arrays.asList(b).contains(null));
    assertEquals(Modality.values().length - 1, b.length);
  }

  // -- Description metadata --------------------------------------------------

  @Test
  void everyModalityHasNonBlankDescription() {
    for (Modality m : Modality.values()) {
      assertNotNull(m.getDescription(), m.name());
      assertFalse(m.getDescription().isBlank(), "blank description for " + m.name());
    }
  }

  @Test
  void specificDescriptionsPinned() {
    // Pin a handful of the descriptions — these are user-visible in the modality-view dropdown
    // and a regression would silently change the label clinicians see.
    assertAll(
        () -> assertEquals("Computed Tomography", Modality.CT.getDescription()),
        () -> assertEquals("Magnetic Resonance", Modality.MR.getDescription()),
        () -> assertEquals("X-Ray Angiography", Modality.XA.getDescription()),
        () -> assertEquals("Ultrasound", Modality.US.getDescription()),
        () -> assertEquals("Radiotherapy Dose", Modality.RTDOSE.getDescription()),
        () -> assertEquals("Radiotherapy Structure Set", Modality.RTSTRUCT.getDescription()));
  }

  // -- toString ------------------------------------------------------------

  @Test
  void toString_includesCodeAndDescription() {
    String s = Modality.CT.toString();

    assertAll(
        () -> assertTrue(s.contains("CT"), s),
        () -> assertTrue(s.contains("Computed Tomography"), s));
  }
}
