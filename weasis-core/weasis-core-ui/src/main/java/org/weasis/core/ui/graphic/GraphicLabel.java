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
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.editor.image.DefaultView2d;

public class GraphicLabel implements Cloneable {
    /**
     * GROWING_BOUND min value is 3 because paintBoundOutline grows of 2 pixels the outer rectangle painting, and
     * paintFontOutline grows of 1 pixel all string painting
     */
    protected static final int GROWING_BOUND = 3;
    protected String[] labelStringArray;

    protected Rectangle2D labelBounds;
    protected double labelWidth;
    protected double labelHeight;

    protected double offsetX = 0;
    protected double offsetY = 0;

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
        if (labelBounds == null)
            return new Area();

        if (transform == null)
            return new Area(labelBounds);

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
            // invTransform.translate(-labelBounds.getWidth(), -labelBounds.getHeight());
        }

        // invTransform.translate(offsetX, offsetY);
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
    public void setLabel(DefaultView2d view2d, double xPos, double yPos, String... labels) {
        if (view2d == null || labels == null || labels.length == 0) {
            reset();
        } else {
            labelStringArray = labels;
            Font defaultFont = view2d.getEventManager().getViewSetting().getFont();
            FontRenderContext fontRenderContext = ((Graphics2D) view2d.getGraphics()).getFontRenderContext();
            updateBoundsSize(defaultFont, fontRenderContext);

            labelBounds =
                new Rectangle.Double(xPos + GROWING_BOUND, yPos + GROWING_BOUND, labelWidth + GROWING_BOUND,
                    (labelHeight * labels.length) + GROWING_BOUND);
            GeomUtil.growRectangle(labelBounds, GROWING_BOUND);
        }
    }

    protected void updateBoundsSize(Font defaultFont, FontRenderContext fontRenderContext) {
        if (defaultFont == null)
            throw new RuntimeException("Font should not be null");
        if (fontRenderContext == null)
            throw new RuntimeException("FontRenderContext should not be null");

        if (labelStringArray == null || labelStringArray.length == 0) {
            reset();
        } else {
            double maxWidth = 0;
            for (String label : labelStringArray) {
                if (label.length() > 0) {
                    TextLayout layout = new TextLayout(label, defaultFont, fontRenderContext);
                    maxWidth = Math.max(layout.getBounds().getWidth(), maxWidth);
                }
            }
            labelHeight = new TextLayout("Tg", defaultFont, fontRenderContext).getBounds().getHeight() + 2;
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
                if (label.length() > 0) {
                    py += labelHeight;
                    paintFontOutline(g2d, label, px, py);
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

    protected void paintFontOutline(Graphics2D g2d, String str, float x, float y) {

        Paint oldPaint = g2d.getPaint();
        // TextLayout layout = new TextLayout(str, g2d.getFont(), g2d.getFontRenderContext());
        // NOTE : when using TextLayout, export to clipboard doesn't work

        g2d.setPaint(Color.BLACK);
        // layout.draw(g2d, x - 1f, y - 1f);
        // layout.draw(g2d, x - 1f, y);
        // layout.draw(g2d, x - 1f, y + 1f);
        // layout.draw(g2d, x, y - 1f);
        // layout.draw(g2d, x, y + 1f);
        // layout.draw(g2d, x + 1f, y - 1f);
        // layout.draw(g2d, x + 1f, y);
        // layout.draw(g2d, x + 1f, y + 1f);

        g2d.drawString(str, x - 1f, y - 1f);
        g2d.drawString(str, x - 1f, y);
        g2d.drawString(str, x - 1f, y + 1f);
        g2d.drawString(str, x, y - 1f);
        g2d.drawString(str, x, y + 1f);
        g2d.drawString(str, x + 1f, y - 1f);
        g2d.drawString(str, x + 1f, y);
        g2d.drawString(str, x + 1f, y + 1f);

        g2d.setPaint(Color.WHITE);
        // layout.draw(g2d, x, y);
        g2d.drawString(str, x, y);

        g2d.setPaint(oldPaint);
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
