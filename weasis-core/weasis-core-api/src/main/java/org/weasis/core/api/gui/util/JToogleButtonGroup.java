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
import java.util.HashMap;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.event.ListDataEvent;

public class JToogleButtonGroup implements ActionListener, ComboBoxModelAdapter {

    protected final List<JToggleButton> itemList;
    protected final HashMap<JToggleButton, Object> map = new HashMap<JToggleButton, Object>();
    protected ComboBoxModel dataModel;

    public JToogleButtonGroup() {
        this.itemList = new ArrayList<JToggleButton>();
    }

    private void init() {
        itemList.clear();
        Object selectedItem = dataModel.getSelectedItem();
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < dataModel.getSize(); i++) {
            Object object = dataModel.getElementAt(i);
            Icon icon = null;
            if (object instanceof GUIEntry) {
                icon = ((GUIEntry) object).getIcon();
            }
            JToggleButton b = new JToggleButton(icon);
            b.setToolTipText(object.toString());
            // b.setMargin(new Insets(2, 2, 2, 2));
            // // b.setUI(new VLButtonUI());
            // b.setBorder(null);
            // b.setRolloverEnabled(true);
            // b.setContentAreaFilled(false);
            // b.setOpaque(false);
            // b.setBorderPainted(false);

            map.put(b, object);
            b.setSelected(object == selectedItem);
            group.add(b);
            b.addActionListener(this);
            itemList.add(b);
        }
    }

    @Override
    public void setModel(ComboBoxModel dataModel) {
        boolean changeListener = dataModel != null && dataModel != this.dataModel;
        if (this.dataModel != null) {
            this.dataModel.removeListDataListener(this);
        }
        this.dataModel = dataModel == null ? new DefaultComboBoxModel() : dataModel;
        init();
        if (changeListener) {
            this.dataModel.addListDataListener(this);
        }
    }

    public JToggleButton[] getJToggleButtonList() {
        return itemList.toArray(new JToggleButton[itemList.size()]);
    }

    public void contentsChanged(ListDataEvent e) {
        setSelected(dataModel.getSelectedItem());
    }

    public void intervalAdded(ListDataEvent e) {
        // TODO Auto-generated method stub

    }

    public void intervalRemoved(ListDataEvent e) {
        // TODO Auto-generated method stub

    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JToggleButton) {
            JToggleButton item = (JToggleButton) e.getSource();
            if (item.isSelected()) {
                dataModel.setSelectedItem(map.get(item));
            }
        }
    };

    public void setSelected(Object selected) {
        if (selected != null) {
            for (int i = 0; i < itemList.size(); i++) {
                JToggleButton item = itemList.get(i);
                Object itemObj = map.get(item);
                if (itemObj == selected) {
                    item.setSelected(true);// Do not trigger actionPerformed
                    dataModel.setSelectedItem(itemObj);
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

    @Override
    public void setEnabled(boolean enabled) {
        for (int i = 0; i < itemList.size(); i++) {
            itemList.get(i).setEnabled(enabled);
        }
    }

}
