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
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.image.cv.CvUtil;
import org.weasis.core.api.vol.ChunkedArray;
import org.weasis.core.api.vol.ChunkedMappedBuffer;

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
  /**
   * Normalisation pass: for every voxel divides the accumulated weighted value by the accumulated
   * weight and writes the result into the volume's data array. Voxels whose total weight is below
   * {@link #WEIGHT_EPSILON} keep the background value written by {@code createData()}.
   *
   * <p>Two execution strategies are used depending on the back-end:
   *
   * <ul>
   *   <li><b>Heap path</b> — work is split into Z-slabs and processed in parallel on the common
   *       ForkJoin pool. Each slab walks its accumulators chunk-by-chunk, hoisting chunk lookups
   *       out of the inner loop, and writes directly through {@link Volume#setNormalizedFloatAt}
   *       (no {@code (x,y,z,c)} decomposition, no {@code linearIndex} recomputation).
   *   <li><b>Disk (mapped) path</b> — runs serially. This back-end is only chosen when heap
   *       allocation has already failed, so the host is RAM-starved and parallel mapped-file access
   *       would thrash the page cache. The slice's accumulators are bulk-read into temp {@code
   *       float[]} arrays via {@link ChunkedMappedBuffer#getFloats}, replacing two per-voxel mapped
   *       reads with two big sequential ones.
   * </ul>
   *
   * <p>If {@code progressListener} is non-null it is invoked once per completed Z-slice so that the
   * caller can advance a UI progress indicator.
   *
   * <p>No-op when the accumulators are {@code null} (non-weighted mode).
   */
  public <T extends Number, A> void normalize(
      Volume<T, A> volume, java.util.function.IntConsumer progressListener) {
    if (!isWeighted()) return;

    final int nx = volume.size.x;
    final int ny = volume.size.y;
    final int nz = volume.size.z;
    final int channels = volume.channels;

    java.util.concurrent.atomic.AtomicInteger doneSlices =
        new java.util.concurrent.atomic.AtomicInteger();

    if (weightAcc != null && valueAcc != null) {
      if (volume.data != null) {
        LOGGER.debug("normalize path: heap-acc → heap-data");
        // Heap → heap: parallel by flat element range using RecursiveAction. The leaf threshold
        // is chosen so each task is large enough (~hundreds of µs) to amortise FJP overhead but
        // small enough to keep all cores busy on uneven volumes.
        long total = (long) nx * ny * nz * channels;
        long sliceLen = (long) nx * ny * channels;
        java.util.concurrent.ForkJoinPool pool = java.util.concurrent.ForkJoinPool.commonPool();
        java.util.concurrent.atomic.AtomicLong cpuNanos =
            new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicInteger leafCount =
            new java.util.concurrent.atomic.AtomicInteger();
        long t0 = System.nanoTime();
        pool.invoke(
            new HeapNormalizeTask(
                volume,
                0L,
                total,
                sliceLen,
                doneSlices,
                progressListener,
                nz,
                cpuNanos,
                leafCount));
        long wallMs = (System.nanoTime() - t0) / 1_000_000L;
        long cpuMs = cpuNanos.get() / 1_000_000L;
        LOGGER.debug(
            "normalize heap-heap: {} voxels, {} leaves, parallelism={}, wall={} ms, sumCpu={} ms,"
                + " speedup≈{}x",
            total,
            leafCount.get(),
            pool.getParallelism(),
            wallMs,
            cpuMs,
            wallMs == 0
                ? 0
                : String.format(java.util.Locale.ROOT, "%.2f", cpuMs / (double) wallMs));
      } else {
        LOGGER.debug("normalize path: heap-acc → mapped-data");
        // Heap accumulators + mapped destination data: each Z-slice still emits a single bulk
        // mapped write; parallelism stays per-slab so the bulk writes don't interleave.
        java.util.stream.IntStream.range(0, nz)
            .parallel()
            .forEach(
                z -> {
                  normalizeSliceHeapToMapped(volume, z, nx, ny, channels);
                  if (progressListener != null) {
                    progressListener.accept(doneSlices.incrementAndGet());
                  }
                });
      }
    } else if (mappedWeightAcc != null && mappedValueAcc != null) {
      long sliceLen = (long) nx * ny * channels;
      if (volume.data != null) {
        LOGGER.debug("normalize path: mapped-acc → heap-data (parallel)");
        // Mapped accumulators but heap-resident destination: parallel by Z-slice. Each slice
        // does two bulk mapped reads (sequential per slice, the OS prefetches pages) into a
        // per-thread scratch float[] pair, then runs a hoisted-dispatch typed inner loop into
        // the heap data array. The mapped reads happen concurrently from multiple threads but
        // the byte ranges are disjoint so there's no false sharing on the page cache lines.
        ThreadLocal<float[][]> scratchTL =
            ThreadLocal.withInitial(
                () -> new float[][] {new float[(int) sliceLen], new float[(int) sliceLen]});
        long t0 = System.nanoTime();
        java.util.stream.IntStream.range(0, nz)
            .parallel()
            .forEach(
                z -> {
                  float[][] sc = scratchTL.get();
                  normalizeSliceMappedToHeap(volume, z, sliceLen, sc[0], sc[1]);
                  if (progressListener != null) {
                    progressListener.accept(doneSlices.incrementAndGet());
                  }
                });
        long wallMs = (System.nanoTime() - t0) / 1_000_000L;
        LOGGER.debug(
            "normalize mapped-heap: {} slices × {} voxels, wall={} ms", nz, sliceLen, wallMs);
      } else {
        LOGGER.debug("normalize path: mapped-acc → mapped-data (serial)");
        // Disk path on both ends: serial, with bulk reads. Parallel access to a memory-mapped
        // file when the working set exceeds RAM (which is why we ended up on disk in the first
        // place) leads to page-cache thrash that more than wipes out the parallel speedup.
        // Reuse the same scratch buffers for every slice — single-threaded so no contention.
        float[] wSlab = new float[(int) sliceLen];
        float[] vSlab = new float[(int) sliceLen];
        for (int z = 0; z < nz; z++) {
          normalizeSliceMapped(volume, z, sliceLen, wSlab, vSlab);
          if (progressListener != null) {
            progressListener.accept(doneSlices.incrementAndGet());
          }
        }
      }
    }
  }

  /** Backward-compatible overload without progress reporting. */
  public <T extends Number, A> void normalize(Volume<T, A> volume) {
    normalize(volume, null);
  }

  /**
   * Heap-accumulator → mapped-data normalisation of a single Z-slice. Materialises the slice into
   * scratch float arrays then bulk-writes it through {@link Volume#writeNormalizedSliceMappedBulk}.
   * Used only when accumulators are heap-resident but the destination volume had to fall back to
   * disk.
   */
  private <T extends Number, A> void normalizeSliceHeapToMapped(
      Volume<T, A> volume, int z, int nx, int ny, int channels) {
    final ChunkedArray<int[]> wa = weightAcc;
    final ChunkedArray<int[]> va = valueAcc;

    long sliceStart = volume.linearIndex(0, 0, z, 0);
    long sliceLen = (long) nx * ny * channels;
    long end = sliceStart + sliceLen;
    int n = (int) sliceLen;
    float[] vals = new float[n];
    float[] weights = new float[n];
    long idx = sliceStart;
    int outOff = 0;
    while (idx < end) {
      int wco = wa.chunkOffset(idx);
      int[] wChunk = wa.getChunk(wa.chunkIndex(idx));
      int[] vChunk = va.getChunk(va.chunkIndex(idx));
      int vco = va.chunkOffset(idx);
      int chunkRemaining = wChunk.length - wco;
      long maxRunByEnd = end - idx;
      int run = (int) Math.min(chunkRemaining, maxRunByEnd);
      for (int k = 0; k < run; k++) {
        float fw = Float.intBitsToFloat(wChunk[wco + k]);
        weights[outOff + k] = fw;
        if (fw >= WEIGHT_EPSILON) {
          vals[outOff + k] = Float.intBitsToFloat(vChunk[vco + k]) / fw;
        }
      }
      idx += run;
      outOff += run;
    }
    volume.writeNormalizedSliceMappedBulk(sliceStart, vals, weights, n);
  }

  /**
   * ForkJoin task for the heap-to-heap normalisation path. Recursively splits a flat element range,
   * eventually walking each leaf chunk-by-chunk and dispatching to {@link Volume#normalizeHeapRun}
   * (which hoists the destination-type switch out of the inner loop).
   */
  private final class HeapNormalizeTask extends java.util.concurrent.RecursiveAction {
    /**
     * Leaf size: balances FJP overhead against load balancing. ~4M elements ≈ 16 MB read of
     * accumulators per leaf — fits comfortably in L3 on modern CPUs.
     */
    private static final long LEAF_THRESHOLD = 1L << 22;

    private final Volume<?, ?> volume;
    private final long start;
    private final long end;
    private final long sliceLen;
    private final java.util.concurrent.atomic.AtomicInteger doneSlices;
    private final java.util.function.IntConsumer progressListener;
    private final int totalSlices;
    private final java.util.concurrent.atomic.AtomicLong cpuNanos;
    private final java.util.concurrent.atomic.AtomicInteger leafCount;

    HeapNormalizeTask(
        Volume<?, ?> volume,
        long start,
        long end,
        long sliceLen,
        java.util.concurrent.atomic.AtomicInteger doneSlices,
        java.util.function.IntConsumer progressListener,
        int totalSlices,
        java.util.concurrent.atomic.AtomicLong cpuNanos,
        java.util.concurrent.atomic.AtomicInteger leafCount) {
      this.volume = volume;
      this.start = start;
      this.end = end;
      this.sliceLen = sliceLen;
      this.doneSlices = doneSlices;
      this.progressListener = progressListener;
      this.totalSlices = totalSlices;
      this.cpuNanos = cpuNanos;
      this.leafCount = leafCount;
    }

    @Override
    protected void compute() {
      if (end - start <= LEAF_THRESHOLD) {
        runLeaf();
        return;
      }
      // Split, but keep the split point on a slice boundary so progress accounting stays exact.
      long mid = start + ((end - start) >>> 1);
      if (sliceLen > 0) {
        long rounded = (mid / sliceLen) * sliceLen;
        if (rounded > start && rounded < end) {
          mid = rounded;
        }
      }
      HeapNormalizeTask left =
          new HeapNormalizeTask(
              volume,
              start,
              mid,
              sliceLen,
              doneSlices,
              progressListener,
              totalSlices,
              cpuNanos,
              leafCount);
      HeapNormalizeTask right =
          new HeapNormalizeTask(
              volume,
              mid,
              end,
              sliceLen,
              doneSlices,
              progressListener,
              totalSlices,
              cpuNanos,
              leafCount);
      invokeAll(left, right);
    }

    private void runLeaf() {
      long t0 = System.nanoTime();
      final ChunkedArray<int[]> wa = weightAcc;
      final ChunkedArray<int[]> va = valueAcc;
      long idx = start;
      while (idx < end) {
        int wco = wa.chunkOffset(idx);
        int[] wChunk = wa.getChunk(wa.chunkIndex(idx));
        int[] vChunk = va.getChunk(va.chunkIndex(idx));
        // Accumulator chunks share offsets — wco == vco by construction.
        int chunkRemaining = wChunk.length - wco;
        long maxRunByEnd = end - idx;
        int run = (int) Math.min(chunkRemaining, maxRunByEnd);
        // One destination-type dispatch per chunk-aligned run, then a tight primitive inner loop.
        volume.normalizeHeapRun(wChunk, vChunk, wco, idx, run);
        idx += run;
      }
      cpuNanos.addAndGet(System.nanoTime() - t0);
      leafCount.incrementAndGet();
      // Slice-granular progress reporting: count whole slices completed within [start, end).
      if (progressListener != null && sliceLen > 0) {
        long firstSlice = (start + sliceLen - 1) / sliceLen;
        long lastSlice = end / sliceLen;
        int slicesInLeaf = (int) Math.max(0, lastSlice - firstSlice);
        if (slicesInLeaf > 0) {
          int done = doneSlices.addAndGet(slicesInLeaf);
          // Always report; the caller throttles delivery to the EDT.
          progressListener.accept(Math.min(done, totalSlices));
        }
      }
    }
  }

  /**
   * Mapped-file normalisation of a single Z-slice using bulk reads into reusable scratch arrays.
   */
  private <T extends Number, A> void normalizeSliceMapped(
      Volume<T, A> volume, int z, long sliceLen, float[] wSlab, float[] vSlab) {
    final ChunkedMappedBuffer mw = mappedWeightAcc;
    final ChunkedMappedBuffer mv = mappedValueAcc;

    long sliceStart = volume.linearIndex(0, 0, z, 0);
    long byteStart = sliceStart * Float.BYTES;
    int n = (int) sliceLen;

    // Two big sequential reads (OS prefetched) replace 2 × n random mapped-buffer dereferences.
    mw.getFloats(byteStart, wSlab, 0, n);
    mv.getFloats(byteStart, vSlab, 0, n);

    if (volume.data == null) {
      // Worst case: accumulators AND data are both disk-backed. Convert in-place into vSlab
      // (vals/weights are independent across iterations) and bulk-write the typed slice via
      // ChunkedMappedBuffer.put*. End-to-end the slice is now three sequential mapped-file
      // streams (W read, V read, data write) — page cache friendly.
      for (int k = 0; k < n; k++) {
        float fw = wSlab[k];
        if (fw >= WEIGHT_EPSILON) {
          vSlab[k] = vSlab[k] / fw;
        }
      }
      volume.writeNormalizedSliceMappedBulk(sliceStart, vSlab, wSlab, n);
      return;
    }

    for (int k = 0; k < n; k++) {
      float fw = wSlab[k];
      if (fw < WEIGHT_EPSILON) continue;
      volume.setNormalizedFloatAt(sliceStart + k, vSlab[k] / fw);
    }
  }

  /**
   * Mapped-accumulator → heap-data normalisation of a single Z-slice. Two bulk {@code getFloats}
   * reads from the mapped weight/value files into per-thread scratch arrays, then a chunk-walk over
   * the destination data array dispatching once per chunk-aligned run to a typed inner loop via
   * {@link Volume#normalizeFloatRunToHeap}. This eliminates the per-voxel {@link
   * Volume#setNormalizedFloatAt} switch that dominated the previous serial path.
   */
  private <T extends Number, A> void normalizeSliceMappedToHeap(
      Volume<T, A> volume, int z, long sliceLen, float[] wSlab, float[] vSlab) {
    final ChunkedMappedBuffer mw = mappedWeightAcc;
    final ChunkedMappedBuffer mv = mappedValueAcc;
    long sliceStart = volume.linearIndex(0, 0, z, 0);
    long byteStart = sliceStart * Float.BYTES;
    int n = (int) sliceLen;
    mw.getFloats(byteStart, wSlab, 0, n);
    mv.getFloats(byteStart, vSlab, 0, n);

    long idx = sliceStart;
    long end = sliceStart + n;
    int srcOff = 0;
    while (idx < end) {
      int dco = volume.data.chunkOffset(idx);
      A dChunk = volume.data.getChunk(volume.data.chunkIndex(idx));
      int chunkRemaining = java.lang.reflect.Array.getLength(dChunk) - dco;
      long maxRunByEnd = end - idx;
      int run = (int) Math.min(chunkRemaining, maxRunByEnd);
      volume.normalizeFloatRunToHeap(wSlab, vSlab, srcOff, idx, run);
      idx += run;
      srcOff += run;
    }
  }

  /**
   * Callback invoked by {@link #normalizeBinary} for each voxel whose accumulated value crosses the
   * given threshold.
   */
  @FunctionalInterface
  public interface VoxelLabelSink {
    void accept(int x, int y, int z);
  }

  /**
   * Binary normalisation pass for label/mask splatting: for every voxel divides the accumulated
   * weighted value by the accumulated weight and invokes {@code sink} when the resulting fraction
   * is at least {@code threshold} (and weight ≥ {@link #WEIGHT_EPSILON}).
   *
   * <p>Used when accumulating binary masks (value=1.0 per source pixel) through trilinear splat to
   * obtain an artefact-free, volume-grid label map regardless of the source plane orientation.
   *
   * @param size the volume size (X, Y, Z)
   * @param threshold majority threshold in [0,1] (typically 0.5)
   * @param sink callback invoked for each voxel that should receive a label
   */
  public void normalizeBinary(Vector3i size, float threshold, VoxelLabelSink sink) {
    if (!isWeighted()) return;

    final ChunkedArray<int[]> wa = weightAcc;
    final ChunkedArray<int[]> va = valueAcc;
    final ChunkedMappedBuffer mw = mappedWeightAcc;
    final ChunkedMappedBuffer mv = mappedValueAcc;

    long sliceStride = (long) size.x * size.y;
    for (int z = 0; z < size.z; z++) {
      long zOff = (long) z * sliceStride;
      for (int y = 0; y < size.y; y++) {
        long yOff = zOff + (long) y * size.x;
        for (int x = 0; x < size.x; x++) {
          long idx = yOff + x;
          float w;
          float v;
          if (wa != null && va != null) {
            w =
                Float.intBitsToFloat(
                    (int)
                        INT_ARRAY_HANDLE.get(wa.getChunk(wa.chunkIndex(idx)), wa.chunkOffset(idx)));
            if (w < WEIGHT_EPSILON) continue;
            v =
                Float.intBitsToFloat(
                    (int)
                        INT_ARRAY_HANDLE.get(va.getChunk(va.chunkIndex(idx)), va.chunkOffset(idx)));
          } else if (mw != null && mv != null) {
            long byteOffset = idx * Float.BYTES;
            w = mw.getFloat(byteOffset);
            if (w < WEIGHT_EPSILON) continue;
            v = mv.getFloat(byteOffset);
          } else {
            continue;
          }
          if ((v / w) >= threshold) {
            sink.accept(x, y, z);
          }
        }
      }
    }
  }
}
