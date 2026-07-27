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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.seg.RegionAttributes;

class SegmentationVolumeTest {

  private static final int SIZE = 8;

  private final SegRegion<DicomImageElement> liver = new SegRegion<>(1, "Liver", Color.RED);
  private final SegRegion<DicomImageElement> kidney = new SegRegion<>(2, "Kidney", Color.BLUE);

  private SegmentationVolume newVolume() {
    Map<Integer, RegionAttributes> attributes = new LinkedHashMap<>();
    attributes.put(1, liver);
    attributes.put(2, kidney);
    return new SegmentationVolume(
        SIZE,
        SIZE,
        SIZE,
        new Vector3d(1, 1, 1),
        new Vector3d(),
        new Vector3d(1, 0, 0),
        new Vector3d(0, 1, 0),
        new Vector3d(0, 0, 1),
        attributes);
  }

  @Test
  void getSegmentNumberResolvesTheRegion() {
    SegmentationVolume volume = newVolume();
    assertAll(
        () -> assertEquals(1, volume.getSegmentNumber(liver)),
        () -> assertEquals(2, volume.getSegmentNumber(kidney)),
        () -> assertEquals(-1, volume.getSegmentNumber(new SegRegion<>(7, "Spleen", Color.GREEN))));
  }

  @Test
  void findSegmentCenterReturnsCentroidOfTheDensestSlice() {
    SegmentationVolume volume = newVolume();
    // A single voxel on z=1 and a 2x2 block on z=5: the block is the densest slice.
    volume.addLabel(0, 0, 1, 1);
    volume.addLabel(4, 2, 5, 1);
    volume.addLabel(5, 2, 5, 1);
    volume.addLabel(4, 3, 5, 1);
    volume.addLabel(5, 3, 5, 1);

    int[] lastProgress = new int[2];
    Vector3i center =
        volume.findSegmentCenter(
            1,
            (done, total) -> {
              lastProgress[0] = done;
              lastProgress[1] = total;
            });

    assertNotNull(center);
    assertAll(
        () -> assertEquals(new Vector3i(5, 3, 5), center),
        () -> assertEquals(SIZE, lastProgress[0]),
        () -> assertEquals(SIZE, lastProgress[1]));
  }

  @Test
  void findSegmentCenterReturnsNullWhenSegmentIsAbsent() {
    SegmentationVolume volume = newVolume();
    volume.addLabel(1, 1, 1, 1);
    assertAll(
        () -> assertNull(volume.findSegmentCenter(2, null)),
        () -> assertNull(volume.findSegmentCenter(9, null)));
  }

  @Test
  void findSegmentCenterCountsOverlappingVoxels() {
    SegmentationVolume volume = newVolume();
    // Overlapping voxels get a combination id: both segments must still be found there.
    volume.addLabel(2, 2, 3, 1);
    volume.addLabel(2, 2, 3, 2);
    assertAll(
        () -> assertEquals(new Vector3i(2, 2, 3), volume.findSegmentCenter(1, null)),
        () -> assertEquals(new Vector3i(2, 2, 3), volume.findSegmentCenter(2, null)));
  }
}
