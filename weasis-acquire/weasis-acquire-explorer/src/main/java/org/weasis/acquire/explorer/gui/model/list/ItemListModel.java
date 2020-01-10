/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.model.list;

import java.beans.PropertyChangeListener;

import javax.swing.AbstractListModel;

import org.weasis.acquire.explorer.core.ItemList;
import org.weasis.acquire.explorer.core.ItemList.Interval;

public class ItemListModel<T> extends AbstractListModel<T> {
    private static final long serialVersionUID = 4350497424368257421L;

    protected final PropertyChangeListener itemListChangeListener;
    protected ItemList<T> itemList = null;

    public ItemListModel() {
        this(new ItemList<T>());
    }

    public ItemListModel(ItemList<T> newItemList) {
        setItemList(newItemList);
        itemListChangeListener = getPropertyChangeListener();
    }

    public ItemList<T> getItemList() {
        return itemList;
    }

    public void setItemList(ItemList<T> newItemList) {
        if (itemList != null) {
            itemList.removePropertyChangeListener(itemListChangeListener);
            if (itemList.getSize() > 0) {
                fireIntervalRemoved(this, 0, itemList.getSize() - 1);
            }
        }

        itemList = newItemList;

        if (itemList != null) {
            itemList.addPropertyChangeListener(itemListChangeListener);
            if (itemList.getSize() > 0) {
                fireIntervalAdded(this, 0, itemList.getSize() - 1);
            }
        }
    }

    @Override
    public int getSize() {
        return itemList == null ? 0 : itemList.getSize();
    }

    @Override
    public T getElementAt(int index) {
        return itemList == null ? null : itemList.getItem(index);
    }

    protected PropertyChangeListener getPropertyChangeListener() {
        return evt -> {
            if (evt.getNewValue() instanceof Interval) {
                Interval interval = (Interval) evt.getNewValue();
                switch (ItemList.eProperty.valueOf(evt.getPropertyName())) {
                    case INTERVAL_ADDED:
                        fireIntervalAdded(ItemListModel.this, interval.getMin(), interval.getMax());
                        break;
                    case INTERVAL_REMOVED:
                        fireIntervalRemoved(ItemListModel.this, interval.getMin(), interval.getMax());
                        break;
                    case CONTENT_CHANGED:
                        fireContentsChanged(ItemListModel.this, interval.getMin(), interval.getMax());
                        break;
                }
            }
        };
    }
}
