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
import java.awt.Point;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.DefaultView2d;

/**
 * @author Benoit Jacquemoud
 */
public class CobbAngleToolGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(CobbAngleToolGraphic.class.getResource("/icon/22x22/draw-cobb.png")); //$NON-NLS-1$

    public final static Measurement Angle = new Measurement("Angle", true);
    public final static Measurement ComplementaryAngle = new Measurement("Complementary Angle", true);
    protected Stroke strokeDecorator;

    public CobbAngleToolGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(5, paintColor, lineThickness, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.cobb.angle");
    }

    @Override
    protected void updateStroke() {
        super.updateStroke();
        strokeDecorator =
            new BasicStroke(lineThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] { 5.0f, 5.0f }, 0f);
    }

    @Override
    public void updateLabel(Object source, DefaultView2d view2d) {
    }

    @Override
    protected int moveAndResizeOnDrawing(int handlePointIndex, int deltaX, int deltaY, MouseEvent mouseEvent) {
        if (handlePointIndex == -1) {
            for (Point2D point : handlePointList) {
                point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
            }
        } else {
            handlePointList.get(handlePointIndex).setLocation(mouseEvent.getPoint());

            // if (!isGraphicComplete() && handlePointList.size() >= 4) {
            if (handlePointIndex != 4 && handlePointList.size() >= 4) {

                Point2D A = handlePointList.get(0);
                Point2D B = handlePointList.get(1);
                Point2D C = handlePointList.get(2);
                Point2D D = handlePointList.get(3);

                // Let A,B,C,D be 2-space position vectors. Then the directed line segments AB & CD are given
                // by:
                // AB=A+r(B-A), r in [0,1]
                // CD=C+s(D-C), s in [0,1]

                // If lines AB & CD intersect, then
                // A+r(B-A)=C+s(D-C) <=>
                // Ax+r(Bx-Ax)=Cx+s(Dx-Cx)
                // Ay+r(By-Ay)=Cy+s(Dy-Cy) for some r,s in [0,1]

                // Let P be the position vector of the intersection point
                // P=A+r(B-A) <=>
                // Px=Ax+r(Bx-Ax)
                // Py=Ay+r(By-Ay)

                double Ax = A.getX(), Ay = A.getY();
                double Bx = B.getX(), By = B.getY();
                double Cx = C.getX(), Cy = C.getY();
                double Dx = D.getX(), Dy = D.getY();

                double denominator = (Bx - Ax) * (Dy - Cy) - (By - Ay) * (Dx - Cx);
                // If denominator is zero, AB & CD are parallel

                if (denominator != 0) {

                    double r = ((Ay - Cy) * (Dx - Cx) - (Ax - Cx) * (Dy - Cy)) / denominator; // equ1
                    double s = ((Ay - Cy) * (Bx - Ax) - (Ax - Cx) * (By - Ay)) / denominator; // equ2
                    Point2D P = new Point2D.Double(Ax + r * (Bx - Ax), Ay + r * (By - Ay));

                    // If 0<=r<=1 & 0<=s<=1, segment intersection exists
                    // If r<0 or r>1 or s<0 or s>1, lines intersect but not segments

                    // If r>1, P is located on extension of AB
                    // If r<0, P is located on extension of BA
                    // If s>1, P is located on extension of CD
                    // If s<0, P is located on extension of DC

                    // Let ptArrayX be an ordered array of points along line segments.
                    Point2D[] pts1 = new Point2D[3]; // order can be ABP (r>1) or BAP (r<0) or APB (0<=r<=1)
                    Point2D[] pts2 = new Point2D[3]; // order can be CDP (s>1) or DCP (s<0) or CPD (0<=s<=1)

                    pts1[0] = (r >= 0) ? A : B;
                    pts1[1] = (r < 0) ? A : (r > 1) ? B : P;
                    pts1[2] = (r < 0) ? P : (r > 1) ? P : B;

                    if (pts1[1].equals(P)) {
                        if (pts1[1].distance(pts1[0]) < pts1[1].distance(pts1[2])) {
                            Point2D switchPoint = (Point2D) pts1[2].clone();
                            pts1[2] = (Point2D) pts1[0].clone();
                            pts1[0] = switchPoint;
                        }
                    }

                    pts2[0] = (s >= 0) ? C : D;
                    pts2[1] = (s < 0) ? C : (s > 1) ? D : P;
                    pts2[2] = (s < 0) ? P : (s > 1) ? P : D;

                    if (pts2[1].equals(P)) {
                        if (pts2[1].distance(pts2[0]) < pts2[1].distance(pts2[2])) {
                            Point2D switchPoint = (Point2D) pts2[2].clone();
                            pts2[2] = (Point2D) pts2[0].clone();
                            pts2[0] = switchPoint;
                        }
                    }

                    // Let MP be the bisector of Cobb's angle
                    double d1 = P.distance(pts1[0]);
                    double d2 = P.distance(pts2[0]);

                    Point2D M;
                    if (Math.max(d1, d2) == d1) {
                        M = GeomUtil.getMidPoint(pts1[0], GeomUtil.getColinearPointWithLength(P, pts2[0], d1));
                    } else {
                        M = GeomUtil.getMidPoint(pts2[0], GeomUtil.getColinearPointWithLength(P, pts1[0], d2));
                    }

                    // M = getIntersectPoint(ptArray1[0], ptArray2[0], M, P);
                    Line2D lineMP = new Line2D.Double(M, P);
                    // generalpath.append(lineMP, false); // drawing for debug

                    // Let O be center of perpendicular projections in Cobb's angle
                    Point2D H1 = GeomUtil.getMidPoint(pts1[1], GeomUtil.getMidPoint(pts1[0], pts1[1]));
                    Point2D H2 = GeomUtil.getMidPoint(pts2[1], GeomUtil.getMidPoint(pts2[0], pts2[1]));
                    Point2D O1 = GeomUtil.getPerpendicularPointToLine(lineMP, H1);
                    Point2D O2 = GeomUtil.getPerpendicularPointToLine(lineMP, H2);
                    Point2D O = GeomUtil.getMidPoint(O1, O2);
                    // generalpath.append(new Line2D.Double(O1, H1), false); // drawing for debug
                    // generalpath.append(new Line2D.Double(O2, H2), false); // drawing for debug

                    if (handlePointList.size() < handlePointTotalNumber) {
                        handlePointList.add(new Point.Double(O.getX(), O.getY()));
                    } else {
                        handlePointList.get(4).setLocation(O.getX(), O.getY());
                    }

                }
            }
        }
        return handlePointIndex;
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseEvent) {

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);

            if (handlePointList.size() >= 2) {
                AdvancedShape newShape = new AdvancedShape(9);
                Path2D generalpath = new Path2D.Double(Path2D.WIND_NON_ZERO, handlePointList.size());
                newShape.addShape(generalpath);

                Point2D B = handlePointList.get(1);

                generalpath.moveTo(A.getX(), A.getY());
                generalpath.lineTo(B.getX(), B.getY());

                if (handlePointList.size() >= 3) {
                    Point2D C = handlePointList.get(2);

                    if (handlePointList.size() >= 4) {
                        Point2D D = handlePointList.get(3);

                        generalpath.moveTo(C.getX(), C.getY());
                        generalpath.lineTo(D.getX(), D.getY());

                        if (handlePointList.size() >= 5) {
                            Point2D O = handlePointList.get(4);

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

                            Point2D I = new Point2D.Double(Ax + r1 * (Bx - Ax), Ay + r1 * (By - Ay));
                            Point2D J = new Point2D.Double(Cx + r2 * (Dx - Cx), Cy + r2 * (Dy - Cy));

                            if (r1 < 0 || r1 > 1) {
                                newShape.addShape(new Line2D.Double(r1 > 1 ? B : A, I), strokeDecorator);
                            }

                            if (r2 < 0 || r2 > 1) {
                                newShape.addShape(new Line2D.Double(r1 > 1 ? D : C, J), strokeDecorator);
                            }

                            newShape.addShape(new Line2D.Double(O, I));
                            newShape.addShape(new Line2D.Double(O, J));

                            // Let K be the point on extension of IO segment
                            // Let L be the point on extension of JO segment
                            double distOI = O.distance(I);
                            double distOJ = O.distance(J);

                            double extSegLength = 32;

                            Point2D K = GeomUtil.getColinearPointWithLength(I, O, distOI + extSegLength);
                            Point2D L = GeomUtil.getColinearPointWithLength(J, O, distOJ + extSegLength);

                            double dOKmax = (1.0 / 2.0) * distOI;
                            newShape.addInvShape(new Line2D.Double(O, K), (Point2D) O.clone(), extSegLength / dOKmax);

                            double dOLmax = (1.0 / 2.0) * distOJ;
                            newShape.addInvShape(new Line2D.Double(O, L), (Point2D) O.clone(), extSegLength / dOKmax);

                            // Let arcAngle be the partial section of the ellipse that represents the measured angle

                            double startingAngle =
                                K.getY() > L.getY() ? GeomUtil.getAngleDeg(O, J) : GeomUtil.getAngleDeg(O, I);
                            double angularExtent =
                                K.getY() > L.getY() ? GeomUtil.getAngleDeg(J, O, K) : GeomUtil.getAngleDeg(I, O, L);
                            angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);

                            double radius = (2.0 / 3.0) * extSegLength;
                            Rectangle2D arcAngleBounds =
                                new Rectangle2D.Double(O.getX() - radius, O.getY() - radius, 2 * radius, 2 * radius);

                            Arc2D arcAngle = new Arc2D.Double(arcAngleBounds, startingAngle, angularExtent, Arc2D.OPEN);

                            double rMax = (2.0 / 3.0) * (K.getY() > L.getY() ? dOKmax : dOLmax);
                            newShape.addInvShape(arcAngle, (Point2D) O.clone(), radius / rMax);

                            double cornerLength = 10;

                            double cImax = (2.0 / 3.0) * Math.min(distOI, Math.max(I.distance(A), I.distance(B)));
                            newShape.addInvShape(
                                GeomUtil.getCornerShape(GeomUtil.getMidPoint(A, B), I, O, cornerLength),
                                (Point2D) I.clone(), cornerLength / cImax);

                            double cJmax = (2.0 / 3.0) * Math.min(distOJ, Math.max(J.distance(C), J.distance(D)));
                            newShape.addInvShape(
                                GeomUtil.getCornerShape(GeomUtil.getMidPoint(C, D), J, O, cornerLength),
                                (Point2D) J.clone(), cornerLength / cJmax);
                        }
                    }
                }
                setShape(newShape, mouseEvent);
                updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
            }
        }
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent) {
        if (imageElement != null && handlePointList.size() >= 5) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();
            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();
                if (Angle.isComputed()) {
                    Point2D A = handlePointList.get(0);
                    Point2D B = handlePointList.get(1);
                    Point2D C = handlePointList.get(2);
                    Point2D D = handlePointList.get(3);
                    Point2D O = handlePointList.get(4);

                    double Ax = A.getX(), Ay = A.getY();
                    double Bx = B.getX(), By = B.getY();
                    double Cx = C.getX(), Cy = C.getY();
                    double Dx = D.getX(), Dy = D.getY();
                    double distAB2 = Point2D.distanceSq(Ax, Ay, Bx, By);
                    double distCD2 = Point2D.distanceSq(Cx, Cy, Dx, Dy);
                    double r1 = ((Ay - O.getY()) * (Ay - By) + (Ax - O.getX()) * (Ax - Bx)) / distAB2;
                    double r2 = ((Cy - O.getY()) * (Cy - Dy) + (Cx - O.getX()) * (Cx - Dx)) / distCD2;
                    Point2D I = new Point2D.Double(Ax + r1 * (Bx - Ax), Ay + r1 * (By - Ay));
                    Point2D J = new Point2D.Double(Cx + r2 * (Dx - Cx), Cy + r2 * (Dy - Cy));
                    double distOI = O.distance(I);
                    double distOJ = O.distance(J);
                    Point2D K =
                        distOI < 64 ? GeomUtil.getColinearPointWithRatio(I, O, 1.5) : GeomUtil
                            .getColinearPointWithLength(I, O, distOI + 32);
                    Point2D L =
                        distOJ < 64 ? GeomUtil.getColinearPointWithRatio(J, O, 1.5) : GeomUtil
                            .getColinearPointWithLength(J, O, distOJ + 32);

                    double realAngle;
                    if (K.getY() > L.getY()) {
                        realAngle = Math.abs(GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(J, O, K)));
                    } else {
                        realAngle = Math.abs(GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(I, O, L)));
                    }

                    if (Angle.isComputed() && (releaseEvent || Angle.isGraphicLabel())) {
                        measVal.add(new MeasureItem(Angle, realAngle, "deg"));
                    }
                    if (ComplementaryAngle.isComputed() && (releaseEvent || ComplementaryAngle.isGraphicLabel())) {
                        measVal.add(new MeasureItem(ComplementaryAngle, 180.0 - realAngle, "deg"));
                    }
                }
                return measVal;
            }
        }
        return null;
    }

}
