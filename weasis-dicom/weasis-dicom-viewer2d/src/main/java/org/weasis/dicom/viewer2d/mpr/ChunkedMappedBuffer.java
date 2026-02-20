/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import org.weasis.core.util.FileUtil;

/**
 * A chunked memory-mapped file buffer that supports long byte-offset addressing for volumes larger
 * than {@code Integer.MAX_VALUE} bytes (~2 GB). Each chunk is a separate {@link MappedByteBuffer}
 * mapped to a contiguous region of the backing file.
 */
public final class ChunkedMappedBuffer {

  /**
   * Maximum bytes per chunk. Must be a multiple of 8 (largest primitive = double) to avoid
   * splitting a single element across chunk boundaries when used with aligned access.
   */
  static final long CHUNK_BYTE_SIZE = 1L << 30; // 1 GB per chunk

  private final MappedByteBuffer[] chunks;
  private final long totalBytes;
  private final File backingFile;

  /**
   * Creates a chunked mapped buffer backed by the given file.
   *
   * @param file the backing file (will be resized to {@code totalBytes})
   * @param totalBytes total byte size of the volume data
   * @throws IOException if file I/O fails
   */
  public ChunkedMappedBuffer(File file, long totalBytes) throws IOException {
    if (totalBytes < 0) {
      throw new IllegalArgumentException("Negative total bytes: " + totalBytes);
    }
    this.totalBytes = totalBytes;
    this.backingFile = file;

    int numChunks = (int) ((totalBytes + CHUNK_BYTE_SIZE - 1) / CHUNK_BYTE_SIZE);
    this.chunks = new MappedByteBuffer[Math.max(numChunks, 1)];

    try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
      raf.setLength(totalBytes);
      FileChannel channel = raf.getChannel();
      for (int i = 0; i < chunks.length; i++) {
        long offset = (long) i * CHUNK_BYTE_SIZE;
        long remaining = totalBytes - offset;
        long chunkLen = Math.min(remaining, CHUNK_BYTE_SIZE);
        chunks[i] = channel.map(MapMode.READ_WRITE, offset, chunkLen);
      }
    }
  }

  public long totalBytes() {
    return totalBytes;
  }

  private int chunkIndex(long byteOffset) {
    return (int) (byteOffset / CHUNK_BYTE_SIZE);
  }

  private int chunkOffset(long byteOffset) {
    return (int) (byteOffset % CHUNK_BYTE_SIZE);
  }

  /**
   * Reads elements from the mapped buffer into a ChunkedArray, dispatching on the chunk's primitive
   * type.
   */
  public <A> void readInto(
      ChunkedArray<A> raster, long byteOffset, int totalElements, int byteDepth) {
    long globalIndex = 0;
    int remaining = totalElements;
    while (remaining > 0) {
      int ci = raster.chunkIndex(globalIndex);
      int co = raster.chunkOffset(globalIndex);
      A chunk = raster.getChunk(ci);
      int chunkCapacity = java.lang.reflect.Array.getLength(chunk) - co;
      int count = Math.min(remaining, chunkCapacity);

      long bufferPos = byteOffset + globalIndex * byteDepth;
      switch (chunk) {
        case byte[] a -> {
          for (int i = 0; i < count; i++) a[co + i] = get(bufferPos + (long) i * byteDepth);
        }
        case short[] a -> {
          for (int i = 0; i < count; i++) a[co + i] = getShort(bufferPos + (long) i * byteDepth);
        }
        case int[] a -> {
          for (int i = 0; i < count; i++) a[co + i] = getInt(bufferPos + (long) i * byteDepth);
        }
        case float[] a -> {
          for (int i = 0; i < count; i++) a[co + i] = getFloat(bufferPos + (long) i * byteDepth);
        }
        case double[] a -> {
          for (int i = 0; i < count; i++) a[co + i] = getDouble(bufferPos + (long) i * byteDepth);
        }
        default -> throw new IllegalStateException("Unexpected array type: " + chunk.getClass());
      }

      globalIndex += count;
      remaining -= count;
    }
  }

  // ---- Primitive accessors (absolute positioning) ----

  public byte get(long byteOffset) {
    return chunks[chunkIndex(byteOffset)].get(chunkOffset(byteOffset));
  }

  public void put(long byteOffset, byte value) {
    chunks[chunkIndex(byteOffset)].put(chunkOffset(byteOffset), value);
  }

  public short getShort(long byteOffset) {
    return chunks[chunkIndex(byteOffset)].getShort(chunkOffset(byteOffset));
  }

  public void putShort(long byteOffset, short value) {
    chunks[chunkIndex(byteOffset)].putShort(chunkOffset(byteOffset), value);
  }

  public int getInt(long byteOffset) {
    return chunks[chunkIndex(byteOffset)].getInt(chunkOffset(byteOffset));
  }

  public void putInt(long byteOffset, int value) {
    chunks[chunkIndex(byteOffset)].putInt(chunkOffset(byteOffset), value);
  }

  public float getFloat(long byteOffset) {
    return chunks[chunkIndex(byteOffset)].getFloat(chunkOffset(byteOffset));
  }

  public void putFloat(long byteOffset, float value) {
    chunks[chunkIndex(byteOffset)].putFloat(chunkOffset(byteOffset), value);
  }

  public double getDouble(long byteOffset) {
    return chunks[chunkIndex(byteOffset)].getDouble(chunkOffset(byteOffset));
  }

  public void putDouble(long byteOffset, double value) {
    chunks[chunkIndex(byteOffset)].putDouble(chunkOffset(byteOffset), value);
  }

  /**
   * Reads a contiguous range of bytes into the destination array.
   *
   * @param byteOffset starting byte offset in the mapped file
   * @param dest destination byte array
   * @param destPos starting index in dest
   * @param length number of bytes to read
   */
  public void getBytes(long byteOffset, byte[] dest, int destPos, int length) {
    int remaining = length;
    long pos = byteOffset;
    int dIdx = destPos;
    while (remaining > 0) {
      int ci = chunkIndex(pos);
      int co = chunkOffset(pos);
      int toCopy = (int) Math.min(remaining, CHUNK_BYTE_SIZE - co);
      MappedByteBuffer chunk = chunks[ci];
      // Use absolute bulk get via slice to avoid affecting buffer position
      for (int i = 0; i < toCopy; i++) {
        dest[dIdx + i] = chunk.get(co + i);
      }
      remaining -= toCopy;
      pos += toCopy;
      dIdx += toCopy;
    }
  }

  /** Releases all mapped buffers and deletes the backing file. */
  public void close() {
    for (int i = 0; i < chunks.length; i++) {
      if (chunks[i] != null) {
        chunks[i].force();
        chunks[i] = null;
      }
    }
    if (backingFile != null) {
      FileUtil.delete(backingFile.toPath());
    }
  }
}
