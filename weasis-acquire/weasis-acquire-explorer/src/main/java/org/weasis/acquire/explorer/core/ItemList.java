/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.core;

import java.util.ArrayList;
import java.util.List;

import org.weasis.acquire.explorer.util.AbstractBean;

public class ItemList<T> extends AbstractBean<ItemList.eProperty> {

    public enum eProperty {
        INTERVAL_ADDED, INTERVAL_REMOVED, CONTENT_CHANGED
    }

    protected final ArrayList<T> itemList;

    public ItemList() {
        itemList = new ArrayList<>();
    }

    public ItemList(List<T> itemList) {
        this.itemList = new ArrayList<>(itemList);
    }

    public int getSize() {
        return itemList.size();
    }

    public boolean isEmpty() {
        return itemList.isEmpty();
    }

    public T getFirstItem() {
        return getItem(0);
    }

    public T getLastItem() {
        return getItem(itemList.size() - 1);
    }

    public T getItem(int index) {
        if (index >= 0 && index < itemList.size()) {
            return itemList.get(index);
        } else {
            return null;
        }
    }

    public int getIndex(T item) {
        return itemList.indexOf(item);
    }

    public boolean containsItem(T item) {
        return itemList.contains(item);
    }

    public List<T> getList() {
        return itemList;
    }

    public void addItem(T item) {
        insertItem(Integer.MAX_VALUE, item); // append to the end of list
    }

    public void insertItem(int index, T item) {
        if (item != null && !itemList.contains(item)) {
            int insert = index < 0 ? 0 : index >= itemList.size() ? itemList.size() : index;
            itemList.add(insert, item);
            firePropertyChange(eProperty.INTERVAL_ADDED, null, new Interval(insert, insert));
        }
    }

    public void addItems(List<T> list) {
        if (list != null && !list.isEmpty()) {
            for (T item : list) {
                if (item != null) {
                    itemList.add(item);
                }
            }
            firePropertyChange(eProperty.INTERVAL_ADDED, null,
                new Interval(itemList.size() - list.size(), itemList.size() - 1));
        }
    }

    public void removeItem(T item) {
        removeItem(itemList.indexOf(item));
    }

    public void removeItem(int index) {
        if (index >= 0 && index < itemList.size()) {
            firePropertyChange(eProperty.INTERVAL_REMOVED, null, new Interval(index, index));
            itemList.remove(index);
        }
    }

    public void removeItems(List<T> list) {
        if (list != null && !list.isEmpty()) {
            for (T item : list) {
                removeItem(item);
            }
        }
    }

    public void clear() {
        int size = itemList.size();
        if (size > 0) {
            firePropertyChange(eProperty.INTERVAL_REMOVED, null, new Interval(0, size - 1));
            itemList.clear();
        }
    }

    public void notifyItemUpdate(T item) {
        notifyItemUpdate(itemList.indexOf(item));
    }

    public void notifyItemUpdate(int index) {
        if (index >= 0 && index < itemList.size()) {
            firePropertyChange(eProperty.CONTENT_CHANGED, null, new Interval(index, index));
        }
    }

    public void notifyItemsUpdate(List<T> list) {
        for (T item : list) {
            notifyItemUpdate(item);
        }
    }

    public static class Interval {
        private final int min;
        private final int max;

        public Interval(int index0, int index1) {
            this.min = Math.min(index0, index1);
            this.max = Math.max(index0, index1);
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }
    }

}
