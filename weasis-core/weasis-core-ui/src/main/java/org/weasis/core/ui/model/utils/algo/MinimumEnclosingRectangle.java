/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.algo;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.weasis.core.util.MathUtil;

public class MinimumEnclosingRectangle {
  private final List<Point2D> points;
  private final boolean isConvex;

  private List<Point2D> cvxPts = null;
  private Line2D minDistSeg = new Line2D.Double();
  private Point2D minDistPt = null;
  private double minDist = 0.0;

  public MinimumEnclosingRectangle(List<Point2D> points) {
    this(points, false);
  }

  public MinimumEnclosingRectangle(List<Point2D> points, boolean isConvex) {
    this.points = points;
    this.isConvex = isConvex;
  }

  private void computeWidthConvex(List<Point2D> pts) {
    this.cvxPts = pts;

    int size = cvxPts.size();
    if (size == 0) {
      minDist = 0.0;
      minDistPt = null;
      minDistSeg = null;
    } else if (size == 1) {
      minDist = 0.0;
      minDistPt = cvxPts.get(0);
      minDistSeg.setLine(minDistPt.getX(), minDistPt.getY(), minDistPt.getX(), minDistPt.getY());
    } else if (size == 2 || size == 3) {
      minDist = 0.0;
      minDistPt = cvxPts.get(0);
      Point2D p2 = cvxPts.get(1);
      minDistSeg.setLine(minDistPt.getX(), minDistPt.getY(), p2.getX(), p2.getY());
    } else {
      searchRingMinDiameter(cvxPts);
    }
  }

  private void searchRingMinDiameter(List<Point2D> pts) {
    minDist = Double.MAX_VALUE;
    int curIndex = 1;

    for (int i = 0; i < pts.size() - 1; i++) {
      curIndex = findMaxPerpendicularDistanceFromAB(pts, pts.get(i), pts.get(i + 1), curIndex);
    }
  }

  private int findMaxPerpendicularDistanceFromAB(
      List<Point2D> pts, Point2D ptA, Point2D ptB, int startIndex) {
    double maxDist = distanceFromAB(pts.get(startIndex), ptA, ptB);
    double nextDist = maxDist;
    int maxIndex = startIndex;
    int nextIndex = maxIndex;

    while (nextDist >= maxDist) {
      maxDist = nextDist;
      maxIndex = nextIndex;
      nextIndex = nextIndex(pts, maxIndex);
      nextDist = distanceFromAB(pts.get(nextIndex), ptA, ptB);
    }

    if (maxDist < minDist) {
      minDist = maxDist;
      minDistPt = pts.get(maxIndex);
      minDistSeg = new Line2D.Double(ptA, ptB);
    }
    return maxIndex;
  }

  private static int nextIndex(List<Point2D> pts, int index) {
    int i = index + 1;
    return i >= pts.size() ? 0 : i;
  }

  public static double distanceFromAB(Point2D p, Point2D a, Point2D b) {
    double len2 =
        (b.getX() - a.getX()) * (b.getX() - a.getX())
            + (b.getY() - a.getY()) * (b.getY() - a.getY());
    double s =
        ((a.getY() - p.getY()) * (b.getX() - a.getX())
                - (a.getX() - p.getX()) * (b.getY() - a.getY()))
            / len2;
    return Math.abs(s) * Math.sqrt(len2);
  }

  /**
   * Gets the minimum rectangle enclosing the points
   *
   * @return the minimum rectangle
   */
  public List<Point2D> getMinimumRectangle() {
    if (minDistPt == null) {
      if (isConvex) {
        computeWidthConvex(points);
      } else {
        List<java.awt.geom.Point2D> convexPts = (new ConvexHull(points)).getConvexHull();
        computeWidthConvex(convexPts);
      }
    }

    if (MathUtil.isEqualToZero(minDist)) {
      return Collections.emptyList();
    }

    double dx = minDistSeg.getX2() - minDistSeg.getX1();
    double dy = minDistSeg.getY2() - minDistSeg.getY1();

    double minPara = Double.MAX_VALUE;
    double maxPara = -Double.MAX_VALUE;
    double minPerp = Double.MAX_VALUE;
    double maxPerp = -Double.MAX_VALUE;

    // compute maxima and minima of lines parallel and perpendicular to AB segment
    for (Point2D cvxPt : cvxPts) {

      double paraC = getC(dx, dy, cvxPt);
      if (paraC > maxPara) {
        maxPara = paraC;
      }
      if (paraC < minPara) {
        minPara = paraC;
      }

      double perpC = getC(-dy, dx, cvxPt);
      if (perpC > maxPerp) {
        maxPerp = perpC;
      }
      if (perpC < minPerp) {
        minPerp = perpC;
      }
    }

    Line2D.Double maxPerpLine = getLine(-dx, -dy, maxPerp);
    Line2D.Double minPerpLine = getLine(-dx, -dy, minPerp);
    Line2D.Double maxParaLine = getLine(-dy, dx, maxPara);
    Line2D.Double minParaLine = getLine(-dy, dx, minPara);

    List<Point2D> rect = new ArrayList<>();
    rect.add(
        intersection(
            maxParaLine.getP1(), maxParaLine.getP2(), maxPerpLine.getP1(), maxPerpLine.getP2()));
    rect.add(
        intersection(
            minParaLine.getP1(), minParaLine.getP2(), maxPerpLine.getP1(), maxPerpLine.getP2()));
    rect.add(
        intersection(
            minParaLine.getP1(), minParaLine.getP2(), minPerpLine.getP1(), minPerpLine.getP2()));
    rect.add(
        intersection(
            maxParaLine.getP1(), maxParaLine.getP2(), minPerpLine.getP1(), minPerpLine.getP2()));

    return rect;
  }

  /** Intersection point between two line segments. */
  public static Point2D intersection(Point2D u1, Point2D u2, Point2D w1, Point2D w2) {

    double ux = u1.getY() - u2.getY();
    double uy = u2.getX() - u1.getX();
    double ul = u1.getX() * u2.getY() - u2.getX() * u1.getY();

    double wx = w1.getY() - w2.getY();
    double wy = w2.getX() - w1.getX();
    double wl = w1.getX() * w2.getY() - w2.getX() * w1.getY();

    double x = uy * wl - wy * ul;
    double y = wx * ul - ux * wl;
    double l = ux * wy - wx * uy;

    double xNorm = x / l;
    double yNorm = y / l;

    if ((Double.isNaN(xNorm))
        || (Double.isInfinite(xNorm) || Double.isNaN(yNorm))
        || (Double.isInfinite(yNorm))) {
      return null;
    }

    return new Point2D.Double(xNorm, yNorm);
  }

  private static double getC(double a, double b, Point2D p) {
    return a * p.getY() - b * p.getX();
  }

  private static Line2D.Double getLine(double a, double b, double c) {
    // Line equation: ax + by = c
    if (Math.abs(b) > Math.abs(a)) {
      return new Line2D.Double(0.0, c / b, 1.0, c / b - a / b);
    } else {
      return new Line2D.Double(c / a, 0.0, c / a - b / a, 1.0);
    }
  }
}
