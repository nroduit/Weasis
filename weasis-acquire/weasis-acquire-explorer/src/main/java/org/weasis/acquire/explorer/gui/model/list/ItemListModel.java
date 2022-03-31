/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.model.list;

import java.beans.PropertyChangeListener;
import javax.swing.AbstractListModel;
import org.weasis.acquire.explorer.core.ItemList;
import org.weasis.acquire.explorer.core.ItemList.Interval;

public class ItemListModel<T> extends AbstractListModel<T> {

  protected final PropertyChangeListener itemListChangeListener;
  protected ItemList<T> itemList = null;

  public ItemListModel() {
    this(new ItemList<>());
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
      if (evt.getNewValue() instanceof Interval interval) {
        switch (ItemList.eProperty.valueOf(evt.getPropertyName())) {
          case INTERVAL_ADDED -> fireIntervalAdded(
              ItemListModel.this, interval.getMin(), interval.getMax());
          case INTERVAL_REMOVED -> fireIntervalRemoved(
              ItemListModel.this, interval.getMin(), interval.getMax());
          case CONTENT_CHANGED -> fireContentsChanged(
              ItemListModel.this, interval.getMin(), interval.getMax());
        }
      }
    };
  }
}
