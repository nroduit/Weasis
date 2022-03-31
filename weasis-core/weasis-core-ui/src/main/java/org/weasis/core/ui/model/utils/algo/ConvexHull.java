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

import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.TreeSet;
import org.weasis.core.util.MathUtil;

public class ConvexHull {
  public static final int COUNTERCLOCKWISE = 1;
  public static final int CLOCKWISE = -1;

  private final List<Point2D> pts;

  public ConvexHull(List<Point2D> pts) {
    this.pts = removeDuplicates(pts);
  }

  public static List<Point2D> removeDuplicates(List<Point2D> points) {
    TreeSet<Point2D> treeSet =
        new TreeSet<>(
            (p1, p2) -> {
              if (p1.getY() < p2.getY()) {
                return -1;
              }
              if (p1.getY() > p2.getY()) {
                return +1;
              }
              return Double.compare(p1.getX(), p2.getX());
            });
    treeSet.addAll(points);
    return new ArrayList<>(treeSet);
  }

  public List<Point2D> getConvexHull() {

    if (pts.size() < 3) {
      return pts;
    }
    return new ArrayList<>(grahamScan(preSort(pts)));
  }

  private static List<Point2D> preSort(List<Point2D> pts) {

    Point2D p = pts.get(0);
    for (int i = 1; i < pts.size(); i++) {
      Point2D pc = pts.get(i);
      if ((pc.getY() < p.getY())
          || (MathUtil.isEqual(pc.getY(), p.getY()) && (pc.getX() < p.getX()))) {
        p = pc;
        Collections.swap(pts, 0, i);
      }
    }

    pts.sort(new RadialSorter(p));
    return pts;
  }

  /**
   * compute the convex hull with the Graham Scan algorithm
   *
   * @param pts a list of points
   * @return a Deque containing the ordered points of the convex hull ring
   */
  private static Deque<Point2D> grahamScan(List<Point2D> pts) {
    ArrayDeque<Point2D> ps = new ArrayDeque<>();
    ps.addFirst(pts.get(2));
    ps.addFirst(pts.get(1));
    ps.addFirst(pts.get(0));

    Point2D p;
    for (int i = 3; i < pts.size(); i++) {
      p = ps.removeLast();
      Point2D pc = pts.get(i);
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
   * @param a the origin point of the vector
   * @param b the final point of the vector
   * @param c the point to compute the direction to
   * @return 1 if c is counter-clockwise, (left) from a-b
   * @return -1 if c is clockwise, (right) from a-b
   * @return 0 if c is collinear with a-b
   */
  public static int getOrientation(Point2D a, Point2D b, Point2D c) {
    return signum(
        (b.getX() - a.getX()) * (c.getY() - a.getY())
            - (b.getY() - a.getY()) * (c.getX() - a.getX()));
  }

  private static class RadialSorter implements Comparator<Point2D> {
    private final Point2D origin;

    public RadialSorter(Point2D origin) {
      this.origin = origin;
    }

    @Override
    public int compare(Point2D p1, Point2D p2) {
      return polarCompare(origin, p1, p2);
    }

    private static int polarCompare(Point2D o, Point2D p, Point2D q) {
      double dxp = p.getX() - o.getX();
      double dyp = p.getY() - o.getY();
      double dxq = q.getX() - o.getX();
      double dyq = q.getY() - o.getY();

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

      return Double.compare(op, oq);
    }
  }
}
