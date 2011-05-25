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
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.ui.Messages;

/**
 * @author Nicolas Roduit
 */
public class EllipseGraphic extends RectangleGraphic {

    public static final Icon ICON = new ImageIcon(EllipseGraphic.class.getResource("/icon/22x22/draw-eclipse.png")); //$NON-NLS-1$

    public EllipseGraphic(float lineThickness, Color paint, boolean labelVisible) {
        super(lineThickness, paint, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.ellipse"); //$NON-NLS-1$
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseevent) {

        Rectangle2D rectangle = new Rectangle2D.Double();
        rectangle.setFrameFromDiagonal(handlePointList.get(eHandlePoint.NW.index),
            handlePointList.get(eHandlePoint.SE.index));
        setShape(new Ellipse2D.Double(rectangle.getX(), rectangle.getY(), rectangle.getWidth(), rectangle.getHeight()),
            mouseevent);

        updateLabel(mouseevent, getDefaultView2d(mouseevent));
    }

    @Override
    protected double getGraphicArea(double scaleX, double scaleY) {

        Rectangle2D rectangle = new Rectangle2D.Double();
        rectangle.setFrameFromDiagonal(handlePointList.get(eHandlePoint.NW.index),
            handlePointList.get(eHandlePoint.SE.index));

        return Math.PI * rectangle.getWidth() * scaleX * rectangle.getHeight() * scaleY / 4.0;
    }

}
