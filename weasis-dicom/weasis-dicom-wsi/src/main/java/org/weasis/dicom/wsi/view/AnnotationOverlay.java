/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.weasis.dicom.wsi.ann.AnnotationIndex;
import org.weasis.dicom.wsi.ann.BulkAnnotations.GraphicType;
import org.weasis.dicom.wsi.ann.BulkAnnotations.Group;

/**
 * Paints the bulk annotations of a view: the items of each shown group that meet the viewport, up
 * to a cap shared by the groups, in the colour of the group. An item smaller than a device pixel is
 * a dot, so that a field of nuclei stays a texture when zoomed out.
 */
public final class AnnotationOverlay {

  /** Device pixels below which an item is a dot. */
  private static final double DOT_SIZE = 2.0;

  private static final float LINE_WIDTH = 1.5f;
  private static final int ALPHA = 220;

  private AnnotationOverlay() {}

  /**
   * @param g2d graphics whose user space is the view pixels
   * @param modelToView base level pixels to view pixels
   * @param viewport the visible area in base level pixels
   * @param indexes the indexes of the shown groups
   * @param cap the most items painted
   * @return how many items were painted
   */
  public static int paint(
      Graphics2D g2d,
      AffineTransform modelToView,
      Rectangle2D viewport,
      List<AnnotationIndex> indexes,
      int cap) {
    if (indexes.isEmpty() || cap <= 0) {
      return 0;
    }
    Graphics2D g = (Graphics2D) g2d.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setStroke(new BasicStroke(LINE_WIDTH));
      int painted = 0;
      for (AnnotationIndex index : indexes) {
        if (painted >= cap) {
          break;
        }
        painted += paintGroup(g, modelToView, viewport, index, cap - painted);
      }
      return painted;
    } finally {
      g.dispose();
    }
  }

  private static int paintGroup(
      Graphics2D g, AffineTransform t, Rectangle2D viewport, AnnotationIndex index, int limit) {
    Group group = index.group();
    Color color = group.color();
    g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), ALPHA));
    int[] items = index.query(viewport, limit);
    float[] bounds = new float[4];
    Point2D.Double p = new Point2D.Double();
    Path2D.Double path = new Path2D.Double();
    for (int item : items) {
      index.bounds(item, bounds);
      double extent =
          Math.max(bounds[2] - bounds[0], bounds[3] - bounds[1])
              * Math.sqrt(Math.abs(t.getDeterminant()));
      if (extent < DOT_SIZE || group.type() == GraphicType.POINT) {
        p.setLocation((bounds[0] + bounds[2]) / 2.0, (bounds[1] + bounds[3]) / 2.0);
        t.transform(p, p);
        g.fillRect((int) Math.round(p.x) - 1, (int) Math.round(p.y) - 1, 2, 2);
        continue;
      }
      path.reset();
      appendItem(path, group, item, t, p);
      g.draw(path);
    }
    return items.length;
  }

  private static void appendItem(
      Path2D.Double path, Group group, int item, AffineTransform t, Point2D.Double p) {
    int start = group.start(item);
    int end = group.end(item);
    if (group.type() == GraphicType.RECTANGLE || group.type() == GraphicType.ELLIPSE) {
      // Four corners of a rectangle, or the end points of the two axes of an ellipse
      if (group.type() == GraphicType.ELLIPSE && end - start == 4) {
        appendEllipse(path, group, start, t, p);
        return;
      }
    }
    for (int i = start; i < end; i++) {
      p.setLocation(group.points().x(i) * group.scaleX(), group.points().y(i) * group.scaleY());
      t.transform(p, p);
      if (i == start) {
        path.moveTo(p.x, p.y);
      } else {
        path.lineTo(p.x, p.y);
      }
    }
    if (group.type() != GraphicType.POLYLINE) {
      path.closePath();
    }
  }

  // The ellipse from the end points of its major then minor axis (PS3.3 C.37.1.2.2)
  private static void appendEllipse(
      Path2D.Double path, Group group, int start, AffineTransform t, Point2D.Double p) {
    double[] pts = new double[8];
    for (int i = 0; i < 4; i++) {
      p.setLocation(
          group.points().x(start + i) * group.scaleX(),
          group.points().y(start + i) * group.scaleY());
      t.transform(p, p);
      pts[2 * i] = p.x;
      pts[2 * i + 1] = p.y;
    }
    double cx = (pts[0] + pts[2]) / 2;
    double cy = (pts[1] + pts[3]) / 2;
    double ax = pts[2] - cx;
    double ay = pts[3] - cy;
    double bx = pts[6] - cx;
    double by = pts[7] - cy;
    int segments = 32;
    for (int i = 0; i <= segments; i++) {
      double angle = 2 * Math.PI * i / segments;
      double x = cx + ax * Math.cos(angle) + bx * Math.sin(angle);
      double y = cy + ay * Math.cos(angle) + by * Math.sin(angle);
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    path.closePath();
  }
}
