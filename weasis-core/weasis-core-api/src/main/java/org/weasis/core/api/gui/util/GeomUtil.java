package org.weasis.core.api.gui.util;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

public final class GeomUtil {

    /**
     * @param A
     * @param B
     * @param C
     * @return
     */
    public static double getAngleRad(Point2D A, Point2D B, Point2D C) {
        if (A != null && B != null && C != null)
            return getAngleRad(B, C) - getAngleRad(B, A);
        return 0;
    }

    public static double getAngleDeg(Point2D A, Point2D B, Point2D C) {
        if (A != null && B != null && C != null)
            return Math.toDegrees(getAngleRad(A, B, C));
        return 0;
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
        if (Math.abs(angle) > Math.PI) {
            angle -= Math.signum(angle) * 2.0 * Math.PI;
        }
        return angle;
    }

    /**
     * @param angle
     *            in Degrees
     * @return angle in the range of [ -180 ; 180 ]
     */
    public static double getSmallestRotationAngleDeg(double angle) {
        angle = angle % 360.0;
        if (Math.abs(angle) > 180.0) {
            angle -= Math.signum(angle) * 360.0;
        }
        return angle;
    }

    /**
     * @param A
     * @param B
     * @return
     */
    public static Point2D getMidPoint(Point2D A, Point2D B) {
        if (A != null && B != null)
            return new Point2D.Double((A.getX() + B.getX()) / 2.0, (A.getY() + B.getY()) / 2.0);
        return null;
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
        if (A != null && B != null)
            return getColinearPointWithRatio(A, B, newLength / A.distance(B));
        return null;
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
        if (A != null && B != null)
            return new Point2D.Double(B.getX() * k + A.getX() * (1 - k), B.getY() * k + A.getY() * (1 - k));
        return null;
    }

    /**
     * 
     * @param line1
     * @param line2
     * @return
     */
    public static Line2D getMedianLine(Line2D line1, Line2D line2) {
        if (line1 == null || line2 == null)
            return null;

        Point2D A = line1.getP1(), B = line1.getP2();
        Point2D C = line2.getP1(), D = line2.getP2();

        Line2D line3 = new Line2D.Double(A, C);
        Line2D line4 = new Line2D.Double(B, D);

        Point2D M, N;

        if (line3.intersectsLine(line4)) {
            M = new Point2D.Double((A.getX() + D.getX()) / 2, (A.getY() + D.getY()) / 2);
            N = new Point2D.Double((B.getX() + C.getX()) / 2, (B.getY() + C.getY()) / 2);
        } else {
            M = new Point2D.Double((A.getX() + C.getX()) / 2, (A.getY() + C.getY()) / 2);
            N = new Point2D.Double((B.getX() + D.getX()) / 2, (B.getY() + D.getY()) / 2);
        }

        return new Line2D.Double(M, N);
    }

    public static Line2D getMedianLine(Point2D A, Point2D B, Point2D C, Point2D D) {
        return getMedianLine(new Line2D.Double(A, B), new Line2D.Double(C, D));
    }

    /**
     * 
     * Let A,B,C,D be 2-space position vectors. .......
     * 
     * @param A
     * @param B
     * @param C
     * @param D
     * @return null if segment lines are parallel
     */
    public static Point2D getIntersectPoint(Point2D A, Point2D B, Point2D C, Point2D D) {
        Point2D P = null;

        double denominator =
            (B.getX() - A.getX()) * (D.getY() - C.getY()) - (B.getY() - A.getY()) * (D.getX() - C.getX());

        if (denominator != 0) {
            double numerator =
                (A.getY() - C.getY()) * (D.getX() - C.getX()) - (A.getX() - C.getX()) * (D.getY() - C.getY());

            double r = numerator / denominator;

            P = new Point2D.Double(A.getX() + r * (B.getX() - A.getX()), A.getY() + r * (B.getY() - A.getY()));
        }

        return P;
    }

    public static Point2D getIntersectPoint(Line2D line1, Line2D line2) {
        return getIntersectPoint(line1.getP1(), line1.getP2(), line2.getP1(), line2.getP2());
    }

    public static boolean lineParallel(Point2D A, Point2D B, Point2D C, Point2D D) {
        if (((B.getX() - A.getX()) * (D.getY() - C.getY()) - (B.getY() - A.getY()) * (D.getX() - C.getX())) == 0)
            return true;

        return false;
    }

    public static boolean lineColinear(Point2D A, Point2D B, Point2D C, Point2D D) {
        if (lineParallel(A, B, C, D))
            if (((A.getY() - C.getY()) * (D.getX() - C.getX()) - (A.getX() - C.getX()) * (D.getY() - C.getY())) == 0)
                return true;

        return false;
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
     * Let assume that the AffineTransform is a composite of scales, translates, and rotates. <br>
     * No independent shear has to be applied and scaling must be uniform along the two axes.
     * 
     * @param transform
     *            current AffineTransform
     */
    public static double extractScalingFactor(AffineTransform transform) {
        double scalingFactor = 1.0;

        if ((transform != null)) {
            double sx = transform.getScaleX();
            double shx = transform.getShearX();
            if (sx != 0 || shx != 0) {
                scalingFactor = Math.sqrt(sx * sx + shx * shx);
            }
        }

        return scalingFactor;
    }

    /**
     * Extract rotation Angle from a given AffineTransform Matrix.<br>
     * This function handle cases of mirror image flip about some axis. This changes right handed coordinate system into
     * a left handed system. Hence, returned angle has an opposite value.
     * 
     * @param transform
     * @return angle in the range of [ -PI ; PI ]
     */
    public static double extractAngleRad(AffineTransform transform) {
        double angleRad = 0.0;

        if ((transform != null)) {
            double sinTheta = transform.getShearY();
            double cosTheta = transform.getScaleX();

            angleRad = Math.atan2(sinTheta, cosTheta);

            if ((transform.getType() & AffineTransform.TYPE_FLIP) != 0) {
                angleRad *= -1.0;
            }
        }

        return angleRad;
    }

    // public static double extractRotationAngleLegacy(AffineTransform transform) {
    // double rotationAngle = 0.0;
    //
    // if ((transform != null)) {
    // Point2D pt1 = new Point2D.Double(1, 0);
    // Point2D pt2 = transform.deltaTransform(pt1, null);
    // rotationAngle = GeomUtil.getAngleRad(pt2, new Point2D.Double(0, 0), pt1);
    // }
    //
    // return rotationAngle;
    // }

    /**
     * 
     * Do a scaling transformation around the anchor point
     * 
     * @param shape
     * @param scalingFactor
     * @param anchorPoint
     *            can be null
     * @return null if either shape is null or scaling factor is zero
     */
    public static Shape getScaledShape(Shape shape, double scalingFactor, Point2D anchorPoint) {
        if (shape == null || scalingFactor == 0)
            return null;

        AffineTransform scaleTransform = new AffineTransform(); // Identity transformation.

        if (scalingFactor != 1) {
            if (anchorPoint != null) {
                scaleTransform.translate(anchorPoint.getX(), anchorPoint.getY());
            }

            scaleTransform.scale(scalingFactor, scalingFactor);

            if (anchorPoint != null) {
                scaleTransform.translate(-anchorPoint.getX(), -anchorPoint.getY());
            }
        }

        return scaleTransform.createTransformedShape(shape);
    }

    public static Rectangle2D getScaledRectangle(Rectangle2D rect, double scalingFactor) {
        if (rect == null)
            return null;

        if (scalingFactor != 1) {
            double resizedWidth = rect.getWidth() * scalingFactor;
            double resizedHeight = rect.getHeight() * scalingFactor;
            rect.setRect(rect.getX(), rect.getY(), resizedWidth, resizedHeight);
        }

        return rect;
    }

    public static Shape getCornerShape(Point2D A, Point2D O, Point2D B, double cornerSize) {
        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 2);

        Point2D I1 = GeomUtil.getColinearPointWithLength(O, A, cornerSize);
        Point2D I2 = GeomUtil.getColinearPointWithLength(O, B, cornerSize);

        double rotSignum = Math.signum(GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(B, O, A)));
        Point2D I3 = GeomUtil.getPerpendicularPointFromLine(O, A, I1, rotSignum * cornerSize);

        path.append(new Line2D.Double(I1, I3), false);
        path.append(new Line2D.Double(I2, I3), false);

        return path;

    }

    public static Rectangle2D getGrowingRectangle(Rectangle2D rect, double growingSize) {
        Rectangle2D growingRect = rect != null ? (Rectangle2D) rect.clone() : null;
        growRectangle(growingRect, growingSize);
        return growingRect;
    }

    public static void growRectangle(Rectangle2D rect, double growingSize) {
        if (rect == null)
            return;

        if (growingSize != 0) {
            double newX = rect.getX() - growingSize;
            double newY = rect.getY() - growingSize;
            double newWidth = rect.getWidth() + (2.0 * growingSize);
            double newHeight = rect.getHeight() + (2.0 * growingSize);
            rect.setRect(newX, newY, newWidth, newHeight);
        }
    }
}
