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
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.media.data.ImageElement;

/**
 * The Class AngleToolGraphic.
 * 
 * @author Nicolas Roduit
 */
public class AngleToolGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(AngleToolGraphic.class.getResource("/icon/22x22/draw-angle.png")); //$NON-NLS-1$
    public final static int ARC_RADIUS = 24;

    public AngleToolGraphic(float lineThickness, Color paint, boolean fill) {
        super(3);
        setLineThickness(lineThickness);
        setPaint(paint);
        setFilled(fill);
        setLabelVisible(true);
        updateStroke();
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return "Open Angle";
    }

    @Override
    public void updateLabel(Object source, Graphics2D g2d) {
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseEvent) {
        GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, handlePointList.size());

        String label = "";

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);
            generalpath.moveTo(A.getX(), A.getY());

            if (handlePointList.size() >= 2) {
                Point2D P = handlePointList.get(1);
                generalpath.lineTo(P.getX(), P.getY());

                if (handlePointList.size() >= 3) {
                    Point2D B = handlePointList.get(2);
                    generalpath.lineTo(B.getX(), B.getY());

                    double angularExtent = GeomUtil.getAngleDeg(A, P, B);
                    angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);

                    if (Math.signum(angularExtent) < 0) {
                        Point2D switchPoint = (Point2D) B.clone();
                        B = (Point2D) A.clone();
                        A = switchPoint;
                    }

                    angularExtent = Math.abs(angularExtent);

                    double startingAngle = GeomUtil.getAngleDeg(P, A);
                    label = getRealAngleLabel(getImageElement(mouseEvent), A, P, B);

                    unTransformedShape =
                        computeUnTransformedDrawingShape(P, ARC_RADIUS, startingAngle, angularExtent,
                            getAffineTransform(mouseEvent));
                }
            }
        }

        setShape(generalpath, mouseEvent);
        setLabel(new String[] { label }, getGraphics2D(mouseEvent));
    }

    @Override
    protected void updateUnTransformedDrawingShape(AffineTransform transform) {
        unTransformedShape = computeUnTransformedDrawingShape(ARC_RADIUS, transform);
    }

    protected static Shape computeUnTransformedDrawingShape(Point2D P, double radius, double startingAngle,
        double angularExtent, AffineTransform transform) {

        Point2D newP = transform == null ? P : transform.transform(P, null);

        Rectangle2D ellipseBounds =
            new Rectangle2D.Double(newP.getX() - radius, newP.getY() - radius, 2 * radius, 2 * radius);
        // can be simplified !!! by just rescale radius

        if (transform != null) {
            try {
                AffineTransform inverseT = transform.createInverse();
                Point2D upLeftCornerPt = new Point2D.Double(ellipseBounds.getX(), ellipseBounds.getY());
                Point2D newUpLeftCornerPt = inverseT.transform(upLeftCornerPt, null);
                double newWidthHeight = ellipseBounds.getWidth() * inverseT.getScaleX();

                ellipseBounds =
                    new Rectangle2D.Double(newUpLeftCornerPt.getX(), newUpLeftCornerPt.getY(), newWidthHeight,
                        newWidthHeight);

            } catch (NoninvertibleTransformException e) {
                e.printStackTrace();
            }
        }

        return new Arc2D.Double(ellipseBounds, startingAngle, angularExtent, Arc2D.OPEN);
    }

    protected Shape computeUnTransformedDrawingShape(double radius, AffineTransform transform) {
        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);

            if (handlePointList.size() >= 2) {
                Point2D P = handlePointList.get(1);

                if (handlePointList.size() >= 3) {
                    Point2D B = handlePointList.get(2);

                    double angularExtent = GeomUtil.getAngleDeg(A, P, B);
                    angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);

                    if (Math.signum(angularExtent) < 0) {
                        Point2D pt = (Point2D) B.clone();
                        B = (Point2D) A.clone();
                        A = pt;
                    }

                    angularExtent = Math.abs(angularExtent);

                    double startingAngle = GeomUtil.getAngleDeg(P, A);
                    return computeUnTransformedDrawingShape(P, ARC_RADIUS, startingAngle, angularExtent, transform);
                }
            }
        }
        return null;
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

    @Override
    public AngleToolGraphic clone() {
        return (AngleToolGraphic) super.clone();
    }

    @Override
    public Graphic clone(int xPos, int yPos) {
        AngleToolGraphic newGraphic = clone();
        newGraphic.updateStroke();
        newGraphic.updateShapeOnDrawing(null);
        return newGraphic;
    }
}
