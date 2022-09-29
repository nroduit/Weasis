/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Optional;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.utils.imp.DefaultGraphicLabel;
import org.weasis.core.util.StringUtil;

public abstract class AbstractGraphicLabel implements GraphicLabel {
  protected String[] labels;
  protected Rectangle2D labelBounds;
  protected Double labelWidth;
  protected Double labelHeight;
  protected Double offsetX;
  protected Double offsetY;

  protected AbstractGraphicLabel() {
    this(DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
  }

  protected AbstractGraphicLabel(Double offsetX, Double offsetY) {
    this.offsetX = Optional.ofNullable(offsetX).orElse(DEFAULT_OFFSET_X);
    this.offsetY = Optional.ofNullable(offsetY).orElse(DEFAULT_OFFSET_Y);
    reset();
  }

  protected AbstractGraphicLabel(AbstractGraphicLabel object) {
    this.offsetX = object.offsetX;
    this.offsetY = object.offsetY;
    this.labels = Optional.ofNullable(object.labels).map(String[]::clone).orElse(null);
    this.labelBounds =
        Optional.ofNullable(object.labelBounds).map(Rectangle2D::getBounds2D).orElse(null);
    this.labelWidth = object.labelWidth;
    this.labelHeight = object.labelHeight;
  }

  @Override
  public void reset() {
    labels = null;
    labelBounds = null;
    labelHeight = 0d;
    labelWidth = 0d;
  }

  @XmlElementWrapper(name = "labels")
  @XmlElement(name = "label")
  @Override
  public String[] getLabels() {
    return labels;
  }

  @XmlElement(name = "offsetX")
  @Override
  public Double getOffsetX() {
    return offsetX;
  }

  @XmlElement(name = "offsetY")
  @Override
  public Double getOffsetY() {
    return offsetY;
  }

  public void setLabels(String[] labels) {
    this.labels = labels;
  }

  public void setOffsetX(Double offsetX) {
    this.offsetX = offsetX;
  }

  public void setOffsetY(Double offsetY) {
    this.offsetY = offsetY;
  }

  @Override
  public Rectangle2D getBounds(AffineTransform transform) {
    return getArea(transform).getBounds2D();
  }

  @Override
  public Area getArea(AffineTransform transform) {
    if (Objects.isNull(labelBounds)) {
      return new Area();
    }

    if (Objects.isNull(transform)) {
      return new Area(labelBounds);
    }

    AffineTransform invTransform = new AffineTransform(); // Identity transformation.
    Point2D anchorPt = new Point2D.Double(labelBounds.getX(), labelBounds.getY());

    double scale = GeomUtil.extractScalingFactor(transform);
    double angleRad = GeomUtil.extractAngleRad(transform);

    invTransform.translate(anchorPt.getX(), anchorPt.getY());

    if (!Objects.equals(scale, 1d)) {
      invTransform.scale(1 / scale, 1 / scale);
    }
    if (!Objects.equals(angleRad, 0d)) {
      invTransform.rotate(-angleRad);
    }

    invTransform.translate(-anchorPt.getX(), -anchorPt.getY());

    if ((transform.getType() & AffineTransform.TYPE_FLIP) != 0) {
      invTransform.translate(0, -labelBounds.getHeight());
    }

    Area areaBounds = new Area(invTransform.createTransformedShape(labelBounds));
    areaBounds.transform(AffineTransform.getTranslateInstance(offsetX, offsetY));

    return areaBounds;
  }

  @Override
  public Rectangle2D getTransformedBounds(AffineTransform transform) {
    // Only translates origin because no rotation or scaling is applied
    Point2D anchorPoint =
        new Point2D.Double(labelBounds.getX() + offsetX, labelBounds.getY() + offsetY);
    Optional.ofNullable(transform).ifPresent(t -> transform.transform(anchorPoint, anchorPoint));

    return new Rectangle2D.Double(
        anchorPoint.getX(), anchorPoint.getY(), labelBounds.getWidth(), labelBounds.getHeight());
  }

  @Override
  public void setLabel(ViewCanvas<?> view2d, Double xPos, Double yPos, String... labels) {
    if (labels == null || labels.length == 0) {
      reset();
    } else {
      this.labels = labels;
      Font defaultFont = view2d == null ? FontItem.DEFAULT.getFont() : view2d.getFont();
      Graphics2D g2d = view2d == null ? null : (Graphics2D) view2d.getJComponent().getGraphics();
      FontRenderContext fontRenderContext =
          g2d == null ? new FontRenderContext(null, false, false) : g2d.getFontRenderContext();
      updateBoundsSize(defaultFont, fontRenderContext);

      labelBounds = new Rectangle.Double(xPos, yPos, labelWidth, (labelHeight * labels.length));
      GeomUtil.growRectangle(labelBounds, GROWING_BOUND);
    }
  }

  protected void updateBoundsSize(Font defaultFont, FontRenderContext fontRenderContext) {
    Objects.requireNonNull(defaultFont);
    Objects.requireNonNull(fontRenderContext);

    if (labels == null || labels.length == 0) {
      reset();
    } else {
      double maxWidth = 0;
      for (String label : labels) {
        if (StringUtil.hasText(label)) {
          TextLayout layout = new TextLayout(label, defaultFont, fontRenderContext);
          maxWidth = Math.max(layout.getBounds().getWidth(), maxWidth);
        }
      }
      labelHeight =
          new TextLayout("Tg", defaultFont, fontRenderContext).getBounds().getHeight(); // NON-NLS
      labelWidth = maxWidth;
    }
  }

  @Override
  public void move(Double deltaX, Double deltaY) {
    Optional.ofNullable(deltaX).ifPresent(delta -> this.offsetX += delta);
    Optional.ofNullable(deltaY).ifPresent(delta -> this.offsetY += delta);
  }

  @Override
  public void paint(Graphics2D g2d, AffineTransform transform, boolean selected) {
    if (labels != null && labelBounds != null) {

      Paint oldPaint = g2d.getPaint();

      Point2D pt = new Point2D.Double(labelBounds.getX() + offsetX, labelBounds.getY() + offsetY);

      if (transform != null) {
        transform.transform(pt, pt);
      }

      float px = (float) pt.getX() + GROWING_BOUND;
      float py = (float) pt.getY() + GROWING_BOUND - g2d.getFontMetrics().getDescent() + 1;

      for (String label : labels) {
        if (StringUtil.hasText(label)) {
          py += labelHeight;
          FontTools.paintColorFontOutline(g2d, label, px, py, Color.WHITE);
        }
      }

      // Graphics DEBUG
      // Point2D pt2 = new Point2D.Double(labelBounds.getX(), labelBounds.getY());
      // if (transform != null) {
      // transform.transform(pt2, pt2);
      // }
      //
      // g2d.setPaint(Color.RED);
      // g2d.draw(new Line2D.Double(pt2.getX() - 5, pt2.getY(), pt2.getX() + 5, pt2.getY()));
      // g2d.draw(new Line2D.Double(pt2.getX(), pt2.getY() - 5, pt2.getX(), pt2.getY() + 5));
      //
      // if (transform != null) {
      // g2d.setPaint(Color.GREEN);
      // g2d.draw(transform.createTransformedShape(getBounds(transform)));
      // }
      // if (transform != null) {
      // g2d.setPaint(Color.RED);
      // g2d.draw(transform.createTransformedShape(getArea(transform)));
      // }
      // Graphics DEBUG

      if (selected) {
        paintBoundOutline(g2d, transform);
      }

      g2d.setPaint(oldPaint);
    }
  }

  protected void paintBoundOutline(Graphics2D g2d, AffineTransform transform) {
    Rectangle2D boundingRect = getTransformedBounds(transform);
    Paint oldPaint = g2d.getPaint();

    g2d.setPaint(Color.BLACK);
    g2d.draw(boundingRect);

    GeomUtil.growRectangle(boundingRect, -1);

    g2d.setPaint(Color.WHITE);
    g2d.draw(boundingRect);

    g2d.setPaint(oldPaint);
  }

  @Override
  public Rectangle2D getLabelBounds() {
    return labelBounds;
  }

  public static class Adapter extends XmlAdapter<DefaultGraphicLabel, GraphicLabel> {

    @Override
    public GraphicLabel unmarshal(DefaultGraphicLabel v) throws Exception {
      return v;
    }

    @Override
    public DefaultGraphicLabel marshal(GraphicLabel v) throws Exception {
      return (DefaultGraphicLabel) v;
    }
  }
}
