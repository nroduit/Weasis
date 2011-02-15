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

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

public class GraphicLabel {

    protected String[] label;
    protected Rectangle2D labelBound;

    public GraphicLabel() {

    }

    public void paint(Graphics2D g2d, Font font) {
        if (!label.equals(this.label)) {
            FontRenderContext frc = g2d.getFontRenderContext();
            // TextLayout tl = new TextLayout(graphicLabel, font, frc);
            // labelBound = tl.getBounds();
        }
    }

    public String[] getLabel() {
        return label;
    }

    public void setLabel(String label) {
        if (label == null || label.equals("")) { //$NON-NLS-1$
            labelBound = null;
            this.label = null;
        } else if (!label.equals(this.label)) {
            // FontRenderContext frc = g2d.getFontRenderContext();
            // TextLayout tl = new TextLayout(graphicLabel, font, frc);
            // labelBound = tl.getBounds();
            // this.label = graphicLabel;
        }
    }

    public void setLabelBound(double x, double y, double width, double height) {
        if (labelBound == null) {
            labelBound = new Rectangle2D.Double(x, y, width, height);
        } else {
            labelBound.setRect(x, y, width, height);
        }
    }

    public double getOffsetX() {
        return 3.0;
    }

    public double getOffsetY() {
        if (labelBound == null) {
            return 0;
        }
        return -10;
    }

    public Rectangle2D getLabelBound() {
        return labelBound == null ? null : labelBound.getBounds2D();
    }

    public Rectangle getBound() {
        return labelBound == null ? null : labelBound.getBounds2D().getBounds();
    }
}
