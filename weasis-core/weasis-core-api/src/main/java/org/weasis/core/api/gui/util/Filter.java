/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.gui.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public abstract class Filter<T> {
    public abstract boolean passes(T item);

    public Iterator<T> filter(Iterator<T> iterator) {
        return new FilterIterator(iterator);
    }

    public Iterable<T> filter(final Iterable<T> iterable) {
        return () -> filter(iterable.iterator());
    }

    public static int size(Iterable<?> iterable) {
        return (iterable instanceof Collection) ? ((Collection<?>) iterable).size() : Filter.size(iterable.iterator());
    }

    public static int size(Iterator<?> iterator) {
        int count = 0;
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        return count;
    }

    public static <T> List<T> makeList(Iterable<T> iter) {
        List<T> list = new ArrayList<>();
        for (T item : iter) {
            list.add(item);
        }
        return list;
    }

    private class FilterIterator implements Iterator<T> {
        private Iterator<T> iterator;
        private T next;

        private FilterIterator(Iterator<T> iterator) {
            this.iterator = iterator;
            toNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public T next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            T returnValue = next;
            toNext();
            return returnValue;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void toNext() {
            next = null;
            while (iterator.hasNext()) {
                T item = iterator.next();
                if (item != null && passes(item)) {
                    next = item;
                    break;
                }
            }
        }
    }
}