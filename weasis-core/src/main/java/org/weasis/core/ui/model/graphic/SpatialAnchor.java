/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.opencv.core.Point3;
import org.weasis.core.api.image.measure.PlaneGeometry;
import org.weasis.core.ui.serialize.Point3Adapter;

/**
 * Where a graphic sits in patient space: its handle points in DICOM LPS millimetres, the frame of
 * reference they belong to and the normal of the plane it was drawn on. Set when the graphic is
 * edited on an image that has a {@link PlaneGeometry}; absent on images without geometry.
 */
@XmlAccessorType(XmlAccessType.NONE)
public class SpatialAnchor {

  @XmlAttribute(name = "frameOfReference")
  private String frameOfReferenceUID;

  @XmlAttribute(name = "nx")
  private double nx;

  @XmlAttribute(name = "ny")
  private double ny;

  @XmlAttribute(name = "nz")
  private double nz;

  @XmlElement(name = "pt")
  @XmlJavaTypeAdapter(Point3Adapter.class)
  private List<Point3> points = new ArrayList<>();

  public SpatialAnchor() {
    // JAXB
  }

  public SpatialAnchor(String frameOfReferenceUID, Point3 normal, List<Point3> points) {
    this.frameOfReferenceUID = frameOfReferenceUID;
    Objects.requireNonNull(normal);
    this.nx = normal.x;
    this.ny = normal.y;
    this.nz = normal.z;
    this.points = new ArrayList<>(points);
  }

  /** Anchor of image points through the geometry of the plane they were drawn on. */
  public static SpatialAnchor of(PlaneGeometry geometry, List<Point2D> imagePoints) {
    List<Point3> pts = new ArrayList<>(imagePoints.size());
    for (Point2D p : imagePoints) {
      pts.add(p == null ? null : geometry.toPatient(p));
    }
    return new SpatialAnchor(geometry.getFrameOfReferenceUID(), geometry.getNormal(), pts);
  }

  public String getFrameOfReferenceUID() {
    return frameOfReferenceUID;
  }

  public Point3 getNormal() {
    return new Point3(nx, ny, nz);
  }

  public List<Point3> getPoints() {
    return Collections.unmodifiableList(points);
  }

  /** Image points of this anchor on another plane of the same frame of reference. */
  public List<Point2D> toImage(PlaneGeometry geometry) {
    List<Point2D> pts = new ArrayList<>(points.size());
    for (Point3 p : points) {
      pts.add(p == null ? null : geometry.toImage(p));
    }
    return pts;
  }

  public SpatialAnchor copy() {
    return new SpatialAnchor(frameOfReferenceUID, getNormal(), points);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof SpatialAnchor a
        && Objects.equals(frameOfReferenceUID, a.frameOfReferenceUID)
        && nx == a.nx
        && ny == a.ny
        && nz == a.nz
        && points.equals(a.points);
  }

  @Override
  public int hashCode() {
    return Objects.hash(frameOfReferenceUID, nx, ny, nz, points);
  }
}
