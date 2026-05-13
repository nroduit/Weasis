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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
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
   * Bulk reads {@code count} consecutive {@code float} values starting at {@code byteOffset} into
   * {@code dst[dstOff..dstOff+count]}. Handles chunk boundary crossings transparently. This is
   * substantially faster than a per-element {@link #getFloat(long)} loop because it uses {@code
   * MappedByteBuffer.asFloatBuffer()} which lets the JVM emit unrolled, vectorisable copies and
   * benefits from OS prefetch on sequential mapped pages.
   *
   * @param byteOffset starting byte offset (must be a multiple of {@code Float.BYTES})
   * @param dst destination array
   * @param dstOff first index in {@code dst} to write
   * @param count number of float values to read
   */
  public void getFloats(long byteOffset, float[] dst, int dstOff, int count) {
    while (count > 0) {
      int ci = chunkIndex(byteOffset);
      int co = chunkOffset(byteOffset);
      MappedByteBuffer chunk = chunks[ci];
      // Floats remaining in this chunk past `co`
      int floatsInChunk = (chunk.limit() - co) >>> 2;
      int n = Math.min(count, floatsInChunk);
      // Use a duplicated view so we can position it without touching the master buffer.
      java.nio.ByteBuffer slice = chunk.duplicate();
      slice.position(co);
      slice.asFloatBuffer().get(dst, dstOff, n);
      byteOffset += (long) n * Float.BYTES;
      dstOff += n;
      count -= n;
    }
  }

  /**
   * Bulk writes {@code count} consecutive {@code byte} values from {@code
   * src[srcOff..srcOff+count]} starting at {@code byteOffset}. Handles chunk boundary crossings
   * transparently.
   */
  public void putBytes(long byteOffset, byte[] src, int srcOff, int count) {
    while (count > 0) {
      int ci = chunkIndex(byteOffset);
      int co = chunkOffset(byteOffset);
      MappedByteBuffer chunk = chunks[ci];
      int bytesInChunk = chunk.limit() - co;
      int n = Math.min(count, bytesInChunk);
      java.nio.ByteBuffer slice = chunk.duplicate();
      slice.position(co);
      slice.put(src, srcOff, n);
      byteOffset += n;
      srcOff += n;
      count -= n;
    }
  }

  /**
   * Bulk writes {@code count} consecutive {@code short} values. Uses {@code asShortBuffer()} for
   * vectorisable copies. {@code byteOffset} must be a multiple of {@code Short.BYTES}.
   */
  public void putShorts(long byteOffset, short[] src, int srcOff, int count) {
    while (count > 0) {
      int ci = chunkIndex(byteOffset);
      int co = chunkOffset(byteOffset);
      MappedByteBuffer chunk = chunks[ci];
      int shortsInChunk = (chunk.limit() - co) >>> 1;
      int n = Math.min(count, shortsInChunk);
      java.nio.ByteBuffer slice = chunk.duplicate();
      slice.position(co);
      slice.asShortBuffer().put(src, srcOff, n);
      byteOffset += (long) n * Short.BYTES;
      srcOff += n;
      count -= n;
    }
  }

  /** Bulk writes {@code count} consecutive {@code int} values. */
  public void putInts(long byteOffset, int[] src, int srcOff, int count) {
    while (count > 0) {
      int ci = chunkIndex(byteOffset);
      int co = chunkOffset(byteOffset);
      MappedByteBuffer chunk = chunks[ci];
      int intsInChunk = (chunk.limit() - co) >>> 2;
      int n = Math.min(count, intsInChunk);
      java.nio.ByteBuffer slice = chunk.duplicate();
      slice.position(co);
      slice.asIntBuffer().put(src, srcOff, n);
      byteOffset += (long) n * Integer.BYTES;
      srcOff += n;
      count -= n;
    }
  }

  /** Bulk writes {@code count} consecutive {@code float} values. */
  public void putFloats(long byteOffset, float[] src, int srcOff, int count) {
    while (count > 0) {
      int ci = chunkIndex(byteOffset);
      int co = chunkOffset(byteOffset);
      MappedByteBuffer chunk = chunks[ci];
      int floatsInChunk = (chunk.limit() - co) >>> 2;
      int n = Math.min(count, floatsInChunk);
      java.nio.ByteBuffer slice = chunk.duplicate();
      slice.position(co);
      slice.asFloatBuffer().put(src, srcOff, n);
      byteOffset += (long) n * Float.BYTES;
      srcOff += n;
      count -= n;
    }
  }

  /** Bulk writes {@code count} consecutive {@code double} values. */
  public void putDoubles(long byteOffset, double[] src, int srcOff, int count) {
    while (count > 0) {
      int ci = chunkIndex(byteOffset);
      int co = chunkOffset(byteOffset);
      MappedByteBuffer chunk = chunks[ci];
      int doublesInChunk = (chunk.limit() - co) >>> 3;
      int n = Math.min(count, doublesInChunk);
      java.nio.ByteBuffer slice = chunk.duplicate();
      slice.position(co);
      slice.asDoubleBuffer().put(src, srcOff, n);
      byteOffset += (long) n * Double.BYTES;
      srcOff += n;
      count -= n;
    }
  }

  /**
   * Explicitly flushes all dirty pages to the backing file. Call this only when the file's contents
   * must persist after this buffer is released; for temp files that {@link #close} will delete it
   * is wasted I/O (potentially gigabytes of {@code msync} traffic) and should be skipped.
   */
  public void force() {
    for (MappedByteBuffer chunk : chunks) {
      if (chunk != null) {
        chunk.force();
      }
    }
  }

  /**
   * Releases all mapped buffers and deletes the backing file. Does <b>not</b> {@code force()} the
   * mapped pages first — the file is being deleted, so flushing them to disk would be a pure waste
   * (frequently several GB of synchronous I/O on large volumes). If callers need persistence they
   * must invoke {@link #force()} explicitly before {@code close()}.
   */
  public void close() {
    Arrays.fill(chunks, null);
    if (backingFile != null) {
      FileUtil.delete(backingFile.toPath());
    }
  }
}
