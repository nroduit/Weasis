/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.vol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The bulk accessors agree with the per-element ones, across the boundary of two mapped chunks. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChunkedMappedBufferTest {

  private static final int COUNT = 1000;
  // Starts before the end of the first 1 GB chunk, ends in the second (the file is sparse)
  private static final long ACROSS = ChunkedMappedBuffer.CHUNK_BYTE_SIZE - 3L * 8 * 100;

  @TempDir Path dir;

  private ChunkedMappedBuffer buffer() throws IOException {
    return new ChunkedMappedBuffer(
        dir.resolve("volume.raw").toFile(), ChunkedMappedBuffer.CHUNK_BYTE_SIZE + 64 * 1024);
  }

  @Test
  void shorts_written_in_bulk_read_back_one_by_one_and_in_bulk() throws IOException {
    ChunkedMappedBuffer buffer = buffer();
    short[] values = new short[COUNT];
    for (int i = 0; i < COUNT; i++) {
      values[i] = (short) (i * 37 - 20000);
    }
    buffer.putShorts(ACROSS, values, 0, COUNT);
    for (int i = 0; i < COUNT; i++) {
      assertEquals(values[i], buffer.getShort(ACROSS + 2L * i), "index " + i);
    }
    short[] read = new short[COUNT + 2];
    buffer.getShorts(ACROSS, read, 2, COUNT);
    assertArrayEquals(values, java.util.Arrays.copyOfRange(read, 2, COUNT + 2));
  }

  @Test
  void every_type_reads_in_bulk_what_was_put_one_by_one() throws IOException {
    ChunkedMappedBuffer buffer = buffer();
    for (int i = 0; i < COUNT; i++) {
      buffer.put(ACROSS + i, (byte) i);
    }
    byte[] bytes = new byte[COUNT];
    buffer.getBytes(ACROSS, bytes, 0, COUNT);
    for (int i = 0; i < COUNT; i++) {
      assertEquals((byte) i, bytes[i]);
    }

    for (int i = 0; i < COUNT; i++) {
      buffer.putInt(ACROSS + 4L * i, i * 1_000_003);
    }
    int[] ints = new int[COUNT];
    buffer.getInts(ACROSS, ints, 0, COUNT);
    for (int i = 0; i < COUNT; i++) {
      assertEquals(i * 1_000_003, ints[i]);
    }

    for (int i = 0; i < COUNT; i++) {
      buffer.putDouble(ACROSS + 8L * i, i / 3.0);
    }
    double[] doubles = new double[COUNT];
    buffer.getDoubles(ACROSS, doubles, 0, COUNT);
    for (int i = 0; i < COUNT; i++) {
      assertEquals(i / 3.0, doubles[i]);
    }
  }

  @Test
  void read_into_a_chunked_array_matches_the_mapped_values() throws IOException {
    ChunkedMappedBuffer buffer = buffer();
    for (int i = 0; i < COUNT; i++) {
      buffer.putFloat(ACROSS + 4L * i, i * 0.5f);
    }
    ChunkedArray<float[]> raster = ChunkedArray.ofFloat(COUNT);
    buffer.readInto(raster, ACROSS, COUNT, Float.BYTES);
    for (int i = 0; i < COUNT; i++) {
      assertEquals(i * 0.5f, raster.getChunk(0)[i]);
    }
  }
}
