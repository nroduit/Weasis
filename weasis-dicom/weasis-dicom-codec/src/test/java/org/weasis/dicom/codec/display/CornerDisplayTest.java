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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link CornerDisplay} — the four-corner enum that drives the modality-view overlay.
 *
 * <p>The enum identity (name + ordinal) is the load-bearing contract: a corner-display XML
 * configuration file references corners by enum-name ("TOP_LEFT", "TOP_RIGHT", …), so renaming an
 * enum constant would silently invalidate every shipped/loaded layout.
 */
class CornerDisplayTest {

  @Test
  void fourCornersAreDefined() {
    assertEquals(4, CornerDisplay.values().length, "exactly four corners");
  }

  @Test
  void enumConstantsHaveStableNames() {
    // Pin the enum-name strings — they're used as XML attribute values in modality-view layout
    // files. A rename here breaks every shipped layout silently.
    assertAll(
        () -> assertEquals(CornerDisplay.TOP_LEFT, CornerDisplay.valueOf("TOP_LEFT")),
        () -> assertEquals(CornerDisplay.TOP_RIGHT, CornerDisplay.valueOf("TOP_RIGHT")),
        () -> assertEquals(CornerDisplay.BOTTOM_LEFT, CornerDisplay.valueOf("BOTTOM_LEFT")),
        () -> assertEquals(CornerDisplay.BOTTOM_RIGHT, CornerDisplay.valueOf("BOTTOM_RIGHT")));
  }

  @Test
  void enumOrdinalsAreStable() {
    // Pin the ordinal layout because callers index into the ModalityInfoData.cornerInfo[] array
    // by the same ordinal (the constructor walks `CornerDisplay.values()` in order). A reorder
    // would silently swap corner contents.
    assertAll(
        () -> assertEquals(0, CornerDisplay.TOP_LEFT.ordinal()),
        () -> assertEquals(1, CornerDisplay.TOP_RIGHT.ordinal()),
        () -> assertEquals(2, CornerDisplay.BOTTOM_LEFT.ordinal()),
        () -> assertEquals(3, CornerDisplay.BOTTOM_RIGHT.ordinal()));
  }

  @Test
  void toStringIsLocalizedAndNonBlank() {
    for (CornerDisplay corner : CornerDisplay.values()) {
      assertNotNull(corner.toString(), corner.name());
      assertFalse(corner.toString().isBlank(), "blank toString for " + corner.name());
    }
  }

  @Test
  void toStringDiffersFromEnumName() {
    // The toString resolves through the i18n bundle — it must NOT be the raw enum name. If
    // Messages.getString is mis-wired and returns the lookup key, this assertion catches it.
    assertNotEquals(
        "TOP_LEFT",
        CornerDisplay.TOP_LEFT.toString(),
        "toString should be the localized label, not the enum-name");
  }
}
