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
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class SelectGraphic.
 * 
 * @author Nicolas Roduit
 */
// public class SelectGraphic extends RectangleGraphic {
public class SelectGraphic extends RectangleGraphic {

    public static final Icon ICON = new ImageIcon(SelectGraphic.class.getResource("/icon/22x22/draw-selection.png")); //$NON-NLS-1$

    public SelectGraphic() {
        this(1.0f, Color.WHITE); // called in AbstractLayerModel, usefull ?????
    }

    public SelectGraphic(float lineThickness, Color paint) {
        super(lineThickness, paint, false);
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
    public Rectangle getBounds(AffineTransform transform) {
        Rectangle bound = super.getBounds(transform);
        if (bound != null) {
            bound.grow(bound.width < 1 ? 1 : 0, bound.height < 1 ? 1 : 0); // tricks for single click when no draging
        }
        return bound;
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
    public DragSequence createResizeDrag(int i) {
        return new SelectedDragSequence();
    }

    protected class SelectedDragSequence extends AbstractDragGraphic.DefaultDragSequence {

        @Override
        public boolean completeDrag(MouseEventDouble mouseEvent) {
            fireRemoveAndRepaintAction();
            return true;
        }
    }

}
