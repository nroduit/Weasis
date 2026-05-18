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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.image.cv.CvUtil;
import org.weasis.core.api.vol.ChunkedArray;
import org.weasis.core.api.vol.ChunkedMappedBuffer;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.seg.Region;
import org.weasis.opencv.seg.RegionAttributes;
import org.weasis.opencv.seg.Segment;

/**
 * A 3D multi-label segmentation volume backed by {@link org.weasis.core.api.vol.ChunkedArray}
 * (in-memory) or {@link org.weasis.core.api.vol.ChunkedMappedBuffer} (disk-backed fallback for
 * large volumes).
 *
 * <h2>Storage model</h2>
 *
 * <p>Each voxel stores a small <em>storage&nbsp;ID</em> (1&hellip;N) that indexes a remapping table
 * {@link #idToSegments}: {@code ID 0} is the background, IDs {@code 1..segmentCount} are
 * pre-allocated for the SEG's individual segments (one element in the list), and any voxel where
 * two or more segments overlap allocates a new ID whose list contains the sorted union of the
 * overlapping segment numbers. The original segment numbers come from {@code
 * ReferencedSegmentNumber} (BINARY/FRACTIONAL SEGs) or from the pixel value itself (LABELMAP SEGs).
 *
 * <p>The voxel raster therefore uses only 1 byte (when the total number of distinct IDs &le;
 * {@value #MAX_BYTE_ID}) or 2 bytes per voxel — never 4 — and gracefully scales to overlapping
 * segmentations without the 32-segment ceiling that a flat int-bitmask storage would impose.
 * Promotion from byte to short happens transparently the first time {@link #nextId} would exceed
 * {@value #MAX_BYTE_ID}.
 *
 * <h2>Output API</h2>
 *
 * <p>Two complementary output paths read from the same ID storage:
 *
 * <ul>
 *   <li><b>3D GPU rendering</b> — {@link #exportSliceBitmask(int)} writes the storage ID directly
 *       into a {@code GL_R32UI} 3D texture, paired with the per-ID colour table from {@link
 *       #buildSegmentColorLUT()} (overlap combinations get a pre-composited RGBA so the fragment
 *       shader needs only one texel fetch).
 *   <li><b>2D vector contour overlays</b> — {@link #getSliceContours} and {@link
 *       #getContoursForImagePlane} resample the volume on an arbitrary plane and emit one {@link
 *       SegContour} per visible segment. The resliced raster carries raw <em>storage IDs</em> (not
 *       bitmasks), and {@link #buildContours} demultiplexes each ID into its constituent segments
 *       via {@link #idToSegments}. There is therefore <strong>no fixed cap</strong> on the number
 *       of segments a SEG may contain — the only hard limit is {@value #MAX_SHORT_ID} distinct
 *       storage IDs (singletons + observed overlap combinations).
 * </ul>
 *
 * <p>The volume stores its own spatial metadata (origin, axis directions, pixel spacing, and
 * dimensions) so that it can be resliced in any arbitrary direction for MPR overlay rendering and
 * volume rendering.
 */
public final class SegmentationVolume {

  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentationVolume.class);

  /**
   * @deprecated The 2D contour pipeline no longer encodes segments as bits in a fixed-width
   *     bitmask: rasters now carry raw storage IDs and {@link #buildContours} demultiplexes them
   *     through {@link #idToSegments}, so a SEG may contain an arbitrary number of segments (up to
   *     the {@value #MAX_SHORT_ID} storage-ID ceiling). This constant is kept only to preserve
   *     binary compatibility with external callers and is unused internally.
   */
  @Deprecated public static final int MAX_SEGMENTS_FOR_BITMASK = Integer.MAX_VALUE;

  /** Largest storage ID representable in byte mode (unsigned, 0 reserved for background). */
  private static final int MAX_BYTE_ID = 0xFF;

  /** Largest storage ID representable in short mode (unsigned, 0 reserved for background). */
  private static final int MAX_SHORT_ID = 0xFFFF;

  private final Vector3i size;
  private final Vector3d pixelSpacing;
  private final Vector3d volumeOrigin;
  private final Vector3d volumeAxisX;
  private final Vector3d volumeAxisY;
  private final Vector3d volumeAxisZ;
  private final long sliceStride;

  private final Map<Integer, ? extends RegionAttributes> segAttributes;

  // ---- ID remapping tables ----

  /**
   * Storage ID &rarr; sorted list of original segment numbers. ID 0 is implicit background and is
   * NOT present in the map. IDs in {@code [1, segmentCount]} are pre-allocated singletons; IDs
   * above that range are overlap-combination IDs allocated on demand.
   */
  private final Map<Integer, List<Integer>> idToSegments = new LinkedHashMap<>();

  /** Reverse lookup: sorted segment list &rarr; storage ID (used during overlap merging). */
  private final Map<List<Integer>, Integer> combinationToId = new HashMap<>();

  /**
   * Direct lookup from a segment number to its pre-allocated singleton storage ID, indexed by
   * segment number. {@code segNumToSoloId[segNum] == 0} means the segment is unknown.
   */
  private final int[] segNumToSoloId;

  /** Synchronisation guard for combination-ID allocation and storage promotion. */
  private final Object idLock = new Object();

  /** Next storage ID to allocate (starts after the pre-allocated singletons). */
  private int nextId;

  /**
   * Per-segment stamping counter, indexed by segment number. Non-atomic: stamping is invoked from a
   * single thread per frame via the {@code SegmentationVolumeBuilder}. Used purely for diagnostic
   * logging in {@link #logBuildContoursDiagnostics}.
   */
  private final long[] segStampCount;

  // ---- Adaptive storage fields ----

  /**
   * When {@code true}, the volume uses 2 bytes per voxel (either {@link #shortData} or {@link
   * #mappedBuffer}). When {@code false}, it uses 1 byte per voxel ({@link #byteData}). Starts
   * {@code false} and may flip to {@code true} once when the ID space overflows the byte range or
   * when the in-memory allocation falls back to disk; never flips back.
   */
  private volatile boolean shortMode;

  /**
   * When {@code true}, the segmentation declared {@code SegmentsOverlap=NO} (or LABELMAP), so we
   * skip the per-voxel read+compare overlap check. Voxels with conflicting writes will be
   * overwritten (last write wins). Halves CPU on the stamping hot path; storage byte/short choice
   * is unaffected.
   */
  private final boolean forceExclusiveMode;

  /** In-memory byte storage. Non-null when {@code !shortMode} and in-memory. */
  private ChunkedArray<byte[]> byteData;

  /** In-memory short storage. Non-null when {@code shortMode} and in-memory. */
  private ChunkedArray<short[]> shortData;

  /**
   * Disk-backed storage fallback. Non-null when the volume is too large for RAM. Always uses short
   * mode (2 bytes per element via {@code getShort/putShort}).
   */
  private ChunkedMappedBuffer mappedBuffer;

  /**
   * Creates a new segmentation volume.
   *
   * @param sizeX number of columns (X)
   * @param sizeY number of rows (Y)
   * @param sizeZ number of slices (Z)
   * @param pixelSpacing voxel spacing in mm (column spacing, row spacing, slice spacing)
   * @param volumeOrigin LPS position of voxel (0,0,0)
   * @param volumeAxisX unit direction vector for the volume X axis in LPS
   * @param volumeAxisY unit direction vector for the volume Y axis in LPS
   * @param volumeAxisZ unit direction vector for the volume Z axis in LPS
   * @param segAttributes segment number → region attributes (colors, labels, visibility)
   */
  public SegmentationVolume(
      int sizeX,
      int sizeY,
      int sizeZ,
      Vector3d pixelSpacing,
      Vector3d volumeOrigin,
      Vector3d volumeAxisX,
      Vector3d volumeAxisY,
      Vector3d volumeAxisZ,
      Map<Integer, ? extends RegionAttributes> segAttributes) {
    this(
        sizeX,
        sizeY,
        sizeZ,
        pixelSpacing,
        volumeOrigin,
        volumeAxisX,
        volumeAxisY,
        volumeAxisZ,
        segAttributes,
        false);
  }

  /**
   * Creates a new segmentation volume with explicit control over the overlap-detection fast path.
   *
   * @param forceExclusiveMode when {@code true}, the volume skips the per-voxel read+compare
   *     overlap check. Use this when the SEG declares {@code SegmentsOverlap=NO} (or for LABELMAP)
   *     so that stamping only writes single-segment IDs. Conflicting writes (if any) follow
   *     last-write-wins. Has no effect on whether the storage uses byte or short voxels.
   */
  public SegmentationVolume(
      int sizeX,
      int sizeY,
      int sizeZ,
      Vector3d pixelSpacing,
      Vector3d volumeOrigin,
      Vector3d volumeAxisX,
      Vector3d volumeAxisY,
      Vector3d volumeAxisZ,
      Map<Integer, ? extends RegionAttributes> segAttributes,
      boolean forceExclusiveMode) {
    this.size = new Vector3i(sizeX, sizeY, sizeZ);
    this.sliceStride = (long) sizeX * sizeY;
    this.pixelSpacing = new Vector3d(pixelSpacing);
    this.volumeOrigin = new Vector3d(volumeOrigin);
    this.volumeAxisX = new Vector3d(volumeAxisX);
    this.volumeAxisY = new Vector3d(volumeAxisY);
    this.volumeAxisZ = new Vector3d(volumeAxisZ);
    this.segAttributes = segAttributes;
    this.forceExclusiveMode = forceExclusiveMode;

    // ---- Pre-allocate one singleton storage ID per segment ----
    List<Integer> segNumbers = new ArrayList<>(segAttributes.keySet());

    int maxSegNum = segNumbers.stream().mapToInt(Integer::intValue).max().orElse(0);
    this.segNumToSoloId = new int[maxSegNum + 1];
    this.segStampCount = new long[maxSegNum + 1];
    int id = 1;
    for (Integer segNum : segNumbers) {
      segNumToSoloId[segNum] = id;
      List<Integer> singleton = List.of(segNum);
      idToSegments.put(id, singleton);
      combinationToId.put(singleton, id);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Segment #{} '{}' -> soloId={}", segNum, labelOf(segNum), id);
      }
      id++;
    }
    this.nextId = id;

    createData();
  }

  // ---- Data allocation (starts in byte mode) ----

  private void createData() {
    long totalElements = (long) size.x * size.y * size.z;
    // Skip the byte mode entirely if the singleton ID space already overflows it.
    boolean startShort = nextId - 1 > MAX_BYTE_ID;
    try {
      allocateInMemory(totalElements, startShort);
    } catch (OutOfMemoryError e) {
      CvUtil.runGarbageCollectorAndWait(100);
      try {
        allocateInMemory(totalElements, startShort);
      } catch (OutOfMemoryError ex) {
        createDataFile();
      }
    }
  }

  private void allocateInMemory(long totalElements, boolean asShort) {
    if (asShort) {
      this.shortData = ChunkedArray.ofShort(totalElements);
      this.shortMode = true;
    } else {
      this.byteData = ChunkedArray.ofByte(totalElements);
    }
  }

  /** Disk-backed fallback (always uses short mode to avoid a later byte→short migration). */
  private void createDataFile() {
    try {
      removeData();
      File dataFile =
          File.createTempFile("seg_volume_", ".tmp", AppProperties.FILE_CACHE_DIR.toFile());
      this.mappedBuffer = new ChunkedMappedBuffer(dataFile, totalVoxels() * 2L);
      this.shortMode = true;
    } catch (IOException e) {
      throw new RuntimeException("Failed to create disk-backed segmentation volume", e);
    }
  }

  private long totalVoxels() {
    return (long) size.x * size.y * size.z;
  }

  // ---- Mode promotion (byte → short) ----

  /** Promotes the in-memory storage from byte to short. Caller must hold {@link #idLock}. */
  private void promoteToShortMode() {
    if (shortMode) {
      return;
    }
    long totalElements = totalVoxels();

    ChunkedArray<short[]> newData;
    try {
      newData = ChunkedArray.ofShort(totalElements);
    } catch (OutOfMemoryError e) {
      CvUtil.runGarbageCollectorAndWait(100);
      try {
        newData = ChunkedArray.ofShort(totalElements);
      } catch (OutOfMemoryError ex) {
        promoteToDiskShortMode();
        return;
      }
    }

    copyByteDataInto(allocateShortSink(newData));
    shortData = newData;
    shortMode = true;
    LOGGER.info("SegmentationVolume promoted to short storage (>{} distinct IDs)", MAX_BYTE_ID);
  }

  private static ShortSink allocateShortSink(ChunkedArray<short[]> dst) {
    return (i, v) -> dst.getChunk(dst.chunkIndex(i))[dst.chunkOffset(i)] = v;
  }

  /** Disk-backed short fallback when {@code short[]} in-memory allocation fails. */
  private void promoteToDiskShortMode() {
    try {
      File dataFile =
          File.createTempFile("seg_volume_", ".tmp", AppProperties.FILE_CACHE_DIR.toFile());
      ChunkedMappedBuffer newBuffer = new ChunkedMappedBuffer(dataFile, totalVoxels() * 2L);
      copyByteDataInto((i, v) -> newBuffer.putShort(i * 2L, v));
      mappedBuffer = newBuffer;
      shortMode = true;
      LOGGER.info(
          "SegmentationVolume promoted to disk-backed short storage (memory pressure during ID promotion)");
    } catch (IOException e) {
      throw new RuntimeException("Failed to create disk-backed short segmentation volume", e);
    }
  }

  /** Streams every non-zero byte ID into the supplied short-mode sink, then clears byteData. */
  private void copyByteDataInto(ShortSink sink) {
    if (byteData == null) {
      return;
    }
    long totalElements = totalVoxels();
    for (long i = 0; i < totalElements; i++) {
      byte b = byteData.getChunk(byteData.chunkIndex(i))[byteData.chunkOffset(i)];
      if (b != 0) {
        sink.put(i, (short) (b & 0xFF));
      }
    }
    byteData = null;
  }

  @FunctionalInterface
  private interface ShortSink {
    void put(long index, short value);
  }

  // ---- Voxel access ----

  private long linearIndex(int x, int y, int z) {
    return (long) z * sliceStride + (long) y * size.x + x;
  }

  /** Reads the storage ID at the given linear index (0 = background). */
  private int readId(long index) {
    if (shortMode) {
      if (shortData != null) {
        return shortData.getChunk(shortData.chunkIndex(index))[shortData.chunkOffset(index)]
            & 0xFFFF;
      } else if (mappedBuffer != null) {
        return mappedBuffer.getShort(index * 2L) & 0xFFFF;
      }
    } else if (byteData != null) {
      return byteData.getChunk(byteData.chunkIndex(index))[byteData.chunkOffset(index)] & 0xFF;
    }
    return 0;
  }

  /** Writes the storage ID at the given linear index. */
  private void writeId(long index, int newId) {
    if (shortMode) {
      if (shortData != null) {
        shortData.getChunk(shortData.chunkIndex(index))[shortData.chunkOffset(index)] =
            (short) newId;
      } else if (mappedBuffer != null) {
        mappedBuffer.putShort(index * 2L, (short) newId);
      }
    } else if (byteData != null) {
      byteData.getChunk(byteData.chunkIndex(index))[byteData.chunkOffset(index)] = (byte) newId;
    }
  }

  /**
   * Adds a segment label at voxel (x, y, z). If the voxel is empty or already carries the same
   * single segment, the singleton storage ID is written directly (fast path). Otherwise, the union
   * of the existing combination and the new segment is looked up (or allocated) in {@link
   * #combinationToId} and the resulting ID is written.
   */
  public void addLabel(int x, int y, int z, int segmentNumber) {
    if (segmentNumber < 0
        || segmentNumber >= segNumToSoloId.length
        || segNumToSoloId[segmentNumber] == 0) {
      return;
    }
    if (x < 0 || x >= size.x || y < 0 || y >= size.y || z < 0 || z >= size.z) {
      return;
    }
    int soloId = segNumToSoloId[segmentNumber];
    long index = linearIndex(x, y, z);
    segStampCount[segmentNumber]++;

    if (forceExclusiveMode) {
      // SegmentsOverlap=NO / LABELMAP: trust the declaration and write directly without
      // read-compare. Conflicting writes (if any) follow last-write-wins semantics.
      writeId(index, soloId);
      return;
    }

    int currentId = readId(index);
    if (currentId == soloId) {
      writeId(index, soloId);
      return;
    }

    // Voxel already carries another (single or combined) ID — merge.
    List<Integer> currentSegs = idToSegments.get(currentId);
    if (currentSegs != null && currentSegs.contains(segmentNumber)) {
      return; // segment already present in the combination
    }
    int mergedId = getOrAllocateCombination(currentSegs, segmentNumber);
    if (mergedId > 0) {
      writeId(index, mergedId);
    }
  }

  /**
   * Returns the storage ID for the union of {@code currentSegs} and {@code newSeg}, allocating a
   * new ID (and promoting storage if needed) when first seen. Returns {@code 0} when exhausted.
   */
  private int getOrAllocateCombination(List<Integer> currentSegs, int newSeg) {
    List<Integer> merged = mergeSorted(currentSegs, newSeg);
    Integer existing = combinationToId.get(merged);
    if (existing != null) {
      return existing;
    }
    synchronized (idLock) {
      existing = combinationToId.get(merged);
      if (existing != null) {
        return existing;
      }
      int candidate = nextId;
      if (!shortMode && candidate > MAX_BYTE_ID) {
        promoteToShortMode();
      }
      if (candidate > MAX_SHORT_ID) {
        LOGGER.warn(
            "SegmentationVolume ID space exhausted ({} IDs); voxel skipped for combination {}",
            MAX_SHORT_ID,
            merged);
        return 0;
      }
      nextId = candidate + 1;
      registerCombinationId(candidate, merged);
      return candidate;
    }
  }

  /**
   * Registers a freshly-allocated combination ID in all lookup tables. Caller holds {@link
   * #idLock}.
   */
  private void registerCombinationId(int id, List<Integer> sortedSegs) {
    idToSegments.put(id, sortedSegs);
    combinationToId.put(sortedSegs, id);
  }

  /**
   * Returns the storage ID whose segment list is the union of the segments behind {@code idA} and
   * {@code idB}, allocating a fresh combination ID on demand. Used by the resliced-raster
   * accumulation path so that oversampling can OR contributions from sub-pixels covering different
   * IDs without losing per-segment information.
   *
   * <p>Returns {@code idA} (the dominant ID) if the storage-ID space is exhausted.
   */
  private int idForUnion(int idA, int idB) {
    if (idA == idB || idB == 0) return idA;
    if (idA == 0) return idB;
    List<Integer> a = idToSegments.get(idA);
    List<Integer> b = idToSegments.get(idB);
    if (a == null) return idB;
    if (b == null) return idA;
    List<Integer> merged = mergeSortedLists(a, b);
    if (merged.size() == a.size()) return idA; // b ⊆ a
    if (merged.size() == b.size()) return idB; // a ⊆ b
    Integer existing = combinationToId.get(merged);
    if (existing != null) return existing;
    synchronized (idLock) {
      existing = combinationToId.get(merged);
      if (existing != null) return existing;
      int candidate = nextId;
      if (candidate > MAX_SHORT_ID) {
        // ID space exhausted — degrade gracefully by keeping the dominant id.
        return idA;
      }
      nextId = candidate + 1;
      registerCombinationId(candidate, merged);
      return candidate;
    }
  }

  /** Returns a new immutable sorted list = {@code base ∪ {value}}. */
  private static List<Integer> mergeSorted(List<Integer> base, int value) {
    if (base == null || base.isEmpty()) {
      return List.of(value);
    }
    Integer[] merged = new Integer[base.size() + 1];
    int i = 0;
    boolean inserted = false;
    for (Integer v : base) {
      if (!inserted && value < v) {
        merged[i++] = value;
        inserted = true;
      }
      merged[i++] = v;
    }
    if (!inserted) {
      merged[i] = value;
    }
    return List.of(merged);
  }

  /**
   * Returns a new immutable sorted list = {@code a ∪ b} where {@code a} and {@code b} are already
   * sorted ascending and contain no duplicates.
   */
  private static List<Integer> mergeSortedLists(List<Integer> a, List<Integer> b) {
    int sa = a.size();
    int sb = b.size();
    Integer[] out = new Integer[sa + sb];
    int i = 0;
    int j = 0;
    int k = 0;
    while (i < sa && j < sb) {
      int va = a.get(i);
      int vb = b.get(j);
      if (va < vb) {
        out[k++] = va;
        i++;
      } else if (va > vb) {
        out[k++] = vb;
        j++;
      } else {
        out[k++] = va;
        i++;
        j++;
      }
    }
    while (i < sa) out[k++] = a.get(i++);
    while (j < sb) out[k++] = b.get(j++);
    return List.of(Arrays.copyOf(out, k));
  }

  /**
   * Returns the storage ID at voxel {@code (x, y, z)}; 0 when out-of-bounds or background. Resolve
   * to segment numbers via {@link #idToSegments}.
   */
  private int getStorageId(int x, int y, int z) {
    if (x < 0 || x >= size.x || y < 0 || y >= size.y || z < 0 || z >= size.z) {
      return 0;
    }
    return readId(linearIndex(x, y, z));
  }

  // ---- Resampling onto a different grid ----

  /**
   * Mapping from a target voxel {@code (x, y, z)} to its patient-space (LPS) position in mm. Used
   * by {@link #resampleInto(SegmentationVolume, VoxelToLps)} so callers can plug in any inverse
   * voxel→LPS transform — including the non-textbook one used by the MPR image volume (see {@code
   * Volume.voxelToLps}).
   */
  @FunctionalInterface
  public interface VoxelToLps {
    /**
     * Writes the LPS position of voxel {@code (x, y, z)} into {@code dst} and returns it. The
     * coordinate components may be fractional (the centre of the voxel is at integer indices).
     */
    Vector3d apply(double x, double y, double z, Vector3d dst);
  }

  /**
   * Resamples this segmentation volume onto {@code target}'s voxel grid, writing every non-zero
   * label into {@code target} via {@link #addLabel(int, int, int, int)}. Each target voxel is
   * mapped to LPS through {@code targetVoxelToLps} and looked up here with nearest-neighbour
   * sampling in the source's own (textbook) {@code (LPS - origin) · axis_i / spacing_i} mapping.
   * Overlapping segments are preserved: every segment behind the source storage ID is added
   * independently so {@code target}'s combination tables stay consistent.
   *
   * <p>This is the resample-from-canonical fast path used by image-aligned SEG builders (MPR / 3D):
   * the per-frame mask decode + splatting is paid once when the canonical volume is built;
   * subsequent image-aligned copies only walk voxels and copy labels.
   *
   * <p>Calling threads must hold no locks on {@code target}: {@link #addLabel} synchronises
   * internally via the volume's own locks.
   *
   * @param target the destination volume (typically empty); must declare at least the same segment
   *     numbers as this volume in its {@code segAttributes} for the labels to land.
   * @param targetVoxelToLps inverse voxel→LPS mapping for the target's grid
   * @return the number of target voxels that received at least one label
   */
  public long resampleInto(SegmentationVolume target, VoxelToLps targetVoxelToLps) {
    if (target == null || targetVoxelToLps == null) {
      return 0L;
    }
    final double invSx = 1.0 / pixelSpacing.x;
    final double invSy = 1.0 / pixelSpacing.y;
    final double invSz = 1.0 / pixelSpacing.z;
    final int tx = target.size.x;
    final int ty = target.size.y;
    final int tz = target.size.z;
    Vector3d lps = new Vector3d();
    long stamped = 0L;
    for (int z = 0; z < tz; z++) {
      for (int y = 0; y < ty; y++) {
        for (int x = 0; x < tx; x++) {
          targetVoxelToLps.apply(x, y, z, lps);
          double dx = lps.x - volumeOrigin.x;
          double dy = lps.y - volumeOrigin.y;
          double dz = lps.z - volumeOrigin.z;
          int sx =
              (int)
                  Math.round(
                      (dx * volumeAxisX.x + dy * volumeAxisX.y + dz * volumeAxisX.z) * invSx);
          int sy =
              (int)
                  Math.round(
                      (dx * volumeAxisY.x + dy * volumeAxisY.y + dz * volumeAxisY.z) * invSy);
          int sz =
              (int)
                  Math.round(
                      (dx * volumeAxisZ.x + dy * volumeAxisZ.y + dz * volumeAxisZ.z) * invSz);
          int id = getStorageId(sx, sy, sz);
          if (id == 0) continue;
          List<Integer> segs = idToSegments.get(id);
          if (segs == null || segs.isEmpty()) continue;
          for (int segNum : segs) {
            target.addLabel(x, y, z, segNum);
          }
          stamped++;
        }
      }
    }
    return stamped;
  }

  // ---- Stamping binary masks ----

  @FunctionalInterface
  private interface NonZeroPixelConsumer {
    void accept(int x, int y);
  }

  /** Pulls one int sample from a typed pixel buffer. */
  @FunctionalInterface
  private interface PixelExtractor {
    int valueAt(int index);
  }

  /**
   * Bulk-reads {@code mat} into a typed array and invokes {@code consumer} for every non-zero
   * pixel. Returns the non-zero count, or {@code -1} when the depth/channel layout is unsupported.
   */
  private static int forEachNonZero(Mat mat, NonZeroPixelConsumer consumer) {
    if (mat.channels() != 1) {
      return -1;
    }
    int total = mat.rows() * mat.cols();
    if (total == 0) {
      return 0;
    }
    PixelExtractor extractor = createExtractor(mat, total, mat.depth());
    return extractor == null ? -1 : walkPixels(total, mat.cols(), extractor, consumer);
  }

  private static PixelExtractor createExtractor(Mat mat, int total, int depth) {
    return switch (depth) {
      case CvType.CV_8U, CvType.CV_8S -> {
        byte[] buf = new byte[total];
        mat.get(0, 0, buf);
        yield i -> buf[i];
      }
      case CvType.CV_16U, CvType.CV_16S -> {
        short[] buf = new short[total];
        mat.get(0, 0, buf);
        yield depth == CvType.CV_16U ? i -> buf[i] & 0xFFFF : i -> buf[i];
      }
      case CvType.CV_32F -> {
        float[] buf = new float[total];
        mat.get(0, 0, buf);
        yield i -> buf[i] != 0f ? 1 : 0;
      }
      default -> null;
    };
  }

  private static int walkPixels(
      int total, int cols, PixelExtractor extractor, NonZeroPixelConsumer consumer) {
    int count = 0;
    int x = 0;
    int y = 0;
    for (int i = 0; i < total; i++) {
      if (extractor.valueAt(i) != 0) {
        consumer.accept(x, y);
        count++;
      }
      if (++x == cols) {
        x = 0;
        y++;
      }
    }
    return count;
  }

  /** Per-pixel JNI fallback for unsupported depths/channel layouts (rare, slow). */
  private static int forEachNonZeroGeneric(Mat mat, NonZeroPixelConsumer consumer) {
    int rows = mat.rows();
    int cols = mat.cols();
    int count = 0;
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        double[] pixel = mat.get(y, x);
        if (pixel != null && pixel.length > 0 && pixel[0] != 0) {
          consumer.accept(x, y);
          count++;
        }
      }
    }
    return count;
  }

  /**
   * Stamps a binary mask image into the volume at the given slice Z-index, adding the specified
   * segment number on each non-zero pixel. Multiple segments can overlap at the same voxel (overlap
   * is handled via combination-ID allocation).
   *
   * <p>The mask image must match the volume's X/Y dimensions.
   *
   * @param mask a binary mask image (any type, non-zero = foreground)
   * @param sliceZ the Z-index in the volume to stamp into
   * @param segmentNumber the segment number to add
   */
  public void stampAxialMask(PlanarImage mask, int sliceZ, int segmentNumber) {
    if (mask == null || sliceZ < 0 || sliceZ >= size.z) {
      return;
    }
    int cols = mask.width();
    int rows = mask.height();
    if (cols != size.x || rows != size.y) {
      LOGGER.warn(
          "Mask dimensions {}x{} do not match volume X/Y {}x{} at slice {} — frame skipped. "
              + "Decoded SEG mask should have been re-aligned to declared (Columns, Rows) "
              + "upstream (see SegMaskOrientation.normalize); reaching this branch indicates a "
              + "regression in the SEG builder.",
          cols,
          rows,
          size.x,
          size.y,
          sliceZ);
      return;
    }
    Mat mat = mask.toMat();
    NonZeroPixelConsumer stamp = (x, y) -> addLabel(x, y, sliceZ, segmentNumber);
    int stamped = forEachNonZero(mat, stamp);
    if (stamped < 0) {
      stamped = forEachNonZeroGeneric(mat, stamp);
    }
    if (LOGGER.isDebugEnabled()) {
      int total = rows * cols;
      LOGGER.debug(
          "Stamped frame seg={} sliceZ={} type=ch{}depth{} non-zero={}/{} ({}%)",
          segmentNumber,
          sliceZ,
          mat.channels(),
          mat.depth(),
          stamped,
          total,
          total == 0 ? 0 : (stamped * 100L / total));
    }
  }

  /**
   * Stamps a binary mask image into the volume using an affine mapping from the mask's pixel
   * coordinates to volume voxel coordinates. Handles SEG frames that have a different spatial grid
   * (position, spacing, or orientation) than the volume.
   *
   * @param mask a binary mask image
   * @param maskOrigin LPS position of the mask's top-left pixel
   * @param maskRowDir row direction cosines (unit vector)
   * @param maskColDir column direction cosines (unit vector)
   * @param maskPixelSpacing DICOM PixelSpacing as {@code [rowSpacing, colSpacing]} in mm
   * @param segmentNumber the segment number to add
   */
  public void stampMaskWithTransform(
      PlanarImage mask,
      Vector3d maskOrigin,
      Vector3d maskRowDir,
      Vector3d maskColDir,
      double[] maskPixelSpacing,
      int segmentNumber) {
    if (mask == null) {
      return;
    }
    Mat mat = mask.toMat();

    // Hoist constant per-step LPS deltas so the inner loop avoids redundant multiplications.
    double colSpacing = maskPixelSpacing[1];
    double rowSpacing = maskPixelSpacing[0];
    double stepXx = maskRowDir.x * colSpacing;
    double stepXy = maskRowDir.y * colSpacing;
    double stepXz = maskRowDir.z * colSpacing;
    double stepYx = maskColDir.x * rowSpacing;
    double stepYy = maskColDir.y * rowSpacing;
    double stepYz = maskColDir.z * rowSpacing;
    double invSx = 1.0 / pixelSpacing.x;
    double invSy = 1.0 / pixelSpacing.y;
    double invSz = 1.0 / pixelSpacing.z;

    NonZeroPixelConsumer stamp =
        (mx, my) -> {
          double dx = maskOrigin.x + mx * stepXx + my * stepYx - volumeOrigin.x;
          double dy = maskOrigin.y + mx * stepXy + my * stepYy - volumeOrigin.y;
          double dz = maskOrigin.z + mx * stepXz + my * stepYz - volumeOrigin.z;
          int ix =
              (int)
                  Math.round(
                      (dx * volumeAxisX.x + dy * volumeAxisX.y + dz * volumeAxisX.z) * invSx);
          int iy =
              (int)
                  Math.round(
                      (dx * volumeAxisY.x + dy * volumeAxisY.y + dz * volumeAxisY.z) * invSy);
          int iz =
              (int)
                  Math.round(
                      (dx * volumeAxisZ.x + dy * volumeAxisZ.y + dz * volumeAxisZ.z) * invSz);
          addLabel(ix, iy, iz, segmentNumber);
        };

    int stamped = forEachNonZero(mat, stamp);
    if (stamped < 0) {
      forEachNonZeroGeneric(mat, stamp);
    }
  }

  // ---- Reslicing ----

  /**
   * Reslices the segmentation volume onto an arbitrary 2D image plane defined by its LPS origin,
   * row/column unit directions, pixel spacing and dimensions, and returns one {@link SegContour}
   * map per visible segment. Uses <b>nearest-neighbor</b> sampling with adaptive oversampling.
   *
   * <p>Used by 2D overlays when a segmentation has to be displayed on an image whose orientation
   * does not match the segmentation's native plane (reformat, oblique series, MPR view).
   *
   * @param imgOrigin LPS position of the image's top-left pixel
   * @param rowDir unit vector along the image's rows (X direction)
   * @param colDir unit vector along the image's columns (Y direction)
   * @param colSpacing physical spacing between columns in mm (along {@code rowDir})
   * @param rowSpacing physical spacing between rows in mm (along {@code colDir})
   * @param width image width in pixels
   * @param height image height in pixels
   */
  public Map<Integer, List<SegContour>> getContoursForImagePlane(
      Vector3d imgOrigin,
      Vector3d rowDir,
      Vector3d colDir,
      double colSpacing,
      double rowSpacing,
      int width,
      int height) {
    if (imgOrigin == null || rowDir == null || colDir == null || width <= 0 || height <= 0) {
      return Collections.emptyMap();
    }
    // Compute an oversampling factor so every output pixel covers at most one SEG voxel along
    // each in-plane axis. Without this, an MR pixel that spans several SEG voxels samples only
    // one of them with nearest-neighbor, turning a continuous structure into a dotted column /
    // row pattern that findContours then traces as thin parallel stripes. Capped at 4×.
    double minSegSpacing = Math.min(pixelSpacing.x, Math.min(pixelSpacing.y, pixelSpacing.z));
    int factorU = Math.max(1, (int) Math.ceil(colSpacing / minSegSpacing));
    int factorV = Math.max(1, (int) Math.ceil(rowSpacing / minSegSpacing));
    int factor = Math.clamp(Math.max(factorU, factorV), 1, 4);
    int[] raster =
        sampleImagePlane(imgOrigin, rowDir, colDir, colSpacing, rowSpacing, width, height, factor);
    return buildContours(raster, width, height);
  }

  private int[] sampleImagePlane(
      Vector3d imgOrigin,
      Vector3d rowDir,
      Vector3d colDir,
      double colSpacing,
      double rowSpacing,
      int width,
      int height,
      int oversample) {
    int[] raster = new int[width * height];

    double invSx = 1.0 / pixelSpacing.x;
    double invSy = 1.0 / pixelSpacing.y;
    double invSz = 1.0 / pixelSpacing.z;

    // Hoist per-step LPS deltas — these are constant across the entire plane sweep.
    double colStepX = rowDir.x * colSpacing;
    double colStepY = rowDir.y * colSpacing;
    double colStepZ = rowDir.z * colSpacing;
    double rowStepX = colDir.x * rowSpacing;
    double rowStepY = colDir.y * rowSpacing;
    double rowStepZ = colDir.z * rowSpacing;

    int n = Math.max(1, oversample);
    double[] subOffsets = new double[n];
    for (int i = 0; i < n; i++) {
      subOffsets[i] = (i + 0.5) / n - 0.5;
    }

    for (int v = 0; v < height; v++) {
      int rowBase = v * width;
      for (int u = 0; u < width; u++) {
        int combined = 0;
        for (int sv = 0; sv < n; sv++) {
          double vv = v + subOffsets[sv];
          double oxV = imgOrigin.x + vv * rowStepX;
          double oyV = imgOrigin.y + vv * rowStepY;
          double ozV = imgOrigin.z + vv * rowStepZ;
          for (int su = 0; su < n; su++) {
            double uu = u + subOffsets[su];
            double dx = oxV + uu * colStepX - volumeOrigin.x;
            double dy = oyV + uu * colStepY - volumeOrigin.y;
            double dz = ozV + uu * colStepZ - volumeOrigin.z;

            int vx =
                (int)
                    Math.round(
                        (dx * volumeAxisX.x + dy * volumeAxisX.y + dz * volumeAxisX.z) * invSx);
            int vy =
                (int)
                    Math.round(
                        (dx * volumeAxisY.x + dy * volumeAxisY.y + dz * volumeAxisY.z) * invSy);
            int vz =
                (int)
                    Math.round(
                        (dx * volumeAxisZ.x + dy * volumeAxisZ.y + dz * volumeAxisZ.z) * invSz);

            int id = getStorageId(vx, vy, vz);
            if (id != 0) {
              // Compose sub-samples by union of their segment lists; allocates a fresh
              // combination ID on demand so every observed (sub-)voxel state is preserved.
              combined = idForUnion(combined, id);
            }
          }
        }
        if (combined != 0) {
          raster[rowBase + u] = combined;
        }
      }
    }
    return raster;
  }

  /**
   * Reslices the segmentation volume along an arbitrary plane defined by a combined transformation
   * matrix and returns one {@link SegContour} list per visible segment. Used by 2D MPR overlays.
   *
   * @param combinedTransform the 4×4 transformation from output pixel to volume voxel space
   * @param outputSize the width/height of the output square image
   * @param voxelRatio the volume's voxel ratio (pixelSpacing / minPixelSpacing)
   */
  public Map<Integer, List<SegContour>> getSliceContours(
      Matrix4d combinedTransform, int outputSize, Vector3d voxelRatio) {
    if (combinedTransform == null || outputSize <= 0) {
      return Collections.emptyMap();
    }
    int totalPixels = outputSize * outputSize;
    int[] raster = new int[totalPixels];
    // Submit on the common pool — DO NOT close it via try-with-resources (close() shuts it down).
    @SuppressWarnings("resource")
    ForkJoinPool pool = ForkJoinPool.commonPool();
    pool.invoke(
        new BitmaskSliceTask(
            0, totalPixels, outputSize, combinedTransform, voxelRatio, raster, this));
    return buildContours(raster, outputSize, outputSize);
  }

  /**
   * Builds {@link SegContour} objects from a resliced storage-ID raster for overlay rendering. Each
   * non-zero raster value is a storage ID whose constituent segments are looked up in {@link
   * #idToSegments}; one binary mask is allocated lazily per visible segment that actually appears
   * in the raster. There is no fixed cap on the number of segments — only the total storage-ID
   * count is bounded by {@value #MAX_SHORT_ID}.
   */
  private Map<Integer, List<SegContour>> buildContours(int[] raster, int width, int height) {
    if (raster == null || raster.length == 0) {
      return Collections.emptyMap();
    }

    // Pre-resolve visibility so the inner loop stays cheap (one boolean lookup per segment).
    boolean[] visibleSeg = new boolean[segNumToSoloId.length];
    RegionAttributes[] attrsBySeg = new RegionAttributes[segNumToSoloId.length];
    boolean anyVisible = false;
    for (int s = 0; s < visibleSeg.length; s++) {
      if (segNumToSoloId[s] == 0) continue;
      RegionAttributes a = segAttributes.get(s);
      attrsBySeg[s] = a;
      if (a != null && a.isVisible()) {
        visibleSeg[s] = true;
        anyVisible = true;
      }
    }
    if (!anyVisible) {
      return Collections.emptyMap();
    }

    // Lazy per-segment outputs (allocated only for segments that appear in the raster).
    Map<Integer, byte[]> segMask = new LinkedHashMap<>();
    Map<Integer, int[]> segPixelCount = new LinkedHashMap<>();
    int totalNonZero = 0;

    // Single pass over the raster: for each non-zero ID, OR each constituent segment into its
    // own per-segment mask (overlap is handled naturally because the same pixel can contribute
    // to several segments).
    for (int i = 0; i < raster.length; i++) {
      int id = raster[i];
      if (id == 0) continue;
      List<Integer> segs = idToSegments.get(id);
      if (segs == null) continue;
      totalNonZero++;
      for (int segNum : segs) {
        if (segNum < 0 || segNum >= visibleSeg.length || !visibleSeg[segNum]) continue;
        byte[] mask = segMask.get(segNum);
        if (mask == null) {
          mask = new byte[raster.length];
          segMask.put(segNum, mask);
          segPixelCount.put(segNum, new int[1]);
        }
        if (mask[i] == 0) {
          mask[i] = (byte) 255;
          segPixelCount.get(segNum)[0]++;
        }
      }
    }

    if (segMask.isEmpty()) {
      logBuildContoursDiagnostics();
      return Collections.emptyMap();
    }

    Map<Integer, List<SegContour>> result = new LinkedHashMap<>();
    for (Map.Entry<Integer, byte[]> entry : segMask.entrySet()) {
      int segNum = entry.getKey();
      byte[] maskData = entry.getValue();
      int pixelCount = segPixelCount.get(segNum)[0];
      RegionAttributes attrs = attrsBySeg[segNum];
      ImageCV binaryMask = new ImageCV(height, width, CvType.CV_8UC1);
      try {
        binaryMask.toMat().put(0, 0, maskData);
        List<Segment> segmentList = Region.buildSegmentList(binaryMask);
        if (!segmentList.isEmpty()) {
          SegContour contour = new SegContour(String.valueOf(segNum), segmentList, pixelCount);
          contour.setAttributes(attrs);
          result.computeIfAbsent(segNum, _ -> new ArrayList<>()).add(contour);
        } else if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(
              "buildContours: segment #{} '{}' had {} pixels in the resliced raster but"
                  + " Region.buildSegmentList returned no contour",
              segNum,
              labelOf(segNum),
              pixelCount);
        }
      } finally {
        binaryMask.release();
      }
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "buildContours: {} non-zero raster pixels, {} visible segment(s) emitted out of {}"
              + " stamped",
          totalNonZero,
          result.size(),
          countStampedSegments());
    }
    logBuildContoursDiagnostics();
    return result;
  }

  private int countStampedSegments() {
    int n = 0;
    for (long c : segStampCount) if (c > 0) n++;
    return n;
  }

  /**
   * Reports any segment that has stamped voxels but no soloId allocated. With the storage-ID raster
   * pipeline there is no longer a per-segment cap, so this is purely a sanity check for stamping
   * with an out-of-table segment number.
   */
  private void logBuildContoursDiagnostics() {
    if (!LOGGER.isDebugEnabled()) {
      return;
    }
    for (int segNum = 0; segNum < segStampCount.length; segNum++) {
      long stamped = segStampCount[segNum];
      if (stamped <= 0) continue;
      if (segNumToSoloId[segNum] == 0) {
        LOGGER.debug(
            "buildContours: segment #{} '{}' has {} stamped voxels but no soloId allocated",
            segNum,
            labelOf(segNum),
            stamped);
      }
    }
  }

  private String labelOf(int segNum) {
    RegionAttributes attrs = segAttributes == null ? null : segAttributes.get(segNum);
    return attrs == null ? "?" : attrs.getLabel();
  }

  // ---- Spatial metadata accessors ----

  public int getSizeX() {
    return size.x;
  }

  public int getSizeY() {
    return size.y;
  }

  public int getSizeZ() {
    return size.z;
  }

  /**
   * Returns {@code true} when the storage uses 2 bytes per voxel (either because the ID space
   * exceeded {@value #MAX_BYTE_ID} due to overlap combinations or because the volume falls back to
   * disk-backed storage). Used by SEG builders for diagnostic logging.
   */
  public boolean isShortMode() {
    return shortMode;
  }

  public boolean isEmpty() {
    long total = totalVoxels();
    for (long i = 0; i < total; i++) {
      if (readId(i) != 0) {
        return false;
      }
    }
    return true;
  }

  // ---- Reference counting ----

  /**
   * Active-consumer reference count. Starts at zero. Each consumer that obtains and actively holds
   * an instance should call {@link #retain()} and pair it with a matching {@link #release()} when
   * done. When the count reaches zero {@link #release()} automatically calls {@link #removeData()}
   * so the CPU buffers are freed as soon as the last consumer lets go.
   *
   * <p>Use-count interactions:
   *
   * <ul>
   *   <li>{@code SegVolumeTexture} retains on construction, releases on {@code destroy()} — the 3D
   *       overlay texture owns one reference.
   *   <li>{@code MprController} retains each volume added to its {@code segVolumes} list and
   *       releases when the list is cleared in {@code disposeSegVolumes()}.
   * </ul>
   */
  private final AtomicInteger useCount = new AtomicInteger(0);

  /**
   * Increments the active-consumer reference count. Every caller that wants to ensure the CPU
   * buffers remain valid must call {@code retain()} and balance it with a matching {@link
   * #release()}.
   *
   * @return {@code this} for chaining
   */
  public SegmentationVolume retain() {
    useCount.incrementAndGet();
    return this;
  }

  /**
   * Decrements the use count. When it reaches zero {@link #removeData()} is called automatically,
   * freeing all CPU buffers. Passing the count below zero is handled defensively (count is reset to
   * zero) and {@link #removeData()} is still called to ensure cleanup.
   */
  public void release() {
    int n = useCount.decrementAndGet();
    if (n <= 0) {
      if (n < 0) {
        LOGGER.warn(
            "SegmentationVolume.release() called more times than retain() (count={}) — resetting to 0",
            n);
        useCount.set(0);
      }
      removeData();
    }
  }

  /**
   * Returns the current active-consumer reference count. A value of {@code 0} means no consumers
   * are holding an explicit retain; the data may be freed by the next {@code
   * SegSpecialElement.disposeAlignedVolume} call.
   */
  public int getUseCount() {
    return useCount.get();
  }

  /** Releases all allocated memory (chunked arrays and/or mapped buffer). */
  public void removeData() {
    this.byteData = null;
    this.shortData = null;
    if (mappedBuffer != null) {
      mappedBuffer.close();
      mappedBuffer = null;
    }
  }

  /**
   * Returns {@code true} when the CPU buffers have been freed by {@link #removeData()} — typically
   * after the last consumer called {@link #release()}. A disposed volume reads as all-background
   * and must be rebuilt before it can be reused.
   */
  public boolean isDisposed() {
    return byteData == null && shortData == null && mappedBuffer == null;
  }

  /**
   * Counts, in a single pass over the whole volume, how many voxels belong to each segment and
   * stores the total on the matching {@link SegRegion} via {@link SegRegion#setNumberOfPixels}. An
   * overlap-combination storage ID contributes to every constituent segment. This gives the
   * Segmentation tool an exact voxel count / volume without relying on lazy per-slice contour
   * loading.
   */
  public void applySegmentVoxelCounts() {
    if (segAttributes == null || segAttributes.isEmpty() || isDisposed()) {
      return;
    }
    // Per storage-ID voxel counts, gathered in a single pass over the raster.
    long[] idCounts = new long[nextId];
    long total = totalVoxels();
    for (long i = 0; i < total; i++) {
      int id = readId(i);
      if (id > 0 && id < idCounts.length) {
        idCounts[id]++;
      }
    }
    // Distribute each storage ID's count to the segment(s) it represents.
    Map<Integer, Long> segCounts = new HashMap<>();
    for (int id = 1; id < idCounts.length; id++) {
      long c = idCounts[id];
      if (c == 0) {
        continue;
      }
      List<Integer> segs = idToSegments.get(id);
      if (segs != null) {
        for (int segNum : segs) {
          segCounts.merge(segNum, c, Long::sum);
        }
      }
    }
    for (Map.Entry<Integer, ? extends RegionAttributes> e : segAttributes.entrySet()) {
      if (e.getValue() instanceof SegRegion<?> region) {
        region.setNumberOfPixels(segCounts.getOrDefault(e.getKey(), 0L));
      }
    }
  }

  // ---- GPU data export ----

  /** Number of distinct segments declared in this volume (one singleton storage ID each). */
  public int getSegmentCount() {
    return segAttributes == null ? 0 : segAttributes.size();
  }

  /**
   * Exports a single axial slice as an {@code int[]} of <em>storage IDs</em>, suitable for GPU
   * upload via {@code glTexSubImage3D} into a {@code GL_R32UI} (or {@code GL_R16UI}) texture.
   *
   * <p>Each element is the raw storage ID of the voxel: {@code 0} = background, {@code 1..N} =
   * pre-allocated singleton IDs (one per segment), and any value above {@code N} = overlap
   * combination ID. The shader resolves the ID to a (mixed) RGBA colour by sampling {@link
   * #buildSegmentColorLUT()} at index {@code id}, so no per-bit blending is needed at render time.
   *
   * @param z the slice index (0-based, in volume Z order)
   * @return an int[] of size (sizeX * sizeY), or null if z is out of bounds
   */
  public int[] exportSliceBitmask(int z) {
    if (z < 0 || z >= size.z) {
      return null;
    }
    int sliceSize = size.x * size.y;
    int[] raster = new int[sliceSize];
    long base = (long) z * sliceStride;
    for (int i = 0; i < sliceSize; i++) {
      int id = readId(base + i);
      if (id != 0) {
        raster[i] = id;
      }
    }
    return raster;
  }

  /**
   * Builds a per-storage-ID colour lookup table as a flat {@code byte[]} in RGBA8 format, suitable
   * for uploading to a 1D OpenGL texture. The array has {@code idCount * 4} bytes, indexed by the
   * storage ID exported through {@link #exportSliceBitmask(int)}.
   *
   * <p>Slot {@code 0} is the transparent background. Slot {@code id} for a singleton ID holds the
   * segment's own RGBA. Slot {@code id} for an overlap-combination ID holds the <em>pre-composited
   * mix</em> of all visible constituent segments using a stable front-to-back blend in ascending
   * segment-number order — the same blend the shader used to perform per-pixel.
   *
   * <p>Invisible (or unknown) segments contribute nothing; if every constituent of a combination is
   * invisible, the slot stays fully transparent.
   */
  public byte[] buildSegmentColorLUT() {
    return buildSegmentColorLUT(null);
  }

  /**
   * Builds a per-storage-ID colour lookup table with optional override attributes. When {@code
   * overrideAttrs} is non-null, it is consulted first for each segment's colour, opacity, and
   * visibility; the built-in {@code segAttributes} are used as a fallback.
   *
   * @see #buildSegmentColorLUT()
   */
  public byte[] buildSegmentColorLUT(Map<Integer, ? extends RegionAttributes> overrideAttrs) {
    int idCount = nextId; // entries 0..nextId-1
    byte[] lut = new byte[idCount * 4];
    // Slot 0 is the implicit background — already zero (transparent).
    for (int id = 1; id < idCount; id++) {
      List<Integer> segs = idToSegments.get(id);
      if (segs == null) {
        continue;
      }
      // Stable front-to-back composite over the (sorted) segment list. Mirrors the per-pixel
      // blend the fragment shader used to perform; pre-computing it here means the shader needs
      // a single texelFetch per voxel.
      float r = 0f;
      float g = 0f;
      float b = 0f;
      float a = 0f;
      for (int segNum : segs) {
        RegionAttributes attrs = overrideAttrs != null ? overrideAttrs.get(segNum) : null;
        if (attrs == null) {
          attrs = segAttributes.get(segNum);
        }
        if (attrs == null || !attrs.isVisible()) {
          continue;
        }
        java.awt.Color c = attrs.getColor();
        float segA = attrs.getInteriorOpacity();
        float effA = (1f - a) * segA;
        r += effA * (c.getRed() / 255f);
        g += effA * (c.getGreen() / 255f);
        b += effA * (c.getBlue() / 255f);
        a += effA;
        if (a >= 0.99f) {
          break;
        }
      }
      if (a <= 0f) {
        continue; // every constituent invisible — leave transparent
      }
      lut[id * 4] = (byte) Math.round(Math.min(r, 1f) * 255f);
      lut[id * 4 + 1] = (byte) Math.round(Math.min(g, 1f) * 255f);
      lut[id * 4 + 2] = (byte) Math.round(Math.min(b, 1f) * 255f);
      lut[id * 4 + 3] = (byte) Math.round(Math.min(a, 1f) * 255f);
    }
    return lut;
  }

  // ---- Nearest-neighbor storage-ID reslice task (fork-join parallelism) ----

  private static final class BitmaskSliceTask extends RecursiveAction {
    private static final int THRESHOLD = 4096;

    private final int start;
    private final int end;
    private final int width;
    private final Matrix4d combinedTransform;
    private final Vector3d voxelRatio;
    private final int[] raster;
    private final SegmentationVolume volume;

    BitmaskSliceTask(
        int start,
        int end,
        int width,
        Matrix4d combinedTransform,
        Vector3d voxelRatio,
        int[] raster,
        SegmentationVolume volume) {
      this.start = start;
      this.end = end;
      this.width = width;
      this.combinedTransform = combinedTransform;
      this.voxelRatio = voxelRatio;
      this.raster = raster;
      this.volume = volume;
    }

    @Override
    protected void compute() {
      if (end - start <= THRESHOLD) {
        Vector3d coord = new Vector3d();
        int x = start % width;
        int y = start / width;

        for (int i = start; i < end; i++) {
          coord.set(x, y, 0);
          combinedTransform.transformPosition(coord);

          int vx = (int) Math.round(coord.x / voxelRatio.x);
          int vy = (int) Math.round(coord.y / voxelRatio.y);
          int vz = (int) Math.round(coord.z / voxelRatio.z);

          int id = volume.getStorageId(vx, vy, vz);
          if (id != 0) {
            raster[i] = id;
          }

          if (++x >= width) {
            x = 0;
            y++;
          }
        }
      } else {
        int mid = (start + end) / 2;
        invokeAll(
            new BitmaskSliceTask(start, mid, width, combinedTransform, voxelRatio, raster, volume),
            new BitmaskSliceTask(mid, end, width, combinedTransform, voxelRatio, raster, volume));
      }
    }
  }
}
