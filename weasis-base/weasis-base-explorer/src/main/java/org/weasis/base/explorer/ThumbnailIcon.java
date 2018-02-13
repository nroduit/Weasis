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
package org.weasis.base.explorer;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.Icon;

public final class ThumbnailIcon implements Icon {

    private final BufferedImage image;

    public ThumbnailIcon(final BufferedImage image) {
        this.image = image;
    }

    @Override
    public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
        g.drawImage(this.image, x, y, c);
    }

    @Override
    public int getIconWidth() {
        return this.image.getWidth();
    }

    @Override
    public int getIconHeight() {
        return this.image.getHeight();
    }

    public BufferedImage getImage() {
        return this.image;
    }
}