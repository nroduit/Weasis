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

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.ImageIcon;

public final class ImageSectionIcon implements AnimatedIconStatic {

    private final int width;
    private final int height;
    private final ImageIcon img;
    private final int lowEnd;
    private final int highEnd;
    private int num = 0;

    public ImageSectionIcon(ImageIcon img, int width, int height, int lowEnd, int highEnd) {
        this.width = width;
        this.height = height;
        this.img = img;
        this.num = lowEnd + 1;
        this.lowEnd = lowEnd;
        this.highEnd = highEnd;
    }

    @Override
    public String getName() {
        return "ImageSectionIcon"; //$NON-NLS-1$
    }

    @Override
    public int getIconWidth() {
        return this.width;
    }

    @Override
    public int getIconHeight() {
        return this.height;
    }

    @Override
    public void animate() {
        if (++this.num == this.highEnd) {
            this.num = this.lowEnd + 1;
        }
    }

    @Override
    public void reset() {
        this.num = this.lowEnd;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        int dwidth = img.getIconWidth() / width;
        int nx = (this.num % dwidth) * this.width;
        int ny = (this.num / dwidth) * this.height;
        g.drawImage(this.img.getImage(), x, y, x + this.width, y + this.height, nx, ny, nx + this.width,
            ny + this.height, c);
    }
}
