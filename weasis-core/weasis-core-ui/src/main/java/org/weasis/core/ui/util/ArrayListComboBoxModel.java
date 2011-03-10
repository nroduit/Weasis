/*******************************************************************************
 * Copyright (c) 2011 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.MutableComboBoxModel;

public class ArrayListComboBoxModel extends AbstractListModel implements MutableComboBoxModel, ComboBoxModel {
    private Object selectedItem;
    private final ArrayList items;

    public ArrayListComboBoxModel() {
        items = new ArrayList();
    }

    public ArrayListComboBoxModel(ArrayList arrayList) {
        items = arrayList;
    }

    public int binarySearch(Object value, Comparator comp) {
        return Collections.binarySearch(items, value, comp);
    }

    public Object getSelectedItem() {
        return selectedItem;
    }

    public void setSelectedItem(Object newValue) {
        if ((selectedItem != null && !selectedItem.equals(newValue)) || selectedItem == null && newValue != null) {
            selectedItem = newValue;
            fireContentsChanged(this, -1, -1);
        }
    }

    public int getSize() {
        return items.size();
    }

    public Object getElementAt(int index) {
        if (index >= 0 && index < items.size()) {
            return items.get(index);
        } else {
            return null;
        }
    }

    public int getIndexOf(Object anObject) {
        return items.indexOf(anObject);
    }

    // implements javax.swing.MutableComboBoxModel
    public void addElement(Object anObject) {
        items.add(anObject);
        fireIntervalAdded(this, items.size() - 1, items.size() - 1);
        if (items.size() == 1 && selectedItem == null && anObject != null) {
            setSelectedItem(anObject);
        }
    }

    // implements javax.swing.MutableComboBoxModel
    public void insertElementAt(Object anObject, int index) {
        items.add(index, anObject);
        fireIntervalAdded(this, index, index);
    }

    // implements javax.swing.MutableComboBoxModel
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

    // implements javax.swing.MutableComboBoxModel
    public void removeElement(Object anObject) {
        int index = items.indexOf(anObject);
        if (index != -1) {
            removeElementAt(index);
        }
    }

    /**
     * Empties the list.
     */
    public void removeAllElements() {
        if (items.size() > 0) {
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
