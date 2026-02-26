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

import java.util.Arrays;

/**
 * A chunked 1D array that supports long indexing for volumes larger than Integer.MAX_VALUE
 * elements. Data is stored in chunks of int-addressable arrays for compatibility with Java's array
 * size limit.
 *
 * @param <A> the primitive array type (e.g., byte[], short[], int[], float[], double[])
 */
public final class ChunkedArray<A> {

  /** Operations interface for primitive array types. */
  public interface ArrayOps<A> {
    A newArray(int size);

    Class<A> arrayClass();

    void fill(A array, Number value);
  }

  /** Maximum elements per chunk — kept under Integer.MAX_VALUE for safe int indexing. */
  static final int CHUNK_SIZE = 1 << 27; // ~128M elements per chunk

  private final A[] chunks;
  private final long totalElements;
  private final ArrayOps<A> ops;

  @SuppressWarnings("unchecked")
  ChunkedArray(long totalElements, ArrayOps<A> ops) {
    if (totalElements < 0) {
      throw new IllegalArgumentException("Negative total elements: " + totalElements);
    }
    this.totalElements = totalElements;
    this.ops = ops;
    int numChunks = (int) ((totalElements + CHUNK_SIZE - 1) / CHUNK_SIZE);
    this.chunks = (A[]) java.lang.reflect.Array.newInstance(ops.arrayClass(), numChunks);
    for (int i = 0; i < numChunks; i++) {
      long remaining = totalElements - (long) i * CHUNK_SIZE;
      int chunkLen = (int) Math.min(remaining, CHUNK_SIZE);
      chunks[i] = ops.newArray(chunkLen);
    }
  }

  public long size() {
    return totalElements;
  }

  /** Returns true if the total fits in a single chunk (fast path). */
  public boolean isSingleChunk() {
    return chunks.length == 1;
  }

  /** Direct access to the first (and only) chunk — use only when isSingleChunk() is true. */
  public A singleChunk() {
    return chunks[0];
  }

  public A getChunk(int chunkIndex) {
    return chunks[chunkIndex];
  }

  public int chunkCount() {
    return chunks.length;
  }

  public int chunkOffset(long globalIndex) {
    return (int) (globalIndex % CHUNK_SIZE);
  }

  public int chunkIndex(long globalIndex) {
    return (int) (globalIndex / CHUNK_SIZE);
  }

  /** Fills the entire chunked array with the given value. */
  public void fill(Number value) {
    for (A chunk : chunks) {
      ops.fill(chunk, value);
    }
  }

  /**
   * Copies a contiguous range from the chunked array into a destination primitive array.
   *
   * @param srcPos the starting global index in this chunked array
   * @param dest the destination primitive array
   * @param destPos the starting index in the destination array
   * @param length the number of elements to copy
   */
  public void copyTo(long srcPos, A dest, int destPos, long length) {
    long remaining = length;
    long gIdx = srcPos;
    int dIdx = destPos;
    while (remaining > 0) {
      int ci = chunkIndex(gIdx);
      int co = chunkOffset(gIdx);
      int toCopy = (int) Math.min(remaining, CHUNK_SIZE - co);
      System.arraycopy(chunks[ci], co, dest, dIdx, toCopy);
      remaining -= toCopy;
      gIdx += toCopy;
      dIdx += toCopy;
    }
  }

  /**
   * Copies from a source primitive array into this chunked array.
   *
   * @param destPos the starting global index in this chunked array
   * @param src the source primitive array
   * @param srcPos the starting index in the source array
   * @param length the number of elements to copy
   */
  public void copyFrom(long destPos, A src, int srcPos, long length) {
    long remaining = length;
    long gIdx = destPos;
    int sIdx = srcPos;
    while (remaining > 0) {
      int ci = chunkIndex(gIdx);
      int co = chunkOffset(gIdx);
      int toCopy = (int) Math.min(remaining, CHUNK_SIZE - co);
      System.arraycopy(src, sIdx, chunks[ci], co, toCopy);
      remaining -= toCopy;
      gIdx += toCopy;
      sIdx += toCopy;
    }
  }

  /**
   * Copies a contiguous range from this chunked array into a destination chunked array, correctly
   * handling chunk boundaries on both sides.
   *
   * @param srcPos the starting global index in this chunked array
   * @param dest the destination chunked array (must hold the same primitive array type)
   * @param destPos the starting global index in the destination chunked array
   * @param length the number of elements to copy
   */
  public void copyTo(long srcPos, ChunkedArray<A> dest, long destPos, long length) {
    long remaining = length;
    long sIdx = srcPos;
    long dIdx = destPos;
    while (remaining > 0) {
      int sci = chunkIndex(sIdx);
      int sco = chunkOffset(sIdx);
      int dci = dest.chunkIndex(dIdx);
      int dco = dest.chunkOffset(dIdx);
      int srcAvail = CHUNK_SIZE - sco;
      int destAvail = CHUNK_SIZE - dco;
      int toCopy = (int) Math.min(remaining, Math.min(srcAvail, destAvail));
      System.arraycopy(chunks[sci], sco, dest.chunks[dci], dco, toCopy);
      remaining -= toCopy;
      sIdx += toCopy;
      dIdx += toCopy;
    }
  }

  /**
   * Copies from a source chunked array into this chunked array, correctly handling chunk boundaries
   * on both sides.
   *
   * @param destPos the starting global index in this chunked array
   * @param src the source chunked array (must hold the same primitive array type)
   * @param srcPos the starting global index in the source chunked array
   * @param length the number of elements to copy
   */
  public void copyFrom(long destPos, ChunkedArray<A> src, long srcPos, long length) {
    src.copyTo(srcPos, this, destPos, length);
  }

  // ... existing code ...

  // ---- Factory methods for each primitive type ----

  private static final ArrayOps<byte[]> BYTE_OPS =
      new ArrayOps<>() {
        @Override
        public byte[] newArray(int size) {
          return new byte[size];
        }

        @Override
        public Class<byte[]> arrayClass() {
          return byte[].class;
        }

        @Override
        public void fill(byte[] array, Number value) {
          Arrays.fill(array, value.byteValue());
        }
      };

  private static final ArrayOps<short[]> SHORT_OPS =
      new ArrayOps<>() {
        @Override
        public short[] newArray(int size) {
          return new short[size];
        }

        @Override
        public Class<short[]> arrayClass() {
          return short[].class;
        }

        @Override
        public void fill(short[] array, Number value) {
          Arrays.fill(array, value.shortValue());
        }
      };

  private static final ArrayOps<int[]> INT_OPS =
      new ArrayOps<>() {
        @Override
        public int[] newArray(int size) {
          return new int[size];
        }

        @Override
        public Class<int[]> arrayClass() {
          return int[].class;
        }

        @Override
        public void fill(int[] array, Number value) {
          Arrays.fill(array, value.intValue());
        }
      };

  private static final ArrayOps<float[]> FLOAT_OPS =
      new ArrayOps<>() {
        @Override
        public float[] newArray(int size) {
          return new float[size];
        }

        @Override
        public Class<float[]> arrayClass() {
          return float[].class;
        }

        @Override
        public void fill(float[] array, Number value) {
          Arrays.fill(array, value.floatValue());
        }
      };

  private static final ArrayOps<double[]> DOUBLE_OPS =
      new ArrayOps<>() {
        @Override
        public double[] newArray(int size) {
          return new double[size];
        }

        @Override
        public Class<double[]> arrayClass() {
          return double[].class;
        }

        @Override
        public void fill(double[] array, Number value) {
          Arrays.fill(array, value.doubleValue());
        }
      };

  public static ChunkedArray<byte[]> ofByte(long totalElements) {
    return new ChunkedArray<>(totalElements, BYTE_OPS);
  }

  public static ChunkedArray<short[]> ofShort(long totalElements) {
    return new ChunkedArray<>(totalElements, SHORT_OPS);
  }

  public static ChunkedArray<int[]> ofInt(long totalElements) {
    return new ChunkedArray<>(totalElements, INT_OPS);
  }

  public static ChunkedArray<float[]> ofFloat(long totalElements) {
    return new ChunkedArray<>(totalElements, FLOAT_OPS);
  }

  public static ChunkedArray<double[]> ofDouble(long totalElements) {
    return new ChunkedArray<>(totalElements, DOUBLE_OPS);
  }
}
