/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.util;

import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.JLabel;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.border.EmptyBorder;

/**
 * Title menu item for JPopupMenu
 *
 * The area of title allows to release the click without hiding the popup.
 *
 */
@SuppressWarnings("serial")
public class TitleMenuItem extends JLabel implements MenuElement {

    public TitleMenuItem(String title, Insets insets) {
        Font f = getFont();
        if (f != null) {
            setFont(new Font(f.getName(), Font.BOLD, f.getSize()));
        }
        if (insets != null) {
            setBorder(new EmptyBorder(insets));
        }
        setText(title);
    }

    @Override
    public void processMouseEvent(MouseEvent e, MenuElement[] path, MenuSelectionManager manager) {
        // Do nothing
    }

    @Override
    public void processKeyEvent(KeyEvent e, MenuElement[] path, MenuSelectionManager manager) {
        // Do nothing
    }

    @Override
    public void menuSelectionChanged(boolean isIncluded) {
        // Do nothing
    }

    @Override
    public MenuElement[] getSubElements() {
        return new MenuElement[0];
    }

    @Override
    public Component getComponent() {
        return this;
    }

}