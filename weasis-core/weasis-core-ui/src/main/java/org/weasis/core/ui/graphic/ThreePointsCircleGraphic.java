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
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.GeomUtil;

/**
 * @author Benoit Jacquemoud
 */
public class ThreePointsCircleGraphic extends AbstractDragGraphicArea {

    public static final Icon ICON = new ImageIcon(
        ThreePointsCircleGraphic.class.getResource("/icon/22x22/draw-circle.png")); //$NON-NLS-1$

    public ThreePointsCircleGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(3, paintColor, lineThickness, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return "Three Points Circle";
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseEvent) {
        Shape newShape = null;

        if (handlePointList.size() > 1) {
            Point2D centerPt = GeomUtil.getCircleCenter(handlePointList);
            if (centerPt != null) {
                double radius = centerPt.distance(handlePointList.get(0));
                if (radius < 5000) {
                    Rectangle2D rectangle = new Rectangle2D.Double();
                    rectangle.setFrameFromCenter(centerPt.getX(), centerPt.getY(), centerPt.getX() - radius,
                        centerPt.getY() - radius);

                    // GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, handlePointList.size());
                    // generalpath.append(new Ellipse2D.Double(rectangle.getX(), rectangle.getY(), rectangle.getWidth(),
                    // rectangle.getHeight()), false);

                    newShape =
                        new Ellipse2D.Double(rectangle.getX(), rectangle.getY(), rectangle.getWidth(),
                            rectangle.getHeight());
                }
            }
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    protected double getGraphicArea(double scaleX, double scaleY) {
        if (handlePointList.size() > 1) {
            Point2D centerPt = GeomUtil.getCircleCenter(handlePointList);
            if (centerPt != null) {
                double radius = centerPt.distance(handlePointList.get(0));
                return Math.PI * radius * radius * scaleX * scaleY;
            }
        }
        return 0;
    }
}