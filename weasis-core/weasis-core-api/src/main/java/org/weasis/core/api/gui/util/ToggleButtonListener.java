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

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JToggleButton.ToggleButtonModel;

public abstract class ToggleButtonListener implements ActionListener, ActionState {

    protected final ArrayList<AbstractButton> itemList;
    protected ButtonModel model;
    protected final ActionW action;

    public ToggleButtonListener(ActionW action, boolean selected) {
        this.action = action;
        this.itemList = new ArrayList<AbstractButton>();
        model = new ToggleButtonModel();
        model.setSelected(selected);
        model.addActionListener(this);
    }

    public void enableAction(boolean enabled) {
        model.setEnabled(enabled);
    }

    @Override
    public String toString() {
        return action.getTitle();
    }

    public ActionW getActionW() {
        return action;
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof ButtonModel) {
            actionPerformed(model.isSelected());
        }
    };

    public abstract void actionPerformed(boolean selected);

    public synchronized void setSelectedWithoutTriggerAction(boolean selected) {
        model.setSelected(selected);
    }

    public synchronized void setSelected(boolean selected) {
        boolean oldVal = model.isSelected();
        if (oldVal != selected) {
            model.setSelected(selected);
            actionPerformed(selected);
        }
    }

    public boolean isSelected() {
        return model.isSelected();
    }

    /**
     * Register a component and add at the same the ItemListener
     * 
     * @param slider
     */
    public void registerComponent(AbstractButton component) {
        if (!itemList.contains(component)) {
            itemList.add(component);
            component.setModel(model);
        }
    }

    public void unregisterComponent(JComponent component) {
        itemList.remove(component);
    }

    public JCheckBoxMenuItem createUnregiteredJCheckBoxMenuItem(String text) {
        final JCheckBoxMenuItem checkBoxItem = new JCheckBoxMenuItem(text, null, model.isSelected());
        checkBoxItem.setModel(model);
        return checkBoxItem;
    }

    public JCheckBox createCheckBox(String title) {
        final JCheckBox check = new JCheckBox(title);
        registerComponent(check);
        return check;
    }

    public JCheckBoxMenuItem createMenu(String title) {
        final JCheckBoxMenuItem menu = new JCheckBoxMenuItem(title);
        registerComponent(menu);
        return menu;
    }
}
