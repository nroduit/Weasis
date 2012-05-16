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

import java.util.ArrayList;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

public abstract class ComboItemListener implements ListDataListener, ChangeListener, ActionState {

    protected final ActionW action;
    protected final ArrayList<ComboBoxModelAdapter> itemList;
    protected final DefaultComboBoxModel model;
    private boolean enable;

    public ComboItemListener(ActionW action, Object[] objects) {
        super();
        this.action = action;
        enable = true;
        itemList = new ArrayList<ComboBoxModelAdapter>();
        model = (objects != null) ? new DefaultComboBoxModel(objects) : new DefaultComboBoxModel();
        model.addListDataListener(this);
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
        // if (model.equals(e.getSource())) {
        itemStateChanged(model.getSelectedItem());
        // }
    }

    @Override
    public void intervalAdded(ListDataEvent e) {
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
    }

    @Override
    public void enableAction(boolean enabled) {
        this.enable = enabled;
        for (ComboBoxModelAdapter c : itemList) {
            c.setEnabled(enabled);
        }
    }

    @Override
    public String toString() {
        return action.getTitle();
    }

    @Override
    public ActionW getActionW() {
        return action;
    }

    public abstract void itemStateChanged(Object object);

    /**
     * Register a component and add at the same the ItemListener
     * 
     * @param slider
     */
    public void registerComponent(ComboBoxModelAdapter component) {
        if (!itemList.contains(component)) {
            itemList.add(component);
            component.setModel(model);
            component.setEnabled(enable);
        }
    }

    public void unregisterJComponent(ComboBoxModelAdapter component) {
        itemList.remove(component);
        component.setModel(new DefaultComboBoxModel());
    }

    public synchronized Object[] getAllItem() {
        Object[] array = new Object[model.getSize()];
        for (int i = 0; i < array.length; i++) {
            array[i] = model.getElementAt(i);
        }
        return array;
    }

    public synchronized Object getFirstItem() {
        return model.getElementAt(0);
    }

    public synchronized Object getSelectedItem() {
        return model.getSelectedItem();
    }

    public synchronized void setSelectedItem(Object object) {
        model.setSelectedItem(object);
    }

    public synchronized void setSelectedItemWithoutTriggerAction(Object object) {
        model.removeListDataListener(this);
        model.setSelectedItem(object);
        model.addListDataListener(this);
    }

    public synchronized void setDataListWithoutTriggerAction(Object[] objects) {
        setDataList(objects, false);
    }

    public synchronized void setDataList(Object[] objects) {
        setDataList(objects, true);
    }

    protected synchronized void setDataList(Object[] objects, boolean doTriggerAction) {
        if (objects != null && objects.length > 0) {
            Object oldSelection = model.getSelectedItem();
            model.removeListDataListener(this);
            model.removeAllElements();
            boolean oldSelectionStillExist = false;

            for (Object object : objects) {
                model.addElement(object);
                if (object.equals(oldSelection)) {
                    oldSelectionStillExist = true;
                }
            }

            for (ComboBoxModelAdapter c : itemList) {
                c.setModel(model);
            }

            model.setSelectedItem(null);

            if (doTriggerAction) {
                model.addListDataListener(this);
            }

            if (oldSelection != null && oldSelectionStillExist) {
                model.setSelectedItem(oldSelection);
                // } else if (objects[0] == model.getSelectedItem()) {
                // itemStateChanged(model.getSelectedItem());
            } else {
                model.setSelectedItem(objects[0]);
            }

            if (!doTriggerAction) {
                model.addListDataListener(this);
            }

        }
    }

    public JMenu createUnregisteredRadioMenu(String title) {
        GroupRadioMenu radioMenu = new GroupRadioMenu();
        radioMenu.setModel(model);
        JMenu menu = radioMenu.createMenu(title);
        if (!enable) {
            menu.setEnabled(false);
        }
        return menu;
    }

    public ArrayList<ComboBoxModelAdapter> getItemList() {
        return itemList;
    }

    public DefaultComboBoxModel getModel() {
        return model;
    }

    public JToogleButtonGroup createButtonGroup() {
        final JToogleButtonGroup group = new JToogleButtonGroup();
        registerComponent(group);
        return group;
    }

    public JComboBox createCombo() {
        final ComboItems combo = new ComboItems();
        registerComponent(combo);
        JMVUtils.setPreferredWidth(combo, 150, 150);
        // Update UI before adding the Tooltip feature in the combobox list
        combo.updateUI();
        JMVUtils.addTooltipToComboList(combo);
        return combo;
    }

    public JMenu createMenu(String title) {
        JMenu menu = new JMenu(title);
        GroupRadioMenu radioMenu = new GroupRadioMenu();
        radioMenu.setModel(model);
        radioMenu.fillMenu(menu);
        // registerComponent(radioMenu);
        return menu;
    }

    public GroupRadioMenu createGroupRadioMenu() {
        GroupRadioMenu radioMenu = new GroupRadioMenu();
        registerComponent(radioMenu);
        return radioMenu;
    }

    static class ComboItems extends JComboBox implements ComboBoxModelAdapter {

    }
}
