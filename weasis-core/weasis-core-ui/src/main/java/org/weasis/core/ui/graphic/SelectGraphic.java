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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.ui.Messages;

/**
 * The Class SelectGraphic.
 * 
 * @author Nicolas Roduit
 */
public class SelectGraphic extends RectangleGraphic {
    public static final Icon ICON = new ImageIcon(SelectGraphic.class.getResource("/icon/22x22/draw-selection.png")); //$NON-NLS-1$

    /**
     * The Class SelectedDragSequence.
     * 
     * @author Nicolas Roduit
     */
    protected class SelectedDragSequence extends AbstractDragGraphic.DefaultDragSequence {

        @Override
        public boolean completeDrag(MouseEvent mouseevent) {
            // updateSelection();
            fireRemoveAndRepaintAction();
            // getLayer().removeGraphicAndRepaint(SelectGraphic.this);
            return true;
        }

        public SelectedDragSequence(int i) {
            super(true, i);
        }
    }

    @Override
    public void showProperties() {
    }

    public SelectGraphic() {
        super(1f, Color.white, false);
        showLabel = false;
    }

    @Override
    public void paint(Graphics2D g2d, AffineTransform transform) {
        Color oldColor = g2d.getColor();
        Stroke oldStroke = g2d.getStroke();
        g2d.setPaint(Color.white);
        float dash[] = { 5F };
        // Rectangle rectangle = getBounds();
        // affineTransform = transform;
        Shape transformedShape = transform == null ? shape : transform.createTransformedShape(shape);

        g2d.setStroke(new BasicStroke(1.0F, 0, 0, 5F, dash, 0));
        // boolean drawable = rectangle.width > 1 && rectangle.height > 1;
        g2d.draw(transformedShape);

        g2d.setColor(Color.black);
        g2d.setStroke(new BasicStroke(1.0F, 0, 0, 5F, dash, 5F));
        g2d.draw(transformedShape);

        g2d.setColor(oldColor);
        g2d.setStroke(oldStroke);
    }

    @Override
    public void paintHandles(Graphics2D graphics2d, AffineTransform transform) {
    }

    @Override
    protected int resizeOnDrawing(int i, int j, int k, MouseEvent mouseevent) {
        int l = super.resizeOnDrawing(i, j, k, mouseevent);
        return l;
    }

    // rend une nouvelle instance au lieu d'une selection du rectangle
    @Override
    protected DragSequence createResizeDrag(MouseEvent mouseevent, int i) {
        return new SelectedDragSequence(i);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.sel"); //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return null;
    }
}
