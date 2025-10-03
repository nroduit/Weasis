/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pr;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.weasis.core.util.MathUtil;

public final class InterpolatedPath2D {

  private static final double ALPHA = 0.5;
  private static final double EPSILON = 1e-9;

  public static Path2D buildCentripetal(List<Point2D> pts, boolean closed, double tension) {
    return buildCentripetal(pts, closed, tension, 1.0);
  }

  public static Path2D buildCentripetal(
      List<Point2D> points, boolean closed, double tension, double proximityRoundness) {
    Path2D.Double path = new Path2D.Double();
    if (points == null || points.isEmpty()) return path;

    List<Point2D> pts = new ArrayList<>(points);

    // Remove duplicate last point for closed curves
    if (closed && pts.size() >= 2 && pts.getFirst().distance(pts.getLast()) < EPSILON) {
      pts.removeLast();
    }

    int n = pts.size();
    if (n == 1) {
      Point2D first = pts.getFirst();
      path.moveTo(first.getX(), first.getY());
      return path;
    }

    if (!closed && n == 2) {
      Point2D p0 = pts.get(0);
      Point2D p1 = pts.get(1);
      path.moveTo(p0.getX(), p0.getY());
      path.lineTo(p1.getX(), p1.getY());
      return path;
    }

    generateCurveSegments(path, pts, closed, tension, proximityRoundness);

    if (closed) path.closePath();

    return path;
  }

  private static void generateCurveSegments(
      Path2D.Double path,
      List<Point2D> pts,
      boolean closed,
      double tension,
      double proximityRoundness) {
    int segCount = closed ? pts.size() : pts.size() - 1;

    for (int i = 0; i < segCount; i++) {
      Point2D[] controlPoints = getControlPoints(pts, i, closed);
      CentripetalParams params = calculateCentripetalParams(controlPoints);
      TangentVectors tangents = calculateTangents(controlPoints, params, tension);

      addCurveSegment(
          path, controlPoints[1], controlPoints[2], tangents, params, i == 0, proximityRoundness);
    }
  }

  private static Point2D[] getControlPoints(List<Point2D> pts, int i, boolean closed) {
    return new Point2D[] {
      get(pts, i - 1, closed), get(pts, i, closed), get(pts, i + 1, closed), get(pts, i + 2, closed)
    };
  }

  private static CentripetalParams calculateCentripetalParams(Point2D[] points) {
    double t0 = 0.0;
    double t1 = t0 + Math.pow(points[0].distance(points[1]), ALPHA);
    double t2 = t1 + Math.pow(points[1].distance(points[2]), ALPHA);
    double t3 = t2 + Math.pow(points[2].distance(points[3]), ALPHA);

    // Guard against coincident points
    if (t1 == t0) t1 = t0 + MathUtil.DOUBLE_EPSILON;
    if (t2 == t1) t2 = t1 + MathUtil.DOUBLE_EPSILON;
    if (t3 == t2) t3 = t2 + MathUtil.DOUBLE_EPSILON;

    return new CentripetalParams(t0, t1, t2, t3);
  }

  private static TangentVectors calculateTangents(
      Point2D[] points, CentripetalParams params, double tension) {
    double dt10 = params.t2 - params.t0;
    double dt21 = params.t3 - params.t1;

    Point2D.Double m1 =
        new Point2D.Double(
            (points[2].getX() - points[0].getX()) / dt10 * tension,
            (points[2].getY() - points[0].getY()) / dt10 * tension);

    Point2D.Double m2 =
        new Point2D.Double(
            (points[3].getX() - points[1].getX()) / dt21 * tension,
            (points[3].getY() - points[1].getY()) / dt21 * tension);

    return new TangentVectors(m1, m2);
  }

  private static void addCurveSegment(
      Path2D.Double path,
      Point2D P1,
      Point2D P2,
      TangentVectors tangents,
      CentripetalParams params,
      boolean isFirst,
      double proximityRoundness) {
    // Convert Hermite to cubic Bézier
    double h = (params.t2 - params.t1) / 3.0;

    // Calculate distance between points to determine proximity effect
    double distance = P1.distance(P2);
    double proximityFactor = Math.min(distance * 0.2, 1.0) * proximityRoundness;

    // Adjust control points to create wider curves near the original points
    Point2D.Double C1 =
        new Point2D.Double(
            P1.getX() + tangents.m1.x * h + (tangents.m1.x * proximityFactor),
            P1.getY() + tangents.m1.y * h + (tangents.m1.y * proximityFactor));
    Point2D.Double C2 =
        new Point2D.Double(
            P2.getX() - tangents.m2.x * h - (tangents.m2.x * proximityFactor),
            P2.getY() - tangents.m2.y * h - (tangents.m2.y * proximityFactor));

    if (isFirst) path.moveTo(P1.getX(), P1.getY());
    path.curveTo(C1.x, C1.y, C2.x, C2.y, P2.getX(), P2.getY());
  }

  private static Point2D get(List<Point2D> pts, int idx, boolean closed) {
    int n = pts.size();
    if (closed) {
      idx = ((idx % n) + n) % n;
    } else {
      idx = Math.max(0, Math.min(n - 1, idx));
    }
    return pts.get(idx);
  }

  private record CentripetalParams(double t0, double t1, double t2, double t3) {}

  private record TangentVectors(Point2D.Double m1, Point2D.Double m2) {}
}
