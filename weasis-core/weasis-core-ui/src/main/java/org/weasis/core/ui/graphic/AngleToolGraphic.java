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
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.media.data.ImageElement;

/**
 * 
 * @author Nicolas Roduit,Benoit Jacquemoud
 */
public class AngleToolGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(AngleToolGraphic.class.getResource("/icon/22x22/draw-angle.png")); //$NON-NLS-1$

    public final static int ARC_RADIUS = 32;

    public AngleToolGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(3, paintColor, lineThickness, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return "Angle measure";
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseEvent) {

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);

            if (handlePointList.size() >= 2) {
                AdvancedShape newShape = new AdvancedShape(2);
                Path2D generalpath = new Path2D.Double(Path2D.WIND_NON_ZERO, handlePointList.size());
                newShape.addShape(generalpath);

                Point2D P = handlePointList.get(1);

                generalpath.moveTo(A.getX(), A.getY());
                generalpath.lineTo(P.getX(), P.getY());

                if (handlePointList.size() >= 3) {
                    Point2D B = handlePointList.get(2);
                    generalpath.lineTo(B.getX(), B.getY());

                    double angularExtent = GeomUtil.getAngleDeg(A, P, B);
                    angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);
                    double startingAngle = GeomUtil.getAngleDeg(P, A);

                    // double radius = (ARC_RADIUS < rMax) ? ARC_RADIUS : rMax;
                    double radius = ARC_RADIUS;
                    Rectangle2D arcAngleBounds =
                        new Rectangle2D.Double(P.getX() - radius, P.getY() - radius, 2.0 * radius, 2.0 * radius);

                    Shape arcAngle = new Arc2D.Double(arcAngleBounds, startingAngle, angularExtent, Arc2D.OPEN);

                    double rMax = (2.0 / 3.0) * Math.min(P.distance(A), P.distance(B));
                    double scalingMin = radius / rMax;

                    newShape.addInvShape(arcAngle, (Point2D) P.clone(), scalingMin);

                    String label = getRealAngleLabel(getImageElement(mouseEvent), A, P, B);
                    setLabel(new String[] { label }, getDefaultView2d(mouseEvent));
                }
                setShape(newShape, mouseEvent);
            }
        }
    }

    protected String getRealAngleLabel(ImageElement image, Point2D A, Point2D O, Point2D B) {
        String label = "";
        if (image != null) {
            AffineTransform rescale = AffineTransform.getScaleInstance(image.getPixelSizeX(), image.getPixelSizeY());

            Point2D At = rescale.transform(A, null);
            Point2D Ot = rescale.transform(O, null);
            Point2D Bt = rescale.transform(B, null);

            double realAngle = GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(At, Ot, Bt));
            label = "Angle : " + DecFormater.twoDecimal(Math.abs(realAngle)) + "°";
            label += " / " + DecFormater.twoDecimal(180 - Math.abs(realAngle)) + "°";
        }
        return label;
    }
}
