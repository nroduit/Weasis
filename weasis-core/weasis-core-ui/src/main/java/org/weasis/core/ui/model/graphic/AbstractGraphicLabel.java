/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.graphic;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Optional;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.util.StringUtil;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.utils.imp.DefaultGraphicLabel;

public abstract class AbstractGraphicLabel implements GraphicLabel {
    protected String[] labels;
    protected Rectangle2D labelBounds;
    protected Double labelWidth;
    protected Double labelHeight;
    protected Double offsetX;
    protected Double offsetY;

    public AbstractGraphicLabel() {
        this(DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
    }

    public AbstractGraphicLabel(Double offsetX, Double offsetY) {
        this.offsetX = Optional.ofNullable(offsetX).orElse(DEFAULT_OFFSET_X);
        this.offsetY = Optional.ofNullable(offsetY).orElse(DEFAULT_OFFSET_Y);
        reset();
    }

    public AbstractGraphicLabel(AbstractGraphicLabel object) {
        this.offsetX = object.offsetX;
        this.offsetY = object.offsetY;
        this.labels = Optional.ofNullable(object.labels).map(String[]::clone).orElse(null);
        this.labelBounds = Optional.ofNullable(object.labelBounds).map(Rectangle2D::getBounds2D).orElse(null);
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
    @XmlElement(name = "label", required = false)
    @Override
    public String[] getLabels() {
        return labels;
    }

    @XmlElement(name = "offsetX", required = false)
    @Override
    public Double getOffsetX() {
        return offsetX;
    }

    @XmlElement(name = "offsetY", required = false)
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
        Point2D.Double anchorPoint = new Point2D.Double(labelBounds.getX() + offsetX, labelBounds.getY() + offsetY);
        Optional.ofNullable(transform).ifPresent(t -> transform.transform(anchorPoint, anchorPoint));

        return new Rectangle2D.Double(anchorPoint.getX(), anchorPoint.getY(), labelBounds.getWidth(),
            labelBounds.getHeight());
    }

    @Override
    public void setLabel(ViewCanvas<?> view2d, Double xPos, Double yPos, String... labels) {
        if (labels == null || labels.length == 0) {
            reset();
        } else {
            this.labels = labels;
            Font defaultFont = view2d == null ? FontTools.getFont12() : view2d.getFont();
            Graphics2D g2d = view2d == null ? null : (Graphics2D) view2d.getJComponent().getGraphics();
            FontRenderContext fontRenderContext =
                g2d == null ? new FontRenderContext(null, false, false) : g2d.getFontRenderContext();
            updateBoundsSize(defaultFont, fontRenderContext);

            labelBounds = new Rectangle.Double(xPos + GROWING_BOUND, yPos + GROWING_BOUND, labelWidth + GROWING_BOUND,
                (labelHeight * labels.length) + GROWING_BOUND);
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
            labelHeight = new TextLayout("Tg", defaultFont, fontRenderContext).getBounds().getHeight() + 2; //$NON-NLS-1$
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
            float py = (float) pt.getY() + GROWING_BOUND;

            for (String label : labels) {
                if (StringUtil.hasText(label)) {
                    py += labelHeight;
                    paintColorFontOutline(g2d, label, px, py, Color.WHITE);
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

    public static void paintColorFontOutline(Graphics2D g2, String str, float x, float y, Color color) {
        g2.setPaint(Color.BLACK);

        if (RenderingHints.VALUE_TEXT_ANTIALIAS_ON.equals(g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING))) {
            TextLayout layout = new TextLayout(str, g2.getFont(), g2.getFontRenderContext());
            AffineTransform textAt = new AffineTransform();
            textAt.translate(x, y);
            Shape outline = layout.getOutline(textAt);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g2.draw(outline);
            g2.setPaint(color);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g2.fill(outline);
        } else {
            g2.drawString(str, x - 1f, y - 1f);
            g2.drawString(str, x - 1f, y);
            g2.drawString(str, x - 1f, y + 1f);
            g2.drawString(str, x, y - 1f);
            g2.drawString(str, x, y + 1f);
            g2.drawString(str, x + 1f, y - 1f);
            g2.drawString(str, x + 1f, y);
            g2.drawString(str, x + 1f, y + 1f);
            g2.setPaint(color);
            g2.drawString(str, x, y);
        }
    }

    public static void paintFontOutline(Graphics2D g2, String str, float x, float y) {
        paintColorFontOutline(g2, str, x, y, Color.WHITE);
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
