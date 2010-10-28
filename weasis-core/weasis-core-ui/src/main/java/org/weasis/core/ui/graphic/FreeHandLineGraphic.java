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
import java.awt.geom.AffineTransform;

/**
 * The Class FreeHandLineGraphic.
 * 
 * @author Nicolas Roduit
 */
public class FreeHandLineGraphic extends FreeHandGraphic {

    private static final long serialVersionUID = -4598193427257672035L;

    public FreeHandLineGraphic(float lineThickness, Color paint) {
        super(lineThickness, paint, false, true);
    }

    // dessine le handle de sélection
    @Override
    public void paintHandles(Graphics2D g2d, AffineTransform transform) {
        if (closed) {
            g2d.setPaint(Color.white);
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.draw(getShape());
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(2.0f, 0, 0, 3F, new float[] { 3F }, 0));
            g2d.draw(getShape());
        }
    }

    public Rectangle getRepaintBounds(Shape shape1) {
        if (shape1 == null) {
            return null;
        } else {
            Rectangle rectangle = shape1.getBounds();
            int i = (int) (lineThickness / 2.0f) + 1;
            rectangle.width += 2 * i + 2;
            rectangle.height += 2 * i + 2;
            rectangle.x -= i - 1;
            rectangle.y -= i - 1;
            return rectangle;
        }
    }

    public float[] getPoints() {
        return points;
    }

    @Override
    public Rectangle getRepaintBounds() {
        // renvoie la dimension du dessin + le handle de sélection
        Shape shape1 = getShape();
        if (shape1 == null) {
            return null;
        }
        return getRepaintBounds(shape1);
    }

    // @Override
    // public void repaint() {
    // AbstractLayer layer1 = getLayer();
    // if (layer1 != null) {
    // layer1.repaint(getRepaintBounds());
    // }
    // }
}
