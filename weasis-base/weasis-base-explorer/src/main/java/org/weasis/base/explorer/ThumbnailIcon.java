package org.weasis.base.explorer;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.Icon;

/**
 * 
 * @author rem
 */
public final class ThumbnailIcon implements Icon {

    private final BufferedImage image;

    /** Creates a new instance of ThumbnailIcon */
    public ThumbnailIcon(final BufferedImage image) {
        this.image = image;
    }

    public final void paintIcon(final Component c, final Graphics g, final int x, final int y) {
        g.drawImage(this.image, x, y, c);
    }

    public final int getIconWidth() {
        return this.image.getWidth();
    }

    public final int getIconHeight() {
        return this.image.getHeight();
    }

    public final BufferedImage getImage() {
        return this.image;
    }
}