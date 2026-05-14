/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.cmpr;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure curve-geometry helpers shared by the curved-MPR panoramic generator and the cross-section
 * series builder: Catmull-Rom smoothing, arc-length resampling, and per-sample perpendicular
 * direction computation in the source plane.
 */
public final class CurveSampler {
  private static final Logger LOGGER = LoggerFactory.getLogger(CurveSampler.class);

  /** Derived curve geometry produced by {@link #sample(List, Vector3d)}. */
  public record Sampling(
      List<Vector3d> smoothedPoints, List<Vector3d> sampledPoints, List<Vector3d> perpDirections) {}

  private CurveSampler() {}

  /**
   * Smooth, resample at the requested arc-length step, and compute perpendiculars in one call.
   * Output order follows the user's polyline drawing direction — the panoramic image's left edge
   * maps to the first polyline handle, the right edge to the last. Pass {@code stepMm <= 0} or
   * {@code pixelMm <= 0} to fall back to the 1-voxel default spacing.
   */
  public static Sampling sample(
      List<Vector3d> curvePoints, Vector3d planeNormal, double stepMm, double pixelMm) {
    List<Vector3d> smoothed = smooth(curvePoints);
    List<Vector3d> sampled = resample(smoothed, stepMm, pixelMm);
    List<Vector3d> perps = computePerpendicularDirections(sampled, planeNormal);
    return new Sampling(smoothed, sampled, perps);
  }

  /**
   * Smooth the curve using Catmull-Rom spline interpolation. This converts rough user-drawn
   * polylines into smooth curves. The number of samples per segment is proportional to segment
   * length so density stays uniform along the entire curve.
   */
  static List<Vector3d> smooth(List<Vector3d> points) {
    if (points.size() < 2) {
      return new ArrayList<>(points);
    }
    if (points.size() == 2) {
      return new ArrayList<>(points);
    }
    List<Vector3d> result = new ArrayList<>();
    double[] segmentLengths = new double[points.size() - 1];
    double totalLength = 0;
    for (int i = 0; i < points.size() - 1; i++) {
      segmentLengths[i] = points.get(i).distance(points.get(i + 1));
      totalLength += segmentLengths[i];
    }
    double samplesPerVoxel = 2.0;
    for (int i = 0; i < points.size() - 1; i++) {
      Vector3d p0 = points.get(Math.max(0, i - 1));
      Vector3d p1 = points.get(i);
      Vector3d p2 = points.get(i + 1);
      Vector3d p3 = points.get(Math.min(points.size() - 1, i + 2));
      int segmentSamples = Math.max(2, (int) Math.round(segmentLengths[i] * samplesPerVoxel));
      for (int j = 0; j < segmentSamples; j++) {
        double t = (double) j / segmentSamples;
        result.add(catmullRom(p0, p1, p2, p3, t));
      }
    }
    result.add(new Vector3d(points.getLast()));
    LOGGER.debug(
        "Smoothed curve: {} input points -> {} output points (total length: {} voxels)",
        points.size(),
        result.size(),
        totalLength);
    return result;
  }

  /**
   * Resample at a uniform arc-length step. Points are in voxel coords; the step is given in mm and
   * converted via {@code pixelMm}. Non-positive inputs fall back to 1-voxel spacing.
   */
  static List<Vector3d> resample(List<Vector3d> points, double stepMm, double pixelMm) {
    List<Vector3d> result = new ArrayList<>();
    if (points.size() < 2) {
      return result;
    }
    double totalLengthVoxels = 0;
    for (int i = 1; i < points.size(); i++) {
      totalLengthVoxels += points.get(i).distance(points.get(i - 1));
    }
    if (totalLengthVoxels <= 0) {
      return result;
    }
    double stepVoxels = (stepMm > 0 && pixelMm > 0) ? stepMm / pixelMm : 1.0;
    if (stepVoxels <= 0) {
      stepVoxels = 1.0;
    }
    int numSamples = (int) Math.ceil(totalLengthVoxels / stepVoxels);
    for (int i = 0; i <= numSamples; i++) {
      double targetDist = i * stepVoxels;
      Vector3d point = interpolateAlongCurve(points, targetDist);
      if (point != null) {
        result.add(point);
      }
    }
    return result;
  }

  /**
   * Compute the in-plane perpendicular direction to the curve tangent at each sample. The
   * perpendicular is computed in the plane defined by {@code planeNormal} (typically XY plane,
   * normal = Z). Perpendiculars are kept consistent along the curve (no sudden flips), and all are
   * negated if the middle perpendicular points inward toward the curve's centroid — for a dental
   * arch that means flipping them to point "outward" (away from the throat).
   */
  static List<Vector3d> computePerpendicularDirections(
      List<Vector3d> sampledPoints, Vector3d planeNormal) {
    List<Vector3d> perpDirs = new ArrayList<>();
    int n = sampledPoints.size();
    Vector3d prevPerp = null;
    for (int i = 0; i < n; i++) {
      Vector3d tangent;
      if (i == 0) {
        tangent = new Vector3d(sampledPoints.get(1)).sub(sampledPoints.get(0));
      } else if (i == n - 1) {
        tangent = new Vector3d(sampledPoints.get(n - 1)).sub(sampledPoints.get(n - 2));
      } else {
        tangent = new Vector3d(sampledPoints.get(i + 1)).sub(sampledPoints.get(i - 1));
      }
      Vector3d perp = new Vector3d(planeNormal).cross(tangent);
      if (perp.lengthSquared() > 1e-10) {
        perp.normalize();
      } else {
        perp = (prevPerp != null) ? new Vector3d(prevPerp) : new Vector3d(1, 0, 0);
      }
      if (prevPerp != null && perp.dot(prevPerp) < 0) {
        perp.negate();
      }
      perpDirs.add(perp);
      prevPerp = perp;
    }
    if (n >= 3) {
      Vector3d centroid = new Vector3d(0, 0, 0);
      for (Vector3d p : sampledPoints) {
        centroid.add(p);
      }
      centroid.div(n);
      int midIdx = n / 2;
      Vector3d midPoint = sampledPoints.get(midIdx);
      Vector3d midPerp = perpDirs.get(midIdx);
      Vector3d toMid = new Vector3d(midPoint).sub(centroid);
      if (midPerp.dot(toMid) < 0) {
        for (Vector3d p : perpDirs) {
          p.negate();
        }
      }
    }
    return perpDirs;
  }

  private static Vector3d catmullRom(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, double t) {
    double t2 = t * t;
    double t3 = t2 * t;
    double b0 = -0.5 * t3 + t2 - 0.5 * t;
    double b1 = 1.5 * t3 - 2.5 * t2 + 1.0;
    double b2 = -1.5 * t3 + 2.0 * t2 + 0.5 * t;
    double b3 = 0.5 * t3 - 0.5 * t2;
    return new Vector3d(
        b0 * p0.x + b1 * p1.x + b2 * p2.x + b3 * p3.x,
        b0 * p0.y + b1 * p1.y + b2 * p2.y + b3 * p3.y,
        b0 * p0.z + b1 * p1.z + b2 * p2.z + b3 * p3.z);
  }

  private static Vector3d interpolateAlongCurve(List<Vector3d> points, double targetDistVoxels) {
    double accumulated = 0;
    for (int i = 1; i < points.size(); i++) {
      Vector3d p0 = points.get(i - 1);
      Vector3d p1 = points.get(i);
      double segmentLength = p0.distance(p1);
      if (accumulated + segmentLength >= targetDistVoxels) {
        double remaining = targetDistVoxels - accumulated;
        double t = segmentLength > 0 ? remaining / segmentLength : 0;
        return new Vector3d(p0).lerp(p1, t);
      }
      accumulated += segmentLength;
    }
    return points.isEmpty() ? null : new Vector3d(points.getLast());
  }
}
