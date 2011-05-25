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
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.media.data.ImageElement;

/**
 * @author Benoit Jacquemoud
 */
public class OpenAngleToolGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(
        OpenAngleToolGraphic.class.getResource("/icon/22x22/draw-open-angle.png")); //$NON-NLS-1$

    protected Stroke strokeDecorator;
    protected Stroke strokeDecorator2;

    public OpenAngleToolGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(4, paintColor, lineThickness, labelVisible);
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
    protected void updateStroke() {
        super.updateStroke();
        strokeDecorator =
            new BasicStroke(lineThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] { 5.0f, 5.0f }, 0f);
        strokeDecorator2 = new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseEvent) {

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);

            if (handlePointList.size() >= 2) {
                AdvancedShape newShape = new AdvancedShape(5);
                Path2D generalpath = new Path2D.Double(Path2D.WIND_NON_ZERO, handlePointList.size());
                newShape.addShape(generalpath);

                Point2D B = handlePointList.get(1);

                generalpath.moveTo(A.getX(), A.getY());
                generalpath.lineTo(B.getX(), B.getY());

                if (handlePointList.size() >= 3) {
                    Point2D C = handlePointList.get(2);

                    if (handlePointList.size() == 4) {
                        Point2D D = handlePointList.get(3);

                        generalpath.moveTo(C.getX(), C.getY());
                        generalpath.lineTo(D.getX(), D.getY());

                        double Ax = A.getX(), Ay = A.getY();
                        double Bx = B.getX(), By = B.getY();
                        double Cx = C.getX(), Cy = C.getY();
                        double Dx = D.getX(), Dy = D.getY();

                        double denominator = (Bx - Ax) * (Dy - Cy) - (By - Ay) * (Dx - Cx);

                        // If denominator is zero, AB & CD are parallel
                        if (denominator != 0) {

                            double r = ((Ay - Cy) * (Dx - Cx) - (Ax - Cx) * (Dy - Cy)) / denominator; // equ1
                            double s = ((Ay - Cy) * (Bx - Ax) - (Ax - Cx) * (By - Ay)) / denominator; // equ2

                            // Let P be the intersection point of the two line segments
                            Point2D P = new Point2D.Double(Ax + r * (Bx - Ax), Ay + r * (By - Ay));

                            // If 0<=r<=1 & 0<=s<=1, segment intersection exists
                            // If r<0 or r>1 or s<0 or s>1, lines intersect but not segments

                            // If r>1, P is located on extension of AB
                            // If r<0, P is located on extension of BA
                            // If s>1, P is located on extension of CD
                            // If s<0, P is located on extension of DC

                            // Let ptsX be an ordered array of points along line segments.
                            Point2D[] pts1 = new Point2D[3]; // order can be ABP (r>1) or BAP (r<0) or APB (0<=r<=1)
                            Point2D[] pts2 = new Point2D[3]; // order can be CDP (s>1) or DCP (s<0) or CPD (0<=s<=1)

                            pts1[0] = r >= 0 ? A : B;
                            pts1[1] = r < 0 ? A : r > 1 ? B : P;
                            pts1[2] = r < 0 ? P : r > 1 ? P : B;

                            if (pts1[1].equals(P)) {
                                if (pts1[1].distance(pts1[0]) < pts1[1].distance(pts1[2])) {
                                    Point2D switchPoint = (Point2D) pts1[2].clone();
                                    pts1[2] = (Point2D) pts1[0].clone();
                                    pts1[0] = switchPoint;
                                }
                            }

                            pts2[0] = s >= 0 ? C : D;
                            pts2[1] = s < 0 ? C : s > 1 ? D : P;
                            pts2[2] = s < 0 ? P : s > 1 ? P : D;

                            if (pts2[1].equals(P)) {
                                if (pts2[1].distance(pts2[0]) < pts2[1].distance(pts2[2])) {
                                    Point2D switchPoint = (Point2D) pts2[2].clone();
                                    pts2[2] = (Point2D) pts2[0].clone();
                                    pts2[0] = switchPoint;
                                }
                            }

                            // Let arcAngle be the partial section of the ellipse that represents the measured angle
                            // Let I,J points of segment lines AB & CD used to compute arcAngle radius
                            Point2D I1 = GeomUtil.getColinearPointWithRatio(pts1[1], pts1[0], 0.25);
                            Point2D J1 = GeomUtil.getColinearPointWithRatio(pts2[1], pts2[0], 0.25);
                            Point2D I2 = GeomUtil.getColinearPointWithRatio(pts1[0], pts1[1], 0.25);
                            Point2D J2 = GeomUtil.getColinearPointWithRatio(pts2[0], pts2[1], 0.25);

                            double maxRadius = Math.min(P.distance(I2), P.distance(J2));
                            double radius = Math.min(maxRadius, (P.distance(I1) + P.distance(J1)) / 2.0);

                            double angularExtent = GeomUtil.getAngleDeg(pts1[0], P, pts2[0]);
                            angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);

                            double startingAngle = GeomUtil.getAngleDeg(P, pts1[0]);

                            Rectangle2D arcAngleBounds =
                                new Rectangle2D.Double(P.getX() - radius, P.getY() - radius, 2 * radius, 2 * radius);

                            Arc2D arcAngle = new Arc2D.Double(arcAngleBounds, startingAngle, angularExtent, Arc2D.OPEN);
                            newShape.addShape(arcAngle);

                            if (pts1[2].equals(P))
                                newShape.addShape(new Line2D.Double(pts1[2], pts1[1]), strokeDecorator);

                            if (pts2[2].equals(P))
                                newShape.addShape(new Line2D.Double(pts2[2], pts2[1]), strokeDecorator);

                            // Let K,L,M,N points dedicated to the drawing of the intersection point
                            int size = 8;
                            Point2D K = new Point2D.Double(P.getX() - size, P.getY());
                            Point2D L = new Point2D.Double(P.getX() + size, P.getY());
                            Point2D M = new Point2D.Double(P.getX(), P.getY() - size);
                            Point2D N = new Point2D.Double(P.getX(), P.getY() + size);

                            Path2D intersectPtShape = new Path2D.Double(Path2D.WIND_NON_ZERO, 3);

                            intersectPtShape.append(new Line2D.Double(K, L), false);
                            intersectPtShape.append(new Line2D.Double(M, N), false);

                            Point2D intersecPt = GeomUtil.getIntersectPoint(K, L, M, N);
                            Rectangle2D intersecPtBounds =
                                new Rectangle2D.Double(intersecPt.getX() - size / 2.0, intersecPt.getY() - size / 2.0,
                                    size, size);
                            intersectPtShape.append(new Arc2D.Double(intersecPtBounds, 0, 360, Arc2D.OPEN), false);

                            newShape.addInvShape(intersectPtShape, (Point2D) P.clone(), strokeDecorator2);

                            Rectangle rect = generalpath.getBounds();
                            int xPos = rect.x + rect.width;
                            int yPos = (int) Math.ceil(rect.y + rect.height * 0.5);

                            String label = getRealAngleLabel(getImageElement(mouseEvent), pts1[0], P, pts2[0]);
                            setLabel(new String[] { label }, getDefaultView2d(mouseEvent), xPos, yPos);
                        }

                    }
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
