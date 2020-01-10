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

import java.util.Arrays;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.weasis.core.api.service.AuditLog;

public abstract class ComboItemListener<T> extends BasicActionState implements ListDataListener, ChangeListener {

    protected final DefaultComboBoxModel<T> model;

    public ComboItemListener(ActionW action, T[] objects) {
        super(action);
        model = (objects != null) ? new DefaultComboBoxModel<>(objects) : new DefaultComboBoxModel<>();
        model.addListDataListener(this);
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
        Object val = model.getSelectedItem();
        itemStateChanged(val);
        AuditLog.LOGGER.info("action:{} val:{}", action.cmd(), val); //$NON-NLS-1$
    }

    @Override
    public void intervalAdded(ListDataEvent e) {
        // Do nothing
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
        // Do nothing
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
        // Do nothing
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

    @Override
    public boolean registerActionState(Object c) {
        if (super.registerActionState(c)) {
            if (c instanceof ComboBoxModelAdapter) {
                ((ComboBoxModelAdapter<T>) c).setModel(model);
            }
            return true;
        }
        return false;
    }

    @Override
    public void unregisterActionState(Object c) {
        super.unregisterActionState(c);
        if (c instanceof ComboBoxModelAdapter) {
            ((ComboBoxModelAdapter<T>) c).setModel(new DefaultComboBoxModel<T>());
        }
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

    public synchronized void setDataListWithoutTriggerAction(T[] objects) {
        setDataList(objects, false);
    }

    public synchronized void setDataList(T[] objects) {
        setDataList(objects, true);
    }

    private boolean isDataEquals(Object[] objects) {
        if (model.getSize() != objects.length) {
            return false;
        }
        Object[] data = new Object[objects.length];
        for (int i = 0; i < data.length; i++) {
            data[i] = model.getElementAt(i);
        }
        return Arrays.equals(data, objects);
    }

    protected synchronized void setDataList(T[] objects, boolean doTriggerAction) {
        if (isDataEquals(objects)) {
            // Do not update model and menu model,
            return;
        }
        Object oldSelection = model.getSelectedItem();
        model.removeListDataListener(this);
        model.removeAllElements();
        if (objects != null && objects.length > 0) {
            boolean oldSelectionStillExist = false;

            for (T object : objects) {
                model.addElement(object);
                if (object.equals(oldSelection)) {
                    oldSelectionStillExist = true;
                }
            }

            for (Object c : components) {
                if (c instanceof ComboBoxModelAdapter) {
                    ((ComboBoxModelAdapter<T>) c).setModel(model);
                }
            }
            model.setSelectedItem(null);

            if (doTriggerAction) {
                model.addListDataListener(this);
            }

            if (oldSelection != null && oldSelectionStillExist) {
                model.setSelectedItem(oldSelection);
            } else {
                model.setSelectedItem(objects[0]);
            }

            if (!doTriggerAction) {
                model.addListDataListener(this);
            }
        }
    }

    public DefaultComboBoxModel<T> getModel() {
        return model;
    }

    public JToogleButtonGroup<T> createButtonGroup() {
        final JToogleButtonGroup<T> group = new JToogleButtonGroup<>();
        registerActionState(group);
        return group;
    }

    public JComboBox<T> createCombo(int width) {
        final ComboItems combo = new ComboItems();
        registerActionState(combo);
        JMVUtils.setPreferredWidth(combo, width, width);
        // Update UI before adding the Tooltip feature in the combobox list
        combo.updateUI();
        JMVUtils.addTooltipToComboList(combo);
        return combo;
    }

    public JMenu createUnregisteredRadioMenu(String title) {
        GroupRadioMenu<T> radioMenu = new GroupRadioMenu<>();
        radioMenu.setModel(model);
        JMenu menu = radioMenu.createMenu(title);
        if (!enabled) {
            menu.setEnabled(false);
        }
        return menu;
    }

    public GroupPopup createGroupRadioMenu() {
        GroupRadioMenu<T> radioMenu = new GroupRadioMenu<>();
        radioMenu.setModel(model);
        registerActionState(radioMenu);
        return radioMenu;
    }

    public GroupPopup createUnregisteredGroupRadioMenu() {
        GroupRadioMenu<T> radioMenu = new GroupRadioMenu<>();
        radioMenu.setModel(model);
        return radioMenu;
    }

    // Trick to wrap JComboBox in the same interface as the GroupRadioMenu
    @SuppressWarnings("serial")
    class ComboItems extends JComboBox<T> implements ComboBoxModelAdapter<T> {
    }
}
