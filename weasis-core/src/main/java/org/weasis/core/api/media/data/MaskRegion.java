/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One region of a {@link PixelMask}, in coordinates normalized to the frame it was drawn on, so the
 * same region serves every format of the same layout. The category decides which profiles hide it:
 * a region is burned when the mask in force does not {@link AnonymizationAction#KEEP} its category.
 */
public sealed interface MaskRegion {

  /** JSON type name, also the discriminator of the document. */
  String kind();

  TagCategory category();

  /** The region on an image of that size, in image coordinates. */
  Shape toShape(double columns, double rows);

  /** The normalized bounds, for a preview or an export that takes rectangles only. */
  Rectangle2D bounds();

  MaskRegion withCategory(TagCategory category);

  String RECT = "rect"; // NON-NLS
  String ELLIPSE = "ellipse"; // NON-NLS
  String POLYGON = "polygon"; // NON-NLS

  record Rect(double x, double y, double w, double h, TagCategory category) implements MaskRegion {
    public Rect {
      Objects.requireNonNull(category);
    }

    @Override
    public String kind() {
      return RECT;
    }

    @Override
    public Shape toShape(double columns, double rows) {
      return new Rectangle2D.Double(x * columns, y * rows, w * columns, h * rows);
    }

    @Override
    public Rectangle2D bounds() {
      return new Rectangle2D.Double(x, y, w, h);
    }

    @Override
    public MaskRegion withCategory(TagCategory category) {
      return new Rect(x, y, w, h, category);
    }
  }

  record Ellipse(double x, double y, double w, double h, TagCategory category)
      implements MaskRegion {
    public Ellipse {
      Objects.requireNonNull(category);
    }

    @Override
    public String kind() {
      return ELLIPSE;
    }

    @Override
    public Shape toShape(double columns, double rows) {
      return new Ellipse2D.Double(x * columns, y * rows, w * columns, h * rows);
    }

    @Override
    public Rectangle2D bounds() {
      return new Rectangle2D.Double(x, y, w, h);
    }

    @Override
    public MaskRegion withCategory(TagCategory category) {
      return new Ellipse(x, y, w, h, category);
    }
  }

  /** A closed outline, as x, y pairs. */
  record Polygon(List<Double> points, TagCategory category) implements MaskRegion {
    public Polygon {
      points = List.copyOf(points);
      Objects.requireNonNull(category);
      if (points.size() < 6 || points.size() % 2 != 0) {
        throw new IllegalArgumentException("A polygon needs at least three x,y pairs");
      }
    }

    @Override
    public String kind() {
      return POLYGON;
    }

    @Override
    public Shape toShape(double columns, double rows) {
      Path2D path = new Path2D.Double();
      for (int i = 0; i < points.size(); i += 2) {
        double x = points.get(i) * columns;
        double y = points.get(i + 1) * rows;
        if (i == 0) {
          path.moveTo(x, y);
        } else {
          path.lineTo(x, y);
        }
      }
      path.closePath();
      return path;
    }

    @Override
    public Rectangle2D bounds() {
      return toShape(1, 1).getBounds2D();
    }

    @Override
    public MaskRegion withCategory(TagCategory category) {
      return new Polygon(points, category);
    }
  }

  /**
   * Normalizes a shape drawn on an image of that size. A rectangle and an ellipse keep their type;
   * anything else is flattened to a polygon, which hides the same pixels for a fill.
   */
  static MaskRegion of(Shape shape, double columns, double rows, TagCategory category) {
    if (columns <= 0 || rows <= 0) {
      throw new IllegalArgumentException("Image size must be positive");
    }
    if (shape instanceof Rectangle2D rect) {
      return new Rect(
          rect.getX() / columns,
          rect.getY() / rows,
          rect.getWidth() / columns,
          rect.getHeight() / rows,
          category);
    }
    if (shape instanceof Ellipse2D ellipse) {
      return new Ellipse(
          ellipse.getX() / columns,
          ellipse.getY() / rows,
          ellipse.getWidth() / columns,
          ellipse.getHeight() / rows,
          category);
    }
    List<Double> points = new ArrayList<>();
    double[] segment = new double[6];
    for (PathIterator it = shape.getPathIterator(null, Math.max(columns, rows) / 1000.0);
        !it.isDone();
        it.next()) {
      int type = it.currentSegment(segment);
      if (type != PathIterator.SEG_CLOSE) {
        points.add(segment[0] / columns);
        points.add(segment[1] / rows);
      }
    }
    return new Polygon(points, category);
  }
}
