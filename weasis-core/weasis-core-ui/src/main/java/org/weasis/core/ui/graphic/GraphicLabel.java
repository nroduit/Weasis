/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.editor.image.ViewCanvas;

@Root(name = "label")
public class GraphicLabel implements Cloneable {
    /**
     * GROWING_BOUND min value is 3 because paintBoundOutline grows of 2 pixels the outer rectangle painting, and
     * paintFontOutline grows of 1 pixel all string painting
     */
    public static final int GROWING_BOUND = 3;
    protected String[] labelStringArray;

    protected Rectangle2D labelBounds;
    protected double labelWidth;
    protected double labelHeight;

    @Attribute(name = "offsetX")
    protected double offsetX;
    @Attribute(name = "offsetY")
    protected double offsetY;

    public GraphicLabel() {
        this(0.0, 0.0);
    }

    public GraphicLabel(@Attribute(name = "offsetX") double offsetX, @Attribute(name = "offsetY") double offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        reset();
    }

    protected void reset() {
        labelStringArray = null;
        labelBounds = null;
        labelHeight = labelWidth = 0;
    }

    /**
     * @return Label array of strings if defined
     */
    public String[] getLabels() {
        return labelStringArray;
    }

    /**
     * Should be used to check if mouse coordinates are inside/outside label bounding rectangle. Also useful to check
     * intersection with clipping rectangle.
     *
     * @param transform
     * @return Labels bounding rectangle in real world with size rescaled. It takes care of the current transformation
     *         scaling factor so labels painting have invariant size according to the given transformation.
     */
    public Rectangle2D getBounds(AffineTransform transform) {
        return getArea(transform).getBounds2D();
    }

    public Area getArea(AffineTransform transform) {
        if (labelBounds == null) {
            return new Area();
        }

        if (transform == null) {
            return new Area(labelBounds);
        }

        AffineTransform invTransform = new AffineTransform(); // Identity transformation.
        Point2D anchorPt = new Point2D.Double(labelBounds.getX(), labelBounds.getY());

        double scale = GeomUtil.extractScalingFactor(transform);
        double angleRad = GeomUtil.extractAngleRad(transform);

        invTransform.translate(anchorPt.getX(), anchorPt.getY());

        if (scale != 1.0) {
            invTransform.scale(1 / scale, 1 / scale);
        }
        if (angleRad != 0) {
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

    /**
     * @param transform
     * @return Real label bounding rectangle translated according to given transformation. <br>
     */
    public Rectangle2D getTransformedBounds(AffineTransform transform) {

        // Only translates origin because no rotation or scaling is applied
        Point2D.Double anchorPoint = new Point2D.Double(labelBounds.getX() + offsetX, labelBounds.getY() + offsetY);
        if (transform != null) {
            transform.transform(anchorPoint, anchorPoint);
        }

        return new Rectangle2D.Double(anchorPoint.getX(), anchorPoint.getY(), labelBounds.getWidth(),
            labelBounds.getHeight());
    }

    /**
     * Sets label strings and compute bounding rectangle size and position in pixel world according to the DefaultView
     * which defines current "Font"<br>
     */
    public void setLabel(ViewCanvas view2d, double xPos, double yPos, String... labels) {
        if (view2d == null || labels == null || labels.length == 0) {
            reset();
        } else {
            Graphics2D g2d = (Graphics2D) view2d.getJComponent().getGraphics();
            if (g2d == null) {
                return;
            }
            labelStringArray = labels;
            Font defaultFont = g2d.getFont();
            FontRenderContext fontRenderContext =
                ((Graphics2D) view2d.getJComponent().getGraphics()).getFontRenderContext();
            updateBoundsSize(defaultFont, fontRenderContext);

            labelBounds = new Rectangle.Double(xPos + GROWING_BOUND, yPos + GROWING_BOUND, labelWidth + GROWING_BOUND,
                (labelHeight * labels.length) + GROWING_BOUND);
            GeomUtil.growRectangle(labelBounds, GROWING_BOUND);
        }
    }

    protected void updateBoundsSize(Font defaultFont, FontRenderContext fontRenderContext) {
        if (defaultFont == null) {
            throw new RuntimeException("Font should not be null"); //$NON-NLS-1$
        }
        if (fontRenderContext == null) {
            throw new RuntimeException("FontRenderContext should not be null"); //$NON-NLS-1$
        }

        if (labelStringArray == null || labelStringArray.length == 0) {
            reset();
        } else {
            double maxWidth = 0;
            for (String label : labelStringArray) {
                if (StringUtil.hasText(label)) {
                    TextLayout layout = new TextLayout(label, defaultFont, fontRenderContext);
                    maxWidth = Math.max(layout.getBounds().getWidth(), maxWidth);
                }
            }
            labelHeight = new TextLayout("Tg", defaultFont, fontRenderContext).getBounds().getHeight() + 2; //$NON-NLS-1$
            labelWidth = maxWidth;
        }
    }

    protected void move(double deltaX, double deltaY) {
        offsetX += deltaX;
        offsetY += deltaY;
    }

    public void paint(Graphics2D g2d, AffineTransform transform, boolean selected) {
        if (labelStringArray != null && labelBounds != null) {

            Paint oldPaint = g2d.getPaint();

            Point2D pt = new Point2D.Double(labelBounds.getX() + offsetX, labelBounds.getY() + offsetY);

            if (transform != null) {
                transform.transform(pt, pt);
            }

            float px = (float) pt.getX() + GROWING_BOUND;
            float py = (float) pt.getY() + GROWING_BOUND;

            for (String label : labelStringArray) {
                if (StringUtil.hasText(label)) {
                    py += labelHeight;
                    paintColorFontOutline(g2d, label, px, py, Color.WHITE);
                }
            }

            // Graphics DEBUG
            // Point2D pt2 = new Point2D.Double(labelBounds.getX(), labelBounds.getY());
            // if (transform != null)
            // transform.transform(pt2, pt2);
            //
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

    public static void paintFontOutline(Graphics2D g2, String str, float x, float y) {
        paintColorFontOutline(g2, str, x, y, Color.WHITE);
    }

    public static void paintColorFontOutline(Graphics2D g2, String str, float x, float y, Color color) {
        g2.setPaint(Color.BLACK);

        if (RenderingHints.VALUE_TEXT_ANTIALIAS_ON.equals(g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING))) {
            TextLayout layout = new TextLayout(str, g2.getFont(), g2.getFontRenderContext());
            Rectangle2D b = layout.getBounds();
            b.setRect(x + b.getX() - 0.75, y + b.getY() - 0.75, b.getWidth() + 1.5, b.getHeight() + 1.5);
            g2.fill(b);
        } else {
            g2.drawString(str, x - 1f, y - 1f);
            g2.drawString(str, x - 1f, y);
            g2.drawString(str, x - 1f, y + 1f);
            g2.drawString(str, x, y - 1f);
            g2.drawString(str, x, y + 1f);
            g2.drawString(str, x + 1f, y - 1f);
            g2.drawString(str, x + 1f, y);
            g2.drawString(str, x + 1f, y + 1f);
        }
        g2.setPaint(color);
        g2.drawString(str, x, y);
    }

    @Override
    protected GraphicLabel clone() {
        GraphicLabel cloneLabel = null;
        try {
            cloneLabel = (GraphicLabel) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return cloneLabel;
    }
}
