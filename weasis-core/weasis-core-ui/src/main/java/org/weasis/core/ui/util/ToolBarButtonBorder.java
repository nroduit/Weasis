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
package org.weasis.core.ui.util;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Insets;

import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.border.Border;

import org.weasis.core.ui.Messages;

public class ToolBarButtonBorder implements Border {

    private static Image borderImage =
        new ImageIcon(ToolBarButtonBorder.class.getResource("/icon/toolbar/buttonborder.png")).getImage(); //$NON-NLS-1$
    private static Image pressedBorderImage =
        new ImageIcon(ToolBarButtonBorder.class.getResource("/icon/toolbar/buttonborder_pressed.png")).getImage(); //$NON-NLS-1$

    private static int borderWidth = borderImage.getWidth(null);
    private static int borderHeight = borderImage.getHeight(null);
    private boolean pressed;
    private Insets insets = new Insets(2, 2, 2, 2);

    public ToolBarButtonBorder() {
    }

    public boolean isBorderOpaque() {
        return false;
    }

    public void paintBorder(Component component, Graphics graphics, int x, int y, int w, int h) {
        Image img = pressed ? pressedBorderImage : borderImage;
        graphics.drawImage(img, x + w - 5, y, x + w, y + 5, borderWidth - 5, 0, borderWidth, 5, null);
        graphics.drawImage(img, x + w - 5, y + 5, x + w, y + h - 5, borderWidth - 5, 5, borderWidth, borderHeight - 5,
            null);
        graphics.drawImage(img, x + w - 5, y + h - 5, x + w, y + h, borderWidth - 5, borderHeight - 5, borderWidth,
            borderHeight, null);

        graphics.drawImage(img, x + 5, y + h - 5, x + w - 5, y + h, 5, borderHeight - 5, borderWidth - 5, borderHeight,
            null);
        graphics.drawImage(img, x, y + h - 5, x + 5, y + h, 0, borderHeight - 5, 5, borderHeight, null);
        graphics.drawImage(img, x + 5, y, x + w - 5, y + 5, 5, 0, borderHeight - 5, 5, null);
        graphics.drawImage(img, x, y + 5, x + 5, y + h - 5, 0, 5, 5, borderHeight - 5, null);
        graphics.drawImage(img, x, y, x + 5, y + 5, 0, 0, 5, 5, null);

    }

    public Insets getBorderInsets(Component component) {
        if (component instanceof AbstractButton) {
            AbstractButton btn = (AbstractButton) component;
            Insets i = btn.getMargin();
            i.top += insets.top;
            i.left += insets.left;
            i.right += insets.right;
            i.bottom += insets.bottom;
            return i;
        } else {
            return insets;
        }
    }

    public boolean isPressed() {
        return pressed;
    }

    public void setPressed(boolean pressed) {
        this.pressed = pressed;
    }
}
