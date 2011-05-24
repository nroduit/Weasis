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
package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import org.weasis.core.ui.editor.image.DefaultView2d;

public class GraphicLabel {

    protected String[] labels;
    protected Rectangle labelBound;
    protected int labelHeight;

    public GraphicLabel() {
    }

    public String[] getLabel() {
        return labels;
    }

    public void setLabel(String[] labels, DefaultView2d view2d) {

        if (labels == null || labels.length == 0 || view2d == null) {
            labelHeight = 0;
            labelBound = null;
            this.labels = null;
        } else {
            this.labels = labels;
            Font defaultFont = view2d.getEventManager().getViewSetting().getFont();
            Rectangle longestBound = null;
            for (String label : labels) {
                Rectangle bound =
                    defaultFont.getStringBounds(label, ((Graphics2D) view2d.getGraphics()).getFontRenderContext())
                        .getBounds();
                if (longestBound == null || bound.getWidth() > longestBound.getWidth()) {
                    longestBound = bound;
                }
            }
            labelHeight = longestBound.height;
            setLabelBound(0, 0, longestBound.getWidth() + 6, labelHeight * labels.length + 6);
        }
    }

    public void setLabelPosition(int posX, int posY) {
        if (labelBound != null) {
            labelBound.setLocation(posX, posY);
        }
    }

    @Deprecated
    public void setLabelBound(double x, double y, double width, double height) {
        if (labelBound == null) {
            labelBound = new Rectangle.Double(x, y, width, height).getBounds();
        } else {
            labelBound.setRect(x, y, width, height);
        }
    }

    public double getOffsetX() {
        return 3.0;
    }

    public double getOffsetY() {
        if (labelBound == null)
            return 0;
        return -10;
    }

    public Rectangle2D getLabelBound() {
        return labelBound == null ? null : labelBound.getBounds2D();
    }

    public Rectangle getBound() {
        return labelBound == null ? null : labelBound.getBounds2D().getBounds();
    }

    public void paint(Graphics2D g2d, AffineTransform transform) {
        if (labelBound != null && labels != null) {
            Point pt = new Point(labelBound.x, labelBound.y);
            transform.transform(pt, pt);
            pt.x += getOffsetX();
            pt.y += getOffsetY();
            for (int i = 0; i < labels.length; i++) {
                paintFontOutline(g2d, labels[i], (pt.x + 3), (pt.y + labelHeight * (i + 1)));
            }
        }
    }

    protected void paintFontOutline(Graphics2D g2, String str, float x, float y) {
        g2.setPaint(Color.BLACK);
        g2.drawString(str, x - 1f, y - 1f);
        g2.drawString(str, x - 1f, y);
        g2.drawString(str, x - 1f, y + 1f);
        g2.drawString(str, x, y - 1f);
        g2.drawString(str, x, y + 1f);
        g2.drawString(str, x + 1f, y - 1f);
        g2.drawString(str, x + 1f, y);
        g2.drawString(str, x + 1f, y + 1f);
        g2.setPaint(Color.WHITE);
        g2.drawString(str, x, y);
    }

}
