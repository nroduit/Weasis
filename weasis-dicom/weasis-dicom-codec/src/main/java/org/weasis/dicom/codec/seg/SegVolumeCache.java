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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Heap budget for the canonical segmentation volumes cached on {@link SegSpecialElement}.
 *
 * <p>A canonical volume is one byte per voxel of the segmentation's own grid, so a single
 * high-resolution SEG easily reaches tens of megabytes. A study carrying forty of them therefore
 * wants more heap for segmentation rasters alone than the whole application is given. The volumes
 * are held through a {@link java.lang.ref.SoftReference}, which prevents an {@code
 * OutOfMemoryError} but not the state that leads to one: soft references are only cleared as a last
 * resort, so the application ends up parked just below the heap ceiling, collecting continuously.
 *
 * <p>This cache keeps the total bounded instead. Volumes are tracked in least-recently-used order
 * and the oldest are disposed as soon as the budget is exceeded, so the heap stays at a level the
 * collector can work with. A disposed volume is simply rebuilt on demand.
 */
public final class SegVolumeCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(SegVolumeCache.class);

  /** Share of the maximum heap the canonical volumes may occupy together. */
  private static final double HEAP_FRACTION = 0.25;

  private static final long MIN_BUDGET = 128L << 20;

  private static final long BUDGET =
      Math.max(MIN_BUDGET, (long) (Runtime.getRuntime().maxMemory() * HEAP_FRACTION));

  private static final Object LOCK = new Object();

  /**
   * Access-ordered, so iteration starts at the least recently used volume. Keyed by identity string
   * and holding its owner weakly: this cache is static, and a strong key would keep every
   * segmentation ever built alive for the life of the process — together with the DICOM dataset its
   * reader holds, tens of megabytes per SEG. A closed study is dropped by {@link #purge}, called
   * from every mutating operation.
   */
  private static final Map<String, Entry> TRACKED = new LinkedHashMap<>(16, 0.75f, true);

  private static long used;

  /** Single thread, so evictions never run under a caller's segmentation lock. */
  private static final ExecutorService EVICTOR =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "seg-volume-evict"); // NON-NLS
            t.setDaemon(true);
            return t;
          });

  /** Tracked volume: what it costs, and who to ask to release it while that owner still exists. */
  private record Entry(WeakReference<SegSpecialElement> owner, long bytes) {}

  private SegVolumeCache() {}

  /**
   * Identity of a segmentation in {@link #TRACKED}. The SOP Instance UID when there is one, so
   * re-registering the same SEG replaces its entry rather than accumulating one per instance.
   */
  private static String key(SegSpecialElement owner) {
    String uid = owner.getRegionUID();
    return uid == null ? Integer.toHexString(System.identityHashCode(owner)) : uid;
  }

  /** Total heap the canonical volumes may hold together, in bytes. */
  public static long getBudget() {
    return BUDGET;
  }

  /** Heap currently held by the tracked canonical volumes, in bytes. */
  public static long getUsed() {
    synchronized (LOCK) {
      return used;
    }
  }

  /**
   * Records a freshly built volume and schedules the least recently used ones for disposal until
   * the budget is met. The accounting is applied immediately, so the budget is correct as soon as
   * this returns; only the raster free itself is deferred.
   *
   * @param owner the segmentation the volume belongs to
   * @param bytes the heap the volume holds, from {@link SegmentationVolume#heapBytes()}
   */
  public static void register(SegSpecialElement owner, long bytes) {
    if (owner == null || bytes <= 0) {
      return;
    }
    List<SegSpecialElement> victims;
    synchronized (LOCK) {
      purge();
      Entry previous = TRACKED.put(key(owner), new Entry(new WeakReference<>(owner), bytes));
      used += bytes - (previous == null ? 0L : previous.bytes());
      victims = collectVictims(key(owner));
    }
    if (victims.isEmpty()) {
      return;
    }
    // Never release on the calling thread. Releasing takes the victim's own volume lock, and this
    // method runs under the lock of the segmentation that was just built — getOrBuildAlignedVolume
    // holds it across the whole builder, which is where the canonical build happens for MPR and
    // the 3D view. Two concurrent builds would then take each other's lock in opposite order.
    EVICTOR.execute(
        () -> {
          victims.forEach(SegSpecialElement::releaseCanonicalVolume);
          LOGGER.debug(
              "Segmentation volume cache over budget: disposed {} volume(s), {} MB of {} MB in use",
              victims.size(),
              getUsed() >> 20,
              BUDGET >> 20);
        });
  }

  /**
   * Blocks until every eviction scheduled so far has been applied. The evictor runs a single
   * thread, so an empty task queued behind them settles only once they are done. For tests.
   */
  static void awaitEvictions() {
    try {
      EVICTOR.submit(() -> {}).get(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Segmentation volume evictions did not settle", e);
    }
  }

  /** Marks a volume as recently used so eviction prefers the others. */
  public static void touch(SegSpecialElement owner) {
    if (owner != null) {
      synchronized (LOCK) {
        TRACKED.get(key(owner));
      }
    }
  }

  /** Stops tracking a volume that its owner has released. */
  public static void remove(SegSpecialElement owner) {
    if (owner != null) {
      synchronized (LOCK) {
        purge();
        Entry entry = TRACKED.remove(key(owner));
        if (entry != null) {
          used -= entry.bytes();
        }
      }
    }
  }

  /**
   * Drops the entries whose segmentation has been collected. Without this the budget would stay
   * consumed by studies the user closed, and the volumes of the next one would be evicted at once.
   * Caller holds {@link #LOCK}.
   */
  private static void purge() {
    Iterator<Map.Entry<String, Entry>> it = TRACKED.entrySet().iterator();
    while (it.hasNext()) {
      Entry entry = it.next().getValue();
      if (entry.owner().refersTo(null)) {
        used -= entry.bytes();
        it.remove();
      }
    }
  }

  /**
   * Picks the least recently used volumes to dispose, never the one just registered. Caller holds
   * {@link #LOCK}; the entries are dropped here so the accounting stays correct even though the
   * owners are released afterwards, outside the lock.
   */
  private static List<SegSpecialElement> collectVictims(String keep) {
    List<SegSpecialElement> victims = new ArrayList<>();
    Iterator<Map.Entry<String, Entry>> it = TRACKED.entrySet().iterator();
    while (used > BUDGET && it.hasNext()) {
      Map.Entry<String, Entry> entry = it.next();
      if (entry.getKey().equals(keep)) {
        continue; // the newest volume is the one the caller is about to use
      }
      used -= entry.getValue().bytes();
      SegSpecialElement owner = entry.getValue().owner().get();
      if (owner != null) {
        victims.add(owner);
      }
      it.remove();
    }
    return victims;
  }
}
