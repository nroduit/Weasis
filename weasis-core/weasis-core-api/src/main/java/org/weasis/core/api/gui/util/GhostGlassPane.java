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

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.Icon;
import javax.swing.JComponent;

@SuppressWarnings("serial")
public class GhostGlassPane extends JComponent {

    private static final AlphaComposite composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
    private transient Image dragged = null;
    private Point location = null;
    private transient Icon draggedIcon = null;

    public GhostGlassPane() {
        setBorder(null);
    }

    public void setImage(Image dragged) {
        this.dragged = dragged;
    }

    public void setIcon(Icon dragged) {
        this.draggedIcon = dragged;
    }

    public void setImagePosition(Point location) {
        Point oldLocation = this.location == null ? location : this.location;
        this.location = location;
        int width = dragged == null ? draggedIcon.getIconWidth() + 1 : dragged.getWidth(this) + 1;
        int height = dragged == null ? draggedIcon.getIconHeight() + 1 : dragged.getHeight(this) + 1;

        if (location == null) {
            if (oldLocation == null) {
                repaint();
            } else {
                repaint(new Rectangle(oldLocation.x, oldLocation.y, width, height));
            }
        } else {
            Rectangle newClip = new Rectangle(location.x, location.y, width, height);
            if (oldLocation != null) {
                newClip.add(new Rectangle(oldLocation.x, oldLocation.y, width, height));
            }
            repaint(newClip);
        }
    }

    @Override
    public void paintComponent(Graphics g) {

        if (location == null || (dragged == null && draggedIcon == null)) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g;
        Composite oldComposite = g2.getComposite();
        g2.setComposite(composite);
        if (dragged == null) {
            draggedIcon.paintIcon(this, g2, location.x, location.y);
        } else {
            g2.drawImage(dragged, location.x, location.y, null);
        }
        g2.setComposite(oldComposite);
    }
}
