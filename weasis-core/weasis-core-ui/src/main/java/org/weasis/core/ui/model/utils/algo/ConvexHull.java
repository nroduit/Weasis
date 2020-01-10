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

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.weasis.core.api.gui.util.MathUtil;

public class ConvexHull {
    public static final int COUNTERCLOCKWISE = 1;
    public static final int CLOCKWISE = -1;

    private List<Point2D.Double> pts;

    public ConvexHull(List<Point2D.Double> pts) {
        this.pts = removeDuplicates(pts);
    }

    public static List<Point2D.Double> removeDuplicates(List<Point2D.Double> points) {
        TreeSet<Point2D.Double> treeSet = new TreeSet<>((p1, p2) -> {
            if (p1.y < p2.y) {
                return -1;
            }
            if (p1.y > p2.y) {
                return +1;
            }
            if (p1.x < p2.x) {
                return -1;
            }
            if (p1.x > p2.x) {
                return +1;
            }
            return 0;
        });
        treeSet.addAll(points);
        return new ArrayList<>(treeSet);
    }

    public List<Point2D.Double> getConvexHull() {

        if (pts.size() < 3) {
            return pts;
        }
        return grahamScan(preSort(pts)).stream().collect(Collectors.toList());
    }

    private static List<Double> preSort(List<Point2D.Double> pts) {

        Point2D.Double p = pts.get(0);
        for (int i = 1; i < pts.size(); i++) {
            Point2D.Double pc = pts.get(i);
            if ((pc.y < p.y) || (MathUtil.isEqual(pc.y, p.y) && (pc.x < p.x))) {
                p = pc;
                Collections.swap(pts, 0, i);
            }
        }

        Collections.sort(pts, new RadialSorter(p));
        return pts;
    }

    /**
     * compute the convex hull with the Graham Scan algorithm
     *
     * @param pts
     *            a list of points
     *
     * @return a Deque containing the ordered points of the convex hull ring
     */
    private static Deque<Point2D.Double> grahamScan(List<Point2D.Double> pts) {
        ArrayDeque<Point2D.Double> ps = new ArrayDeque<>();
        ps.addFirst(pts.get(2));
        ps.addFirst(pts.get(1));
        ps.addFirst(pts.get(0));

        Point2D.Double p;
        for (int i = 3; i < pts.size(); i++) {
            p = ps.removeLast();
            Point2D.Double pc = pts.get(i);
            while (!ps.isEmpty() && getOrientation(ps.peekLast(), p, pc) > 0) {
                p = ps.removeLast();
            }
            ps.addLast(p);
            ps.addLast(pc);
        }
        ps.addLast(pts.get(0));
        return ps;
    }

    private static int signum(double x) {
        if (x > 0) {
            return 1;
        }
        if (x < 0) {
            return -1;
        }
        return 0;
    }

    /**
     * Returns the index of the direction of the point c relative to a vector a-b.
     *
     * @param a
     *            the origin point of the vector
     * @param b
     *            the final point of the vector
     * @param c
     *            the point to compute the direction to
     *
     * @return 1 if c is counter-clockwise, (left) from a-b
     * @return -1 if c is clockwise, (right) from a-b
     * @return 0 if c is collinear with a-b
     */
    public static int getOrientation(Point2D.Double a, Point2D.Double b, Point2D.Double c) {
        return signum((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x));
    }

    private static class RadialSorter implements Comparator<Point2D.Double> {
        private Point2D.Double origin;

        public RadialSorter(Point2D.Double origin) {
            this.origin = origin;
        }

        @Override
        public int compare(Point2D.Double p1, Point2D.Double p2) {
            return polarCompare(origin, p1, p2);
        }

        private static int polarCompare(Point2D.Double o, Point2D.Double p, Point2D.Double q) {
            double dxp = p.x - o.x;
            double dyp = p.y - o.y;
            double dxq = q.x - o.x;
            double dyq = q.y - o.y;

            int orient = getOrientation(o, p, q);
            if (orient == COUNTERCLOCKWISE) {
                return 1;
            }
            if (orient == CLOCKWISE) {
                return -1;
            }

            // collinear
            double op = dxp * dxp + dyp * dyp;
            double oq = dxq * dxq + dyq * dyq;

            return (op < oq) ? -1 : (op > oq) ? 1 : 0;
        }
    }
}