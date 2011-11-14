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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;
import javax.swing.JComponent;

public class GhostGlassPane extends JComponent {

    private static final AlphaComposite composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
    private Image dragged = null;
    private Point iconPosition = null;
    private Icon draggedIcon = null;
    private String[] message = null;
    private Point messagePosition = null;
    private Rectangle messageBound = null;

    public GhostGlassPane() {
        setBorder(null);
    }

    public void setImage(Image dragged) {
        this.dragged = dragged;
    }

    public void setIcon(Icon dragged) {
        this.draggedIcon = dragged;
    }

    public void setMessage(String[] message, Point messagePosition) {
        this.message = message;
        Point oldLocation = this.messagePosition;
        this.messagePosition = messagePosition;
        if (messagePosition == null) {
            if (messageBound == null) {
                repaint();
            } else {
                repaint(messageBound);
            }
        } else {
            if (message != null && message.length > 0) {
                Graphics2D g2 = (Graphics2D) this.getGraphics();
                if (g2 != null) {
                    double maxWidth = 0;
                    for (String label : message) {
                        if (label.length() > 0) {
                            TextLayout layout = new TextLayout(label, g2.getFont(), g2.getFontRenderContext());
                            maxWidth = Math.max(layout.getBounds().getWidth(), maxWidth);
                        }
                    }
                    double labelHeight =
                        new TextLayout("Tg", g2.getFont(), g2.getFontRenderContext()).getBounds().getHeight() + 2; //$NON-NLS-1$
                    double labelWidth = maxWidth;

                    Rectangle2D labelBounds =
                        new Rectangle.Double(messagePosition.x, messagePosition.y, labelWidth,
                            (labelHeight * message.length));
                    GeomUtil.growRectangle(labelBounds, 5);
                    Rectangle2D newClip = labelBounds.getBounds2D();
                    if (messageBound != null) {
                        newClip.add(messageBound);
                    }
                    messageBound = newClip.getBounds();
                    repaint(newClip.getBounds());
                }
            }
        }
    }

    public void setImagePosition(Point location) {
        Point oldLocation = this.iconPosition == null ? location : this.iconPosition;
        this.iconPosition = location;
        int width = dragged == null ? draggedIcon.getIconWidth() + 1 : dragged.getWidth(this) + 1;
        int height = dragged == null ? draggedIcon.getIconHeight() + 1 : dragged.getHeight(this) + 1;

        if (location == null) {
            if (this.iconPosition == null) {
                repaint();
            } else {
                repaint(new Rectangle(oldLocation.x, oldLocation.y, width, height));
            }
        } else {
            Rectangle newClip = new Rectangle(location.x, location.y, width, height);
            newClip.add(new Rectangle(oldLocation.x, oldLocation.y, width, height));
            repaint(newClip);
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        Composite OldComposite = g2.getComposite();
        if (iconPosition != null && (dragged != null || draggedIcon != null)) {
            g2.setComposite(composite);
            if (dragged != null) {
                g2.drawImage(dragged, iconPosition.x, iconPosition.y, null);
            } else if (draggedIcon != null) {
                draggedIcon.paintIcon(this, g2, iconPosition.x, iconPosition.y);
            }
            g2.setComposite(OldComposite);
        }
        if (messagePosition != null && message != null) {
            g2.setComposite(composite);
            double labelHeight =
                new TextLayout("Tg", g2.getFont(), g2.getFontRenderContext()).getBounds().getHeight() + 2; //$NON-NLS-1$
            float drawY = messagePosition.y - 5.0f;
            if (messageBound != null) {
                g2.setColor(Color.LIGHT_GRAY);
                g2.fill(messageBound);
            }
            g2.setColor(Color.BLACK);
            for (int i = message.length - 1; i >= 0; i--) {
                g2.drawString(message[i], messagePosition.x, drawY);
                drawY -= labelHeight;
            }
            g2.setComposite(OldComposite);
        }
    }
}
