package org.weasis.core.api.gui.util;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

public final class GeomUtil {

    public static boolean isLineValid(Point2D ptA, Point2D ptB) {
        return (ptA != null && ptB != null && !ptA.equals(ptB));
    }

    /**
     * @return angle between BA & BC line segment in Degree <br>
     *         0 is returned if any argument is invalid
     */
    public static double getAngleRad(Point2D ptA, Point2D ptB, Point2D ptC) {
        if (ptA != null && ptB != null && ptC != null)
            return getAngleRad(ptB, ptC) - getAngleRad(ptB, ptA);
        return 0;
    }

    /**
     * @return angle between BA & BC line segment in Radiant<br>
     *         0 is returned if any argument is invalid
     */
    public static double getAngleDeg(Point2D ptA, Point2D ptB, Point2D ptC) {
        if (ptA != null && ptB != null && ptC != null)
            return Math.toDegrees(getAngleRad(ptA, ptB, ptC));
        return 0;
    }

    /**
     * Compute angle into image system basis where positive angle are defined in a ClockWise orientation<br>
     * Note : angle should be computed with "Math.atan2(ptB.getY() - ptA.getY(), ptB.getX() - ptA.getX())" in an
     * ortho-normal basis system where positive angle is defined in a CounterClockWise orientation.
     * 
     * @return angle of AB line segment in radiant<br>
     *         0 is returned if any argument is invalid
     */

    public static double getAngleRad(Point2D ptA, Point2D ptB) {
        return (ptA != null && ptB != null) ? Math.atan2(ptA.getY() - ptB.getY(), ptB.getX() - ptA.getX()) : null;
    }

    /**
     * @return angle of AB line segment in radiant<br>
     *         0 is returned if any argument is invalid
     */
    public static double getAngleDeg(Point2D ptA, Point2D ptB) {
        return (ptA != null && ptB != null) ? Math.toDegrees(getAngleRad(ptA, ptB)) : null;
    }

    /**
     * @param angle
     *            in Radiant
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
     *            in Degree
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
     * @return midPoint or null if any argument is invalid
     */
    public static Point2D getMidPoint(Point2D ptA, Point2D ptB) {
        if (ptA != null && ptB != null)
            return new Point2D.Double((ptA.getX() + ptB.getX()) / 2.0, (ptA.getY() + ptB.getY()) / 2.0);
        return null;
    }

    /**
     * @param ptA
     *            the first point of line segment
     * @param ptB
     *            the last point of line segment
     * @param newLength
     *            represents length from ptA to the return point ptC along AB line segment.<br>
     *            If >AB , ptC point will be located on extension of AB<br>
     *            If <0 , ptC point will be located on extension of BA <br>
     *            If >0 && < AB , ptC point will be interior of AB<br>
     * @return New point ptC coordinates or null if any argument is invalid
     */
    public static Point2D getColinearPointWithLength(Point2D ptA, Point2D ptB, double newLength) {
        if (ptA != null && ptB != null)
            return getColinearPointWithRatio(ptA, ptB, newLength / ptA.distance(ptB));
        return null;
    }

    /**
     * @param ptA
     *            first point of line segment
     * @param ptB
     *            last point of line segment
     * @param k
     *            represents ratio between AB and AC, ptC being the returned point along AB line segment.<br>
     *            If >1 , ptC point will be located on extension of AB<br>
     *            If <0 , ptC point will be located on extension of BA <br>
     *            If >0 && <AB , ptC point will be interior of AB<br>
     * @return New point ptC coordinates or null if any argument is invalid
     */
    public static Point2D getColinearPointWithRatio(Point2D ptA, Point2D ptB, double k) {
        if (ptA != null && ptB != null)
            return new Point2D.Double(ptB.getX() * k + ptA.getX() * (1 - k), ptB.getY() * k + ptA.getY() * (1 - k));
        return null;
    }

    /**
     * @return median line or null if any argument is invalid
     */
    public static Line2D getMedianLine(Line2D line1, Line2D line2) {
        if (line1 == null || line2 == null)
            return null;

        Point2D ptA = line1.getP1(), ptB = line1.getP2();
        Point2D ptC = line2.getP1(), ptD = line2.getP2();

        Line2D line3 = new Line2D.Double(ptA, ptC);
        Line2D line4 = new Line2D.Double(ptB, ptD);

        Point2D ptM, ptN;

        if (line3.intersectsLine(line4)) {
            ptM = new Point2D.Double((ptA.getX() + ptD.getX()) / 2, (ptA.getY() + ptD.getY()) / 2);
            ptN = new Point2D.Double((ptB.getX() + ptC.getX()) / 2, (ptB.getY() + ptC.getY()) / 2);
        } else {
            ptM = new Point2D.Double((ptA.getX() + ptC.getX()) / 2, (ptA.getY() + ptC.getY()) / 2);
            ptN = new Point2D.Double((ptB.getX() + ptD.getX()) / 2, (ptB.getY() + ptD.getY()) / 2);
        }

        return new Line2D.Double(ptM, ptN);
    }

    /**
     * @return median line or null if any argument is invalid
     */

    public static Line2D getMedianLine(Point2D ptA, Point2D ptB, Point2D ptC, Point2D ptD) {
        if (ptA == null || ptB == null || ptC == null || ptD == null)
            return null;
        return getMedianLine(new Line2D.Double(ptA, ptB), new Line2D.Double(ptC, ptD));
    }

    /**
     * Let ptA,ptB,ptC,ptD be 2-space position vectors. .......
     * 
     * @return null if segment lines are parallel
     */
    public static Point2D getIntersectPoint(Point2D ptA, Point2D ptB, Point2D ptC, Point2D ptD) {
        if (ptA == null || ptB == null || ptC == null || ptD == null)
            return null;

        Point2D ptP = null;

        double denominator =
            (ptB.getX() - ptA.getX()) * (ptD.getY() - ptC.getY()) - (ptB.getY() - ptA.getY())
                * (ptD.getX() - ptC.getX());

        if (denominator != 0) {
            double numerator =
                (ptA.getY() - ptC.getY()) * (ptD.getX() - ptC.getX()) - (ptA.getX() - ptC.getX())
                    * (ptD.getY() - ptC.getY());

            double r = numerator / denominator;

            ptP =
                new Point2D.Double(ptA.getX() + r * (ptB.getX() - ptA.getX()), ptA.getY() + r
                    * (ptB.getY() - ptA.getY()));
        }

        return ptP;
    }

    /**
     * @return
     */
    public static Point2D getIntersectPoint(Line2D line1, Line2D line2) {
        if (line1 == null || line2 == null)
            return null;
        return getIntersectPoint(line1.getP1(), line1.getP2(), line2.getP1(), line2.getP2());
    }

    /**
     * @return
     */
    public static boolean lineParallel(Point2D ptA, Point2D ptB, Point2D ptC, Point2D ptD) {
        if (ptA == null || ptB == null || ptC == null || ptD == null)
            throw new IllegalArgumentException("All the points must not be null");

        if (((ptB.getX() - ptA.getX()) * (ptD.getY() - ptC.getY()) - (ptB.getY() - ptA.getY())
            * (ptD.getX() - ptC.getX())) == 0)
            return true;

        return false;
    }

    /**
     * @return
     */
    public static boolean lineColinear(Point2D ptA, Point2D ptB, Point2D ptC, Point2D ptD) {
        if (lineParallel(ptA, ptB, ptC, ptD))
            if (((ptA.getY() - ptC.getY()) * (ptD.getX() - ptC.getX()) - (ptA.getX() - ptC.getX())
                * (ptD.getY() - ptC.getY())) == 0)
                return true;
        return false;
    }

    /**
     * @return
     */
    public static Point2D getPerpendicularPointToLine(Point2D ptA, Point2D ptB, Point2D ptC) {
        if (ptA == null || ptB == null || ptA.equals(ptB) || ptC == null)
            return null;

        double ax = ptA.getX(), ay = ptA.getY();
        double bx = ptB.getX(), by = ptB.getY();
        double cx = ptC.getX(), cy = ptC.getY();

        double r = ((ay - cy) * (ay - by) + (ax - cx) * (ax - bx)) / Point2D.distanceSq(ax, ay, bx, by);

        return new Point2D.Double(ax + r * (bx - ax), ay + r * (by - ay));
    }

    public static Point2D getPerpendicularPointToLine(Line2D line, Point2D ptC) {
        if (line == null || ptC == null)
            return null;
        return getPerpendicularPointToLine(line.getP1(), line.getP2(), ptC);
    }

    /**
     * Find a point at a given perpendicular distance from a line
     * 
     * @param ptA
     *            Start of line segment
     * @param ptB
     *            End of line segment
     * @param ptP
     *            Point of AB line
     * @param distPC
     *            Distance from line to return Point ptC <br>
     *            If >0 angle between AB and PC is +90° <br>
     *            If <0 angle between AB and PC is -+90°
     * @return ptC point
     */
    public static Point2D getPerpendicularPointFromLine(Point2D ptA, Point2D ptB, Point2D ptP, double distPC) {
        if (ptA == null || ptB == null || ptA.equals(ptB) || ptP == null)
            return null;

        double distAB = ptA.distance(ptB);
        double ux = -(ptB.getY() - ptA.getY()) / distAB;
        double uy = (ptB.getX() - ptA.getX()) / distAB;

        return new Point2D.Double(ptP.getX() + distPC * ux, ptP.getY() + distPC * uy);
    }

    public static Point2D getPerpendicularPointFromLine(Point2D ptA, Point2D ptB, double distAP, double distPC) {
        return getPerpendicularPointFromLine(ptA, ptB, getColinearPointWithLength(ptA, ptB, distAP), distPC);
    }

    /**
     * 
     * @param ptA
     *            Start of line segment
     * @param ptB
     *            End of line segment
     * @param dist
     *            Distance from AB line to the parallel CD line <br>
     *            If >0 angle between AB and AC is +90° <br>
     *            If <0 angle between AB and AC is -+90°
     * @return
     */
    public static Line2D getParallelLine(Point2D ptA, Point2D ptB, double dist) {
        if (ptA == null || ptB == null || ptA.equals(ptB))
            return null;

        double distAB2 = ptA.distanceSq(ptB);
        double ux = -(ptB.getY() - ptA.getY()) / distAB2;
        double uy = (ptB.getX() - ptA.getX()) / distAB2;

        Point2D ptC = new Point2D.Double(ptA.getX() + dist * ux, ptA.getY() + dist * uy);
        Point2D ptD = new Point2D.Double(ptB.getX() + dist * ux, ptB.getY() + dist * uy);

        return new Line2D.Double(ptC, ptD);
    }

    /**
     * 
     * @param ptList
     * @return
     */
    public static Point2D getCircleCenter(List<Point2D> ptList) {
        if (ptList == null)
            return null;

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

    /**
     * @return
     */
    public static Point2D getCircleCenter(Point2D ptA, Point2D ptB, Point2D ptC) {
        if (ptA == null || ptB == null || ptC == null)
            return null;

        double ax = ptA.getX();
        double ay = ptA.getY();
        double bx = ptB.getX();
        double by = ptB.getY();
        double cx = ptC.getX();
        double cy = ptC.getY();

        double c1 = bx - ax;
        double c2 = by - ay;
        double c3 = cx - ax;
        double c4 = cy - ay;
        double c5 = c1 * (ax + bx) + c2 * (ay + by);
        double c6 = c3 * (ax + cx) + c4 * (ay + cy);

        double denom = 2 * (c1 * (cy - by) - c2 * (cx - bx));

        if (denom == 0.0)
            return null; // a, b, c must be collinear

        double px = (c4 * c5 - c2 * c6) / denom;
        double py = (c1 * c6 - c3 * c5) / denom;

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

        if (transform != null) {
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

        if (transform != null) {
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
    public static Shape getScaledShape(final Shape shape, double scalingFactor, Point2D anchorPoint) {
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

    /**
     * @return
     */
    public static Rectangle2D getScaledRectangle(final Rectangle2D rect, double scalingFactor) {
        Rectangle2D newRect = null;

        if (rect != null && scalingFactor != 1) {
            double resizedWidth = rect.getWidth() * scalingFactor;
            double resizedHeight = rect.getHeight() * scalingFactor;

            newRect = (Rectangle2D) rect.clone();
            newRect.setRect(rect.getX(), rect.getY(), resizedWidth, resizedHeight);
        }

        return newRect;
    }

    /**
     * @return
     */

    public static Shape getCornerShape(Point2D ptA, Point2D ptO, Point2D ptB, double cornerSize) {
        if (ptA == null || ptO == null || ptB == null || ptA.equals(ptO) || ptB.equals(ptO))
            return null;

        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 2);

        Point2D ptI1 = GeomUtil.getColinearPointWithLength(ptO, ptA, cornerSize);
        Point2D ptI2 = GeomUtil.getColinearPointWithLength(ptO, ptB, cornerSize);

        double rotSignum = Math.signum(GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(ptB, ptO, ptA)));
        Point2D ptI3 = GeomUtil.getPerpendicularPointFromLine(ptO, ptA, ptI1, rotSignum * cornerSize);

        path.append(new Line2D.Double(ptI1, ptI3), false);
        path.append(new Line2D.Double(ptI2, ptI3), false);

        return path;
    }

    /**
     * @return
     */
    public static Rectangle2D getGrowingRectangle(Rectangle2D rect, double growingSize) {
        Rectangle2D growingRect = rect != null ? (Rectangle2D) rect.clone() : null;
        growRectangle(growingRect, growingSize);
        return growingRect;
    }

    /**
     * @param growingSize
     */
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
