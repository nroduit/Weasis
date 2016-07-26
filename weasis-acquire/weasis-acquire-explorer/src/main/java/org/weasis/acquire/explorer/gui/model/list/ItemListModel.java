package org.weasis.acquire.explorer.gui.model.list;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.AbstractListModel;

import org.weasis.acquire.explorer.core.ItemList;
import org.weasis.acquire.explorer.core.ItemList.Interval;

public class ItemListModel<T> extends AbstractListModel<T> {
    private static final long serialVersionUID = 4350497424368257421L;

    protected ItemList<T> itemList = null;

    public ItemListModel() {
        setItemList(new ItemList<T>());
    }

    public ItemListModel(ItemList<T> newItemList) {
        setItemList(newItemList);
    }

    public ItemList<T> getItemList() {
        return itemList;
    }


    public void setItemList(ItemList<T> newItemList) {
        // if (newItemList == null)
        // throw new IllegalArgumentException("newItemList must be non null");

        if (itemList != null) {
            itemList.removePropertyChangeListener(getItemListChangeListener());
            if (itemList.getSize() > 0) {
                fireIntervalRemoved(this, 0, itemList.getSize() - 1);
            }
        }

        itemList = newItemList;

        if (itemList != null) {
            itemList.addPropertyChangeListener(getItemListChangeListener());
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

    // TODO - do it static
    // protected final ItemListChangeListener itemListChangeListener = new ItemListChangeListener();
    protected PropertyChangeListener itemListChangeListener = getItemListChangeListener();

    protected PropertyChangeListener getItemListChangeListener() {
        if (itemListChangeListener == null) {
            itemListChangeListener = new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {

                    if (evt.getNewValue() instanceof Interval) {
                        Interval interval = (Interval) evt.getNewValue();

                        switch (ItemList.eProperty.valueOf(evt.getPropertyName())) {
                            case INTERVAL_ADDED:
                                fireIntervalAdded(ItemListModel.this, interval.index0, interval.index1);
                                break;
                            case INTERVAL_REMOVED:
                                fireIntervalRemoved(ItemListModel.this, interval.index0, interval.index1);
                                break;
                            case CONTENT_CHANGED:
                                fireContentsChanged(ItemListModel.this, interval.index0, interval.index1);
                                break;
                        }
                    }
                }
            };
        }
        return itemListChangeListener;
    }

}
