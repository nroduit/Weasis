/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.opencv.core.Point3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.measure.PlaneGeometry;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicArea;
import org.weasis.core.ui.model.graphic.SpatialAnchor;
import org.weasis.core.ui.model.graphic.imp.NonEditableGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;

/**
 * The graphics drawn on any plane of a volume, kept by their patient-space anchor. When a view
 * changes plane the store puts back the graphics lying in the new plane, editable, and marks where
 * the others cross it.
 */
public final class MprGraphicStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(MprGraphicStore.class);

  /** Radius in image pixels of the mark drawn where a graphic crosses the plane. */
  static final double FOOTPRINT_RADIUS = 3;

  enum Placement {
    IN_PLANE,
    CROSSING,
    OUTSIDE
  }

  private final Map<String, Graphic> graphics = new LinkedHashMap<>();
  private final Map<String, SpatialAnchor> anchors = new HashMap<>();
  private final PropertyChangeListener removal =
      evt -> {
        if (Graphic.ACTION_REMOVE.equals(evt.getPropertyName())
            && evt.getSource() instanceof Graphic g) {
          remove(g);
        }
      };

  /** Keeps or refreshes an anchored graphic; true when it is new or its anchor moved. */
  public synchronized boolean adopt(Graphic graphic) {
    if (!isStorable(graphic)) {
      return false;
    }
    String uuid = graphic.getUuid();
    SpatialAnchor anchor = graphic.getAnchor().copy();
    if (graphics.putIfAbsent(uuid, graphic) == null) {
      graphic.addPropertyChangeListener(removal);
      anchors.put(uuid, anchor);
      return true;
    }
    if (!anchor.equals(anchors.get(uuid))) {
      anchors.put(uuid, anchor);
      return true;
    }
    return false;
  }

  public synchronized void adopt(GraphicModel model) {
    if (model != null) {
      model.getModels().forEach(this::adopt);
    }
  }

  public synchronized boolean remove(Graphic graphic) {
    if (graphic != null && graphics.remove(graphic.getUuid()) != null) {
      anchors.remove(graphic.getUuid());
      graphic.removePropertyChangeListener(removal);
      return true;
    }
    return false;
  }

  public synchronized List<Graphic> graphics() {
    return new ArrayList<>(graphics.values());
  }

  static boolean isStorable(Graphic graphic) {
    SpatialAnchor anchor = graphic == null ? null : graphic.getAnchor();
    return anchor != null
        && !anchor.getPoints().isEmpty()
        && graphic.isGraphicComplete()
        && Boolean.TRUE.equals(graphic.getLayerType().getSerializable());
  }

  /** Fills the model of a view for its plane: in-plane graphics editable, the others as marks. */
  public void populate(ViewCanvas<?> view, PlaneGeometry geometry, double halfThickness) {
    for (Graphic g : graphics()) {
      switch (classify(g.getAnchor(), geometry, halfThickness)) {
        case IN_PLANE -> place(view, g, geometry);
        case CROSSING -> addFootprint(view, g, geometry);
        case OUTSIDE -> {
          // Not visible on this plane
        }
      }
    }
  }

  /** Rebuilds the marks of the graphics that are not shown in the view but cross its plane. */
  public void refreshFootprints(ViewCanvas<?> view, PlaneGeometry geometry, double halfThickness) {
    GraphicModel model = view.getGraphicManager();
    model.deleteByLayerType(LayerType.FOOTPRINT);
    Set<String> shown =
        model.getModels().stream().map(Graphic::getUuid).collect(Collectors.toSet());
    for (Graphic g : graphics()) {
      if (!shown.contains(g.getUuid())
          && classify(g.getAnchor(), geometry, halfThickness) == Placement.CROSSING) {
        addFootprint(view, g, geometry);
      }
    }
  }

  static Placement classify(SpatialAnchor anchor, PlaneGeometry geometry, double halfThickness) {
    if (anchor == null || geometry == null || !sameFrameOfReference(anchor, geometry)) {
      return Placement.OUTSIDE;
    }
    Point3 normal = geometry.getNormal();
    Point3 origin = geometry.toPatient(new Point2D.Double(0, 0));
    boolean inPlane = true;
    boolean above = false;
    boolean below = false;
    for (Point3 p : anchor.getPoints()) {
      if (p == null) {
        continue;
      }
      double d = distance(p, normal, origin);
      if (Math.abs(d) > halfThickness) {
        inPlane = false;
        if (d > 0) {
          above = true;
        } else {
          below = true;
        }
      } else {
        above = true;
        below = true;
      }
    }
    if (inPlane) {
      return Placement.IN_PLANE;
    }
    return above && below ? Placement.CROSSING : Placement.OUTSIDE;
  }

  private static boolean sameFrameOfReference(SpatialAnchor anchor, PlaneGeometry geometry) {
    String a = anchor.getFrameOfReferenceUID();
    String b = geometry.getFrameOfReferenceUID();
    return a == null || b == null || a.equals(b);
  }

  static double distance(Point3 p, Point3 normal, Point3 origin) {
    return (p.x - origin.x) * normal.x + (p.y - origin.y) * normal.y + (p.z - origin.z) * normal.z;
  }

  /** Points where the polyline of the anchor crosses the plane, in patient space. */
  static List<Point3> planeIntersections(
      List<Point3> points, boolean closed, Point3 normal, Point3 origin) {
    List<Point3> pts = points.stream().filter(Objects::nonNull).toList();
    List<Point3> result = new ArrayList<>();
    int n = pts.size();
    int segments = closed && n > 2 ? n : n - 1;
    for (int i = 0; i < segments; i++) {
      Point3 p = pts.get(i);
      Point3 q = pts.get((i + 1) % n);
      double d1 = distance(p, normal, origin);
      double d2 = distance(q, normal, origin);
      if (d1 == 0) {
        result.add(p);
      } else if (d1 * d2 < 0) {
        double t = d1 / (d1 - d2);
        result.add(new Point3(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t, p.z + (q.z - p.z) * t));
      }
    }
    if (!closed && n > 1 && distance(pts.getLast(), normal, origin) == 0) {
      result.add(pts.getLast());
    }
    return result;
  }

  private static void place(ViewCanvas<?> view, Graphic graphic, PlaneGeometry geometry) {
    List<Point2D> pts = graphic.getAnchor().toImage(geometry);
    try {
      graphic.buildGraphic(pts);
    } catch (InvalidShapeException e) {
      LOGGER.warn("Graphic {} cannot be placed on the plane", graphic.getUuid(), e);
      return;
    }
    for (PropertyChangeListener listener : view.getGraphicManager().getGraphicsListeners()) {
      graphic.removePropertyChangeListener(listener);
    }
    AbstractGraphicModel.addGraphicToModel(view, graphic);
  }

  private static void addFootprint(ViewCanvas<?> view, Graphic graphic, PlaneGeometry geometry) {
    Point3 normal = geometry.getNormal();
    Point3 origin = geometry.toPatient(new Point2D.Double(0, 0));
    List<Point3> crossings =
        planeIntersections(
            graphic.getAnchor().getPoints(), graphic instanceof GraphicArea, normal, origin);
    if (crossings.isEmpty()) {
      return;
    }
    Path2D path = new Path2D.Double();
    for (Point3 p : crossings) {
      Point2D p2 = geometry.toImage(p);
      path.append(
          new Ellipse2D.Double(
              p2.getX() - FOOTPRINT_RADIUS,
              p2.getY() - FOOTPRINT_RADIUS,
              2 * FOOTPRINT_RADIUS,
              2 * FOOTPRINT_RADIUS),
          false);
    }
    NonEditableGraphic footprint = new NonEditableGraphic(path);
    if (graphic.getColorPaint() instanceof Color color) {
      footprint.setPaint(color);
    }
    footprint.setLineThickness(graphic.getLineThickness());
    footprint.setLayerType(LayerType.FOOTPRINT);
    AbstractGraphicModel.addGraphicToModel(view, footprint);
  }
}
