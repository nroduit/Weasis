/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.items.SeriesItem;

class RejectedVisibilityTest {

  @Test
  void theSettingReadsItsThreeValuesAndFallsBackToCount() {
    assertAll(
        () -> assertEquals(RejectedVisibility.HIDDEN, RejectedVisibility.fromId("hidden")),
        () -> assertEquals(RejectedVisibility.COUNT, RejectedVisibility.fromId("count")),
        () -> assertEquals(RejectedVisibility.LIST, RejectedVisibility.fromId(" List ")),
        () -> assertEquals(RejectedVisibility.COUNT, RejectedVisibility.fromId("bogus")),
        () -> assertEquals(RejectedVisibility.COUNT, RejectedVisibility.fromId(null)),
        () -> assertEquals("list", RejectedVisibility.LIST.getId()));
  }

  @Test
  void aSeriesWithoutImagesHasNoRejectedCount() {
    assertEquals(0, SeriesItem.of(new DicomSeries("1.2.3"), null).rejectedImageCount());
  }
}
