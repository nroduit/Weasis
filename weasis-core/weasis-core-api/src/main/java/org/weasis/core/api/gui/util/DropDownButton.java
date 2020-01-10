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

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

@SuppressWarnings("serial")
public abstract class DropDownButton extends JButton implements PopupMenuListener, ActionListener {

    private final String type;
    private final GroupPopup menuModel;

    public DropDownButton(String type, Icon icon, GroupPopup model) {
        super(icon);
        this.menuModel = model;
        this.type = type;
        init();
    }

    public DropDownButton(String type, Icon icon) {
        this(type, icon, (GroupPopup) null);
    }

    public DropDownButton(String type, String text, JComponent parent) {
        super(new DropDownLabel(text, parent));
        this.type = type;
        this.menuModel = null;
        init();
    }

    private void init() {
        addActionListener(this);
    }

    public GroupPopup getMenuModel() {
        return menuModel;
    }

    @Override
    public void setLabel(String label) {
        Icon icon = this.getIcon();
        if (icon instanceof DropDownLabel) {
            DropDownLabel iconLabel = (DropDownLabel) icon;
            iconLabel.setLabel(label, this);
            Insets insets = getInsets();
            iconLabel.paintIcon(this, getGraphics(), insets.left, insets.top);
            revalidate();
            repaint();
        }
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        JPopupMenu popup = getPopupMenu();
        popup.addPopupMenuListener(this);
        popup.show(this, 0, getHeight());
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        getModel().setRollover(true);
        getModel().setSelected(true);
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        getModel().setRollover(false);
        getModel().setSelected(false);
        ((JPopupMenu) e.getSource()).removePopupMenuListener(this);
    }

    @Override
    public void popupMenuCanceled(PopupMenuEvent e) {
    }

    public String getType() {
        return type;
    }

    protected abstract JPopupMenu getPopupMenu();

}
