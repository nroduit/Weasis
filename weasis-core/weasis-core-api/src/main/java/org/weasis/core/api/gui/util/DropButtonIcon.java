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

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.UIManager;

public class DropButtonIcon implements Icon {

    private Icon leftIcon;

    public DropButtonIcon(Icon leftIcon) {
        this.leftIcon = leftIcon;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g;
        leftIcon.paintIcon(c, g2d, x, y);
        if (c instanceof DropDownButton) {
            ButtonModel model = ((DropDownButton) c).getModel();
            if (model.isRollover() && !model.isPressed()) {
                g2d.setPaint(UIManager.getColor("controlShadow")); //$NON-NLS-1$
            } else {
                g2d.setPaint(UIManager.getColor("controlHighlight")); //$NON-NLS-1$
            }
        }
        int shiftx = x + leftIcon.getIconWidth() + 1;
        int shifty = y + leftIcon.getIconHeight() - 5;
        int[] xPoints = { shiftx, shiftx + 8, shiftx + 4 };
        int[] yPoints = { shifty, shifty, shifty + 4 };
        // g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.fillPolygon(xPoints, yPoints, xPoints.length);
        // g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);

    }

    public int getIconWidth() {
        return leftIcon.getIconWidth() + 10;
    }

    public int getIconHeight() {
        return leftIcon.getIconHeight();
    }

}
