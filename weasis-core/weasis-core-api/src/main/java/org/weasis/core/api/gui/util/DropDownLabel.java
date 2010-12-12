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

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.Icon;

public class DropDownLabel implements Icon {

    private String label;
    private int iconWidth = 30;
    private int iconHeight = 25;

    public DropDownLabel(String label) {
        this.label = label;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setPaint(Color.black);
        iconHeight = g2d.getFontMetrics().getHeight() + 1;
        iconWidth = g2d.getFontMetrics().stringWidth(label) + 1;
        int baseText = g2d.getFontMetrics().getAscent();
        g2d.drawString(label, x, y + baseText);
        int shiftx = x + iconWidth + 1;
        int shifty = y + baseText - 5;
        int[] xPoints = { shiftx, shiftx + 8, shiftx + 4 };
        int[] yPoints = { shifty, shifty, shifty + 4 };
        // g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.fillPolygon(xPoints, yPoints, xPoints.length);
        // g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);

    }

    public int getIconWidth() {
        return iconWidth + 10;
    }

    public int getIconHeight() {
        return iconHeight;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label; //$NON-NLS-1$
    }

}
