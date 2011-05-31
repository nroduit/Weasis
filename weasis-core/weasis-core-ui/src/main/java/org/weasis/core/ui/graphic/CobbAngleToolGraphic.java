package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.DefaultView2d;

public class CobbAngleToolGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(CobbAngleToolGraphic.class.getResource("/icon/22x22/draw-cobb.png")); //$NON-NLS-1$

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

            if (!graphicComplete && handlePointList.size() >= 4) {

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
                    Point2D[] ptArray1 = new Point2D[3]; // order can be ABP (r>1) or BAP (r<0) or APB (0<=r<=1)
                    Point2D[] ptArray2 = new Point2D[3]; // order can be CDP (s>1) or DCP (s<0) or CPD (0<=s<=1)

                    ptArray1[0] = (r >= 0) ? A : B;
                    ptArray1[1] = (r < 0) ? A : (r > 1) ? B : P;
                    ptArray1[2] = (r < 0) ? P : (r > 1) ? P : B;

                    ptArray2[0] = (s >= 0) ? C : D;
                    ptArray2[1] = (s < 0) ? C : (s > 1) ? D : P;
                    ptArray2[2] = (s < 0) ? P : (s > 1) ? P : D;

                    // Let MP be the bisector of Cobb's angle
                    double d1 = P.distance(ptArray1[0]);
                    double d2 = P.distance(ptArray2[0]);

                    Point2D M;
                    if (Math.max(d1, d2) == d1) {
                        M = GeomUtil.getMidPoint(ptArray1[0], GeomUtil.getColinearPointWithLength(P, ptArray2[0], d1));
                    } else {
                        M = GeomUtil.getMidPoint(ptArray2[0], GeomUtil.getColinearPointWithLength(P, ptArray1[0], d2));
                    }

                    // M = getIntersectPoint(ptArray1[0], ptArray2[0], M, P);
                    Line2D lineMP = new Line2D.Double(M, P);
                    // generalpath.append(lineMP, false); // drawing for debug

                    // Let O be center of perpendicular projections in Cobb's angle
                    Point2D H1 = GeomUtil.getMidPoint(ptArray1[1], GeomUtil.getMidPoint(ptArray1[0], ptArray1[1]));
                    Point2D H2 = GeomUtil.getMidPoint(ptArray2[1], GeomUtil.getMidPoint(ptArray2[0], ptArray2[1]));
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
        GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, handlePointList.size());

        String label = "";

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);
            generalpath.moveTo(A.getX(), A.getY());

            if (handlePointList.size() >= 2) {
                Point2D B = handlePointList.get(1);
                generalpath.lineTo(B.getX(), B.getY());

                if (handlePointList.size() >= 3) {
                    Point2D C = handlePointList.get(2);
                    generalpath.moveTo(C.getX(), C.getY());

                    if (handlePointList.size() >= 4) {
                        Point2D D = handlePointList.get(3);
                        generalpath.lineTo(D.getX(), D.getY());

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
                            generalpath.append(new Line2D.Double(r1 > 1 ? B : A, I), false);
                        }
                        if (r2 < 0 || r2 > 1) {
                            generalpath.append(new Line2D.Double(r1 > 1 ? D : C, J), false);
                        }

                        // Let K be the point on extension of IO segment
                        // Let L be the point on extension of JO segment
                        double distOI = O.distance(I);
                        double distOJ = O.distance(J);
                        Point2D K =
                            distOI < 64 ? GeomUtil.getColinearPointWithRatio(I, O, 1.5) : GeomUtil
                                .getColinearPointWithLength(I, O, distOI + 32);
                        Point2D L =
                            distOJ < 64 ? GeomUtil.getColinearPointWithRatio(J, O, 1.5) : GeomUtil
                                .getColinearPointWithLength(J, O, distOJ + 32);

                        generalpath.append(new Line2D.Double(I, K), false);
                        generalpath.append(new Line2D.Double(J, L), false);

                        // Let arcAngle be the partial section of the ellipse that represents the measured angle
                        double radius = Math.min(O.distance(K), O.distance(L)) * 3 / 4;
                        double startingAngle =
                            K.getY() > L.getY() ? GeomUtil.getAngleDeg(O, J) : GeomUtil.getAngleDeg(O, I);
                        double angularExtent =
                            K.getY() > L.getY() ? GeomUtil.getAngleDeg(J, O, K) : GeomUtil.getAngleDeg(I, O, L);
                        angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);

                        if (K.getY() > L.getY()) {
                            label = getRealAngleLabel(getImageElement(mouseEvent), J, O, K);
                        } else {
                            label = getRealAngleLabel(getImageElement(mouseEvent), I, O, L);
                        }

                        Rectangle2D ellipseBounds =
                            new Rectangle2D.Double(O.getX() - radius, O.getY() - radius, 2 * radius, 2 * radius);
                        Arc2D arcAngle = new Arc2D.Double(ellipseBounds, startingAngle, angularExtent, Arc2D.OPEN);

                        generalpath.append(arcAngle, false);

                        // Let I1,I2,I3 be the points that defines rectangular corner where OI intersect AB line
                        // segment

                        Point2D I1 = GeomUtil.getColinearPointWithLength(I, A, 4);
                        Point2D I2 = GeomUtil.getColinearPointWithLength(I, O, 4);
                        double rotSignum1 =
                            Math.signum(GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(O, I, I1)));
                        Point2D I3 = GeomUtil.getPerpendicularPointFromLine(O, I, I2, rotSignum1 * 4);

                        generalpath.append(new Line2D.Double(I1, I3), false);
                        generalpath.append(new Line2D.Double(I2, I3), false);

                        // Let J1,J2,J3 be the points that defines rectangular corner where OJ intersect CD line
                        // segment
                        Point2D J1 = GeomUtil.getColinearPointWithLength(J, C, 4);
                        Point2D J2 = GeomUtil.getColinearPointWithLength(J, O, 4);
                        double rotSignum2 =
                            Math.signum(GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(O, J, J1)));
                        Point2D J3 = GeomUtil.getPerpendicularPointFromLine(O, J, J2, rotSignum2 * 4);

                        generalpath.append(new Line2D.Double(J1, J3), false);
                        generalpath.append(new Line2D.Double(J2, J3), false);
                    }
                }
            }
        }
        setShape(generalpath, mouseEvent);
        setLabel(new String[] { label }, getDefaultView2d(mouseEvent)); //
        // updateLabel(mouseevent, getGraphics2D(mouseevent));
    }

    protected String getRealAngleLabel(ImageElement image, Point2D A, Point2D O, Point2D B) {
        String label = "";
        if (image != null) {
            AffineTransform rescale = AffineTransform.getScaleInstance(image.getPixelSize(), image.getPixelSize());

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
    public CobbAngleToolGraphic clone() {
        return (CobbAngleToolGraphic) super.clone();
    }

    @Override
    public Graphic clone(int xPos, int yPos) {
        CobbAngleToolGraphic newGraphic = clone();
        newGraphic.updateStroke();
        newGraphic.updateShapeOnDrawing(null);
        return newGraphic;
    }

}

// @Override
// protected void updateShapeOnDrawing(MouseEvent mouseevent) {
// GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, handlePointList.size());
//
// if (handlePointList.size() >= 1) {
// Point2D A = handlePointList.get(0);
// generalpath.moveTo(A.getX(), A.getY());
//
// if (handlePointList.size() >= 2) {
// Point2D B = handlePointList.get(1);
// generalpath.lineTo(B.getX(), B.getY());
//
// if (handlePointList.size() >= 3) {
// Point2D C = handlePointList.get(2);
// generalpath.moveTo(C.getX(), C.getY());
//
// if (handlePointList.size() >= 4) {
// Point2D D = handlePointList.get(3);
// generalpath.lineTo(D.getX(), D.getY());
//
// // Let A,B,C,D be 2-space position vectors. Then the directed line segments AB & CD are given
// // by:
// // AB=A+r(B-A), r in [0,1]
// // CD=C+s(D-C), s in [0,1]
//
// // If lines AB & CD intersect, then
// // A+r(B-A)=C+s(D-C) <=>
// // Ax+r(Bx-Ax)=Cx+s(Dx-Cx)
// // Ay+r(By-Ay)=Cy+s(Dy-Cy) for some r,s in [0,1]
//
// // Let P be the position vector of the intersection point
// // P=A+r(B-A) <=>
// // Px=Ax+r(Bx-Ax)
// // Py=Ay+r(By-Ay)
//
// double Ax = A.getX(), Ay = A.getY();
// double Bx = B.getX(), By = B.getY();
// double Cx = C.getX(), Cy = C.getY();
// double Dx = D.getX(), Dy = D.getY();
//
// double denominator = (Bx - Ax) * (Dy - Cy) - (By - Ay) * (Dx - Cx);
// // If denominator is zero, AB & CD are parallel
//
// if (denominator != 0) {
//
// double r = ((Ay - Cy) * (Dx - Cx) - (Ax - Cx) * (Dy - Cy)) / denominator; // equ1
// double s = ((Ay - Cy) * (Bx - Ax) - (Ax - Cx) * (By - Ay)) / denominator; // equ2
// Point2D P = new Point2D.Double(Ax + r * (Bx - Ax), Ay + r * (By - Ay)); // intersection
// // point
//
// // If 0<=r<=1 & 0<=s<=1, segment intersection exists
// // If r<0 or r>1 or s<0 or s>1, lines intersect but not segments
//
// // If r>1, P is located on extension of AB
// // If r<0, P is located on extension of BA
// // If s>1, P is located on extension of CD
// // If s<0, P is located on extension of DC
//
// // Let ptArrayX be an ordered array of points along line segments.
// Point2D[] ptArray1 = new Point2D[3]; // order can be ABP (r>1) or BAP (r<0) or APB (0<=r<=1)
// Point2D[] ptArray2 = new Point2D[3]; // order can be CDP (s>1) or DCP (s<0) or CPD (0<=s<=1)
//
// ptArray1[0] = (r >= 0) ? A : B;
// ptArray1[1] = (r < 0) ? A : (r > 1) ? B : P;
// ptArray1[2] = (r < 0) ? P : (r > 1) ? P : B;
//
// ptArray2[0] = (s >= 0) ? C : D;
// ptArray2[1] = (s < 0) ? C : (s > 1) ? D : P;
// ptArray2[2] = (s < 0) ? P : (s > 1) ? P : D;
//
// // Let MP be the bisector of Cobb's angle
// double d1 = P.distance(ptArray1[0]);
// double d2 = P.distance(ptArray2[0]);
//
// Point2D M;
// if (Math.max(d1, d2) == d1)
// M =
// GeomUtils.getMidPoint(ptArray1[0],
// GeomUtils.getColinearPointWithLength(P, ptArray2[0], d1));
// else
// M =
// GeomUtils.getMidPoint(ptArray2[0],
// GeomUtils.getColinearPointWithLength(P, ptArray1[0], d2));
//
// // M = getIntersectPoint(ptArray1[0], ptArray2[0], M, P);
// Line2D lineMP = new Line2D.Double(M, P);
// // generalpath.append(lineMP, false); // drawing for debug
//
// // Let O be center of perpendicular projections in Cobb's angle
// Point2D H1 =
// GeomUtils.getMidPoint(ptArray1[1], GeomUtils.getMidPoint(ptArray1[0], ptArray1[1]));
// Point2D H2 =
// GeomUtils.getMidPoint(ptArray2[1], GeomUtils.getMidPoint(ptArray2[0], ptArray2[1]));
// Point2D O1 = GeomUtils.getPerpendicularPointToLine(lineMP, H1);
// Point2D O2 = GeomUtils.getPerpendicularPointToLine(lineMP, H2);
// Point2D O = GeomUtils.getMidPoint(O1, O2);
// // generalpath.append(new Line2D.Double(O1, H1), false); // drawing for debug
// // generalpath.append(new Line2D.Double(O2, H2), false); // drawing for debug
//
// if (handlePointList.size() < handlePointTotalNumber)
// handlePointList.add(new Point.Double(O.getX(), O.getY()));
// else
// handlePointList.get(4).setLocation(O.getX(), O.getY());
//
// // Let I be the perpendicular projection of O onto AB
// // Let J be the perpendicular projection of O onto CD
// double distAB2 = Point2D.distanceSq(Ax, Ay, Bx, By);
// double distCD2 = Point2D.distanceSq(Cx, Cy, Dx, Dy);
//
// double r1 = ((Ay - O.getY()) * (Ay - By) + (Ax - O.getX()) * (Ax - Bx)) / distAB2;
// double r2 = ((Cy - O.getY()) * (Cy - Dy) + (Cx - O.getX()) * (Cx - Dx)) / distCD2;
//
// Point2D I = new Point2D.Double(Ax + r1 * (Bx - Ax), Ay + r1 * (By - Ay));
// Point2D J = new Point2D.Double(Cx + r2 * (Dx - Cx), Cy + r2 * (Dy - Cy));
//
// if (r1 < 0 || r1 > 1)
// generalpath.append(new Line2D.Double(r1 > 1 ? B : A, I), false);
// if (r2 < 0 || r2 > 1)
// generalpath.append(new Line2D.Double(r1 > 1 ? D : C, J), false);
//
// // Let K be the point on extension of IO segment
// // Let L be the point on extension of JO segment
// double distOI = O.distance(I);
// double distOJ = O.distance(J);
// Point2D K =
// distOI < 64 ? GeomUtils.getColinearPointWithRatio(I, O, 1.5) : GeomUtils
// .getColinearPointWithLength(I, O, distOI + 32);
// Point2D L =
// distOJ < 64 ? GeomUtils.getColinearPointWithRatio(J, O, 1.5) : GeomUtils
// .getColinearPointWithLength(J, O, distOJ + 32);
//
// generalpath.append(new Line2D.Double(I, K), false);
// generalpath.append(new Line2D.Double(J, L), false);
//
// // Let arcAngle be the partial section of the ellipse that represents the measured angle
// double radius = Math.min(O.distance(K), O.distance(L)) * 3 / 4;
// double startingAngle =
// K.getY() > L.getY() ? GeomUtils.getAngleDeg(O, J) : GeomUtils.getAngleDeg(O, I);
// double angularExtent =
// K.getY() > L.getY() ? GeomUtils.getAngleDeg(J, O, K) : GeomUtils.getAngleDeg(I, O, L);
// angularExtent = GeomUtils.getSmallestRotationAngleDeg(angularExtent);
//
// Rectangle2D ellipseBounds =
// new Rectangle2D.Double(O.getX() - radius, O.getY() - radius, 2 * radius, 2 * radius);
// Arc2D arcAngle = new Arc2D.Double(ellipseBounds, startingAngle, angularExtent, Arc2D.OPEN);
//
// generalpath.append(arcAngle, false);
//
// // Let I1,I2,I3 be the points that defines rectangular corner where OI intersect AB line
// // segment
// Point2D I1 = GeomUtils.getColinearPointWithLength(I, ptArray1[0], 4);
// Point2D I2 = GeomUtils.getColinearPointWithLength(I, O, 4);
// double rotSignum1 =
// Math.signum(GeomUtils.getSmallestRotationAngleDeg(GeomUtils.getAngleDeg(O, I, I1)));
// Point2D I3 = GeomUtils.getPerpendicularPointFromLine(O, I, I2, rotSignum1 * 4);
//
// generalpath.append(new Line2D.Double(I1, I3), false);
// generalpath.append(new Line2D.Double(I2, I3), false);
//
// // Let J1,J2,J3 be the points that defines rectangular corner where OJ intersect CD line
// // segment
// Point2D J1 = GeomUtils.getColinearPointWithLength(J, ptArray2[0], 4);
// Point2D J2 = GeomUtils.getColinearPointWithLength(J, O, 4);
// double rotSignum2 =
// Math.signum(GeomUtils.getSmallestRotationAngleDeg(GeomUtils.getAngleDeg(O, J, J1)));
// Point2D J3 = GeomUtils.getPerpendicularPointFromLine(O, J, J2, rotSignum2 * 4);
//
// generalpath.append(new Line2D.Double(J1, J3), false);
// generalpath.append(new Line2D.Double(J2, J3), false);
// }
//
// }
// }
// }
// }
// setShape(generalpath, mouseevent);
// }
