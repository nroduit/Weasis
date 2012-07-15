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

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

public abstract class DropDownButton extends JButton implements PopupMenuListener, ActionListener {

    private final String type;
    private final GroupRadioMenu menuModel;

    public DropDownButton(String type, Icon icon, GroupRadioMenu model) {
        super(icon);
        this.menuModel = model;
        this.type = type;
        init();
    }

    public DropDownButton(String type, Icon icon) {
        this(type, icon, (GroupRadioMenu) null);
    }

    public DropDownButton(String type, String text) {
        super(new DropDownLabel(text));
        this.type = type;
        this.menuModel = null;
        init();
    }

    private void init() {
        addActionListener(this);
        // arrowButton.setMargin(new Insets(3, 0, 3, 0));
    }

    public GroupRadioMenu getMenuModel() {
        return menuModel;
    }

    @Override
    public void setLabel(String label) {
        Icon icon = this.getIcon();
        if (icon instanceof DropDownLabel) {
            DropDownLabel iconLabel = (DropDownLabel) icon;
            iconLabel.setLabel(label);
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
        // remove parent of the radiobutton in actionPeformed() item.getParent()
        // ((JPopupMenu) e.getSource()).removeAll();
    }

    @Override
    public void popupMenuCanceled(PopupMenuEvent e) {
    }

    public String getType() {
        return type;
    }

    protected abstract JPopupMenu getPopupMenu();

}
