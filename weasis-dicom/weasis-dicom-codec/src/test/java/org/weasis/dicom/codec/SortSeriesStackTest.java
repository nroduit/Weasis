/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;

/**
 * Tests {@link SortSeriesStack} — the six static comparators used to order DICOM slices in a stack
 * (and therefore drive every cine playback, MPR reconstruction, and cross-reference line in the
 * viewer).
 *
 * <p>A regression in any of these comparators would silently mis-order slices and surface as one of
 * the most clinically dangerous failure modes:
 *
 * <ul>
 *   <li>cine plays the volume in reverse z-order (WEA-004 R18 / R24)
 *   <li>MPR reconstruction interpolates between mis-ordered slices (R18)
 *   <li>localizer cross-reference lines drawn on the wrong slice (R18)
 * </ul>
 *
 * <p>Each comparator must also degrade gracefully when a sort key is null — sort instability is
 * preferred to throwing an NPE mid-render. The {@code compare} contract here is {@code 0} when
 * either side is null, which preserves insertion order.
 */
class SortSeriesStackTest {

  // ---------------------------------------------------------------------------
  // Test fixtures — minimal mocks of DicomImageElement returning controlled
  // tag values via Mockito stubs. The comparators read a single tag per element
  // so a blanket stub on getTagValue(TagW) is safe.
  // ---------------------------------------------------------------------------

  private static DicomImageElement elementWithTagValue(Object value) {
    DicomImageElement element = Mockito.mock(DicomImageElement.class);
    Mockito.when(element.getTagValue(Mockito.any(TagW.class))).thenReturn(value);
    return element;
  }

  // ---------------------------------------------------------------------------
  // instanceNumber — Tag.InstanceNumber (Integer)
  // ---------------------------------------------------------------------------

  @Test
  void instanceNumber_ordersAscending() {
    DicomImageElement low = elementWithTagValue(1);
    DicomImageElement high = elementWithTagValue(5);
    assertTrue(SortSeriesStack.instanceNumber.compare(low, high) < 0);
    assertTrue(SortSeriesStack.instanceNumber.compare(high, low) > 0);
  }

  @Test
  void instanceNumber_equalValuesAreEqual() {
    DicomImageElement a = elementWithTagValue(7);
    DicomImageElement b = elementWithTagValue(7);
    assertEquals(0, SortSeriesStack.instanceNumber.compare(a, b));
  }

  @Test
  void instanceNumber_nullValuePreservesOrder() {
    // Either side null ⇒ 0 ⇒ insertion order preserved (no NPE).
    DicomImageElement nullElem = elementWithTagValue(null);
    DicomImageElement withValue = elementWithTagValue(3);
    assertEquals(0, SortSeriesStack.instanceNumber.compare(nullElem, withValue));
    assertEquals(0, SortSeriesStack.instanceNumber.compare(withValue, nullElem));
    assertEquals(0, SortSeriesStack.instanceNumber.compare(nullElem, nullElem));
  }

  @Test
  void instanceNumber_toStringFromMessages() {
    assertNotNull(SortSeriesStack.instanceNumber.toString());
  }

  // ---------------------------------------------------------------------------
  // slicePosition — TagW.SlicePosition (Double, projection along the slice
  // normal — this is the most clinically load-bearing sort key for CT/MR
  // since it is computed from ImageOrientationPatient + ImagePositionPatient).
  // ---------------------------------------------------------------------------

  @Test
  void slicePosition_ordersAscending() {
    DicomImageElement back = elementWithTagValue(-50.0);
    DicomImageElement front = elementWithTagValue(50.0);
    assertTrue(SortSeriesStack.slicePosition.compare(back, front) < 0);
    assertTrue(SortSeriesStack.slicePosition.compare(front, back) > 0);
  }

  @Test
  void slicePosition_handlesNegativeAndZeroBoundary() {
    DicomImageElement neg = elementWithTagValue(-0.001);
    DicomImageElement zero = elementWithTagValue(0.0);
    DicomImageElement pos = elementWithTagValue(0.001);
    assertTrue(SortSeriesStack.slicePosition.compare(neg, zero) < 0);
    assertTrue(SortSeriesStack.slicePosition.compare(zero, pos) < 0);
    assertEquals(0, SortSeriesStack.slicePosition.compare(zero, zero));
  }

  @Test
  void slicePosition_nullValuePreservesOrder() {
    DicomImageElement nullElem = elementWithTagValue(null);
    DicomImageElement withValue = elementWithTagValue(12.5);
    assertEquals(0, SortSeriesStack.slicePosition.compare(nullElem, withValue));
    assertEquals(0, SortSeriesStack.slicePosition.compare(withValue, nullElem));
  }

  @Test
  void slicePosition_toStringFromMessages() {
    assertNotNull(SortSeriesStack.slicePosition.toString());
  }

  // ---------------------------------------------------------------------------
  // sliceLocation — Tag.SliceLocation (Double, raw vendor-supplied location).
  // ---------------------------------------------------------------------------

  @Test
  void sliceLocation_ordersAscending() {
    DicomImageElement a = elementWithTagValue(10.0);
    DicomImageElement b = elementWithTagValue(20.0);
    assertTrue(SortSeriesStack.sliceLocation.compare(a, b) < 0);
  }

  @Test
  void sliceLocation_nullValuePreservesOrder() {
    DicomImageElement nullElem = elementWithTagValue(null);
    DicomImageElement withValue = elementWithTagValue(42.0);
    assertEquals(0, SortSeriesStack.sliceLocation.compare(nullElem, withValue));
  }

  @Test
  void sliceLocation_toStringFromMessages() {
    assertNotNull(SortSeriesStack.sliceLocation.toString());
  }

  // ---------------------------------------------------------------------------
  // acquisitionTime — Tag.AcquisitionTime (LocalTime, ordering cine frames
  // captured at distinct wall-clock times within a series).
  // ---------------------------------------------------------------------------

  @Test
  void acquisitionTime_ordersAscending() {
    DicomImageElement early = elementWithTagValue(LocalTime.of(8, 30, 0));
    DicomImageElement late = elementWithTagValue(LocalTime.of(8, 30, 30));
    assertTrue(SortSeriesStack.acquisitionTime.compare(early, late) < 0);
    assertTrue(SortSeriesStack.acquisitionTime.compare(late, early) > 0);
  }

  @Test
  void acquisitionTime_nullValuePreservesOrder() {
    DicomImageElement nullElem = elementWithTagValue(null);
    DicomImageElement withValue = elementWithTagValue(LocalTime.NOON);
    assertEquals(0, SortSeriesStack.acquisitionTime.compare(nullElem, withValue));
  }

  @Test
  void acquisitionTime_toStringFromMessages() {
    assertNotNull(SortSeriesStack.acquisitionTime.toString());
  }

  // ---------------------------------------------------------------------------
  // contentTime — Tag.ContentTime (LocalTime, when the pixel data was created).
  // ---------------------------------------------------------------------------

  @Test
  void contentTime_ordersAscending() {
    DicomImageElement a = elementWithTagValue(LocalTime.of(12, 0, 0));
    DicomImageElement b = elementWithTagValue(LocalTime.of(12, 0, 5));
    assertTrue(SortSeriesStack.contentTime.compare(a, b) < 0);
  }

  @Test
  void contentTime_nullValuePreservesOrder() {
    DicomImageElement nullElem = elementWithTagValue(null);
    DicomImageElement withValue = elementWithTagValue(LocalTime.NOON);
    assertEquals(0, SortSeriesStack.contentTime.compare(nullElem, withValue));
    assertEquals(0, SortSeriesStack.contentTime.compare(withValue, nullElem));
  }

  @Test
  void contentTime_toStringFromMessages() {
    assertNotNull(SortSeriesStack.contentTime.toString());
  }

  // ---------------------------------------------------------------------------
  // diffusionBValue — Tag.DiffusionBValue (Double, diffusion-weighted MR).
  // Misordering b-values would silently misrepresent the diffusion gradient
  // strength in cine playback (R18 territory for DWI series).
  // ---------------------------------------------------------------------------

  @Test
  void diffusionBValue_ordersAscending() {
    DicomImageElement b0 = elementWithTagValue(0.0);
    DicomImageElement b1000 = elementWithTagValue(1000.0);
    assertTrue(SortSeriesStack.diffusionBValue.compare(b0, b1000) < 0);
    assertTrue(SortSeriesStack.diffusionBValue.compare(b1000, b0) > 0);
  }

  @Test
  void diffusionBValue_equalValuesAreEqual() {
    DicomImageElement a = elementWithTagValue(800.0);
    DicomImageElement b = elementWithTagValue(800.0);
    assertEquals(0, SortSeriesStack.diffusionBValue.compare(a, b));
  }

  @Test
  void diffusionBValue_nullValuePreservesOrder() {
    DicomImageElement nullElem = elementWithTagValue(null);
    DicomImageElement withValue = elementWithTagValue(500.0);
    assertEquals(0, SortSeriesStack.diffusionBValue.compare(nullElem, withValue));
  }

  @Test
  void diffusionBValue_toStringFromMessages() {
    assertNotNull(SortSeriesStack.diffusionBValue.toString());
  }

  // ---------------------------------------------------------------------------
  // getValues() — exposes the canonical sort-key list used by the UI dropdown.
  // The order is significant: it is the order the user sees in the menu and
  // also the order in which the viewer falls back if a series lacks a key.
  // ---------------------------------------------------------------------------

  @Test
  void getValues_exposesAllSixComparatorsInExpectedOrder() {
    SeriesComparator<DicomImageElement>[] values = SortSeriesStack.getValues();
    assertEquals(6, values.length);
    assertEquals(SortSeriesStack.instanceNumber, values[0]);
    assertEquals(SortSeriesStack.slicePosition, values[1]);
    assertEquals(SortSeriesStack.sliceLocation, values[2]);
    assertEquals(SortSeriesStack.contentTime, values[3]);
    assertEquals(SortSeriesStack.acquisitionTime, values[4]);
    assertEquals(SortSeriesStack.diffusionBValue, values[5]);
  }

  @Test
  void getValues_returnsFreshArrayEachCall() {
    SeriesComparator<DicomImageElement>[] first = SortSeriesStack.getValues();
    SeriesComparator<DicomImageElement>[] second = SortSeriesStack.getValues();
    // Different array instances: a caller mutating the returned array (e.g. for UI sorting)
    // must not poison the canonical list for the next caller.
    org.junit.jupiter.api.Assertions.assertNotSame(first, second);
    // Same contents though.
    for (int i = 0; i < first.length; i++) {
      assertEquals(first[i], second[i]);
    }
  }
}
