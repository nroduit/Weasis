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

public class GraphicLabel {

    protected String[] labelStringArray;

    // protected List<TextLayout> labelTextList;
    // protected Font defaultFont;
    // protected FontRenderContext fontRenderContext;

    // protected Point2D labelPosition;
    protected Rectangle2D labelBounds;
    protected double labelWidth;
    protected double labelHeight;

    // public enum HPos {
    // LEFT, CENTER, RIGHT
    // }
    //
    // public enum VPos {
    // TOP, CENTER, BOTTOM
    // }

    /**
     * GROWING_BOUND min value is 3 because paintBoundOutline grows of 2 pixels the outer rectangle painting, and
     * paintFontOutline grows of 1 pixel all string painting
     */
    protected static final int GROWING_BOUND = 3;

    // /////////////////////////////////////////////////////////////////////////////////////////////////////
    protected void init() {
        labelStringArray = null;

        // if (labelTextList != null)
        // labelTextList.clear();
        // labelTextList = null;
        //
        // fontRenderContext = null;
        // defaultFont = null;
        labelBounds = null;
        // labelPosition = null;
        labelHeight = labelWidth = 0;
    }

    /**
     * @return Label array of strings if defined
     */
    public String[] getLabels() {
        return labelStringArray;
    }

    /**
     * @return Real pixel bounding rectangle with respect to a given Font and Size and independently to any
     *         transformation
     */
    public Rectangle2D getLabelBounds() {
        return labelBounds;
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
        // if (labelBounds == null)
        // return null;
        //
        // Rectangle2D scaledBounds = (Rectangle2D) labelBounds.clone();
        // double scalingFactor = GeomUtil.extractScalingFactor(transform);
        //
        // if (scalingFactor != 1.0) {
        // double invScaledWidth = labelBounds.getWidth() / scalingFactor;
        // double invScaledHeight = labelBounds.getHeight() / scalingFactor;
        // scaledBounds.setRect(scaledBounds.getX(), scaledBounds.getY(), invScaledWidth, invScaledHeight);
        // }
        //
        // Point2D pt1 = new Point2D.Double(1, 0);
        // Point2D pt2 = transform.deltaTransform(pt1, null);
        // double rot = GeomUtil.getAngleDeg(pt1, new Point2D.Double(0, 0), pt2);
        //
        // AffineTransform invRot = AffineTransform.getRotateInstance(-rot, scaledBounds.getX(), scaledBounds.getY());
        //
        // return scaledBounds;
        return getArea(transform).getBounds();
    }

    public Area getArea(AffineTransform transform) {
        if (labelBounds == null)
            return new Area();

        Rectangle2D scaledBounds = (Rectangle2D) labelBounds.clone();
        double scalingFactor = GeomUtil.extractScalingFactor(transform);

        if (scalingFactor != 1.0) {
            double invScaledWidth = labelBounds.getWidth() / scalingFactor;
            double invScaledHeight = labelBounds.getHeight() / scalingFactor;
            scaledBounds.setRect(scaledBounds.getX(), scaledBounds.getY(), invScaledWidth, invScaledHeight);
        }
        Point2D pt1 = new Point2D.Double(1, 0);
        Point2D pt2 = transform.deltaTransform(pt1, null);
        double rot = GeomUtil.getAngleRad(pt2, new Point2D.Double(0, 0), pt1);

        AffineTransform invRot = AffineTransform.getRotateInstance(-rot, scaledBounds.getX(), scaledBounds.getY());

        Area boundingArea = new Area(scaledBounds);
        boundingArea.transform(invRot);

        return boundingArea;
    }

    /**
     * @param realBounds
     * @param transform
     * @return Real label bounding rectangle translated according to given transformation. <br>
     */
    public Rectangle2D getTransformedBounds(Rectangle2D realBounds, AffineTransform transform) {
        if (realBounds == null)
            return null;

        Rectangle2D transformedBounds = (Rectangle2D) realBounds.clone();

        if (transform != null) {
            // Only translates origin because no rotation or scaling is applied
            Point2D.Double p = new Point2D.Double(transformedBounds.getX(), transformedBounds.getY());
            transform.transform(p, p);
            transformedBounds.setRect(p.getX(), p.getY(), transformedBounds.getWidth(), transformedBounds.getHeight());
        }

        return transformedBounds.getBounds();
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Sets label strings and compute bounding rectangle size and position in pixel world according to the DefaultView
     * which defines current "Font"<br>
     */
    public void setLabel(DefaultView2d view2d, double xPos, double yPos, String... labels) {
        if (view2d == null)
            throw new RuntimeException("DefaultView2d should not be null !");

        if (labels == null || labels.length == 0) {
            init();
        } else {
            labelStringArray = labels;

            Font defaultFont = view2d.getEventManager().getViewSetting().getFont();
            FontRenderContext fontRenderContext = ((Graphics2D) view2d.getGraphics()).getFontRenderContext();

            updateBoundsSize(defaultFont, fontRenderContext);

            labelBounds =
                new Rectangle.Double(xPos + GROWING_BOUND, yPos + GROWING_BOUND, labelWidth, labelHeight
                    * labels.length);

            GeomUtil.growRectangle(labelBounds, GROWING_BOUND);
        }
    }

    protected void updateBoundsSize(Font defaultFont, FontRenderContext fontRenderContext) {
        if (defaultFont == null)
            throw new RuntimeException("Font shouldn't ne null");
        if (fontRenderContext == null)
            throw new RuntimeException("FontRenderContext shouldn't ne null");

        if (labelStringArray == null || labelStringArray.length == 0) {
            labelBounds = null;
            labelHeight = labelWidth = 0;
        } else {
            double maxWidth = 0, maxHeight = 0;

            for (String label : labelStringArray) {
                // Rectangle2D stringBounds = defaultFont.getStringBounds(label, fontRenderContext);
                // => approach above does not guaranty to get the more accurate visual bounding

                if (label.length() > 0) {
                    TextLayout layout = new TextLayout(label, defaultFont, fontRenderContext);
                    Rectangle2D stringBounds = layout.getBounds();

                    maxWidth = Math.max(stringBounds.getWidth(), maxWidth);
                    maxHeight = Math.max(stringBounds.getHeight(), maxHeight);
                }
            }

            labelHeight = maxHeight;
            labelWidth = maxWidth;
        }
    }

    // public void setLabelPosition(double xPos, double yPos) {
    // if (labelBounds != null)
    // labelBounds.setRect(xPos, yPos, labelBounds.getWidth(), labelBounds.getHeight());
    // }

    public void paint(Graphics2D g2d, AffineTransform transform, boolean selected) {
        if (labelStringArray != null && labelBounds != null) {

            Paint oldPaint = g2d.getPaint();

            Point2D pt = new Point2D.Double(labelBounds.getX(), labelBounds.getY());
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

            if (selected) {
                paintBoundOutline(g2d, transform);
            }

            // if (transform != null) {
            // g2d.setPaint(Color.RED);
            // Rectangle realBounds = getBound(transform);
            // Rectangle2D transformedBounds = getTransformedBounds(realBounds, transform);
            // g2d.draw(transformedBounds);
            // }
            // if (transform != null) {
            // g2d.setPaint(Color.GREEN);
            // g2d.draw(transform.createTransformedShape(getBounds(transform)));
            // }
            // if (transform != null) {
            // g2d.setPaint(Color.RED);
            // g2d.draw(transform.createTransformedShape(getArea(transform)));
            // }

            g2d.setPaint(oldPaint);
        }
    }

    protected void paintBoundOutline(Graphics2D g2d, AffineTransform transform) {

        Rectangle2D boundingRect = getTransformedBounds(labelBounds, transform);

        // Point2D.Double p = new Point2D.Double(labelBounds.getX(), labelBounds.getY());
        // transform.transform(p, p);
        //
        // Rectangle2D boundingRect =
        // new Rectangle2D.Double(p.getX(), p.getY(), labelBounds.getWidth(), labelBounds.getHeight());

        g2d.setPaint(Color.BLACK);
        g2d.draw(boundingRect);

        GeomUtil.growRectangle(boundingRect, -1);
        g2d.setPaint(Color.WHITE);
        g2d.draw(boundingRect);

        // GeomUtil.growRectangle(boundingRect, -1);
        // g2d.setPaint(Color.RED);
        // g2d.draw(boundingRect);

    }

    protected void paintFontOutline(Graphics2D g2d, String str, float x, float y) {

        Font defaultFont = g2d.getFont();
        FontRenderContext fontRenderContext = g2d.getFontRenderContext();

        assert (defaultFont != null) : "Font shouldn't be null";
        assert (fontRenderContext != null) : "FontRenderContext shouldn't be null";

        TextLayout layout = new TextLayout(str, defaultFont, fontRenderContext);

        g2d.setPaint(Color.BLACK);

        layout.draw(g2d, x - 1f, y - 1f);
        layout.draw(g2d, x - 1f, y);
        layout.draw(g2d, x - 1f, y + 1f);
        layout.draw(g2d, x, y - 1f);
        layout.draw(g2d, x, y + 1f);
        layout.draw(g2d, x + 1f, y - 1f);
        layout.draw(g2d, x + 1f, y);
        layout.draw(g2d, x + 1f, y + 1f);

        // g2d.drawString(str, x - 1f, y - 1f);
        // g2d.drawString(str, x - 1f, y);
        // g2d.drawString(str, x - 1f, y + 1f);
        // g2d.drawString(str, x, y - 1f);
        // g2d.drawString(str, x, y + 1f);
        // g2d.drawString(str, x + 1f, y - 1f);
        // g2d.drawString(str, x + 1f, y);
        // g2d.drawString(str, x + 1f, y + 1f);
        g2d.setPaint(Color.WHITE);
        layout.draw(g2d, x, y);

        // g2d.drawString(str, x, y);
    }

    @Deprecated
    public double getOffsetX() {
        return 3.0;
    }

    @Deprecated
    public double getOffsetY() {
        if (labelBounds == null)
            return 0;
        return -10;
    }

    @Deprecated
    public Rectangle getBound() {
        return labelBounds == null ? null : labelBounds.getBounds2D().getBounds();
    }

    @Deprecated
    public void setLabelBound(double x, double y, double width, double height) {
        if (labelBounds == null) {
            labelBounds = new Rectangle.Double(x, y, width, height).getBounds();
        } else {
            labelBounds.setRect(x, y, width, height);
        }
    }
}
