/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.seg;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.ui.model.graphic.AbstractGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicArea;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

public class SegGraphic extends AbstractGraphic implements GraphicArea {

  /** Extra stroke width, in display pixels, applied while the region is highlighted. */
  public static final float HIGHLIGHT_EXTRA_THICKNESS = 2f;

  private Stroke stroke;
  private SegContour contour;

  /** Cached outline bounds: {@link Shape#getBounds2D()} iterates all the path segments. */
  private Rectangle2D outlineBounds;

  private boolean highlighted;
  private transient BufferedImage cachedBinaryImage;
  private transient int cachedColor;
  private transient float cachedOpacity;
  private transient boolean cachedFilled;
  private transient boolean cachedHighlighted;

  public SegGraphic(Shape path) {
    this(path, null);
  }

  public SegGraphic(Shape path, Stroke stroke) {
    super(0);
    this.stroke = stroke;
    setShape(path, null);
    this.outlineBounds = path == null ? null : path.getBounds2D();
    updateLabel(null, null);
  }

  public SegGraphic(SegGraphic graphic) { // NOSONAR see initCopy()
    super(graphic);
  }

  @Override
  protected void initCopy(Graphic graphic) {
    super.initCopy(graphic);
    if (graphic instanceof SegGraphic other) {
      this.stroke = other.stroke;
      this.contour = other.contour;
      this.shape = other.shape;
      this.outlineBounds = other.outlineBounds;
    }
  }

  @Override
  public SegGraphic copy() {
    return new SegGraphic(this);
  }

  @Override
  public void setFilled(Boolean filled) {
    if (!Objects.equals(this.filled, filled)) {
      this.filled = filled == null ? DEFAULT_FILLED : filled;
      fireDrawingChanged();
    }
  }

  @Override
  protected void prepareShape() throws InvalidShapeException {
    if (!isShapeValid()) {
      throw new InvalidShapeException("This shape cannot be drawn");
    }
    buildShape();
  }

  @Override
  public void buildShape() {
    updateLabel(null, null);
  }

  @Override
  public String getUIName() {
    return "";
  }

  @Override
  public boolean isOnGraphicLabel(MouseEventDouble mouseevent) {
    return false;
  }

  @Override
  public String getDescription() {
    return getUIName();
  }

  @Override
  public Area getArea(AffineTransform transform) {
    return new Area();
  }

  public Stroke getStroke() {
    return stroke;
  }

  public void setStroke(Stroke stroke) {
    this.stroke = stroke;
  }

  public SegContour getContour() {
    return contour;
  }

  public void setContour(SegContour contour) {
    this.contour = contour;
  }

  /** Returns the region this graphic was built from, or {@code null} if unknown. */
  public SegRegion<?> getRegion() {
    return contour != null && contour.getAttributes() instanceof SegRegion<?> region
        ? region
        : null;
  }

  public boolean isHighlighted() {
    return highlighted;
  }

  /**
   * Widens the outline at paint time. Unlike {@link #setLineThickness(Float)} it does not alter the
   * model, so the change never outlives the hover and fires no drawing event: the caller repaints
   * the affected area.
   */
  public void setHighlighted(boolean highlighted) {
    this.highlighted = highlighted;
  }

  /**
   * Tests whether an image-space point lies on the outline, or anywhere inside the region when it
   * is filled. Deliberately avoids building an {@link Area} from the stroked path: on segmentation
   * contours that is orders of magnitude more expensive than the crossing tests used here.
   *
   * @param p the point in image coordinates
   * @param tolerance the half-width, in image coordinates, of the square tested around the point
   */
  public boolean isOnOutline(Point2D p, double tolerance) {
    if (contour != null && contour.getBinaryMask() != null && getFilled()) {
      int x = (int) Math.floor(p.getX() + 0.5);
      int y = (int) Math.floor(p.getY() + 0.5);
      return x >= 0
          && x < contour.getBinaryWidth()
          && y >= 0
          && y < contour.getBinaryHeight()
          && contour.getBinaryMask()[y * contour.getBinaryWidth() + x] != 0;
    }
    if (shape == null || outlineBounds == null || !isInBounds(p, tolerance)) {
      return false;
    }
    Rectangle2D box =
        new Rectangle2D.Double(
            p.getX() - tolerance, p.getY() - tolerance, tolerance * 2, tolerance * 2);
    if (!shape.intersects(box)) {
      return false;
    }
    // The box straddles the outline when it intersects the shape without being fully inside it
    return getFilled() || !shape.contains(box);
  }

  @Override
  public Rectangle getRepaintBounds(AffineTransform transform) {
    return contour != null && contour.getBinaryBounds() != null
        ? getRepaintBounds(contour.getBinaryBounds(), transform)
        : super.getRepaintBounds(transform);
  }

  @Override
  public void paint(Graphics2D g2d, AffineTransform transform) {
    if (contour == null || contour.getBinaryMask() == null) {
      super.paint(g2d, transform);
      return;
    }
    Color color = getColorPaint() instanceof Color c ? c : Color.RED;
    float opacity = getFillOpacity();
    boolean filled = getFilled();
    if (cachedBinaryImage == null
        || cachedColor != color.getRGB()
        || cachedOpacity != opacity
        || cachedFilled != filled
        || cachedHighlighted != highlighted) {
      cachedBinaryImage =
          BinaryMaskOverlay.colorize(
              contour.getBinaryMask(),
              contour.getBinaryWidth(),
              contour.getBinaryHeight(),
              color,
              opacity,
              filled,
              highlighted);
      cachedColor = color.getRGB();
      cachedOpacity = opacity;
      cachedFilled = filled;
      cachedHighlighted = highlighted;
    }
    AffineTransform imageTransform =
        transform == null ? new AffineTransform() : new AffineTransform(transform);
    // DICOM pixel coordinates denote centres; BufferedImage coordinates denote pixel edges.
    imageTransform.translate(-0.5, -0.5);
    RenderingHints oldHints = g2d.getRenderingHints();
    g2d.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    try {
      g2d.drawImage(cachedBinaryImage, imageTransform, null);
    } finally {
      g2d.setRenderingHints(oldHints);
    }
    if (getSelected()) {
      paintHandles(g2d, transform);
    }
    paintLabel(g2d, transform);
  }

  private boolean isInBounds(Point2D p, double tolerance) {
    return p.getX() >= outlineBounds.getMinX() - tolerance
        && p.getX() <= outlineBounds.getMaxX() + tolerance
        && p.getY() >= outlineBounds.getMinY() - tolerance
        && p.getY() <= outlineBounds.getMaxY() + tolerance;
  }

  @Override
  public Stroke getStroke(Float lineThickness) {
    if (highlighted) {
      float width =
          (lineThickness == null ? DEFAULT_LINE_THICKNESS : lineThickness)
              + HIGHLIGHT_EXTRA_THICKNESS;
      return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }
    return stroke != null ? stroke : super.getStroke(lineThickness);
  }

  @Override
  public List<MeasureItem> getImageStatistics(MeasurableLayer layer, Boolean releaseEvent) {
    return null;
  }
}
