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
import java.awt.Image;

import javax.swing.ImageIcon;

import org.weasis.core.api.Messages;

public final class ImageSectionIcon implements AnimatedIconStatic {

    private final int dwidth;
    private final int dheight;
    private final int width;
    private final int height;
    private final Image img;
    private final int lowEnd;
    private final int highEnd;
    private int num = 0;

    public String getName() {
        return "ImageSectionIcon"; //$NON-NLS-1$
    }

    public ImageSectionIcon(ImageIcon img, int width, int height, int lowEnd, int highEnd) {
        this.width = width;
        this.height = height;
        this.dwidth = img.getIconWidth() / width;
        this.dheight = img.getIconHeight() / height;
        this.img = img.getImage();
        this.num = lowEnd + 1;
        this.lowEnd = lowEnd;
        this.highEnd = highEnd;
    }

    public int getIconWidth() {
        return this.width;
    }

    public int getIconHeight() {
        return this.height;
    }

    public void animate() {
        if (++this.num == this.highEnd) {
            this.num = this.lowEnd + 1;
        }
    }

    public void reset() {
        this.num = this.lowEnd;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
        int nx = (this.num % this.dwidth) * this.width;
        int ny = (this.num / this.dwidth) * this.height;
        g.drawImage(this.img, x, y, x + this.width, y + this.height, nx, ny, nx + this.width, ny + this.height, c);
    }
}
