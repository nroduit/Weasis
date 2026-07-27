/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SegSpecialElementTest {

  @Test
  void paletteBaseReservesADistinctRangePerFile() {
    String firstUid = "1.2.826.0.1.3680043.2.1143.1";
    String secondUid = "1.2.826.0.1.3680043.2.1143.2";

    int firstBase = SegSpecialElement.paletteBase(firstUid, 3);
    int secondBase = SegSpecialElement.paletteBase(secondUid, 1);

    assertTrue(
        secondBase >= firstBase + 3,
        "a SEG file must not reuse the palette indexes reserved by another one");
    assertEquals(
        firstBase, SegSpecialElement.paletteBase(firstUid, 3), "the same file keeps its range");
  }

  @Test
  void paletteBaseAdvancesWhenTheUidIsMissing() {
    assertNotEquals(SegSpecialElement.paletteBase(null, 1), SegSpecialElement.paletteBase(null, 1));
  }
}
