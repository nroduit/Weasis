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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;

import javax.swing.Icon;

/**
 * <ul>
 * <li>DOWN - rotated 90 degrees
 * <li>UP (default) - rotated -90 degrees
 * <li>UPSIDE_DOWN - rotated 180 degrees
 * <li>ABOUT_CENTER - a specific angle about its center
 * </ul>
 */
public class RotatedIcon implements Icon {
    public enum Rotate {
        DOWN, UP, UPSIDE_DOWN, ABOUT_CENTER;
    }

    private Icon icon;
    private Rotate rotate;
    private double angle;

    public RotatedIcon(Icon icon) {
        this(icon, Rotate.UP);
    }

    public RotatedIcon(Icon icon, Rotate rotate) {
        this.icon = icon;
        this.rotate = rotate;
    }

    public RotatedIcon(Icon icon, double angle) {
        this(icon, Rotate.ABOUT_CENTER);
        this.angle = angle;
    }

    public Icon getIcon() {
        return icon;
    }

    public Rotate getRotate() {
        return rotate;
    }

    public double getAngle() {
        return angle;
    }

    @Override
    public int getIconWidth() {
        if (rotate == Rotate.ABOUT_CENTER) {
            double radians = Math.toRadians(angle);
            double sin = Math.abs(Math.sin(radians));
            double cos = Math.abs(Math.cos(radians));
            return (int) Math.floor(icon.getIconWidth() * cos + icon.getIconHeight() * sin);
        } else if (rotate == Rotate.UPSIDE_DOWN) {
            return icon.getIconWidth();
        } else {
            return icon.getIconHeight();
        }
    }

    @Override
    public int getIconHeight() {
        if (rotate == Rotate.ABOUT_CENTER) {
            double radians = Math.toRadians(angle);
            double sin = Math.abs(Math.sin(radians));
            double cos = Math.abs(Math.cos(radians));
            return (int) Math.floor(icon.getIconHeight() * cos + icon.getIconWidth() * sin);
        } else if (rotate == Rotate.UPSIDE_DOWN) {
            return icon.getIconHeight();
        } else {
            return icon.getIconWidth();
        }
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();

        int cWidth = icon.getIconWidth() / 2;
        int cHeight = icon.getIconHeight() / 2;
        int xAdjustment = (icon.getIconWidth() % 2) == 0 ? 0 : -1;
        int yAdjustment = (icon.getIconHeight() % 2) == 0 ? 0 : -1;

        if (rotate == Rotate.DOWN) {
            g2.translate(x + cHeight, y + cWidth);
            g2.rotate(Math.toRadians(90));
            icon.paintIcon(c, g2, -cWidth, yAdjustment - cHeight);
        } else if (rotate == Rotate.UP) {
            g2.translate(x + cHeight, y + cWidth);
            g2.rotate(Math.toRadians(-90));
            icon.paintIcon(c, g2, xAdjustment - cWidth, -cHeight);
        } else if (rotate == Rotate.UPSIDE_DOWN) {
            g2.translate(x + cWidth, y + cHeight);
            g2.rotate(Math.toRadians(180));
            icon.paintIcon(c, g2, xAdjustment - cWidth, yAdjustment - cHeight);
        } else if (rotate == Rotate.ABOUT_CENTER) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            AffineTransform original = g2.getTransform();
            AffineTransform at = new AffineTransform();
            at.concatenate(original);
            at.translate((getIconWidth() - icon.getIconWidth()) / 2.0, (getIconHeight() - icon.getIconHeight()) / 2.0);
            at.rotate(Math.toRadians(angle), x + icon.getIconWidth() / 2.0, y + icon.getIconHeight() / 2.0);
            g2.setTransform(at);
            icon.paintIcon(c, g2, x, y);
            g2.setTransform(original);
        }
    }
}