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

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.ReentrantLock;
import org.joml.Matrix4d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.image.cv.CvUtil;

/**
 * Carries all per-build state needed for one splatting pass:
 *
 * <ul>
 *   <li>{@code transform} — the slice-to-volume matrix for the current slice.
 *   <li>{@code dim} — the pixel dimensions of the current source image.
 *   <li>{@code weightAcc} / {@code valueAcc} — accumulated trilinear weights and weighted values as
 *       IEEE-754 {@code float} bits; {@code null} when splatting is not active.
 * </ul>
 *
 * <p>Two storage back-ends are supported for the accumulator arrays:
 *
 * <ol>
 *   <li><b>Heap ({@link ChunkedArray}{@code <int[]>})</b> — preferred path. Each chunk is a plain
 *       {@code int[]} compatible with lock-free {@link VarHandle} CAS, and the chunked layout lets
 *       the JVM satisfy large allocations from multiple smaller heap regions.
 *   <li><b>Disk ({@link ChunkedMappedBuffer})</b> — fallback when even the chunked heap allocation
 *       fails with {@link OutOfMemoryError}. Two temp files are memory-mapped; writes are
 *       serialised per-voxel through a striped {@link ReentrantLock} array so that concurrent slice
 *       threads remain safe without CAS. This path is slower but avoids crashing on low-memory
 *       machines.
 * </ol>
 *
 * <p>{@code SplatContext} implements {@link AutoCloseable}: callers must use it inside a
 * try-with-resources block so that mapped temp files are released when building is done.
 */
public final class SplatContext implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(SplatContext.class);

  /**
   * Minimum accumulated weight below which a voxel is considered unsampled and is left at the
   * background value during weighted-splat normalisation.
   */
  public static final float WEIGHT_EPSILON = 1e-6f;

  /**
   * VarHandle for atomic CAS operations on plain {@code int[]} arrays used by the float-based
   * splatting accumulators (heap path).
   */
  private static final VarHandle INT_ARRAY_HANDLE =
      MethodHandles.arrayElementVarHandle(int[].class);

  /**
   * Number of stripes in the lock array used by the mapped (disk) accumulator path.
   *
   * <p>Must be a power of two for efficient {@code idx & (LOCK_STRIPES-1)} masking.
   */
  private static final int LOCK_STRIPES = 1 << 13; // 8 192 locks

  // ---- Accumulator back-ends (exactly one non-null when splatting is active) ----

  /**
   * Heap-backed accumulators. Each element stores an IEEE-754 {@code float} as raw {@code int}
   * bits. CAS via {@link #INT_ARRAY_HANDLE} provides lock-free thread safety.
   */
  private final ChunkedArray<int[]> weightAcc;

  private final ChunkedArray<int[]> valueAcc;

  /**
   * Disk-backed accumulators used when heap allocation fails. Guarded by {@link #locks}. Each float
   * (4 bytes) is stored at byte offset {@code idx * 4L}.
   */
  private final ChunkedMappedBuffer mappedWeightAcc;

  private final ChunkedMappedBuffer mappedValueAcc;

  /**
   * Striped lock array protecting the mapped accumulator path. {@code locks[idx &
   * (LOCK_STRIPES-1)]} is acquired before any read-modify-write on element {@code idx}.
   */
  private final ReentrantLock[] locks;

  // ---- Per-slice state ----

  private final Matrix4d transform;
  private final Dimension dim;

  // ---- Private constructors ----

  /** Heap path constructor. */
  private SplatContext(
      Matrix4d transform,
      Dimension dim,
      ChunkedArray<int[]> weightAcc,
      ChunkedArray<int[]> valueAcc) {
    this.transform = transform;
    this.dim = dim;
    this.weightAcc = weightAcc;
    this.valueAcc = valueAcc;
    this.mappedWeightAcc = null;
    this.mappedValueAcc = null;
    this.locks = null;
  }

  /** Mapped (disk) path constructor. */
  private SplatContext(
      Matrix4d transform,
      Dimension dim,
      ChunkedMappedBuffer mappedWeightAcc,
      ChunkedMappedBuffer mappedValueAcc,
      ReentrantLock[] locks) {
    this.transform = transform;
    this.dim = dim;
    this.weightAcc = null;
    this.valueAcc = null;
    this.mappedWeightAcc = mappedWeightAcc;
    this.mappedValueAcc = mappedValueAcc;
    this.locks = locks;
  }

  // ---- Factory ----

  /**
   * Creates a {@code SplatContext} with shared accumulator arrays when weighted splatting is
   * appropriate.
   *
   * <p>Allocation strategy:
   *
   * <ol>
   *   <li>Try {@link ChunkedArray}{@code <int[]>} on the heap (preferred — lock-free CAS).
   *   <li>On {@link OutOfMemoryError}, run GC and retry once.
   *   <li>If still OOM, fall back to two memory-mapped temp files (disk path, striped locks).
   *   <li>If the disk fallback also fails, splatting is disabled (nearest-corner fallback).
   * </ol>
   *
   * @param useWeightedSplat {@code true} for rectified, in-memory volumes
   * @param totalVoxels total number of voxel elements (size.x × size.y × size.z × channels)
   */
  public static SplatContext create(boolean useWeightedSplat, long totalVoxels) {
    if (!useWeightedSplat || totalVoxels <= 0) {
      return new SplatContext(null, null, null, null);
    }

    // --- Attempt 1 & 2: heap-backed ChunkedArray<int[]> ---
    ChunkedArray<int[]> wHeap;
    ChunkedArray<int[]> vHeap;
    try {
      wHeap = ChunkedArray.ofInt(totalVoxels);
      vHeap = ChunkedArray.ofInt(totalVoxels);
      // JVM zeroes each int[] chunk — 0 == 0.0f in IEEE-754, no explicit fill needed.
      return new SplatContext(null, null, wHeap, vHeap);
    } catch (OutOfMemoryError e) {
      LOGGER.warn("OOM allocating heap accumulators ({} voxels), retrying after GC…", totalVoxels);
      CvUtil.runGarbageCollectorAndWait(200);
      try {
        wHeap = ChunkedArray.ofInt(totalVoxels);
        vHeap = ChunkedArray.ofInt(totalVoxels);
        return new SplatContext(null, null, wHeap, vHeap);
      } catch (OutOfMemoryError e2) {
        LOGGER.warn(
            "OOM after GC — falling back to disk-backed accumulators for {} voxels", totalVoxels);
      }
    }

    // --- Attempt 3: disk-backed mapped accumulators ---
    long totalBytes = totalVoxels * Float.BYTES; // 4 bytes per float element
    try {
      File wFile =
          File.createTempFile("splat_weight", ".tmp", AppProperties.FILE_CACHE_DIR.toFile());
      File vFile =
          File.createTempFile("splat_value", ".tmp", AppProperties.FILE_CACHE_DIR.toFile());
      ChunkedMappedBuffer mw = new ChunkedMappedBuffer(wFile, totalBytes);
      ChunkedMappedBuffer mv = new ChunkedMappedBuffer(vFile, totalBytes);
      // Build striped lock array (shared across all per-slice contexts)
      ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];
      for (int i = 0; i < LOCK_STRIPES; i++) {
        locks[i] = new ReentrantLock();
      }
      LOGGER.info("Using disk-backed splat accumulators ({} MB × 2)", totalBytes / (1024 * 1024));
      return new SplatContext(null, null, mw, mv, locks);
    } catch (IOException | OutOfMemoryError ex) {
      LOGGER.error(
          "Failed to create disk-backed splat accumulators — weighted splatting disabled", ex);
      return new SplatContext(null, null, null, null);
    }
  }

  // ---- Lifecycle ----

  /**
   * Releases disk-backed temp files if present. No-op for heap-backed or no-op contexts. Must be
   * called after {@link #normalize} completes.
   */
  @Override
  public void close() {
    if (mappedWeightAcc != null) mappedWeightAcc.close();
    if (mappedValueAcc != null) mappedValueAcc.close();
  }

  // ---- Context forking ----

  /** Returns a new context that shares the same accumulators but uses the given transform / dim. */
  public SplatContext withTransformAndDim(Matrix4d transform, Dimension dim) {
    if (mappedWeightAcc != null) {
      return new SplatContext(transform, dim, mappedWeightAcc, mappedValueAcc, locks);
    }
    return new SplatContext(transform, dim, weightAcc, valueAcc);
  }

  // ---- Accessors ----

  public Matrix4d transform() {
    return transform;
  }

  public Dimension dim() {
    return dim;
  }

  /** {@code true} when the accumulator arrays are present and weighted splatting is active. */
  public boolean isWeighted() {
    return weightAcc != null || mappedWeightAcc != null;
  }

  /**
   * Atomically accumulates {@code value * weight} into {@code valueAcc[idx]} and {@code weight}
   * into {@code weightAcc[idx]}.
   *
   * <p><b>Heap path:</b> lock-free CAS loop on the {@code int[]} chunk containing {@code idx}.
   *
   * <p><b>Disk path:</b> per-voxel read-modify-write serialised through a striped {@link
   * ReentrantLock} (stripe = {@code idx & (LOCK_STRIPES - 1)}).
   */
  public void accumulate(long idx, float value, float weight) {
    final ChunkedArray<int[]> wa = weightAcc;
    if (wa != null) {
      // --- Heap CAS path ---
      final ChunkedArray<int[]> va = valueAcc;
      int ci = wa.chunkIndex(idx);
      int co = wa.chunkOffset(idx);

      int[] wChunk = wa.getChunk(ci);
      int prev, next;
      do {
        prev = (int) INT_ARRAY_HANDLE.getVolatile(wChunk, co);
        next = Float.floatToRawIntBits(Float.intBitsToFloat(prev) + weight);
      } while (!INT_ARRAY_HANDLE.compareAndSet(wChunk, co, prev, next));

      // va is always non-null when wa is non-null (invariant enforced by constructors)
      int[] vChunk = va != null ? va.getChunk(ci) : null;
      if (vChunk != null) {
        do {
          prev = (int) INT_ARRAY_HANDLE.getVolatile(vChunk, co);
          next = Float.floatToRawIntBits(Float.intBitsToFloat(prev) + value * weight);
        } while (!INT_ARRAY_HANDLE.compareAndSet(vChunk, co, prev, next));
      }

    } else {
      final ChunkedMappedBuffer mw = mappedWeightAcc;
      final ChunkedMappedBuffer mv = mappedValueAcc;
      final ReentrantLock[] lks = locks;
      if (mw != null && mv != null && lks != null) {
        // --- Disk striped-lock path ---
        long byteOffset = idx * Float.BYTES;
        ReentrantLock lock = lks[(int) (idx & (LOCK_STRIPES - 1))];
        lock.lock();
        try {
          mw.putFloat(byteOffset, mw.getFloat(byteOffset) + weight);
          mv.putFloat(byteOffset, mv.getFloat(byteOffset) + value * weight);
        } finally {
          lock.unlock();
        }
      }
    }
  }

  // ---- Normalisation ----

  /**
   * Normalisation pass: for every voxel divides the accumulated weighted value by the accumulated
   * weight and writes the result into the volume's data array. Voxels whose total weight is below
   * {@link #WEIGHT_EPSILON} keep the background value written by {@code createData()}.
   *
   * <p>No-op when the accumulators are {@code null} (non-weighted mode).
   */
  public <T extends Number, A> void normalize(Volume<T, A> volume) {
    if (!isWeighted()) return;

    final ChunkedArray<int[]> wa = weightAcc;
    final ChunkedArray<int[]> va = valueAcc;
    final ChunkedMappedBuffer mw = mappedWeightAcc;
    final ChunkedMappedBuffer mv = mappedValueAcc;

    int nx = volume.size.x;
    int ny = volume.size.y;
    int nz = volume.size.z;
    for (int z = 0; z < nz; z++) {
      for (int y = 0; y < ny; y++) {
        for (int x = 0; x < nx; x++) {
          for (int c = 0; c < volume.channels; c++) {
            long idx = volume.linearIndex(x, y, z, c);
            float w;
            float v;
            if (wa != null && va != null) {
              w =
                  Float.intBitsToFloat(
                      (int)
                          INT_ARRAY_HANDLE.get(
                              wa.getChunk(wa.chunkIndex(idx)), wa.chunkOffset(idx)));
              if (w < WEIGHT_EPSILON) continue;
              v =
                  Float.intBitsToFloat(
                      (int)
                          INT_ARRAY_HANDLE.get(
                              va.getChunk(va.chunkIndex(idx)), va.chunkOffset(idx)));
            } else if (mw != null && mv != null) {
              long byteOffset = idx * Float.BYTES;
              w = mw.getFloat(byteOffset);
              if (w < WEIGHT_EPSILON) continue;
              v = mv.getFloat(byteOffset);
            } else {
              continue;
            }
            volume.setChannelValue(x, y, z, c, volume.convertToGeneric(v / w));
          }
        }
      }
    }
  }
}
