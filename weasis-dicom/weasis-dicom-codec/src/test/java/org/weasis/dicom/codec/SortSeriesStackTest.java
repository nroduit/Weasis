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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

class SortSeriesStackTest {

  /** An axial slice at {@code z}; {@code reversed} flips the row and column directions. */
  private static DicomImageElement slice(int instance, double z, boolean reversed) {
    Attributes a = new Attributes();
    a.setInt(Tag.InstanceNumber, VR.IS, instance);
    a.setDouble(Tag.ImagePositionPatient, VR.DS, -100, -100, z);
    if (reversed) {
      a.setDouble(Tag.ImageOrientationPatient, VR.DS, -1, 0, 0, 0, -1, 0);
    } else {
      a.setDouble(Tag.ImageOrientationPatient, VR.DS, 1, 0, 0, 0, 1, 0);
    }
    Map<TagW, Object> tags = new HashMap<>();
    for (int tag :
        new int[] {Tag.InstanceNumber, Tag.ImagePositionPatient, Tag.ImageOrientationPatient}) {
      TagW key = TagD.get(tag);
      tags.put(key, key.getValue(a));
    }
    DcmMediaReader reader = mock(DcmMediaReader.class);
    when(reader.getMediaFragmentTags(any())).thenReturn(tags);
    when(reader.getTagValue(any())).thenAnswer(inv -> ((TagW) inv.getArgument(0)).getValue(a));
    DicomImageElement element = new DicomImageElement(reader, 0);
    DicomMediaUtils.computeSlicePosition(element);
    return element;
  }

  private static List<Double> zOrder(List<DicomImageElement> images, boolean headToFeet) {
    List<DicomImageElement> sorted = new ArrayList<>(images);
    sorted.sort(
        headToFeet
            ? SortSeriesStack.slicePosition.getReversOrderComparator()
            : SortSeriesStack.slicePosition);
    return sorted.stream()
        .map(img -> ((double[]) img.getTagValue(TagD.get(Tag.ImagePositionPatient)))[2])
        .toList();
  }

  @Test
  void twoSeriesAcquiredInOppositeDirectionsShowTheSameFirstSliceHeadToFeet() {
    // feet to head acquisition: instance 1 is the lowest slice
    List<DicomImageElement> up =
        List.of(slice(1, 10, false), slice(2, 20, false), slice(3, 30, false));
    // head to feet acquisition, rows and columns reversed: instance 1 is the highest slice
    List<DicomImageElement> down =
        List.of(slice(1, 30, true), slice(2, 20, true), slice(3, 10, true));
    assertAll(
        () -> assertEquals(List.of(30.0, 20.0, 10.0), zOrder(up, true)),
        () -> assertEquals(List.of(30.0, 20.0, 10.0), zOrder(down, true), "same first slice"),
        () -> assertEquals(List.of(10.0, 20.0, 30.0), zOrder(down, false), "feet to head"),
        () ->
            assertTrue(
                (Double) down.getFirst().getTagValue(TagW.SlicePosition) > 0,
                "the position is along the positive patient axis, whatever the acquisition"));
    assertEquals(
        "slice-position", SortSeriesStack.keyOf(SortSeriesStack.slicePosition).orElseThrow());
    assertEquals(SortSeriesStack.instanceNumber, SortSeriesStack.fromKey("instance").orElseThrow());
    assertTrue(SortSeriesStack.fromKey("nope").isEmpty());
  }
}
