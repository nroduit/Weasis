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

import javax.swing.ComboBoxModel;

import org.weasis.acquire.explorer.core.ItemList;
import org.weasis.acquire.explorer.core.ItemList.Interval;

public class ItemListComboBoxModel<T> extends ItemListModel<T> implements ComboBoxModel<T> {
    private static final long serialVersionUID = -7664267643457177724L;

    private T selectedItem = null;
    private T lastSelectedItem = null;

    public ItemListComboBoxModel() {
        super();
        if (itemList.getSize() > 0) {
            selectedItem = itemList.getItem(0);
        }
    }

    public ItemListComboBoxModel(ItemList<T> itemList) {
        super(itemList);
        if (itemList.getSize() > 0) {
            selectedItem = itemList.getItem(0);
        }
    }

    @Override
    public T getSelectedItem() {
        return selectedItem;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setSelectedItem(Object anItem) {
        // Let assume anItem is included in itemList and is an instance of T
        // Same implementation as done in DefaultComboBoxModel since contentsChanged is
        // interpreted in a JCombobox as a selectedItemChanged

        if ((selectedItem != null && !selectedItem.equals(anItem)) || selectedItem == null && anItem != null) {

            // itemList.containsItem((T) anItem)

            // lastSelectedItem = itemList.containsItem((T) selectedItem) ? selectedItem : null;
            lastSelectedItem = selectedItem;
            selectedItem = ((anItem == null) || !itemList.containsItem((T) anItem)) ? null : (T) anItem;
            fireContentsChanged(this, -1, -1);
        }
    }

    @Override
    protected PropertyChangeListener getPropertyChangeListener() {
        return evt -> {
            if (evt.getNewValue() instanceof Interval) {
                Interval interval = (Interval) evt.getNewValue();

                switch (ItemList.eProperty.valueOf(evt.getPropertyName())) {
                    case INTERVAL_ADDED:
                        fireIntervalAdded(ItemListComboBoxModel.this, interval.getMin(), interval.getMax());
                        if (selectedItem == null && itemList.getSize() > 0) {
                            setSelectedItem(itemList.getLastItem());
                        }
                        break;
                    case INTERVAL_REMOVED:
                        for (int i = interval.getMin(); i <= interval.getMax(); i++) {
                            T item = itemList.getItem(i);
                            if (item != null && item.equals(selectedItem)) {
                                int lastSelectionIndex = itemList.getIndex(lastSelectedItem);
                                if (interval.getMin() <= lastSelectionIndex
                                    && interval.getMax() >= lastSelectionIndex) {
                                    setSelectedItem(null);
                                } else {
                                    setSelectedItem(lastSelectedItem);
                                }
                                break;
                            }
                        }
                        fireIntervalRemoved(ItemListComboBoxModel.this, interval.getMin(), interval.getMax());
                        break;
                    case CONTENT_CHANGED:
                        fireContentsChanged(ItemListComboBoxModel.this, interval.getMin(), interval.getMax());
                        // note : used by JComboBox only to check if selectedItem has changed but not used by
                        // the renderer
                        break;
                }
            }
        };
    }
}
