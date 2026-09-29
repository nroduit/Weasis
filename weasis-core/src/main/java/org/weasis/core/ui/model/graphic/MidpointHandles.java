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

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The handles sitting on the middle of each segment of a variable-point path: dragging one inserts
 * a vertex there, clicking one inserts it at the midpoint. They are a rendering and hit-testing
 * concern only — the point list of the graphic is untouched until a vertex is really inserted.
 *
 * <p>A midpoint handle is drawn only when its segment is long enough on screen, so it never covers
 * the vertex handles it sits between and never appears on a dense freehand trace.
 */
public final class MidpointHandles {

  /** Show the midpoint handles at all. */
  public static final String P_MIDPOINT = "weasis.draw.handles.midpoint";

  /** Handle thinning threshold, in units of {@link Graphic#HANDLE_SIZE}. */
  public static final String P_MIN_SPACING = "weasis.draw.handles.minSpacing";

  public static final boolean DEFAULT_MIDPOINT = true;
  public static final double DEFAULT_MIN_SPACING = 2.0;

  /** Screen length of a segment, in handle sizes, below which its midpoint handle is hidden. */
  public static final double SEGMENT_FACTOR = 3.0;

  /** Hit radius of a midpoint handle, relative to a vertex handle. */
  public static final double SIZE_FACTOR = 0.5;

  /** Half-diagonal of the drawn diamond, relative to {@link Graphic#HANDLE_SIZE}. */
  private static final double DIAMOND_FACTOR = 0.65;

  private static final float HALO_WIDTH = 3.0f;
  private static final float CORE_WIDTH = 1.4f;

  private static volatile Boolean enabled;
  private static volatile Double minSpacing;

  private MidpointHandles() {}

  /** Re-reads the preferences, after the Drawings preference page was closed. */
  public static void reload() {
    enabled = null;
    minSpacing = null;
  }

  public static boolean isEnabled() {
    Boolean value = enabled;
    if (value == null) {
      value = readBoolean(P_MIDPOINT, DEFAULT_MIDPOINT);
      enabled = value;
    }
    return value;
  }

  /** Vertex handles closer than this many handle sizes on screen are not drawn. */
  public static double getMinSpacing() {
    Double value = minSpacing;
    if (value == null) {
      value = readDouble(P_MIN_SPACING, DEFAULT_MIN_SPACING);
      minSpacing = value;
    }
    return value;
  }

  private static boolean readBoolean(String key, boolean def) {
    try {
      return GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(key, def);
    } catch (RuntimeException | LinkageError e) {
      return def; // no UI core, e.g. in tests
    }
  }

  private static double readDouble(String key, double def) {
    try {
      return GuiUtils.getUICore().getSystemPreferences().getDoubleProperty(key, def);
    } catch (RuntimeException | LinkageError e) {
      return def;
    }
  }

  /** True when the graphic is one whose path can gain and lose vertices where the user points. */
  public static boolean accepts(Graphic graphic) {
    return graphic instanceof DragGraphic drag
        && Boolean.TRUE.equals(drag.getVariablePointsNumber())
        && drag.isGraphicComplete()
        && drag.getLayer() != null
        && !drag.getLayer().getLocked();
  }

  /**
   * Indexes of the segments whose midpoint handle is shown at this scale: the ones at least {@link
   * #SEGMENT_FACTOR} handle sizes long on screen.
   */
  public static List<Integer> visibleSegments(DragGraphic graphic, double scale) {
    int count = graphic.getSegmentCount();
    List<Integer> visible = new ArrayList<>(count);
    if (count == 0 || scale <= 0) {
      return visible;
    }
    double minLength = SEGMENT_FACTOR * Graphic.HANDLE_SIZE / scale;
    for (int i = 0; i < count; i++) {
      Point2D[] segment = graphic.getSegment(i);
      if (segment != null && segment[0].distance(segment[1]) >= minLength) {
        visible.add(i);
      }
    }
    return visible;
  }

  /**
   * Index of the segment whose midpoint handle is under the cursor, or {@link Graphic#UNDEFINED}.
   * Only the handles that are drawn can be hit.
   */
  public static int hitTest(DragGraphic graphic, MouseEventDouble mouseEvent) {
    if (!isEnabled() || mouseEvent == null || !accepts(graphic)) {
      return Graphic.UNDEFINED;
    }
    Point2D mousePoint = mouseEvent.getImageCoordinates();
    if (mousePoint == null) {
      return Graphic.UNDEFINED;
    }
    double scale = scaleOf(graphic, mouseEvent);
    double maxDistance = Graphic.HANDLE_SIZE * SIZE_FACTOR * 1.5 / scale;
    int nearest = Graphic.UNDEFINED;
    double nearestDistance = Double.MAX_VALUE;
    for (Integer index : visibleSegments(graphic, scale)) {
      Point2D mid = graphic.getSegmentMidPoint(index);
      if (mid == null) {
        continue;
      }
      double distance = mousePoint.distance(mid);
      if (distance <= maxDistance && distance < nearestDistance) {
        nearestDistance = distance;
        nearest = index;
      }
    }
    return nearest;
  }

  /**
   * Index of the segment under the cursor, whatever its length on screen, or {@link
   * Graphic#UNDEFINED}. This is what the context menu offers on a right-click, where no handle has
   * to be visible.
   */
  public static int segmentAt(DragGraphic graphic, MouseEventDouble mouseEvent) {
    if (mouseEvent == null || !accepts(graphic)) {
      return Graphic.UNDEFINED;
    }
    Point2D mousePoint = mouseEvent.getImageCoordinates();
    if (mousePoint == null) {
      return Graphic.UNDEFINED;
    }
    double maxDistance =
        Math.max(Graphic.HANDLE_SIZE, graphic.getLineThickness())
            * 1.5
            / scaleOf(graphic, mouseEvent);
    int nearest = Graphic.UNDEFINED;
    double nearestDistance = Double.MAX_VALUE;
    for (int i = 0; i < graphic.getSegmentCount(); i++) {
      Point2D[] segment = graphic.getSegment(i);
      if (segment == null) {
        continue;
      }
      double distance =
          Line2D.ptSegDist(
              segment[0].getX(),
              segment[0].getY(),
              segment[1].getX(),
              segment[1].getY(),
              mousePoint.getX(),
              mousePoint.getY());
      if (distance <= maxDistance && distance < nearestDistance) {
        nearestDistance = distance;
        nearest = i;
      }
    }
    return nearest;
  }

  /** The point of a segment closest to the cursor, so an inserted vertex lands under it. */
  public static Point2D pointOnSegment(DragGraphic graphic, int segmentIndex, Point2D cursor) {
    Point2D[] segment = graphic.getSegment(segmentIndex);
    if (segment == null) {
      return null;
    }
    if (cursor == null) {
      return graphic.getSegmentMidPoint(segmentIndex);
    }
    double dx = segment[1].getX() - segment[0].getX();
    double dy = segment[1].getY() - segment[0].getY();
    double lengthSquared = dx * dx + dy * dy;
    if (lengthSquared == 0) {
      return new Point2D.Double(segment[0].getX(), segment[0].getY());
    }
    double t =
        ((cursor.getX() - segment[0].getX()) * dx + (cursor.getY() - segment[0].getY()) * dy)
            / lengthSquared;
    t = Math.clamp(t, 0.0, 1.0);
    return new Point2D.Double(segment[0].getX() + t * dx, segment[0].getY() + t * dy);
  }

  private static double scaleOf(DragGraphic graphic, MouseEventDouble mouseEvent) {
    AffineTransform transform =
        graphic instanceof AbstractGraphic abstractGraphic
            ? abstractGraphic.getAffineTransform(mouseEvent)
            : null;
    return GeomUtil.extractScalingFactor(transform);
  }

  /**
   * Draws the midpoint handles of a selected path: a hollow diamond in {@link
   * Graphic#EDIT_HINT_COLOR}, deliberately unlike the filled black and white square of a vertex.
   * Shape and colour both differ, so a place where a vertex <i>could</i> go is never mistaken for
   * one that is already there.
   */
  public static void paint(Graphics2D g2d, DragGraphic graphic, AffineTransform transform) {
    if (!isEnabled() || transform == null || !accepts(graphic)) {
      return;
    }
    double scale = GeomUtil.extractScalingFactor(transform);
    List<Integer> segments = visibleSegments(graphic, scale);
    if (segments.isEmpty()) {
      return;
    }

    List<Point2D> points = new ArrayList<>(segments.size());
    for (Integer index : segments) {
      Point2D mid = graphic.getSegmentMidPoint(index);
      if (mid != null) {
        points.add(new Point2D.Double(mid.getX(), mid.getY()));
      }
    }
    Point2D[] array = points.toArray(new Point2D[0]);
    transform.transform(array, 0, array, 0, array.length);

    Paint oldPaint = g2d.getPaint();
    Stroke oldStroke = g2d.getStroke();
    Object oldHint = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    double radius = Graphic.HANDLE_SIZE * DIAMOND_FACTOR;
    List<Shape> diamonds = new ArrayList<>(array.length);
    for (Point2D point : array) {
      diamonds.add(diamond(point, radius));
    }

    // Every halo first, so that none darkens the core of a neighbouring handle
    g2d.setPaint(Graphic.EDIT_HINT_HALO_COLOR);
    g2d.setStroke(new BasicStroke(HALO_WIDTH));
    diamonds.forEach(g2d::draw);

    g2d.setPaint(Graphic.EDIT_HINT_COLOR);
    g2d.setStroke(new BasicStroke(CORE_WIDTH));
    diamonds.forEach(g2d::draw);

    g2d.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        oldHint == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : oldHint);
    g2d.setPaint(oldPaint);
    g2d.setStroke(oldStroke);
  }

  private static Shape diamond(Point2D center, double radius) {
    Path2D shape = new Path2D.Double();
    shape.moveTo(center.getX(), center.getY() - radius);
    shape.lineTo(center.getX() + radius, center.getY());
    shape.lineTo(center.getX(), center.getY() + radius);
    shape.lineTo(center.getX() - radius, center.getY());
    shape.closePath();
    return shape;
  }

  /**
   * Indexes of the vertex handles to draw at the spacing of the preferences. The point list is
   * never touched — zooming in brings the hidden handles back.
   */
  public static List<Integer> visibleVertices(List<Point2D> pts, double scale) {
    return visibleVertices(pts, scale, getMinSpacing());
  }

  /**
   * Indexes of the vertex handles to draw: all of them when the path is sparse enough, otherwise
   * only the ends and the vertices at least {@code spacing} handle sizes from the previous drawn
   * one, on screen.
   */
  public static List<Integer> visibleVertices(List<Point2D> pts, double scale, double spacing) {
    int size = pts.size();
    List<Integer> visible = new ArrayList<>(size);
    if (size == 0) {
      return visible;
    }
    if (spacing <= 0 || scale <= 0 || size <= 2) {
      for (int i = 0; i < size; i++) {
        visible.add(i);
      }
      return visible;
    }
    double minDistance = spacing * Graphic.HANDLE_SIZE / scale;
    Point2D last = null;
    for (int i = 0; i < size; i++) {
      Point2D point = pts.get(i);
      if (point == null) {
        continue;
      }
      boolean end = i == 0 || i == size - 1;
      if (end || last == null || point.distance(last) >= minDistance) {
        visible.add(i);
        last = point;
      }
    }
    return visible;
  }
}
