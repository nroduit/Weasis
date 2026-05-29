/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.weasis.core.api.util.MemoryManager;
import org.weasis.core.api.util.NativeMemoryConsumer;
import org.weasis.core.api.util.ResourceMonitor;
import org.weasis.opencv.data.PlanarImage;

/**
 * An access-ordered, size-bounded cache for native (off-heap) images. It is intentionally not a
 * {@link Map}: callers only ever store, fetch and drop entries, and exposing a {@code Map} view
 * would let code mutate the backing store while bypassing the native-memory accounting.
 *
 * <p>Entries can be <em>pinned</em>: a pinned entry (typically the image currently displayed in a
 * viewport) is never evicted, even under memory pressure. Eviction therefore reclaims, in
 * access-order, only the entries no longer in use, which are transparently reloaded on the next
 * access. Pins are reference counted so the same image shared by several viewports stays protected
 * until the last holder releases it.
 *
 * <p>The cache registers with the {@link MemoryManager} and reacts to <em>global</em> native-memory
 * pressure, not only to its own budget: when other consumers (e.g. an in-progress 3D volume load)
 * fill the shared budget, the cache evicts unpinned entries to yield room.
 */
public abstract class NativeCache<K, V extends PlanarImage> implements NativeMemoryConsumer {

  protected final Map<K, V> hash;
  private final Map<K, Integer> pinCount;
  private final long maxNativeMemory;
  private final AtomicLong useNativeMemory;

  protected NativeCache(long maxNativeMemory) {
    this.maxNativeMemory = maxNativeMemory;
    this.useNativeMemory = new AtomicLong(0);
    this.hash = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true));
    this.pinCount = new ConcurrentHashMap<>();
    MemoryManager.getInstance().register(this);
  }

  @Override
  public long usedNativeMemory() {
    return useNativeMemory.get();
  }

  public V get(K key) {
    return hash.get(key);
  }

  public boolean isMemoryAvailable() {
    return useNativeMemory.get() < maxNativeMemory
        && MemoryManager.getInstance().isMemoryAvailable();
  }

  public void expungeStaleEntries() {
    if (!isMemoryAvailable()) {
      synchronized (hash) {
        List<K> remKeys = new ArrayList<>();
        MemoryManager memoryManager = MemoryManager.getInstance();
        long localOverage = useNativeMemory.get() - maxNativeMemory;
        long globalOverage = memoryManager.getUsedNativeMemory() - memoryManager.getNativeBudget();
        // Free 5% of the budget plus whichever overage (local or global) is larger, so eviction
        // reclaims a sensible amount whether triggered by this cache or by global pressure.
        long maxFreeSize =
            maxNativeMemory / 20 + Math.max(0, Math.max(localOverage, globalOverage));
        long freeSize = 0;

        for (Map.Entry<K, V> e : hash.entrySet()) {
          K key = e.getKey();
          if (isPinned(key)) {
            // Pinned images are in use by a viewport: skip them and keep scanning.
            continue;
          }
          freeSize += physicalBytes(e.getValue());
          if (freeSize > maxFreeSize) {
            break;
          }
          remKeys.add(key);
        }

        for (K key : remKeys) {
          V val = hash.remove(key);
          useNativeMemory.addAndGet(-physicalBytes(val));
          afterEntryRemove(key, val);
        }
        ResourceMonitor.getInstance().recordCacheEviction(remKeys.size());
      }
    }
  }

  private long physicalBytes(V val) {
    if (val != null) {
      return val.physicalBytes();
    }
    return 0;
  }

  protected abstract void afterEntryRemove(K key, V val);

  public void put(K key, V value) {
    expungeStaleEntries();
    V previous = hash.put(key, value);
    useNativeMemory.addAndGet(physicalBytes(value));
    useNativeMemory.addAndGet(-physicalBytes(previous));
  }

  public void remove(K key) {
    V val = hash.remove(key);
    useNativeMemory.addAndGet(-physicalBytes(val));
    afterEntryRemove(key, val);
  }

  /**
   * Pins an entry so it cannot be evicted while in use (e.g. displayed in a viewport). Pins are
   * reference counted: an entry stays pinned until {@link #unpin} has been called as many times as
   * pin. Pinning is independent of the presence of the entry in the cache.
   */
  public void pin(K key) {
    if (key != null) {
      pinCount.merge(key, 1, Integer::sum);
    }
  }

  /** Releases a pin acquired with {@link #pin}; has no effect if the entry is not pinned. */
  public void unpin(K key) {
    if (key != null) {
      pinCount.computeIfPresent(key, (k, count) -> count <= 1 ? null : count - 1);
    }
  }

  /**
   * @return {@code true} if the entry is currently pinned and therefore protected from eviction.
   */
  public boolean isPinned(K key) {
    return pinCount.containsKey(key);
  }

  public void clear() {
    hash.clear();
    pinCount.clear();
    useNativeMemory.set(0);
  }

  public int size() {
    return hash.size();
  }
}
