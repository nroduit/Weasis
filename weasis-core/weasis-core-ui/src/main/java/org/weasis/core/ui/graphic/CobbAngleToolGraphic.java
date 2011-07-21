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
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Benoit Jacquemoud
 */
public class CobbAngleToolGraphic extends OpenAngleToolGraphic {

    public static final Icon ICON = new ImageIcon(CobbAngleToolGraphic.class.getResource("/icon/22x22/draw-cobb.png")); //$NON-NLS-1$

    public final static Measurement Angle = new Measurement("Angle", true);
    public final static Measurement ComplementaryAngle = new Measurement("Compl. Angle", true, true, false);

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Let O be center of perpendicular projections in Cobb's angle
    protected Point2D O;

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    public CobbAngleToolGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(5, lineThickness, paintColor, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return "Cobb's Angle";
    }

    @Override
    protected int moveAndResizeOnDrawing(int handlePointIndex, double deltaX, double deltaY, MouseEventDouble mouseEvent) {
        handlePointIndex = super.moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, mouseEvent);

        if (handlePointIndex != -1 && handlePointList.size() >= 4) {
            updateTool();

            if (ABvalid && CDvalid) {
                // Let MN be the bisector of the two line segments AB & CD, if parallel MN is the median line
                Line2D lineMN;

                if (lineParallel) {
                    lineMN = GeomUtil.getMedianLine(A, B, C, D);
                } else {
                    AffineTransform rotate =
                        AffineTransform.getRotateInstance(-Math.toRadians(angleDeg) / 2, P.getX(), P.getY());

                    Point2D M = (Point2D) ABPline[0].clone();
                    rotate.transform(M, M);
                    lineMN = new Line2D.Double(M, P);
                }

                if (handlePointIndex == 4 && O != null) {
                    O = GeomUtil.getPerpendicularPointToLine(lineMN, O);
                } else {
                    if (lineParallel) {
                        O = GeomUtil.getMidPoint(lineMN.getP1(), lineMN.getP1());
                    } else {
                        // Point2D H1 = GeomUtil.getColinearPointWithRatio(ABPline[0], ABPline[1], 3 / 4);
                        // Point2D H2 = GeomUtil.getColinearPointWithRatio(CDPline[0], CDPline[1], 3 / 4);

                        Point2D H1 = GeomUtil.getMidPoint(ABPline[1], GeomUtil.getMidPoint(ABPline[0], ABPline[1]));
                        Point2D H2 = GeomUtil.getMidPoint(CDPline[1], GeomUtil.getMidPoint(CDPline[0], CDPline[1]));

                        Point2D O1 = GeomUtil.getPerpendicularPointToLine(lineMN, H1);
                        Point2D O2 = GeomUtil.getPerpendicularPointToLine(lineMN, H2);

                        O = GeomUtil.getMidPoint(O1, O2);
                    }
                }
            } else {
                O = null;
            }

            if (handlePointList.size() < 5) {
                handlePointList.add(O);
            } else {
                handlePointList.set(4, O);
            }
        }

        return handlePointIndex;
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {
        updateTool();

        Shape newShape = null;
        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 6);

        if (ABvalid) {
            path.append(new Line2D.Double(A, B), false);
        }

        if (CDvalid) {
            path.append(new Line2D.Double(C, D), false);
        }

        if (ABvalid && CDvalid && O != null) {
            AdvancedShape aShape = (AdvancedShape) (newShape = new AdvancedShape(10));
            aShape.addShape(path);

            double Ax = A.getX(), Ay = A.getY();
            double Bx = B.getX(), By = B.getY();
            double Cx = C.getX(), Cy = C.getY();
            double Dx = D.getX(), Dy = D.getY();

            // Let I be the perpendicular projection of O onto AB
            // Let J be the perpendicular projection of O onto CD
            double distAB2 = Point2D.distanceSq(Ax, Ay, Bx, By);
            double distCD2 = Point2D.distanceSq(Cx, Cy, Dx, Dy);

            double r1 = ((Ay - O.getY()) * (Ay - By) + (Ax - O.getX()) * (Ax - Bx)) / distAB2;
            double r2 = ((Cy - O.getY()) * (Cy - Dy) + (Cx - O.getX()) * (Cx - Dx)) / distCD2;

            final Point2D I = new Point2D.Double(Ax + r1 * (Bx - Ax), Ay + r1 * (By - Ay));
            final Point2D J = new Point2D.Double(Cx + r2 * (Dx - Cx), Cy + r2 * (Dy - Cy));

            if (r1 < 0 || r1 > 1) {
                aShape.addShape(new Line2D.Double(r1 > 1 ? B : A, I), getDashStroke(1.0f), true);
            }

            if (r2 < 0 || r2 > 1) {
                aShape.addShape(new Line2D.Double(r1 > 1 ? D : C, J), getDashStroke(1.0f), true);
            }

            aShape.addShape(new Line2D.Double(O, I));
            aShape.addShape(new Line2D.Double(O, J));

            // Add angle corners where I & J lies on AB & CD line segments
            double cLength = 10;

            double cImax = (2.0 / 3.0) * Math.min(O.distance(I), Math.max(I.distance(A), I.distance(B)));
            aShape.addInvShape(GeomUtil.getCornerShape(GeomUtil.getMidPoint(A, B), I, O, cLength), I, cLength / cImax,
                getStroke(1.0f), true);

            double cJmax = (2.0 / 3.0) * Math.min(O.distance(J), Math.max(J.distance(C), J.distance(D)));
            aShape.addInvShape(GeomUtil.getCornerShape(GeomUtil.getMidPoint(C, D), J, O, cLength), J, cLength / cJmax,
                getStroke(1.0f), true);

            if (!lineParallel) {

                // Let K be the point on extension of IO segment
                // Let L be the point on extension of JO segment
                double extSegLength = 32;

                Point2D K = GeomUtil.getColinearPointWithLength(I, O, O.distance(I) + extSegLength);
                Point2D L = GeomUtil.getColinearPointWithLength(J, O, O.distance(J) + extSegLength);

                double dOKmax = (1.0 / 2.0) * O.distance(I);
                aShape.addInvShape(new Line2D.Double(O, K), O, extSegLength / dOKmax, false);

                double dOLmax = (1.0 / 2.0) * O.distance(J);
                aShape.addInvShape(new Line2D.Double(O, L), O, extSegLength / dOLmax, false);

                // Let arcAngle be the partial section of the ellipse that represents the measured angle
                double startingAngle = (K.getY() > L.getY()) ? GeomUtil.getAngleDeg(O, J) : GeomUtil.getAngleDeg(O, I);
                double angularExtent =
                    K.getY() > L.getY() ? GeomUtil.getAngleDeg(J, O, K) : GeomUtil.getAngleDeg(I, O, L);
                angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);

                double radius = (2.0 / 3.0) * extSegLength;
                Rectangle2D arcAngleBounds =
                    new Rectangle2D.Double(O.getX() - radius, O.getY() - radius, 2 * radius, 2 * radius);

                Arc2D arcAngle = new Arc2D.Double(arcAngleBounds, startingAngle, angularExtent, Arc2D.OPEN);

                double rMax = (K.getY() > L.getY() ? dOKmax : dOLmax) * 2 / 3;
                aShape.addInvShape(arcAngle, O, radius / rMax, false);
            }

        } else if (path.getCurrentPoint() != null) {
            newShape = path;
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));

    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void updateTool() {
        super.updateTool();
        O = handlePointList.size() >= 5 ? handlePointList.get(4) : null;
    }
}
