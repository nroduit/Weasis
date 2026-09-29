/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.sr;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.util.DicomUtils;
import org.joml.Vector3d;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

/**
 * Projects an SCOORD3D item (PS3.3 C.18.9) onto the plane of an image slice.
 *
 * <p>Graphic Data holds (x,y,z) patient coordinates in millimeters within a Frame of Reference. A
 * point belongs to a slice when its distance to the slice plane is at most half the slice spacing.
 * A planar graphic whose points all lie in the slice is drawn as the matching 2D graphic (POLYLINE,
 * POLYGON, ELLIPSE, POINT, MULTIPOINT). A graphic crossing the slice contributes the intersections
 * of its segments with the plane, drawn as points. An ELLIPSOID is approximated by the axis end
 * points and axis crossings that fall in the slice.
 */
final class SRScoordProjector {

  static final String POINT = "POINT"; // NON-NLS
  static final String MULTIPOINT = "MULTIPOINT"; // NON-NLS
  static final String POLYLINE = "POLYLINE"; // NON-NLS
  static final String POLYGON = "POLYGON"; // NON-NLS
  static final String ELLIPSE = "ELLIPSE"; // NON-NLS
  static final String ELLIPSOID = "ELLIPSOID"; // NON-NLS

  /** Slice half-thickness used when the geometry gives neither a spacing nor a thickness. */
  static final double DEFAULT_TOLERANCE = 1.0;

  private SRScoordProjector() {}

  /**
   * @param item the SCOORD3D content item
   * @param geometry the slice geometry of the image, may be null
   * @param imageFrameOfReferenceUID the Frame of Reference of the image, may be null
   * @param color the graphic color
   * @param thickness the line thickness
   * @return the graphic in image pixel coordinates, or null when nothing of the item lies in the
   *     slice or the frames of reference differ
   */
  static Graphic project(
      Attributes item,
      GeometryOfSlice geometry,
      String imageFrameOfReferenceUID,
      Color color,
      float thickness) {
    if (item == null || geometry == null) {
      return null;
    }
    String itemFor = item.getString(Tag.ReferencedFrameOfReferenceUID);
    if (StringUtil.hasText(itemFor)
        && StringUtil.hasText(imageFrameOfReferenceUID)
        && !itemFor.equals(imageFrameOfReferenceUID)) {
      return null;
    }
    float[] data = DicomUtils.getFloatArrayFromDicomElement(item, Tag.GraphicData, null);
    if (data == null || data.length < 3 || data.length % 3 != 0) {
      return null;
    }
    String type = item.getString(Tag.GraphicType);
    if (type == null) {
      return null;
    }

    int n = data.length / 3;
    Vector3d[] pts = new Vector3d[n];
    double[] dist = new double[n];
    Vector3d normal = geometry.getNormal();
    Vector3d tlhc = geometry.getTLHC();
    for (int i = 0; i < n; i++) {
      pts[i] = new Vector3d(data[3 * i], data[3 * i + 1], data[3 * i + 2]);
      dist[i] = new Vector3d(pts[i]).sub(tlhc).dot(normal);
    }
    double tol = tolerance(geometry);

    return switch (type.toUpperCase()) {
      case POINT, MULTIPOINT -> points(inPlane(pts, dist, tol), geometry, color, thickness);
      case POLYLINE, POLYGON -> {
        if (allInPlane(dist, tol)) {
          List<Point2D> p2 = toImage(pts, geometry);
          if (POLYGON.equals(type.toUpperCase()) && !p2.isEmpty()) {
            Point2D first = p2.get(0);
            Point2D last = p2.get(p2.size() - 1);
            if (first.distance(last) > 1e-6) {
              p2.add(first);
            }
          }
          yield build(POLYLINE, p2, color, thickness, true);
        }
        List<Vector3d> hits = inPlane(pts, dist, tol);
        boolean closed = POLYGON.equals(type.toUpperCase());
        hits.addAll(crossings(pts, dist, closed));
        yield points(hits, geometry, color, thickness);
      }
      case ELLIPSE -> {
        if (n == 4 && allInPlane(dist, tol)) {
          // PR-style ellipse: end points of the major axis then of the minor axis
          yield build(ELLIPSE, toImage(pts, geometry), color, thickness, false);
        }
        yield points(axisHits(pts, dist, tol), geometry, color, thickness);
      }
      case ELLIPSOID -> points(axisHits(pts, dist, tol), geometry, color, thickness);
      default -> null;
    };
  }

  /** Half of the slice spacing, falling back to the slice thickness, then to 1 mm. */
  static double tolerance(GeometryOfSlice geometry) {
    double spacing = geometry.getVoxelSpacing() == null ? 0 : geometry.getVoxelSpacing().z;
    double half = Math.max(spacing, geometry.getSliceThickness()) / 2.0;
    return half > GeometryOfSlice.MIN_SPACING ? half : DEFAULT_TOLERANCE;
  }

  private static boolean allInPlane(double[] dist, double tol) {
    for (double d : dist) {
      if (Math.abs(d) > tol) {
        return false;
      }
    }
    return true;
  }

  private static List<Vector3d> inPlane(Vector3d[] pts, double[] dist, double tol) {
    List<Vector3d> list = new ArrayList<>();
    for (int i = 0; i < pts.length; i++) {
      if (Math.abs(dist[i]) <= tol) {
        list.add(pts[i]);
      }
    }
    return list;
  }

  /** Intersections of the consecutive segments (and the closing one) with the plane. */
  private static List<Vector3d> crossings(Vector3d[] pts, double[] dist, boolean closed) {
    List<Vector3d> list = new ArrayList<>();
    int n = pts.length;
    int segments = closed ? n : n - 1;
    for (int i = 0; i < segments; i++) {
      int j = (i + 1) % n;
      if (dist[i] * dist[j] < 0) {
        double t = dist[i] / (dist[i] - dist[j]);
        list.add(new Vector3d(pts[j]).sub(pts[i]).mul(t).add(pts[i]));
      }
    }
    return list;
  }

  /** In-plane end points and plane crossings of axes given as consecutive point pairs. */
  private static List<Vector3d> axisHits(Vector3d[] pts, double[] dist, double tol) {
    List<Vector3d> list = inPlane(pts, dist, tol);
    for (int i = 0; i + 1 < pts.length; i += 2) {
      if (dist[i] * dist[i + 1] < 0) {
        double t = dist[i] / (dist[i] - dist[i + 1]);
        list.add(new Vector3d(pts[i + 1]).sub(pts[i]).mul(t).add(pts[i]));
      }
    }
    return list;
  }

  private static List<Point2D> toImage(Vector3d[] pts, GeometryOfSlice geometry) {
    List<Point2D> list = new ArrayList<>(pts.length);
    for (Vector3d p : pts) {
      Point2D p2 = geometry.getImagePosition(p);
      if (p2 != null) {
        list.add(p2);
      }
    }
    return list;
  }

  private static Graphic points(
      List<Vector3d> pts, GeometryOfSlice geometry, Color color, float thickness) {
    if (pts.isEmpty()) {
      return null;
    }
    List<Point2D> p2 = toImage(pts.toArray(new Vector3d[0]), geometry);
    return build(p2.size() == 1 ? POINT : MULTIPOINT, p2, color, thickness, true);
  }

  private static Graphic build(
      String type, List<Point2D> points, Color color, float thickness, boolean dcmSR) {
    if (points.isEmpty()) {
      return null;
    }
    float[] coords = new float[points.size() * 2];
    for (int i = 0; i < points.size(); i++) {
      coords[2 * i] = (float) points.get(i).getX();
      coords[2 * i + 1] = (float) points.get(i).getY();
    }
    Attributes a = new Attributes(4);
    a.setString(Tag.GraphicAnnotationUnits, VR.CS, "PIXEL"); // NON-NLS
    a.setInt(Tag.NumberOfGraphicPoints, VR.US, points.size());
    a.setFloat(Tag.GraphicData, VR.FL, coords);
    a.setString(Tag.GraphicType, VR.CS, type);
    return SRGraphic.build2D(a, color, thickness, dcmSR);
  }
}
