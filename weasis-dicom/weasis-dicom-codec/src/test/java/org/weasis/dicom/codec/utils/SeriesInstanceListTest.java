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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.mf.SopInstance;

class SeriesInstanceListTest {

  // -- Initial state -------------------------------------------------------

  @Test
  void newListIsEmpty() {
    SeriesInstanceList list = new SeriesInstanceList();

    assertAll(
        () -> assertTrue(list.isEmpty()),
        () -> assertEquals(0, list.size()),
        () -> assertFalse(list.isContainsMultiframes()));
  }

  // -- addSopInstance ------------------------------------------------------

  @Test
  void addSopInstance_nullIsNoOp() {
    SeriesInstanceList list = new SeriesInstanceList();

    list.addSopInstance(null);

    assertAll(() -> assertTrue(list.isEmpty()), () -> assertFalse(list.isContainsMultiframes()));
  }

  @Test
  void addSopInstance_singleInstanceDoesNotFlagMultiframe() {
    SeriesInstanceList list = new SeriesInstanceList();

    list.addSopInstance(new SopInstance("1.2.3.4", 1));

    assertAll(
        () -> assertEquals(1, list.size()),
        () -> assertFalse(list.isContainsMultiframes(), "single instance is not a multiframe"));
  }

  @Test
  void addSopInstance_distinctSopUidsDoNotFlagMultiframe() {
    SeriesInstanceList list = new SeriesInstanceList();

    list.addSopInstance(new SopInstance("1.2.3.4", 1));
    list.addSopInstance(new SopInstance("1.2.3.5", 2));
    list.addSopInstance(new SopInstance("1.2.3.6", 3));

    assertAll(() -> assertEquals(3, list.size()), () -> assertFalse(list.isContainsMultiframes()));
  }

  @Test
  void addSopInstance_duplicateSopUidFlagsMultiframe() {
    // Same SOP Instance UID added twice (e.g. enhanced multi-frame DICOM with per-frame entries):
    // SeriesInstanceList must flag this so downstream code switches to frame-aware iteration.
    SeriesInstanceList list = new SeriesInstanceList();

    list.addSopInstance(new SopInstance("1.2.3.4", 1));
    list.addSopInstance(new SopInstance("1.2.3.4", 2));

    assertTrue(list.isContainsMultiframes());
  }

  @Test
  void addSopInstance_multiframeFlagStaysOnceSet() {
    // Once a duplicate triggers the flag, adding more unique SOPs after should not clear it.
    SeriesInstanceList list = new SeriesInstanceList();

    list.addSopInstance(new SopInstance("1.2.3.4", 1));
    list.addSopInstance(new SopInstance("1.2.3.4", 2));
    list.addSopInstance(new SopInstance("1.2.3.5", 3));

    assertTrue(list.isContainsMultiframes());
  }

  // -- getSopInstance ------------------------------------------------------

  @Test
  void getSopInstance_byUidFindsAddedInstance() {
    SeriesInstanceList list = new SeriesInstanceList();
    SopInstance added = new SopInstance("1.2.3.4", 1);
    list.addSopInstance(added);

    SopInstance found = list.getSopInstance("1.2.3.4");

    assertNotNull(found);
    assertEquals("1.2.3.4", found.getSopInstanceUID());
  }

  @Test
  void getSopInstance_unknownUidReturnsNull() {
    SeriesInstanceList list = new SeriesInstanceList();
    list.addSopInstance(new SopInstance("1.2.3.4", 1));

    assertNull(list.getSopInstance("9.9.9.9"));
  }

  @Test
  void getSopInstance_byUidAndInstanceNumber() {
    SeriesInstanceList list = new SeriesInstanceList();
    list.addSopInstance(new SopInstance("1.2.3.4", 1));
    list.addSopInstance(new SopInstance("1.2.3.4", 2));

    SopInstance frame1 = list.getSopInstance("1.2.3.4", 1);
    SopInstance frame2 = list.getSopInstance("1.2.3.4", 2);

    assertAll(
        () -> assertNotNull(frame1, "frame 1 lookup"),
        () -> assertNotNull(frame2, "frame 2 lookup"),
        () -> assertEquals(1, frame1.getInstanceNumber()),
        () -> assertEquals(2, frame2.getInstanceNumber()));
  }

  // -- getSortedList -------------------------------------------------------

  @Test
  void getSortedList_isOrderedByInstanceNumber() {
    // SopInstance.compareTo uses instanceNumber as the primary sort key; the sorted list must
    // present frames in display order regardless of insertion order.
    SeriesInstanceList list = new SeriesInstanceList();
    list.addSopInstance(new SopInstance("1.2.3.7", 3));
    list.addSopInstance(new SopInstance("1.2.3.5", 1));
    list.addSopInstance(new SopInstance("1.2.3.6", 2));

    List<SopInstance> sorted = list.getSortedList();

    assertAll(
        () -> assertEquals(3, sorted.size()),
        () -> assertEquals(1, sorted.get(0).getInstanceNumber()),
        () -> assertEquals(2, sorted.get(1).getInstanceNumber()),
        () -> assertEquals(3, sorted.get(2).getInstanceNumber()));
  }

  @Test
  void getSortedList_returnsEmptyListForEmptySeries() {
    assertTrue(new SeriesInstanceList().getSortedList().isEmpty());
  }
}
