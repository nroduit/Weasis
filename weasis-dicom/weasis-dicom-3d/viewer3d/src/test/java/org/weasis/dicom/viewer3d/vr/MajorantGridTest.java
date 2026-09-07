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

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import org.junit.jupiter.api.Test;

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
    grid.accumulate(ByteBuffer.wrap(voxels), 1, 0, SIZE);
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
    grid.accumulate(ByteBuffer.wrap(voxels), 1, 0, SIZE);
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
    grid.accumulate(ShortBuffer.wrap(batch), 1, 0, 4);
    short[] rest = new short[SIZE * SIZE * 6];
    java.util.Arrays.fill(rest, (short) 0xFFFF);
    grid.accumulate(ShortBuffer.wrap(rest), 1, 4, 6);
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
  void colourVolumeContributesLuminance() {
    MajorantGrid grid = new MajorantGrid(2, 2, 2);
    byte[] voxels = new byte[2 * 2 * 2 * 3];
    voxels[0] = (byte) 255; // pure red at the first voxel
    grid.accumulate(ByteBuffer.wrap(voxels), 3, 0, 2);
    grid.complete();
    assertEquals(0.299f, grid.rangeAt(0, 0, 0)[1], 1e-4f);
  }

  @Test
  void unsupportedLayoutLeavesTheGridIncomplete() {
    MajorantGrid grid = new MajorantGrid(2, 2, 2);
    grid.accumulate(ShortBuffer.wrap(new short[2 * 2 * 2 * 3]), 3, 0, 2);
    grid.complete();
    assertFalse(grid.isComplete());
  }

  private static int index(int x, int y, int z) {
    return (z * SIZE + y) * SIZE + x;
  }
}
