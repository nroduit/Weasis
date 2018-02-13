/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.gui.util;

import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.Icon;
import javax.swing.JComponent;

public class DropDownLabel implements Icon {

    private String label;
    private int iconWidth = 30;
    private int iconHeight = 25;

    public DropDownLabel(String label, JComponent parent) {
        setLabel(label, parent);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g;
        int baseText = g2d.getFontMetrics().getAscent();
        g2d.drawString(label, x, y + baseText);
        int shiftx = x + iconWidth + 1;
        int shifty = y + baseText - 5;
        int[] xPoints = { shiftx, shiftx + 8, shiftx + 4 };
        int[] yPoints = { shifty, shifty, shifty + 4 };
        g2d.fillPolygon(xPoints, yPoints, xPoints.length);
    }

    private void updateSize(JComponent parent) {
        if (parent != null) {
            FontMetrics fmetrics = parent.getFontMetrics(parent.getFont());
            iconHeight = fmetrics.getHeight() + 1;
            iconWidth = fmetrics.stringWidth(label) + 5;
        }
    }

    @Override
    public int getIconWidth() {
        return iconWidth + 10;
    }

    @Override
    public int getIconHeight() {
        return iconHeight;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label, JComponent parent) {
        this.label = label == null ? "" : label; //$NON-NLS-1$
        updateSize(parent);
    }

}
