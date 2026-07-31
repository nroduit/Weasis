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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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

    // The scan is parallel, so only the totals are deterministic.
    AtomicInteger scanned = new AtomicInteger();
    AtomicInteger total = new AtomicInteger();
    Vector3i center =
        volume.findSegmentCenter(
            1,
            (done, size) -> {
              scanned.incrementAndGet();
              total.set(size);
            });

    assertNotNull(center);
    assertAll(
        () -> assertEquals(new Vector3i(5, 3, 5), center),
        () -> assertEquals(SIZE, scanned.get()),
        () -> assertEquals(SIZE, total.get()));
  }

  @Test
  void findSegmentCenterKeepsTheLowestSliceOnEqualCounts() {
    SegmentationVolume volume = newVolume();
    volume.addLabel(1, 1, 2, 1);
    volume.addLabel(6, 6, 6, 1);
    assertEquals(new Vector3i(1, 1, 2), volume.findSegmentCenter(1, null));
  }

  @Test
  void voxelToLpsAppliesOriginSpacingAndAxes() {
    Map<Integer, RegionAttributes> attributes = new LinkedHashMap<>();
    attributes.put(1, liver);
    SegmentationVolume volume =
        new SegmentationVolume(
            SIZE,
            SIZE,
            SIZE,
            new Vector3d(0.5, 2, 3),
            new Vector3d(10, 20, 30),
            new Vector3d(0, 1, 0),
            new Vector3d(-1, 0, 0),
            new Vector3d(0, 0, 1),
            attributes);
    assertAll(
        () -> assertEquals(new Vector3d(10, 20, 30), volume.voxelToLps(0, 0, 0)),
        () -> assertEquals(new Vector3d(6, 22, 39), volume.voxelToLps(4, 2, 3)));
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

  @Test
  void resampleIntoCopiesLabelsOnAnIdenticalGrid() {
    SegmentationVolume source = newVolume();
    source.addLabel(1, 2, 3, 1);
    source.addLabel(6, 6, 6, 2);
    // Overlap: the combination id must be demultiplexed back into both segments.
    source.addLabel(4, 4, 4, 1);
    source.addLabel(4, 4, 4, 2);

    SegmentationVolume target = newVolume();
    long stamped = source.resampleInto(target, (x, y, z, dst) -> dst.set(x, y, z));
    target.applySegmentVoxelCounts();

    assertAll(
        () -> assertEquals(3L, stamped),
        () -> assertEquals(2L, liver.getNumberOfPixels()),
        () -> assertEquals(2L, kidney.getNumberOfPixels()),
        () -> assertEquals(new Vector3i(1, 2, 3), target.findSegmentCenter(1, null)));
  }

  @Test
  void resampleIntoAppliesTheTargetToSourceShift() {
    SegmentationVolume source = newVolume();
    source.addLabel(5, 1, 1, 1);

    SegmentationVolume target = newVolume();
    // Target voxel (x, y, z) sits at LPS (x + 2, y, z), i.e. on source voxel (x + 2, y, z).
    long stamped = source.resampleInto(target, (x, y, z, dst) -> dst.set(x + 2, y, z));

    assertAll(
        () -> assertEquals(1L, stamped),
        () -> assertEquals(new Vector3i(3, 1, 1), target.findSegmentCenter(1, null)));
  }

  @Test
  void resampleIntoStampsNothingWhenTheGridsDoNotOverlap() {
    SegmentationVolume source = newVolume();
    source.addLabel(4, 4, 4, 1);

    SegmentationVolume target = newVolume();
    // Shifted far past the source extent: the bounding box is empty.
    assertEquals(0L, source.resampleInto(target, (x, y, z, dst) -> dst.set(x + 100, y, z)));
  }

  @Test
  void addLabelOnBackgroundUsesTheSingletonIdAndStaysMergeable() {
    // The background voxel short-circuit must produce exactly the id the combination lookup would:
    // stamping a second segment on top has to still yield a proper two-segment combination.
    SegmentationVolume volume = newVolume();
    volume.addLabel(2, 2, 2, 1);
    int[] afterFirst = volume.exportSliceBitmask(2);
    volume.addLabel(2, 2, 2, 2);
    int[] afterSecond = volume.exportSliceBitmask(2);
    volume.applySegmentVoxelCounts();

    int index = 2 * SIZE + 2;
    assertAll(
        // A lone segment gets its singleton id, and re-stamping it is idempotent.
        () -> assertEquals(1, afterFirst[index]),
        // Overlaying a second segment must allocate a distinct combination id, not overwrite.
        () ->
            assertTrue(
                afterSecond[index] > 2, "expected a combination id, got " + afterSecond[index]),
        () -> assertEquals(1L, liver.getNumberOfPixels()),
        () -> assertEquals(1L, kidney.getNumberOfPixels()),
        () -> assertEquals(new Vector3i(2, 2, 2), volume.findSegmentCenter(1, null)),
        () -> assertEquals(new Vector3i(2, 2, 2), volume.findSegmentCenter(2, null)));
  }

  @Test
  void mergeIntoShiftsSegmentNumbersAndKeepsExistingLabels() {
    SegmentationVolume first = newVolume();
    first.addLabel(1, 1, 1, 1);
    first.addLabel(3, 3, 3, 2);

    SegmentationVolume second = newVolume();
    second.addLabel(5, 5, 5, 1);
    // Same voxel as `first`: the merged volume must end up carrying both files there.
    second.addLabel(1, 1, 1, 1);

    // Four segments: file #1 keeps 1..2, file #2 is shifted to 3..4.
    Map<Integer, RegionAttributes> combined = new LinkedHashMap<>();
    combined.put(1, liver);
    combined.put(2, kidney);
    combined.put(3, new SegRegion<>(3, "Liver2", Color.GREEN));
    combined.put(4, new SegRegion<>(4, "Kidney2", Color.YELLOW));
    SegmentationVolume merged = first.createCompatible(combined);

    long fromFirst = first.mergeInto(merged, segNum -> segNum);
    long fromSecond = second.mergeInto(merged, segNum -> segNum + 2);
    merged.applySegmentVoxelCounts();

    assertAll(
        () -> assertEquals(2L, fromFirst),
        () -> assertEquals(2L, fromSecond),
        () -> assertEquals(new Vector3i(1, 1, 1), merged.findSegmentCenter(1, null)),
        () -> assertEquals(new Vector3i(3, 3, 3), merged.findSegmentCenter(2, null)),
        // Segment 3 = file #2's segment 1, present at (5,5,5) and at the shared (1,1,1).
        () -> assertEquals(new Vector3i(1, 1, 1), merged.findSegmentCenter(3, null)),
        () -> assertNull(merged.findSegmentCenter(4, null)),
        () -> assertEquals(1L, liver.getNumberOfPixels()),
        () -> assertEquals(2L, combined.get(3).getNumberOfPixels()));
  }

  @Test
  void mergeIntoCombinesFullyOverlappingVolumes() {
    // Mirrors the 3D viewer's worst case: several single-segment SEG files covering the same grid,
    // so every voxel ends up carrying a combination of all of them.
    int files = 4;
    Map<Integer, RegionAttributes> combined = new LinkedHashMap<>();
    for (int f = 1; f <= files; f++) {
      combined.put(f, new SegRegion<>(f, "Seg" + f, Color.RED));
    }
    SegmentationVolume merged = newVolume().createCompatible(combined);

    long expected = (long) SIZE * SIZE * SIZE;
    for (int f = 1; f <= files; f++) {
      SegmentationVolume source = newVolume();
      for (int z = 0; z < SIZE; z++) {
        for (int y = 0; y < SIZE; y++) {
          for (int x = 0; x < SIZE; x++) {
            source.addLabel(x, y, z, 1);
          }
        }
      }
      int base = f - 1;
      assertEquals(expected, source.mergeInto(merged, segNum -> segNum + base));
    }
    merged.applySegmentVoxelCounts();

    // Every segment must cover the whole grid, and each voxel carries one 4-segment combination.
    assertAll(
        combined.values().stream()
            .map(region -> () -> assertEquals(expected, region.getNumberOfPixels())));
  }

  @Test
  void mergeIntoRejectsAGridOfADifferentSize() {
    Map<Integer, RegionAttributes> attributes = new LinkedHashMap<>();
    attributes.put(1, liver);
    SegmentationVolume source = newVolume();
    source.addLabel(1, 1, 1, 1);
    SegmentationVolume other =
        new SegmentationVolume(
            SIZE + 1,
            SIZE,
            SIZE,
            new Vector3d(1, 1, 1),
            new Vector3d(),
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 1),
            attributes);
    assertEquals(0L, source.mergeInto(other, segNum -> segNum));
  }

  @Test
  void exportSliceIntoMatchesTheWidenedExportInTheNativeWidth() {
    SegmentationVolume volume = newVolume();
    volume.addLabel(1, 0, 2, 1);
    volume.addLabel(2, 0, 2, 2);
    int[] expected = volume.exportSliceBitmask(2);

    ByteBuffer buffer = ByteBuffer.allocateDirect(SIZE * SIZE).order(ByteOrder.nativeOrder());
    assertFalse(volume.isShortMode());
    assertTrue(volume.exportSliceInto(2, buffer));
    assertEquals(SIZE * SIZE, buffer.limit());
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], buffer.get(i) & 0xFF, "voxel " + i);
    }
    assertFalse(volume.exportSliceInto(SIZE, buffer));
  }
}
