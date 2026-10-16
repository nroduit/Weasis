/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;

class MajorantGridTest {

  private static final int SIZE = 10;

  @Test
  void neighbourBlockOnlyAtBlockEdges() {
    assertAll(
        () -> assertEquals(-1, MajorantGrid.neighbourBlock(0, 0, 20)),
        () -> assertEquals(0, MajorantGrid.neighbourBlock(8, 1, 20)),
        () -> assertEquals(1, MajorantGrid.neighbourBlock(7, 0, 20)),
        () -> assertEquals(-1, MajorantGrid.neighbourBlock(3, 0, 20)),
        () -> assertEquals(-1, MajorantGrid.neighbourBlock(7, 0, 8)),
        () -> assertEquals(2, MajorantGrid.neighbourBlock(15, 1, 20)));
  }

  @Test
  void gridCoversTheVolumeInBlocks() {
    MajorantGrid grid = new MajorantGrid(512, 300, 9);
    assertAll(
        () -> assertEquals(64, grid.getWidth()),
        () -> assertEquals(38, grid.getHeight()),
        () -> assertEquals(2, grid.getDepth()));
  }

  @Test
  void brightVoxelWidensIntoTheBlocksItBorders() {
    MajorantGrid grid = new MajorantGrid(SIZE, SIZE, SIZE);
    byte[] voxels = new byte[SIZE * SIZE * SIZE];
    voxels[index(8, 8, 8)] = (byte) 255;
    grid.accumulate(MemorySegment.ofArray(voxels), CvType.CV_8UC1, 0, SIZE);
    grid.complete();
    assertTrue(grid.isComplete());
    assertAll(
        () -> assertArrayEquals(new float[] {0f, 1f}, grid.rangeAt(1, 1, 1)),
        () -> assertArrayEquals(new float[] {0f, 1f}, grid.rangeAt(0, 0, 0)),
        () -> assertArrayEquals(new float[] {0f, 1f}, grid.rangeAt(0, 1, 1)));
  }

  @Test
  void voxelInsideABlockStaysInThatBlock() {
    MajorantGrid grid = new MajorantGrid(SIZE, SIZE, SIZE);
    byte[] voxels = new byte[SIZE * SIZE * SIZE];
    voxels[index(3, 3, 3)] = (byte) 255;
    grid.accumulate(MemorySegment.ofArray(voxels), CvType.CV_8UC1, 0, SIZE);
    grid.complete();
    assertAll(
        () -> assertArrayEquals(new float[] {0f, 1f}, grid.rangeAt(0, 0, 0)),
        () -> assertArrayEquals(new float[] {0f, 0f}, grid.rangeAt(1, 0, 0)),
        () -> assertArrayEquals(new float[] {0f, 0f}, grid.rangeAt(1, 1, 1)));
  }

  @Test
  void slicesAccumulateInBatchesAndShortsAreNormalized() {
    MajorantGrid grid = new MajorantGrid(SIZE, SIZE, SIZE);
    short[] batch = new short[SIZE * SIZE * 4];
    java.util.Arrays.fill(batch, (short) 13107);
    grid.accumulate(MemorySegment.ofArray(batch), CvType.CV_16UC1, 0, 4);
    short[] rest = new short[SIZE * SIZE * 6];
    java.util.Arrays.fill(rest, (short) 0xFFFF);
    grid.accumulate(MemorySegment.ofArray(rest), CvType.CV_16UC1, 4, 6);
    grid.complete();
    float[] low = grid.rangeAt(0, 0, 0);
    float[] high = grid.rangeAt(0, 0, 1);
    assertAll(
        () -> assertEquals(0.2f, low[0], 1e-3f),
        () -> assertEquals(1f, low[1], 1e-6f),
        () -> assertEquals(1f, high[0], 1e-6f),
        () -> assertEquals(1f, high[1], 1e-6f));
  }

  @Test
  void oneSegmentPerSliceFoldsLikeOneBlock() {
    short[] voxels = new short[SIZE * SIZE * SIZE];
    for (int i = 0; i < voxels.length; i++) {
      voxels[i] = (short) (i * 7919);
    }
    MajorantGrid block = new MajorantGrid(SIZE, SIZE, SIZE);
    block.accumulate(MemorySegment.ofArray(voxels), CvType.CV_16UC1, 0, SIZE);
    MajorantGrid perSlice = new MajorantGrid(SIZE, SIZE, SIZE);
    List<MemorySegment> slices = new ArrayList<>();
    for (int z = 0; z < SIZE; z++) {
      slices.add(
          MemorySegment.ofArray(
              java.util.Arrays.copyOfRange(voxels, z * SIZE * SIZE, (z + 1) * SIZE * SIZE)));
    }
    perSlice.accumulate(slices.subList(0, 5), CvType.CV_16UC1, 0);
    perSlice.accumulate(slices.subList(5, SIZE), CvType.CV_16UC1, 5);
    block.complete();
    perSlice.complete();
    for (int z = 0; z < block.getDepth(); z++) {
      for (int y = 0; y < block.getHeight(); y++) {
        for (int x = 0; x < block.getWidth(); x++) {
          assertArrayEquals(block.rangeAt(x, y, z), perSlice.rangeAt(x, y, z));
        }
      }
    }
  }

  @Test
  void colourVolumeContributesLuminance() {
    MajorantGrid grid = new MajorantGrid(2, 2, 2);
    byte[] voxels = new byte[2 * 2 * 2 * 3];
    voxels[0] = (byte) 255; // pure red at the first voxel
    grid.accumulate(MemorySegment.ofArray(voxels), CvType.CV_8UC3, 0, 2);
    grid.complete();
    assertEquals(0.299f, grid.rangeAt(0, 0, 0)[1], 1e-4f);
  }

  @Test
  void unsupportedLayoutLeavesTheGridIncomplete() {
    MajorantGrid grid = new MajorantGrid(2, 2, 2);
    grid.accumulate(MemorySegment.ofArray(new short[2 * 2 * 2 * 3]), CvType.CV_16UC3, 0, 2);
    grid.complete();
    assertFalse(grid.isComplete());
  }

  private static int index(int x, int y, int z) {
    return (z * SIZE + y) * SIZE + x;
  }
}
