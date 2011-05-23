package org.weasis.core.api.gui.util;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

public final class GeomUtil {

    /**
     * @param A
     * @param B
     * @return
     */
    public static Point2D getMidPoint(Point2D A, Point2D B) {
        return new Point2D.Double((A.getX() + B.getX()) / 2.0, (A.getY() + B.getY()) / 2.0);
    }

    /**
     * @param A
     * @param B
     * @param C
     * @return
     */
    public static double getAngleRad(Point2D A, Point2D B, Point2D C) {
        return getAngleRad(B, C) - getAngleRad(B, A);
    }

    public static double getAngleDeg(Point2D A, Point2D B, Point2D C) {
        return Math.toDegrees(getAngleRad(A, B, C));
    }

    /**
     * Compute angle into image system basis where positive angle are defined in a ClockWise orientation<br>
     * Note : angle should be computed with "Math.atan2(B.getY() - A.getY(), B.getX() - A.getX())" in an orthonormal
     * basis system where positive angle are defined in a CounterClockWise orientation.
     * 
     * @param A
     * @param B
     * @return angle of AB line segment in radians
     */

    public static double getAngleRad(Point2D A, Point2D B) {
        return Math.atan2(A.getY() - B.getY(), B.getX() - A.getX());
    }

    public static double getAngleDeg(Point2D A, Point2D B) {
        return Math.toDegrees(getAngleRad(A, B));
    }

    /**
     * @param angle
     *            in Radians
     * @return angle in the range of [ -pi ; pi ]
     */
    public static double getSmallestRotationAngleRad(double angle) {
        angle = angle % Math.PI;
        if (Math.abs(angle) > Math.PI)
            angle -= Math.signum(angle) * 2.0 * Math.PI;
        return angle;
    }

    /**
     * @param angle
     *            in Degrees
     * @return angle in the range of [ -180 ; 180 ]
     */
    public static double getSmallestRotationAngleDeg(double angle) {
        angle = angle % 360.0;
        if (Math.abs(angle) > 180.0)
            angle -= Math.signum(angle) * 360.0;
        return angle;
    }

    /**
     * @param A
     *            the first point of line segment
     * @param B
     *            the last point of line segment
     * @param newLength
     *            represents length from A to the return point C along AB line segment.<br>
     *            If >AB , C point will be located on extension of AB<br>
     *            If <0 , C point will be located on extension of BA <br>
     *            If >0 && < AB , C point will be interior of AB<br>
     * @return New point C coordinates
     */
    public static Point2D getColinearPointWithLength(Point2D A, Point2D B, double newLength) {
        return getColinearPointWithRatio(A, B, newLength / A.distance(B));
    }

    /**
     * @param A
     *            the first point of line segment
     * @param B
     *            the last point of line segment
     * @param k
     *            represents ratio between AB and AC, C being the returned point along AB line segment.<br>
     *            If >1 , C point will be located on extension of AB<br>
     *            If <0 , C point will be located on extension of BA <br>
     *            If >0 && <AB , C point will be interior of AB<br>
     * @return New point C coordinates
     */
    public static Point2D getColinearPointWithRatio(Point2D A, Point2D B, double k) {
        return new Point2D.Double(B.getX() * k + A.getX() * (1 - k), B.getY() * k + A.getY() * (1 - k));
    }

    /**
     * 
     * @param line1
     * @param line2
     * @return
     */
    public static Line2D getMedianLine(Line2D line1, Line2D line2) {
        double Ax = line1.getX1(), Ay = line1.getY1();
        double Bx = line1.getX2(), By = line1.getY2();
        double Cx = line2.getX1(), Cy = line2.getY1();
        double Dx = line2.getX2(), Dy = line2.getY2();
        double Mx = (Ax + Cx) / 2;
        double My = (Ay + Cy) / 2;
        double Nx = (Bx + Dx) / 2;
        double Ny = (By + Dy) / 2;
        return new Line2D.Double(Mx, My, Nx, Ny);
    }

    public static Line2D getMedianLine(Point2D A, Point2D B, Point2D C, Point2D D) {
        return getMedianLine(new Line2D.Double(A, B), new Line2D.Double(C, D));
    }

    /**
     * 
     * @param A
     * @param B
     * @param C
     * @param D
     * @return
     */
    public static Point2D getIntersectPoint(Point2D A, Point2D B, Point2D C, Point2D D) {
        Point2D P = null;

        double Ax = A.getX(), Ay = A.getY();
        double Bx = B.getX(), By = B.getY();
        double Cx = C.getX(), Cy = C.getY();
        double Dx = D.getX(), Dy = D.getY();

        double denominator = (Bx - Ax) * (Dy - Cy) - (By - Ay) * (Dx - Cx);

        if (denominator != 0) {
            double r = ((Ay - Cy) * (Dx - Cx) - (Ax - Cx) * (Dy - Cy)) / denominator; // equ1
            double s = ((Ay - Cy) * (Bx - Ax) - (Ax - Cx) * (By - Ay)) / denominator; // equ2
            P = new Point2D.Double(Ax + r * (Bx - Ax), Ay + r * (By - Ay));
        }

        return P;
    }

    public static Point2D getIntersectPoint(Line2D line1, Line2D line2) {
        return getIntersectPoint(line1.getP1(), line1.getP2(), line2.getP1(), line2.getP2());
    }

    /**
     * 
     * @param A
     * @param B
     * @param C
     * @return
     */

    public static Point2D getPerpendicularPointToLine(Point2D A, Point2D B, Point2D C) {
        double Ax = A.getX(), Ay = A.getY();
        double Bx = B.getX(), By = B.getY();
        double Cx = C.getX(), Cy = C.getY();

        double r = ((Ay - Cy) * (Ay - By) + (Ax - Cx) * (Ax - Bx)) / Point2D.distanceSq(Ax, Ay, Bx, By);

        return new Point2D.Double(Ax + r * (Bx - Ax), Ay + r * (By - Ay));
    }

    public static Point2D getPerpendicularPointToLine(Line2D L, Point2D C) {
        return getPerpendicularPointToLine(L.getP1(), L.getP2(), C);
    }

    /**
     * Find a point at a given perpendicular distance from a line
     * 
     * @param A
     *            Start of line segment
     * @param B
     *            End of line segment
     * @param P
     *            Point of AB line
     * @param distPC
     *            Distance from line to return Point C <br>
     *            If >0 angle between AB and PC is +90° <br>
     *            If <0 angle between AB and PC is -+90°
     * @return C point
     */
    public static Point2D getPerpendicularPointFromLine(Point2D A, Point2D B, Point2D P, double distPC) {
        double AB = A.distance(B);
        double ux = -(B.getY() - A.getY()) / AB;
        double uy = (B.getX() - A.getX()) / AB;

        return new Point2D.Double(P.getX() + distPC * ux, P.getY() + distPC * uy);
    }

    public static Point2D getPerpendicularPointFromLine(Point2D A, Point2D B, double distAP, double distPC) {
        return getPerpendicularPointFromLine(A, B, getColinearPointWithLength(A, B, distAP), distPC);
    }

    /**
     * 
     * @param A
     *            Start of line segment
     * @param B
     *            End of line segment
     * @param dist
     *            Distance from AB line to the parallel CD line <br>
     *            If >0 angle between AB and AC is +90° <br>
     *            If <0 angle between AB and AC is -+90°
     * @return
     */
    public static Line2D getParallelLine(Point2D A, Point2D B, double dist) {
        double AB = A.distanceSq(B);
        double ux = -(B.getY() - A.getY()) / AB;
        double uy = (B.getX() - A.getX()) / AB;

        Point2D C = new Point2D.Double(A.getX() + dist * ux, A.getY() + dist * uy);
        Point2D D = new Point2D.Double(B.getX() + dist * ux, B.getY() + dist * uy);

        return new Line2D.Double(C, D);
    }

    /**
     * 
     * @param ptList
     * @return
     */
    public static Point2D getCircleCenter(List<Point2D> ptList) {
        switch (ptList.size()) {
            case 3:
                return getCircleCenter(ptList.get(0), ptList.get(1), ptList.get(2));
            case 2:
                return new Point2D.Double((ptList.get(0).getX() + ptList.get(1).getX()) / 2.0,
                    (ptList.get(0).getY() + ptList.get(1).getY()) / 2.0);
                // case 1:
                // return ptList.get(0);
            default:
                return null;
        }
    }

    public static Point2D getCircleCenter(Point2D a, Point2D b, Point2D c) {
        double ax = a.getX();
        double ay = a.getY();
        double bx = b.getX();
        double by = b.getY();
        double cx = c.getX();
        double cy = c.getY();

        double A = bx - ax;
        double B = by - ay;
        double C = cx - ax;
        double D = cy - ay;
        double E = A * (ax + bx) + B * (ay + by);
        double F = C * (ax + cx) + D * (ay + cy);

        double G = 2 * (A * (cy - by) - B * (cx - bx));
        if (G == 0.0)
            return null; // a, b, c must be collinear

        double px = (D * E - B * F) / G;
        double py = (A * F - C * E) / G;

        return new Point2D.Double(px, py);
    }

    /**
     * Extract scaling from AffineTransform<br>
     * Lets assume that the AffineTransform is a composite of scales, translates, and rotates. <br>
     * No independent shear has to be applied and scaling must be uniform along the two axes.
     * 
     * @param transform
     *            current AffineTransform
     */
    public static double extractScalingFactor(AffineTransform transform) {
        double scalingFactor = 1;

        if ((transform != null)) {
            double sx = transform.getScaleX();
            double shx = transform.getShearX();
            if (sx != 0 || shx != 0)
                scalingFactor = Math.sqrt(sx * sx + shx * shx);
            // scalingFactor = Math.sqrt(Math.pow(sx, 2) + Math.pow(shx, 2));
        }

        return scalingFactor;
    }

    /**
     * 
     * Do a scaling transformation around the anchor point
     * 
     * @param shape
     * @param scalingFactor
     * @param anchorPoint
     * @return
     */
    public static Shape getScaledShape(Shape shape, double scalingFactor, Point2D anchorPoint) {
        if (shape == null)
            return null;

        AffineTransform scaleTransform = new AffineTransform(); // Identity transformation.

        if (scalingFactor != 1) {
            if (anchorPoint != null)
                scaleTransform.translate(anchorPoint.getX(), anchorPoint.getY());

            scaleTransform.scale(scalingFactor, scalingFactor);

            if (anchorPoint != null)
                scaleTransform.translate(-anchorPoint.getX(), -anchorPoint.getY());
        }

        return scaleTransform.createTransformedShape(shape);
    }

    /**
     * 
     * @param A
     * @param B
     * @param growingSize
     * @return
     */
    public static Shape getBoundingShapeOfSegment(Point2D A, Point2D B, double growingSize) {

        Path2D path = new Path2D.Double();

        Point2D tPoint = GeomUtil.getPerpendicularPointFromLine(A, B, -growingSize, growingSize);
        path.moveTo(tPoint.getX(), tPoint.getY());

        tPoint = GeomUtil.getPerpendicularPointFromLine(A, B, -growingSize, -growingSize);
        path.lineTo(tPoint.getX(), tPoint.getY());

        tPoint = GeomUtil.getPerpendicularPointFromLine(B, A, -growingSize, growingSize);
        path.lineTo(tPoint.getX(), tPoint.getY());

        tPoint = GeomUtil.getPerpendicularPointFromLine(B, A, -growingSize, -growingSize);
        path.lineTo(tPoint.getX(), tPoint.getY());

        path.closePath();
        return path;
    }

    // Not Tested
    // public static Shape getBoundingShapeOfSegment2(Point2D A, Point2D B, double growingSize) {
    //
    // Path2D path = new Path2D.Double();
    //
    // double dAB = A.distance(B);
    // double dxu = B.getX() - A.getX() / dAB;
    // double dyu = B.getY() - A.getY() / dAB;
    //
    // AffineTransform t1, t2, t3, t4;
    // Point2D tPoint;
    //
    // t1 = AffineTransform.getTranslateInstance(-dyu * growingSize, dxu * growingSize);// rot +90° CW
    // t2 = AffineTransform.getTranslateInstance(dyu * growingSize, -dxu * growingSize); // rot -90° CCW
    // t3 = AffineTransform.getTranslateInstance(-dxu * growingSize, 0);
    // t4 = AffineTransform.getTranslateInstance(dxu * growingSize, 0);
    //
    // tPoint = t1.transform(A, null);
    // tPoint = t3.transform(tPoint, tPoint);
    // path.moveTo(tPoint.getX(), tPoint.getY());
    //
    // tPoint = t2.transform(A, null);
    // tPoint = t3.transform(tPoint, tPoint);
    // path.lineTo(tPoint.getX(), tPoint.getY());
    //
    // tPoint = t2.transform(B, null);
    // tPoint = t4.transform(tPoint, tPoint);
    // path.lineTo(tPoint.getX(), tPoint.getY());
    //
    // tPoint = t1.transform(B, null);
    // tPoint = t4.transform(tPoint, tPoint);
    // path.lineTo(tPoint.getX(), tPoint.getY());
    //
    // path.closePath();
    //
    // return path;
    // }

}
