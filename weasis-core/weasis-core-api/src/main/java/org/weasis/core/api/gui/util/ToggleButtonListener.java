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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JToggleButton.ToggleButtonModel;

import org.weasis.core.api.service.AuditLog;

public abstract class ToggleButtonListener extends BasicActionState implements ActionListener {

    protected ButtonModel model;

    public ToggleButtonListener(ActionW action, boolean selected) {
        super(action);
        model = new ToggleButtonModel();
        model.setSelected(selected);
        model.addActionListener(this);
    }

    @Override
    public void enableAction(boolean enabled) {
        model.setEnabled(enabled);
    }

    @Override
    public String toString() {
        return action.getTitle();
    }

    @Override
    public ActionW getActionW() {
        return action;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof ButtonModel) {
            actionPerformed(model.isSelected());
            AuditLog.LOGGER.info("action:{} val:{}", action.cmd(), model.isSelected()); //$NON-NLS-1$
        }
    }

    public abstract void actionPerformed(boolean selected);

    public synchronized void setSelectedWithoutTriggerAction(boolean selected) {
        model.setSelected(selected);
    }

    public synchronized void setSelected(boolean selected) {
        boolean oldVal = model.isSelected();
        if (oldVal != selected) {
            model.setSelected(selected);
            actionPerformed(selected);
            AuditLog.LOGGER.info("action:{} val:{}", action.cmd(), model.isSelected()); //$NON-NLS-1$
        }
    }

    public synchronized boolean isSelected() {
        return model.isSelected();
    }

    @Override
    public boolean registerActionState(Object c) {
        if (super.registerActionState(c)) {
            if (c instanceof AbstractButton) {
                ((AbstractButton) c).setModel(model);
            }
            return true;
        }
        return false;
    }

    public JCheckBoxMenuItem createUnregiteredJCheckBoxMenuItem(String text) {
        final JCheckBoxMenuItem checkBoxItem = new JCheckBoxMenuItem(text, null, model.isSelected());
        checkBoxItem.setModel(model);
        return checkBoxItem;
    }

    public JCheckBox createCheckBox(String title) {
        final JCheckBox check = new JCheckBox(title);
        registerActionState(check);
        return check;
    }

    public JCheckBoxMenuItem createMenu(String title) {
        final JCheckBoxMenuItem menu = new JCheckBoxMenuItem(title);
        registerActionState(menu);
        return menu;
    }
}
