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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.weasis.opencv.data.PlanarImage;

public abstract class NativeCache<K, V extends PlanarImage> extends AbstractMap<K, V> {

  protected final Map<K, V> hash;
  private final long maxNativeMemory;
  private final AtomicLong useNativeMemory;

  protected NativeCache(long maxNativeMemory) {
    this.maxNativeMemory = maxNativeMemory;
    this.useNativeMemory = new AtomicLong(0);
    this.hash = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true));
  }

  @Override
  public V get(Object key) {
    return hash.get(key);
  }

  public boolean isMemoryAvailable() {
    return useNativeMemory.get() < maxNativeMemory;
  }

  public void expungeStaleEntries() {
    if (!isMemoryAvailable()) {
      synchronized (hash) {
        List<K> remKeys = new ArrayList<>();
        // 5% of max memory + diff
        long maxfreeSize = maxNativeMemory / 20 + (useNativeMemory.get() - maxNativeMemory);
        long freeSize = 0;

        for (Map.Entry<K, V> e : hash.entrySet()) {
          freeSize += physicalBytes(e.getValue());
          if (freeSize > maxfreeSize) {
            break;
          }
          remKeys.add(e.getKey());
        }

        for (K key : remKeys) {
          V val = hash.remove(key);
          useNativeMemory.addAndGet(-physicalBytes(val));
          afterEntryRemove(key, val);
        }
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

  @Override
  public V put(K key, V value) {
    expungeStaleEntries();
    V result = hash.put(key, value);
    useNativeMemory.addAndGet(physicalBytes(value));
    useNativeMemory.addAndGet(-physicalBytes(result));
    return result;
  }

  @SuppressWarnings("unchecked")
  @Override
  public V remove(Object key) {
    V val = hash.remove(key);
    useNativeMemory.addAndGet(-physicalBytes(val));
    afterEntryRemove((K) key, val);
    return val;
  }

  @Override
  public void clear() {
    hash.clear();
    useNativeMemory.set(0);
  }

  @Override
  public int size() {
    return hash.size();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return hash.entrySet();
  }

  @Override
  public boolean containsKey(Object key) {
    return hash.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return hash.containsValue(value);
  }

  @Override
  public int hashCode() {
    return hash.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    @SuppressWarnings("rawtypes")
    NativeCache other = (NativeCache) obj;
    return hash.equals(other.hash);
  }
}
