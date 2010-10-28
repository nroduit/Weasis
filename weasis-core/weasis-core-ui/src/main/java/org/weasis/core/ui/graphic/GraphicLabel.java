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
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;

import org.weasis.core.ui.Messages;

public class GraphicLabel {

    protected Color filledColor;
    protected String label;
    protected Rectangle2D labelBound;

    public GraphicLabel() {

    }

    public void paint(Graphics2D g2d, Font font) {
        if (!label.equals(this.label)) {
            FontRenderContext frc = g2d.getFontRenderContext();
            TextLayout tl = new TextLayout(label, font, frc);
            labelBound = tl.getBounds();
        }
    }

    public Color getFilledColor() {
        return filledColor;
    }

    public void setFilledColor(Color filledColor) {
        this.filledColor = filledColor;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        if (label == null || label.equals("")) { //$NON-NLS-1$
            labelBound = null;
            this.label = null;
        } else if (!label.equals(this.label)) {
            // FontRenderContext frc = g2d.getFontRenderContext();
            // TextLayout tl = new TextLayout(label, font, frc);
            // labelBound = tl.getBounds();
            // this.label = label;
        }
    }

    public Rectangle2D getLabelBound() {
        return labelBound;
    }

}
