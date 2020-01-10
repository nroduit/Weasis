/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.media.data;

import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class SoftHashMap<K, V> extends AbstractMap<K, V> implements Serializable {
    private static final long serialVersionUID = -1374929894464993435L;

    /** The internal HashMap that will hold the SoftReference. */
    protected final transient Map<K, SoftReference<V>> hash = new HashMap<>();

    protected final transient Map<SoftReference<V>, K> reverseLookup = new HashMap<>();

    /** Reference queue for cleared SoftReference objects. */
    private final transient ReferenceQueue<V> queue = new ReferenceQueue<>();

    @Override
    public V get(Object key) {
        expungeStaleEntries();
        V result = null;
        // We get the SoftReference represented by that key
        SoftReference<V> softRef = hash.get(key);
        if (softRef != null) {
            // From the SoftReference we get the value, which can be
            // null if it has been garbage collected
            result = softRef.get();
            if (result == null) {
                // If the value has been garbage collected, remove the entry from the HashMap.
                removeElement(softRef);
            }
        }
        return result;
    }

    public void removeElement(Reference<? extends V> soft) {
        K key = reverseLookup.remove(soft);
        if (key != null) {
            hash.remove(key);
        }
    }

    public void expungeStaleEntries() {
        Reference<? extends V> sv;
        while ((sv = queue.poll()) != null) {
            removeElement(sv);
        }
    }

    @Override
    public V put(K key, V value) {
        expungeStaleEntries();
        SoftReference<V> softRef = new SoftReference<>(value, queue);
        reverseLookup.put(softRef, key);
        SoftReference<V> result = hash.put(key, softRef);
        if (result == null) {
            return null;
        }
        reverseLookup.remove(result);
        return result.get();
    }

    @Override
    public V remove(Object key) {
        expungeStaleEntries();
        SoftReference<V> result = hash.remove(key);
        if (result == null) {
            return null;
        }
        reverseLookup.remove(result);
        return result.get();
    }

    @Override
    public void clear() {
        hash.clear();
        reverseLookup.clear();
    }

    @Override
    public int size() {
        expungeStaleEntries();
        return hash.size();
    }

    /**
     * Returns a copy of the key/values in the map at the point of calling. However, setValue still sets the value in
     * the actual SoftHashMap.
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        expungeStaleEntries();
        Set<Entry<K, V>> result = new LinkedHashSet<>();
        for (final Entry<K, SoftReference<V>> entry : hash.entrySet()) {
            final V value = entry.getValue().get();
            if (value != null) {
                result.add(new Entry<K, V>() {

                    @Override
                    public K getKey() {
                        return entry.getKey();
                    }

                    @Override
                    public V getValue() {
                        return value;
                    }

                    @Override
                    public V setValue(V v) {
                        entry.setValue(new SoftReference<>(v, queue));
                        return value;
                    }
                });
            }
        }
        return result;
    }

    @Override
    public boolean containsKey(Object key) {
        expungeStaleEntries();
        SoftReference<V> softRef = hash.get(key);
        if (softRef != null) {
            // From the SoftReference we get the value, which can be
            // null if it has been garbage collected
            V result = softRef.get();
            if (result != null) {
                return true;
            }
            // If the value has been garbage collected, remove the
            // entry from the HashMap.
            hash.remove(key);
            reverseLookup.remove(softRef);
        }
        return false;
    }
}
