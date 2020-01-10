/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.weasis.core.ui.model.utils.algo;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.weasis.core.api.gui.util.MathUtil;

public class MinimumEnclosingRectangle {
    private final List<Point2D.Double> points;
    private final boolean isConvex;

    private List<Point2D.Double> cvxPts = null;
    private Line2D.Double minDistSeg = new Line2D.Double();
    private Point2D.Double minDistPt = null;
    private double minDist = 0.0;

    public MinimumEnclosingRectangle(List<Point2D.Double> points) {
        this(points, false);
    }

    public MinimumEnclosingRectangle(List<java.awt.geom.Point2D.Double> points, boolean isConvex) {
        this.points = points;
        this.isConvex = isConvex;
    }

    private void computeWidthConvex(List<Point2D.Double> pts) {
        this.cvxPts = pts;

        int size = cvxPts.size();
        if (size == 0) {
            minDist = 0.0;
            minDistPt = null;
            minDistSeg = null;
        } else if (size == 1) {
            minDist = 0.0;
            minDistPt = cvxPts.get(0);
            minDistSeg.setLine(minDistPt.x, minDistPt.y, minDistPt.x, minDistPt.y);
        } else if (size == 2 || size == 3) {
            minDist = 0.0;
            minDistPt = cvxPts.get(0);
            Point2D.Double p2 = cvxPts.get(1);
            minDistSeg.setLine(minDistPt.x, minDistPt.y, p2.x, p2.y);
        } else {
            searchRingMinDiameter(cvxPts);
        }
    }

    private void searchRingMinDiameter(List<Point2D.Double> pts) {
        minDist = Double.MAX_VALUE;
        int curIndex = 1;

        for (int i = 0; i < pts.size() - 1; i++) {
            curIndex = findMaxPerpendicularDistanceFromAB(pts, pts.get(i), pts.get(i + 1), curIndex);
        }
    }

    private int findMaxPerpendicularDistanceFromAB(List<Point2D.Double> pts, Point2D.Double ptA, Point2D.Double ptB,
        int startIndex) {
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

    private static int nextIndex(List<Point2D.Double> pts, int index) {
        int i = index + 1;
        return i >= pts.size() ? 0 : i;
    }

    public static double distanceFromAB(Point2D.Double p, Point2D.Double a, Point2D.Double b) {
        double len2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y);
        double s = ((a.y - p.y) * (b.x - a.x) - (a.x - p.x) * (b.y - a.y)) / len2;
        return Math.abs(s) * Math.sqrt(len2);
    }

    /**
     * Gets the minimum rectangle enclosing the points
     *
     * @return the minimum rectangle
     */
    public List<Point2D.Double> getMinimumRectangle() {
        if (minDistPt == null) {
            if (isConvex) {
                computeWidthConvex(points);
            } else {
                List<java.awt.geom.Point2D.Double> convexPts = (new ConvexHull(points)).getConvexHull();
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
        for (int i = 0; i < cvxPts.size(); i++) {

            double paraC = getC(dx, dy, cvxPts.get(i));
            if (paraC > maxPara) {
                maxPara = paraC;
            }
            if (paraC < minPara) {
                minPara = paraC;
            }

            double perpC = getC(-dy, dx, cvxPts.get(i));
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

        List<Point2D.Double> rect = new ArrayList<>();
        rect.add(intersection(maxParaLine.getP1(), maxParaLine.getP2(), maxPerpLine.getP1(), maxPerpLine.getP2()));
        rect.add(intersection(minParaLine.getP1(), minParaLine.getP2(), maxPerpLine.getP1(), maxPerpLine.getP2()));
        rect.add(intersection(minParaLine.getP1(), minParaLine.getP2(), minPerpLine.getP1(), minPerpLine.getP2()));
        rect.add(intersection(maxParaLine.getP1(), maxParaLine.getP2(), minPerpLine.getP1(), minPerpLine.getP2()));

        return rect;

    }

    /**
     * Intersection point between two line segments.
     */
    public static Point2D.Double intersection(Point2D u1, Point2D u2, Point2D w1, Point2D w2) {

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

        if ((Double.isNaN(xNorm)) || (Double.isInfinite(xNorm) || Double.isNaN(yNorm)) || (Double.isInfinite(yNorm))) {
            return null;
        }

        return new Point2D.Double(xNorm, yNorm);
    }

    private static double getC(double a, double b, Point2D.Double p) {
        return a * p.y - b * p.x;
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
