/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.weasis.core.util.MathUtil;

public final class GeomUtil {

  private GeomUtil() {}

  public static boolean isLineValid(Point2D ptA, Point2D ptB) {
    return ptA != null && ptB != null && !ptA.equals(ptB);
  }

  /**
   * @return angle between BA & BC line segment in Degree <br>
   *     0 is returned if any argument is invalid
   */
  public static double getAngleRad(Point2D ptA, Point2D ptB, Point2D ptC) {
    if (ptA != null && ptB != null && ptC != null) {
      return getAngleRad(ptB, ptC) - getAngleRad(ptB, ptA);
    }
    return 0;
  }

  /**
   * @return angle between BA & BC line segment in Radiant<br>
   *     0 is returned if any argument is invalid
   */
  public static double getAngleDeg(Point2D ptA, Point2D ptB, Point2D ptC) {
    if (ptA != null && ptB != null && ptC != null) {
      return Math.toDegrees(getAngleRad(ptA, ptB, ptC));
    }
    return 0;
  }

  /**
   * Compute angle into image system basis where positive angle are defined in a ClockWise
   * orientation<br>
   * Note : angle should be computed with "Math.atan2(ptB.getY() - ptA.getY(), ptB.getX() -
   * ptA.getX())" in an ortho-normal basis system where positive angle is defined in a
   * CounterClockWise orientation.
   *
   * @return angle of AB line segment in radiant<br>
   *     0 is returned if any argument is invalid
   */
  public static double getAngleRad(Point2D ptA, Point2D ptB) {
    return Math.atan2(ptA.getY() - ptB.getY(), ptB.getX() - ptA.getX());
  }

  /**
   * @return angle of AB line segment in radiant<br>
   *     0 is returned if any argument is invalid
   */
  public static double getAngleDeg(Point2D ptA, Point2D ptB) {
    return Math.toDegrees(getAngleRad(ptA, ptB));
  }

  /**
   * @param angle in Radiant
   * @return angle in the range of [ -pi ; pi ]
   */
  public static double getSmallestRotationAngleRad(double angle) {
    double a = angle % (2 * Math.PI);
    if (Math.abs(a) > Math.PI) {
      a -= Math.signum(a) * (2.0 * Math.PI);
    }
    return a;
  }

  /**
   * @param angle in Degree
   * @return angle in the range of [ -180 ; 180 ]
   */
  public static double getSmallestRotationAngleDeg(double angle) {
    double a = angle % 360.0;
    if (Math.abs(a) > 180.0) {
      a -= Math.signum(a) * 360.0;
    }
    return a;
  }

  /**
   * @param angle in Radiant
   * @return angle in the range of [ -pi ; pi ]
   */
  public static double getSmallestAngleRad(double angle) {
    double a = angle % Math.PI;
    if (Math.abs(a) > (Math.PI / 2.0)) {
      a -= Math.signum(a) * Math.PI;
    }
    return a;
  }

  /**
   * @param angle in Degree
   * @return angle in the range of [ -90 ; 90 ]
   */
  public static double getSmallestAngleDeg(double angle) {
    double a = angle % 180.0;
    if (Math.abs(a) > 90.0) {
      a -= Math.signum(a) * 180.0;
    }
    return a;
  }

  /**
   * @return midPoint or null if any argument is invalid
   */
  public static Point2D getMidPoint(Point2D ptA, Point2D ptB) {
    if (ptA != null && ptB != null) {
      return new Point2D.Double((ptA.getX() + ptB.getX()) / 2.0, (ptA.getY() + ptB.getY()) / 2.0);
    }
    return null;
  }

  /**
   * @param ptA the first point of line segment
   * @param ptB the last point of line segment
   * @param newLength represents length from ptA to the return point ptC along AB line segment.<br>
   *     If >AB , ptC point will be located on extension of AB<br>
   *     If <0 , ptC point will be located on extension of BA <br>
   *     If >0 && < AB , ptC point will be interior of AB<br>
   * @return New point ptC coordinates or null if any argument is invalid
   */
  public static Point2D getColinearPointWithLength(Point2D ptA, Point2D ptB, double newLength) {
    if (ptA != null && ptB != null) {
      return getColinearPointWithRatio(ptA, ptB, newLength / ptA.distance(ptB));
    }
    return null;
  }

  /**
   * @param ptA first point of line segment
   * @param ptB last point of line segment
   * @param k represents ratio between AB and AC, ptC being the returned point along AB line
   *     segment.<br>
   *     If >1 , ptC point will be located on extension of AB<br>
   *     If <0 , ptC point will be located on extension of BA <br>
   *     If >0 && <AB , ptC point will be interior of AB<br>
   * @return New point ptC coordinates or null if any argument is invalid
   */
  public static Point2D.Double getColinearPointWithRatio(Point2D ptA, Point2D ptB, double k) {
    if (ptA != null && ptB != null) {
      return new Point2D.Double(
          ptB.getX() * k + ptA.getX() * (1 - k), ptB.getY() * k + ptA.getY() * (1 - k));
    }
    return null;
  }

  /**
   * @return median line or null if any argument is invalid
   */
  public static Line2D getMedianLine(Line2D line1, Line2D line2) {
    if (line1 == null || line2 == null) {
      return null;
    }

    Point2D ptA = line1.getP1();
    Point2D ptB = line1.getP2();
    Point2D ptC = line2.getP1();
    Point2D ptD = line2.getP2();

    Line2D line3 = new Line2D.Double(ptA, ptC);
    Line2D line4 = new Line2D.Double(ptB, ptD);

    Point2D ptM;
    Point2D ptN;

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
    if (ptA == null || ptB == null || ptC == null || ptD == null) {
      return null;
    }
    return getMedianLine(new Line2D.Double(ptA, ptB), new Line2D.Double(ptC, ptD));
  }

  /**
   * Let ptA,ptB,ptC,ptD be 2-space position vectors.
   *
   * @return intersect point. Null if segment lines are parallel
   */
  public static Point2D getIntersectPoint(Point2D ptA, Point2D ptB, Point2D ptC, Point2D ptD) {
    if (ptA == null || ptB == null || ptC == null || ptD == null) {
      return null;
    }

    Point2D.Double ptP = null;

    double denominator =
        (ptB.getX() - ptA.getX()) * (ptD.getY() - ptC.getY())
            - (ptB.getY() - ptA.getY()) * (ptD.getX() - ptC.getX());

    if (MathUtil.isDifferentFromZero(denominator)) {
      double numerator =
          (ptA.getY() - ptC.getY()) * (ptD.getX() - ptC.getX())
              - (ptA.getX() - ptC.getX()) * (ptD.getY() - ptC.getY());

      double r = numerator / denominator;

      ptP =
          new Point2D.Double(
              ptA.getX() + r * (ptB.getX() - ptA.getX()),
              ptA.getY() + r * (ptB.getY() - ptA.getY()));
    }

    return ptP;
  }

  public static Point2D getIntersectPoint(Line2D line1, Line2D line2) {
    if (line1 == null || line2 == null) {
      return null;
    }
    return getIntersectPoint(line1.getP1(), line1.getP2(), line2.getP1(), line2.getP2());
  }

  public static Point2D getIntersectPoint(Line2D line, Rectangle2D rect) {
    if (line == null || rect == null) {
      return null;
    }

    Point2D p =
        lineIntersection(
            line,
            new Line2D.Double(rect.getMinX(), rect.getMinY(), rect.getMaxX(), rect.getMinY()));
    if (p == null) {
      p =
          lineIntersection(
              line,
              new Line2D.Double(rect.getMinX(), rect.getMaxY(), rect.getMaxX(), rect.getMaxY()));
      if (p == null) {
        p =
            lineIntersection(
                line,
                new Line2D.Double(rect.getMinX(), rect.getMinY(), rect.getMinX(), rect.getMaxY()));
        if (p == null) {
          p =
              lineIntersection(
                  line,
                  new Line2D.Double(
                      rect.getMaxX(), rect.getMinY(), rect.getMaxX(), rect.getMaxY()));
        }
      }
    }
    return p;
  }

  private static Point2D lineIntersection(Line2D line1, Line2D line2) {
    if (line1.intersectsLine(line2)) {
      return GeomUtil.getIntersectPoint(line1, line2);
    }
    return null;
  }

  public static boolean lineParallel(Point2D ptA, Point2D ptB, Point2D ptC, Point2D ptD) {
    if (ptA == null || ptB == null || ptC == null || ptD == null) {
      throw new IllegalArgumentException("All the points must not be null");
    }
    return MathUtil.isEqualToZero(
        (ptB.getX() - ptA.getX()) * (ptD.getY() - ptC.getY())
            - (ptB.getY() - ptA.getY()) * (ptD.getX() - ptC.getX()));
  }

  public static boolean lineColinear(Point2D ptA, Point2D ptB, Point2D ptC, Point2D ptD) {
    if (lineParallel(ptA, ptB, ptC, ptD)) {
      return MathUtil.isEqualToZero(
          (ptA.getY() - ptC.getY()) * (ptD.getX() - ptC.getX())
              - (ptA.getX() - ptC.getX()) * (ptD.getY() - ptC.getY()));
    }
    return false;
  }

  public static Point2D getPerpendicularPointToLine(Point2D ptA, Point2D ptB, Point2D ptC) {
    if (ptA == null || ptB == null || ptA.equals(ptB) || ptC == null) {
      return null;
    }

    double ax = ptA.getX();
    double ay = ptA.getY();
    double bx = ptB.getX();
    double by = ptB.getY();
    double cx = ptC.getX();
    double cy = ptC.getY();

    double r = ((ay - cy) * (ay - by) + (ax - cx) * (ax - bx)) / Point2D.distanceSq(ax, ay, bx, by);

    return new Point2D.Double(ax + r * (bx - ax), ay + r * (by - ay));
  }

  public static Point2D getPerpendicularPointToLine(Line2D line, Point2D ptC) {
    if (line == null || ptC == null) {
      return null;
    }
    return getPerpendicularPointToLine(line.getP1(), line.getP2(), ptC);
  }

  /**
   * Find a point at a given perpendicular distance from a line
   *
   * @param ptA Start of line segment
   * @param ptB End of line segment
   * @param ptP Point of AB line
   * @param distPC Distance from line to return Point ptC <br>
   *     If >0 angle between AB and PC is +90° <br>
   *     If <0 angle between AB and PC is -+90°
   * @return perpendicular point
   */
  public static Point2D getPerpendicularPointFromLine(
      Point2D ptA, Point2D ptB, Point2D ptP, double distPC) {
    if (ptA == null || ptB == null || ptA.equals(ptB) || ptP == null) {
      return null;
    }

    double distAB = ptA.distance(ptB);
    double ux = -(ptB.getY() - ptA.getY()) / distAB;
    double uy = (ptB.getX() - ptA.getX()) / distAB;

    return new Point2D.Double(ptP.getX() + distPC * ux, ptP.getY() + distPC * uy);
  }

  public static Point2D getPerpendicularPointFromLine(
      Point2D ptA, Point2D ptB, double distAP, double distPC) {
    return getPerpendicularPointFromLine(
        ptA, ptB, getColinearPointWithLength(ptA, ptB, distAP), distPC);
  }

  /**
   * @param ptA Start of line segment
   * @param ptB End of line segment
   * @param dist Distance from AB line to the parallel CD line <br>
   * @return parallel line
   */
  public static Line2D getParallelLine(Point2D ptA, Point2D ptB, double dist) {
    if (ptA == null || ptB == null || ptA.equals(ptB)) {
      return null;
    }

    double abX = ptB.getX() - ptA.getX();
    double abY = ptB.getY() - ptA.getY();

    double perpSize = Math.sqrt(abX * abX + abY * abY);

    double sideVectorX = dist * -abY / perpSize;
    double sideVectorY = dist * abX / perpSize;

    Point2D ptC = new Point2D.Double(ptA.getX() + sideVectorX, ptA.getY() + sideVectorY);
    Point2D ptD = new Point2D.Double(ptB.getX() + sideVectorX, ptB.getY() + sideVectorY);

    return new Line2D.Double(ptC, ptD);
  }

  /**
   * @param ptList the list of Point2D
   * @return center point
   */
  public static Point2D getCircleCenter(List<Point2D> ptList) {
    if (ptList == null) {
      return null;
    }

    return switch (ptList.size()) {
      case 3 -> getCircleCenter(ptList.get(0), ptList.get(1), ptList.get(2));
      case 2 -> new Point2D.Double(
          (ptList.get(0).getX() + ptList.get(1).getX()) / 2.0,
          (ptList.get(0).getY() + ptList.get(1).getY()) / 2.0);
      default -> null;
    };
  }

  public static Point2D getCircleCenter(Point2D ptA, Point2D ptB, Point2D ptC) {
    if (ptA == null || ptB == null || ptC == null) {
      return null;
    }

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

    if (MathUtil.isEqualToZero(denom)) {
      return null; // a, b, c must be collinear
    }

    double px = (c4 * c5 - c2 * c6) / denom;
    double py = (c1 * c6 - c3 * c5) / denom;

    return new Point2D.Double(px, py);
  }

  /**
   * Extract scaling from AffineTransform<br>
   * Let assume that the AffineTransform is a composite of scales, translates, and rotates. <br>
   * No independent shear has to be applied and scaling must be uniform along the two axes.
   *
   * @param transform current AffineTransform
   */
  public static double extractScalingFactor(AffineTransform transform) {
    double scalingFactor = 1.0;

    if (transform != null) {
      double sx = transform.getScaleX();
      double shx = transform.getShearX();
      if (MathUtil.isDifferentFromZero(sx) || MathUtil.isDifferentFromZero(shx)) {
        scalingFactor = Math.sqrt(sx * sx + shx * shx);
      }
    }

    return scalingFactor;
  }

  /**
   * Extract rotation Angle from a given AffineTransform Matrix.<br>
   * This function handle cases of mirror image flip about some axis. This changes right-handed
   * coordinate system into a left-handed system. Hence, returned angle has an opposite value.
   *
   * @param transform the AffineTransform value
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

  /**
   * Do a scaling transformation around the anchor point
   *
   * @param shape the Shape value
   * @param scalingFactor the scaling factor
   * @param anchorPoint can be null
   * @return null if either shape is null or scaling factor is zero
   */
  public static Shape getScaledShape(final Shape shape, double scalingFactor, Point2D anchorPoint) {
    if (shape == null || MathUtil.isEqualToZero(scalingFactor)) {
      return null;
    }

    AffineTransform scaleTransform = new AffineTransform(); // Identity transformation.

    if (MathUtil.isDifferent(scalingFactor, 1.0)) {
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

  public static Rectangle2D getScaledRectangle(final Rectangle2D rect, double scalingFactor) {
    Rectangle2D newRect = null;

    if (rect != null && MathUtil.isDifferent(scalingFactor, 1.0)) {
      double resizedWidth = rect.getWidth() * scalingFactor;
      double resizedHeight = rect.getHeight() * scalingFactor;

      newRect = (Rectangle2D) rect.clone();
      newRect.setRect(rect.getX(), rect.getY(), resizedWidth, resizedHeight);
    }

    return newRect;
  }

  public static Shape getCornerShape(Point2D ptA, Point2D ptO, Point2D ptB, double cornerSize) {
    if (ptA == null || ptO == null || ptB == null || ptA.equals(ptO) || ptB.equals(ptO)) {
      return null;
    }

    Point2D ptI1 = GeomUtil.getColinearPointWithLength(ptO, ptA, cornerSize);
    Point2D ptI2 = GeomUtil.getColinearPointWithLength(ptO, ptB, cornerSize);

    double rotSignum =
        Math.signum(GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(ptB, ptO, ptA)));
    Point2D ptI3 = GeomUtil.getPerpendicularPointFromLine(ptO, ptA, ptI1, rotSignum * cornerSize);
    if (ptI3 == null) {
      return null;
    }

    Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 3);
    path.moveTo(ptI1.getX(), ptI1.getY());
    path.lineTo(ptI3.getX(), ptI3.getY());
    path.lineTo(ptI2.getX(), ptI2.getY());

    return path;
  }

  public static Shape getArrowShape(Point2D ptO, Point2D ptB, double length, double width) {
    if (ptO == null || ptB == null || ptB.equals(ptO)) {
      return null;
    }
    Point2D ptI2 = GeomUtil.getColinearPointWithLength(ptO, ptB, length);
    Point2D ptI3 = GeomUtil.getPerpendicularPointFromLine(ptO, ptB, ptI2, width / 2.0);
    if (ptI3 == null) {
      return null;
    }

    Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 5);
    path.moveTo(ptI2.getX(), ptI2.getY());
    path.lineTo(ptI3.getX(), ptI3.getY());
    path.lineTo(ptO.getX(), ptO.getY());
    path.lineTo(
        ptI2.getX() - (ptI3.getX() - ptI2.getX()), ptI2.getY() - (ptI3.getY() - ptI2.getY()));
    path.lineTo(ptI2.getX(), ptI2.getY());

    return path;
  }

  public static Rectangle2D getGrowingRectangle(Rectangle2D rect, double growingSize) {
    Rectangle2D growingRect = rect != null ? (Rectangle2D) rect.clone() : null;
    growRectangle(growingRect, growingSize);
    return growingRect;
  }

  public static void growRectangle(Rectangle2D rect, double growingSize) {
    if (rect == null) {
      return;
    }

    if (MathUtil.isDifferentFromZero(growingSize)) {
      double newX = rect.getX() - growingSize;
      double newY = rect.getY() - growingSize;
      double newWidth = rect.getWidth() + (2.0 * growingSize);
      double newHeight = rect.getHeight() + (2.0 * growingSize);
      rect.setRect(newX, newY, newWidth, newHeight);
    }
  }
}
