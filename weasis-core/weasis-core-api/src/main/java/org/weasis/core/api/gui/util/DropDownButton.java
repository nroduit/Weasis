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

    public DropDownButton(String type, Icon icon) {
        super(icon);
        this.type = type;
        init();
    }

    public DropDownButton(String type, String text) {
        super(new DropDownLabel(text));
        this.type = type;
        init();
    }

    private void init() {
        addActionListener(this);
        // arrowButton.setMargin(new Insets(3, 0, 3, 0));
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

    public void actionPerformed(ActionEvent ae) {
        JPopupMenu popup = getPopupMenu();
        popup.addPopupMenuListener(this);
        popup.show(this, 0, getHeight());
    }

    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        getModel().setRollover(true);
        getModel().setSelected(true);
    }

    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        getModel().setRollover(false);
        getModel().setSelected(false);
        ((JPopupMenu) e.getSource()).removePopupMenuListener(this);
        ((JPopupMenu) e.getSource()).removeAll();
    }

    public void popupMenuCanceled(PopupMenuEvent e) {
    }

    public String getType() {
        return type;
    }

    protected abstract JPopupMenu getPopupMenu();

}
