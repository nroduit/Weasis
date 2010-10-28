/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
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

    /** The internal HashMap that will hold the SoftReference. */
    protected final Map<K, SoftReference<V>> hash = new HashMap<K, SoftReference<V>>();

    protected final Map<SoftReference<V>, K> reverseLookup = new HashMap<SoftReference<V>, K>();

    /** Reference queue for cleared SoftReference objects. */
    private final ReferenceQueue<V> queue = new ReferenceQueue<V>();

    @Override
    public V get(Object key) {
        expungeStaleEntries();
        V result = null;
        // We get the SoftReference represented by that key
        SoftReference<V> soft_ref = hash.get(key);
        if (soft_ref != null) {
            // From the SoftReference we get the value, which can be
            // null if it has been garbage collected
            result = soft_ref.get();
            if (result == null) {
                // If the value has been garbage collected, remove the
                // entry from the HashMap.
                // hash.remove(key);
                // reverseLookup.remove(soft_ref);
                removeElement(soft_ref);
            }
        }
        return result;
    }

    public void removeElement(Reference<? extends V> soft) {
        K key = reverseLookup.remove(soft);
        hash.remove(key);
    }

    public void expungeStaleEntries() {
        Reference<? extends V> sv;
        while ((sv = queue.poll()) != null) {
            removeElement(sv);
            // hash.remove(reverseLookup.remove(sv));
        }
    }

    @Override
    public V put(K key, V value) {
        expungeStaleEntries();
        SoftReference<V> soft_ref = new SoftReference<V>(value, queue);
        reverseLookup.put(soft_ref, key);
        SoftReference<V> result = hash.put(key, soft_ref);
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
        Set<Entry<K, V>> result = new LinkedHashSet<Entry<K, V>>();
        for (final Entry<K, SoftReference<V>> entry : hash.entrySet()) {
            final V value = entry.getValue().get();
            if (value != null) {
                result.add(new Entry<K, V>() {

                    public K getKey() {
                        return entry.getKey();
                    }

                    public V getValue() {
                        return value;
                    }

                    public V setValue(V v) {
                        entry.setValue(new SoftReference<V>(v, queue));
                        return value;
                    }
                });
            }
        }
        return result;
    }
}
