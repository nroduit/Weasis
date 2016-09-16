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
package org.weasis.core.api.gui.util;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.event.ListDataEvent;

public class GroupRadioMenu implements ActionListener, ComboBoxModelAdapter {

    protected final List<RadioMenuItem> itemList;
    protected final ButtonGroup group;
    protected ComboBoxModel dataModel;

    public GroupRadioMenu() {
        this.itemList = new ArrayList<RadioMenuItem>();
        this.group = new ButtonGroup();
    }

    private void init() {
        for (RadioMenuItem item : itemList) {
            group.remove(item);
        }
        itemList.clear();
        Object selectedItem = dataModel.getSelectedItem();

        for (int i = 0; i < dataModel.getSize(); i++) {
            Object object = dataModel.getElementAt(i);
            Icon icon = null;
            if (object instanceof GUIEntry) {
                icon = ((GUIEntry) object).getIcon();
            }
            RadioMenuItem radioMenuItem = new RadioMenuItem(object.toString(), icon, object);
            radioMenuItem.setSelected(object == selectedItem);
            group.add(radioMenuItem);
            itemList.add(radioMenuItem);
            radioMenuItem.addActionListener(this);
        }
    }

    public List<RadioMenuItem> getRadioMenuItemListCopy() {
        return new ArrayList<RadioMenuItem>(itemList);
    }

    public JPopupMenu createJPopupMenu() {
        JPopupMenu popupMouseButtons = new JPopupMenu();
        for (int i = 0; i < itemList.size(); i++) {
            popupMouseButtons.add(itemList.get(i));
        }
        return popupMouseButtons;
    }

    public JMenu createMenu(String title) {
        JMenu menu = new JMenu(title);
        for (int i = 0; i < itemList.size(); i++) {
            menu.add(itemList.get(i));
        }
        return menu;
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
        setSelected(dataModel.getSelectedItem());
    }

    @Override
    public void intervalAdded(ListDataEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof RadioMenuItem) {
            RadioMenuItem item = (RadioMenuItem) e.getSource();
            if (item.isSelected()) {
                dataModel.setSelectedItem(item.getUserObject());
            }
        }
    };

    public void setSelected(Object selected) {
        if (selected == null) {
            group.clearSelection();
        } else {
            for (int i = 0; i < itemList.size(); i++) {
                RadioMenuItem item = itemList.get(i);
                if (item.getUserObject() == selected) {
                    item.setSelected(true);// Do not trigger actionPerformed
                    dataModel.setSelectedItem(item.getUserObject());
                    return;
                }
            }
        }
    }

    public int getSelectedIndex() {
        Object sObject = dataModel.getSelectedItem();
        int i, c;
        Object obj;

        for (i = 0, c = dataModel.getSize(); i < c; i++) {
            obj = dataModel.getElementAt(i);
            if (obj != null && obj.equals(sObject)) {
                return i;
            }
        }
        return -1;
    }

    public Object getSelectedItem() {
        return dataModel.getSelectedItem();
    }

    public ComboBoxModel getModel() {
        return dataModel;
    }

    @Override
    public void setModel(ComboBoxModel dataModel) {
        if (this.dataModel != null) {
            this.dataModel.removeListDataListener(this);
        }
        if (dataModel != null) {
            dataModel.removeListDataListener(this);
        }
        this.dataModel = dataModel == null ? new DefaultComboBoxModel() : dataModel;
        init();
        this.dataModel.addListDataListener(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        for (int i = 0; i < itemList.size(); i++) {
            itemList.get(i).setEnabled(enabled);
        }
    }

}
