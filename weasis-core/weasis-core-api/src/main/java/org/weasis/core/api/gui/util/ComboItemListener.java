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

import java.util.Arrays;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.weasis.core.api.service.AuditLog;

public abstract class ComboItemListener extends BasicActionState implements ListDataListener, ChangeListener {

    protected final DefaultComboBoxModel model;

    public ComboItemListener(ActionW action, Object[] objects) {
        super(action);
        model = (objects != null) ? new DefaultComboBoxModel(objects) : new DefaultComboBoxModel();
        model.addListDataListener(this);
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
        // if (model.equals(e.getSource())) {
        Object val = model.getSelectedItem();
        itemStateChanged(val);
        AuditLog.LOGGER.info("action:{} val:{}", action.cmd(), val); //$NON-NLS-1$
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
                ((ComboBoxModelAdapter) c).setModel(model);
            }
            return true;
        }
        return false;
    }

    @Override
    public void unregisterActionState(Object c) {
        super.unregisterActionState(c);
        if (c instanceof ComboBoxModelAdapter) {
            ((ComboBoxModelAdapter) c).setModel(new DefaultComboBoxModel());
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

    public synchronized void setDataListWithoutTriggerAction(Object[] objects) {
        setDataList(objects, false);
    }

    public synchronized void setDataList(Object[] objects) {
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

    protected synchronized void setDataList(Object[] objects, boolean doTriggerAction) {
        if (isDataEquals(objects)) {
            // Do not update model and menu model,
            return;
        }
        Object oldSelection = model.getSelectedItem();
        model.removeListDataListener(this);
        model.removeAllElements();
        if (objects != null && objects.length > 0) {
            boolean oldSelectionStillExist = false;

            for (Object object : objects) {
                model.addElement(object);
                if (object.equals(oldSelection)) {
                    oldSelectionStillExist = true;
                }
            }

            for (Object c : components) {
                if (c instanceof ComboBoxModelAdapter) {
                    ((ComboBoxModelAdapter) c).setModel(model);
                }
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

    public DefaultComboBoxModel getModel() {
        return model;
    }

    public JToogleButtonGroup createButtonGroup() {
        final JToogleButtonGroup group = new JToogleButtonGroup();
        registerActionState(group);
        return group;
    }

    public JComboBox createCombo(int width) {
        final ComboItems combo = new ComboItems();
        registerActionState(combo);
        JMVUtils.setPreferredWidth(combo, width, width);
        // Update UI before adding the Tooltip feature in the combobox list
        combo.updateUI();
        JMVUtils.addTooltipToComboList(combo);
        return combo;
    }

    public JMenu createUnregisteredRadioMenu(String title) {
        GroupRadioMenu radioMenu = new GroupRadioMenu();
        radioMenu.setModel(model);
        JMenu menu = radioMenu.createMenu(title);
        if (!enabled) {
            menu.setEnabled(false);
        }
        return menu;
    }

    public GroupRadioMenu createGroupRadioMenu() {
        GroupRadioMenu radioMenu = new GroupRadioMenu();
        radioMenu.setModel(model);
        registerActionState(radioMenu);
        return radioMenu;
    }

    public GroupRadioMenu createUnregisteredGroupRadioMenu() {
        GroupRadioMenu radioMenu = new GroupRadioMenu();
        radioMenu.setModel(model);
        return radioMenu;
    }

    // Trick to wrap JComboBox in the same interface as the GroupRadioMenu
    static class ComboItems extends JComboBox implements ComboBoxModelAdapter {
    }
}
