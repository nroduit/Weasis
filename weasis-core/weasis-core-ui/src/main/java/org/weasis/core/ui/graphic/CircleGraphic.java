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
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.ui.Messages;

/**
 * The Class CircleGraphic.
 * 
 * @author Nicolas Roduit
 */
public class CircleGraphic extends RectangleGraphic {

    private static final long serialVersionUID = -436581811233324820L;
    public static final Icon ICON = new ImageIcon(CircleGraphic.class.getResource("/icon/22x22/draw-eclipse.png")); //$NON-NLS-1$

    public CircleGraphic(float lineThickness, Color paint, boolean fill) {
        super(lineThickness, paint, fill);
    }

    @Override
    public boolean intersects(Rectangle rectangle) {
        if (!super.intersects(rectangle)) {
            return getShape().intersects(rectangle);
        } else {
            return true;
        }
    }

    @Override
    protected double getGraphicArea(double scaleX, double scaleY) {
        return Math.PI * (width < 1 ? 1 : width) * scaleX * (height < 1 ? 1 : height) * scaleY / 4.0;
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseevent) {
        setShape(new java.awt.geom.Ellipse2D.Double(x, y, (width < 1 ? 1 : width), (height < 1 ? 1 : height)),
            mouseevent);
        updateLabel(mouseevent, getGraphics2D(mouseevent));
    }

    @Override
    public Graphic clone(int i, int j) {
        return super.clone(i, j);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.ellipse"); //$NON-NLS-1$
    }

}
