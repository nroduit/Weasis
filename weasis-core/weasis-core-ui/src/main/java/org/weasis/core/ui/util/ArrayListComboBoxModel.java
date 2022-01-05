/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.MutableComboBoxModel;

public class ArrayListComboBoxModel<E> extends AbstractListModel<E>
    implements MutableComboBoxModel<E>, ComboBoxModel<E> {

  private Object selectedItem;
  private final List<E> items;
  private final Comparator<E> comparator;

  public ArrayListComboBoxModel() {
    this(null, null);
  }

  public ArrayListComboBoxModel(List<E> items) {
    this(items, null);
  }

  public ArrayListComboBoxModel(Comparator<E> comparator) {
    this(null, comparator);
  }

  public ArrayListComboBoxModel(List<E> items, Comparator<E> comparator) {
    this.items = items == null ? new ArrayList<>() : items;
    this.comparator = comparator;
  }

  @Override
  public Object getSelectedItem() {
    return selectedItem;
  }

  @Override
  public void setSelectedItem(Object newValue) {
    if ((selectedItem != null && !selectedItem.equals(newValue))
        || selectedItem == null && newValue != null) {
      selectedItem = newValue;
      fireContentsChanged(this, -1, -1);
    }
  }

  @Override
  public int getSize() {
    return items.size();
  }

  @Override
  public E getElementAt(int index) {
    if (index >= 0 && index < items.size()) {
      return items.get(index);
    } else {
      return null;
    }
  }

  public int getIndexOf(Object anObject) {
    return items.indexOf(anObject);
  }

  @Override
  public void addElement(E anObject) {
    int index = Collections.binarySearch(items, anObject, comparator);
    if (index < 0) {
      insertElementAt(anObject, -(index + 1));
    } else {
      insertElementAt(anObject, index);
    }
  }

  @Override
  public void insertElementAt(E anObject, int index) {
    items.add(index, anObject);
    fireIntervalAdded(this, index, index);
  }

  @Override
  public void removeElementAt(int index) {
    if (getElementAt(index) == selectedItem) {
      if (index == 0) {
        setSelectedItem(getSize() == 1 ? null : getElementAt(index + 1));
      } else {
        setSelectedItem(getElementAt(index - 1));
      }
    }
    items.remove(index);
    fireIntervalRemoved(this, index, index);
  }

  @Override
  public void removeElement(Object anObject) {
    int index = items.indexOf(anObject);
    if (index != -1) {
      removeElementAt(index);
    }
  }

  /** Empties the list. */
  public void removeAllElements() {
    if (!items.isEmpty()) {
      int firstIndex = 0;
      int lastIndex = items.size() - 1;
      items.clear();
      selectedItem = null;
      fireIntervalRemoved(this, firstIndex, lastIndex);
    } else {
      selectedItem = null;
    }
  }
}
