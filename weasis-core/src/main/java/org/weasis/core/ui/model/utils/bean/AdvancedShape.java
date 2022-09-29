/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.bean;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.util.MathUtil;

public class AdvancedShape implements Shape {
  private static final Logger LOGGER = LoggerFactory.getLogger(AdvancedShape.class);

  /**
   * First element should be considered as the main shape used for drawing of the main features of
   * graphic.<br>
   * For instance, this first shape defines measurement areas or path lines. Other shape are usually
   * dedicated to decorative drawings, with or without invariant size according to the view.
   */
  public final List<BasicShape> shapeList;

  protected AffineTransform transform;
  private final Graphic graphic;

  public AdvancedShape(Graphic graphic, int initialShapeNumber) {
    this.graphic = Objects.requireNonNull(graphic, "Graphic cannot be null!");
    this.shapeList = new ArrayList<>(initialShapeNumber);
  }

  public List<BasicShape> getShapeList() {
    return shapeList;
  }

  public BasicShape addShape(Shape shape) {
    BasicShape s = new BasicShape(shape);
    shapeList.add(s);
    return s;
  }

  public BasicShape addShape(Shape shape, Stroke stroke, boolean fixedLineWidth) {
    BasicShape s = new BasicShape(shape, stroke, fixedLineWidth);
    shapeList.add(s);
    return s;
  }

  ScaleInvariantShape addScaleInvShape(Shape shape, Point2D anchorPoint) {
    return addScaleInvShape(
        shape, anchorPoint, graphic.getStroke(graphic.getLineThickness()), false);
  }

  public ScaleInvariantShape addScaleInvShape(
      Shape shape, Point2D anchorPoint, double scalingMin, boolean fixedLineWidth) {
    return addScaleInvShape(
        shape,
        anchorPoint,
        scalingMin,
        graphic.getStroke(graphic.getLineThickness()),
        fixedLineWidth);
  }

  public InvariantShape addAllInvShape(
      Shape shape, Point2D anchorPoint, Stroke stroke, boolean fixedLineWidth) {
    InvariantShape s = new InvariantShape(shape, stroke, anchorPoint, fixedLineWidth);
    shapeList.add(s);
    return s;
  }

  public LinkSegmentToInvariantShape addLinkSegmentToInvariantShape(
      Line2D line, Point2D anchorPoint, Shape invShape, Stroke stroke, boolean fixedLineWidth) {
    LinkSegmentToInvariantShape s =
        new LinkSegmentToInvariantShape(line, stroke, anchorPoint, invShape, fixedLineWidth);
    shapeList.add(s);
    return s;
  }

  public ScaleInvariantShape addScaleInvShape(
      Shape shape, Point2D anchorPoint, double scalingMin, Stroke stroke, boolean fixedLineWidth) {
    ScaleInvariantShape s =
        new ScaleInvariantShape(shape, stroke, anchorPoint, scalingMin, fixedLineWidth);
    shapeList.add(s);
    return s;
  }

  public ScaleInvariantShape addScaleInvShape(
      Shape shape, Point2D anchorPoint, Stroke stroke, boolean fixedLineWidth) {
    ScaleInvariantShape s = new ScaleInvariantShape(shape, stroke, anchorPoint, fixedLineWidth);
    shapeList.add(s);
    return s;
  }

  public void setAffineTransform(AffineTransform transform) {
    this.transform = transform;
  }

  public void paint(Graphics2D g2d, AffineTransform transform) {
    setAffineTransform(transform);

    Paint oldPaint = g2d.getPaint();
    Stroke oldStroke = g2d.getStroke();

    Paint paint = graphic.getColorPaint();
    boolean filled = graphic.getFilled();

    for (BasicShape item : shapeList) {
      if (item.isVisible()) {
        Shape drawingShape = item.getRealShape();

        if (drawingShape != null) {
          if (transform != null) {
            drawingShape = transform.createTransformedShape(drawingShape);
          }
          Paint itemPaint = item.getColorPaint();
          g2d.setPaint(itemPaint == null ? paint : itemPaint);
          g2d.setStroke(item.stroke);
          g2d.draw(drawingShape);

          Boolean itemFilled = item.getFilled();
          if (itemFilled == null ? filled : itemFilled) {
            g2d.fill(drawingShape);
          }
        }
      }
    }

    g2d.setPaint(oldPaint);
    g2d.setStroke(oldStroke);
  }

  /**
   * @return a shape which is by convention the first shape in the list which is dedicated to the
   *     user tool drawing
   */
  public Shape getGeneralShape() {
    if (!shapeList.isEmpty()) {
      BasicShape s = shapeList.get(0);
      if (s != null) {
        return s.getRealShape();
      }
    }
    return null;
  }

  @Override
  public Rectangle getBounds() {
    Rectangle rectangle = null;

    for (BasicShape item : shapeList) {
      Shape realShape = item.getRealShape();
      Rectangle bounds = realShape != null ? realShape.getBounds() : null;

      if (bounds != null) {
        if (rectangle == null) {
          rectangle = bounds;
        } else {
          rectangle.add(bounds);
        }
      }
    }

    return rectangle;
  }

  @Override
  public Rectangle2D getBounds2D() {
    Rectangle2D rectangle = null;

    for (BasicShape item : shapeList) {
      Shape realShape = item.getRealShape();
      Rectangle2D bounds = realShape != null ? realShape.getBounds2D() : null;

      if (bounds != null) {
        if (rectangle == null) {
          rectangle = bounds;
        } else {
          rectangle.add(bounds);
        }
      }
    }
    return rectangle;
  }

  @Override
  public boolean contains(double x, double y) {
    for (BasicShape item : shapeList) {
      Shape realShape = item.getRealShape();
      if (realShape != null && realShape.contains(x, y)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean contains(Point2D p) {
    for (BasicShape item : shapeList) {
      Shape realShape = item.getRealShape();

      if (realShape != null && realShape.contains(p)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean contains(double x, double y, double w, double h) {
    for (BasicShape item : shapeList) {
      Shape realShape = item.getRealShape();
      if (realShape != null && realShape.contains(x, y, w, h)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean contains(Rectangle2D r) {
    for (BasicShape item : shapeList) {
      Shape realShape = item.getRealShape();

      if (realShape != null && realShape.contains(r)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean intersects(double x, double y, double w, double h) {
    for (BasicShape item : shapeList) {
      Shape realShape = item.getRealShape();
      if (realShape != null && realShape.intersects(x, y, w, h)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean intersects(Rectangle2D r) {
    for (BasicShape item : shapeList) {
      Shape realShape = item.getRealShape();
      if (realShape != null && realShape.intersects(r)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public PathIterator getPathIterator(AffineTransform at) {
    if (at != null) {
      setAffineTransform(at);
    }
    return getFullPathShape().getPathIterator(at);
  }

  @Override
  public PathIterator getPathIterator(AffineTransform at, double flatness) {
    if (at != null) {
      setAffineTransform(at);
    }
    return getFullPathShape().getPathIterator(at, flatness);
  }

  private Path2D getFullPathShape() {
    Path2D pathShape = new Path2D.Double(Path2D.WIND_NON_ZERO);

    for (BasicShape item : shapeList) {
      Shape realShape = item.getRealShape();
      if (realShape != null) {
        pathShape.append(realShape, false);
      }
    }

    return pathShape;
  }

  public Area getArea(AffineTransform transform) {
    setAffineTransform(transform);
    double scalingFactor = GeomUtil.extractScalingFactor(transform);
    double growingSize = graphic.getHandleSize() * 2.0 / scalingFactor;
    Stroke boundingStroke =
        new BasicStroke((float) growingSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    Area pathBoundingArea = new Area();

    for (BasicShape item : shapeList) {

      // Note : if shape is invalid, like a path with an odd number of curves, creating a new Area
      // involves a
      // "java.lang.InternalError". Because trapping the exception is too much time-consuming it's
      // the user
      // responsibility of this not to happen

      Shape strokedArea = null;
      try {
        Shape realShape = item.getRealShape();
        if (realShape != null) {
          Shape strokedShape = boundingStroke.createStrokedShape(realShape);
          strokedArea = new Area(strokedShape);
        }

      } catch (Exception e) {
        LOGGER.error("This shape cannot be drawn, the graphic is deleted.", e);
        graphic.fireRemoveAction();
      }
      if (strokedArea != null) {
        pathBoundingArea.add(new Area(strokedArea));
      }
    }
    return pathBoundingArea;
  }

  public class BasicShape {
    final Shape shape;
    final boolean fixedLineWidth;
    Stroke stroke;
    boolean visible;
    Boolean filled;
    Paint colorPaint;

    public BasicShape(Shape shape) {
      this(shape, graphic.getStroke(graphic.getLineThickness()), false);
    }

    public BasicShape(Shape shape, Stroke stroke, boolean fixedLineWidth) {
      if (shape == null || stroke == null) {
        throw new IllegalArgumentException();
      }
      this.shape = shape;
      this.stroke = stroke;
      this.fixedLineWidth = fixedLineWidth;
      this.visible = true;
      this.filled = null;
      this.colorPaint = null;
    }

    public Paint getColorPaint() {
      return colorPaint;
    }

    public void setColorPaint(Paint colorPaint) {
      this.colorPaint = colorPaint;
    }

    public Boolean getFilled() {
      return filled;
    }

    public Shape getRealShape() {
      return shape;
    }

    public void changeLineThickness(float width) {
      if (!fixedLineWidth
          && stroke instanceof BasicStroke s
          && MathUtil.isDifferent(s.getLineWidth(), width)) {
        stroke =
            new BasicStroke(
                width,
                s.getEndCap(),
                s.getLineJoin(),
                s.getMiterLimit(),
                s.getDashArray(),
                s.getDashPhase());
      }
    }

    public void setVisible(boolean visible) {
      this.visible = visible;
    }

    public boolean isVisible() {
      return visible;
    }

    public Shape getShape() {
      return shape;
    }

    public boolean isFixedLineWidth() {
      return fixedLineWidth;
    }

    public Stroke getStroke() {
      return stroke;
    }

    public void setStroke(Stroke stroke) {
      this.stroke = stroke;
    }

    public void setFilled(boolean filled) {
      this.filled = filled;
    }
  }

  /** Dedicated to drawings with invariant size around anchorPoint according to the view */
  public class ScaleInvariantShape extends BasicShape {
    final Point2D anchorPoint;
    final double scalingMin;

    public ScaleInvariantShape(
        Shape shape, Stroke stroke, Point2D anchorPoint, boolean fixedLineWidth) {
      this(shape, stroke, anchorPoint, 0.0, fixedLineWidth);
    }

    public ScaleInvariantShape(
        Shape shape,
        Stroke stroke,
        Point2D anchorPoint,
        double scalingMin,
        boolean fixedLineWidth) {
      super(shape, stroke, fixedLineWidth);

      if (anchorPoint == null) {
        throw new IllegalArgumentException();
      }

      if (scalingMin < 0) {
        throw new IllegalArgumentException();
      }

      this.anchorPoint = (Point2D) anchorPoint.clone();
      this.scalingMin = scalingMin;
    }

    @Override
    public Shape getRealShape() {
      double scalingFactor = GeomUtil.extractScalingFactor(transform);
      double scale = Math.max(scalingFactor, scalingMin);
      return MathUtil.isDifferentFromZero(scale)
          ? GeomUtil.getScaledShape(shape, 1 / scale, anchorPoint)
          : null;
    }
  }

  /**
   * Invariant to all the transformations except to flip (horizontal mirror)
   *
   * @version $Rev$ $Date$
   */
  public class InvariantShape extends BasicShape {

    final Point2D anchorPoint;

    public InvariantShape(Shape shape, Stroke stroke, Point2D anchorPoint, boolean fixedLineWidth) {
      super(shape, stroke, fixedLineWidth);

      if (anchorPoint == null) {
        throw new IllegalArgumentException();
      }
      this.anchorPoint = (Point2D) anchorPoint.clone();
    }

    @Override
    public Shape getRealShape() {
      if (transform == null) {
        return shape;
      }
      AffineTransform invTransform = new AffineTransform(); // Identity transformation.

      double scale = GeomUtil.extractScalingFactor(transform);
      double angleRad = GeomUtil.extractAngleRad(transform);
      boolean scaled = MathUtil.isDifferent(scale, 1.0);
      boolean rotated = MathUtil.isDifferentFromZero(angleRad);

      invTransform.translate(anchorPoint.getX(), anchorPoint.getY());

      if (scaled) {
        invTransform.scale(1 / scale, 1 / scale);
      }
      if (rotated) {
        invTransform.rotate(-angleRad);
      }
      if ((transform.getType() & AffineTransform.TYPE_FLIP) != 0) {
        invTransform.scale(-1.0, -1.0);
      }
      invTransform.translate(-anchorPoint.getX(), -anchorPoint.getY());

      return invTransform.createTransformedShape(shape);
    }
  }

  public class LinkSegmentToInvariantShape extends BasicShape {

    final Point2D anchorPoint;
    final Shape invShape;

    public LinkSegmentToInvariantShape(
        Line2D line, Stroke stroke, Point2D anchorPoint, Shape invShape, boolean fixedLineWidth) {
      super(line, stroke, fixedLineWidth);

      if (anchorPoint == null) {
        throw new IllegalArgumentException();
      }
      this.anchorPoint = (Point2D) anchorPoint.clone();
      this.invShape = invShape;
    }

    @Override
    public Shape getRealShape() {
      if (transform == null) {
        return shape;
      }
      Line2D line = (Line2D) shape;

      AffineTransform invTransform = new AffineTransform(); // Identity transformation.

      double scale = GeomUtil.extractScalingFactor(transform);
      double angleRad = GeomUtil.extractAngleRad(transform);
      boolean scaled = MathUtil.isDifferent(scale, 1.0);
      boolean rotated = MathUtil.isDifferentFromZero(angleRad);

      invTransform.translate(anchorPoint.getX(), anchorPoint.getY());

      if (scaled) {
        invTransform.scale(1 / scale, 1 / scale);
      }
      if (rotated) {
        invTransform.rotate(-angleRad);
      }
      invTransform.translate(-anchorPoint.getX(), -anchorPoint.getY());

      Point2D p = null;
      if (invShape instanceof Rectangle2D rectangle2D) {
        // Find the intersection between the line and the text box
        AffineTransform tr = new AffineTransform();
        tr.translate(anchorPoint.getX(), anchorPoint.getY());
        if (scaled) {
          tr.scale(scale, scale);
        }
        if (rotated) {
          tr.rotate(angleRad);
        }
        tr.translate(-anchorPoint.getX(), -anchorPoint.getY());

        Point2D p2 = line.getP2();
        tr.transform(p2, p2);
        p = GeomUtil.getIntersectPoint(new Line2D.Double(anchorPoint, p2), rectangle2D);
      }
      Point2D invpt = invTransform.transform(p == null ? line.getP1() : p, null);
      return new Line2D.Double(invpt, line.getP2());
    }
  }
}
