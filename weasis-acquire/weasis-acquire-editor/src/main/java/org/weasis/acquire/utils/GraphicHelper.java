/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.NonEditableGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.opencv.data.PlanarImage;

/**
 *
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-12 - ylar - Creation
 *
 */
public class GraphicHelper {

    public static final int GRID_SPACING = 35; // build grid of 35x35 px

    private GraphicHelper() {
    }

    /**
     * Create a new layer with a grid line. Lines are centralize vertically and horizontally. The grid is a little bit
     * bigger in case of rotation occurs.
     *
     * @param view
     *            Image view (will retrieve width and height)
     * @return The new layer
     * @since 2.5.0
     */
    public static void newGridLayer(ViewCanvas<ImageElement> view) {
        PlanarImage sourceImage = view.getSourceImage();

        // Retrieve image size
        int width = sourceImage.width();
        int height = sourceImage.height();

        double diagonal = Math.sqrt(Math.pow(width, 2) + Math.pow(width, 2));

        double hOffset = diagonal - width / 2.0;
        double vOffset = diagonal - height / 2.0;

        // New start point
        int x0 = (int) (0 - hOffset);
        int y0 = (int) (0 - vOffset);

        // Calculate in witch pixel we should start vertically and horizontally
        int xStart = (int) (((diagonal % GRID_SPACING) / 2) - hOffset);
        int yStart = (int) (((diagonal % GRID_SPACING) / 2) - vOffset);
        
        AffineTransform transform = view.getInverseTransform();

        // Draw vertical lines
        for (int i = xStart - 1; i < width; i = i + GRID_SPACING) {
            Point2D.Double p1 = new Point2D.Double(i, x0);
            Point2D.Double p2 = new Point2D.Double(i, diagonal);
            transform.transform(p1, p1);
            transform.transform(p2, p2);

            AbstractGraphicModel.addGraphicToModel(view, newLine(p1, p2));
        }

        // Draw horizontal lines
        for (int i = yStart - 1; i < height; i = i + GRID_SPACING) {
            Point2D.Double p1 = new Point2D.Double(y0, i);
            Point2D.Double p2 = new Point2D.Double(diagonal, i);
            transform.transform(p1, p1);
            transform.transform(p2, p2);

            AbstractGraphicModel.addGraphicToModel(view, newLine(p1, p2));
        }
    }

    /**
     * Create a new stroke line
     *
     * @param p1
     *            Start point
     * @param p2
     *            End point
     * @return New Stroke line
     * @since 2.5.0
     */
    private static Graphic newLine(Point2D.Double p1, Point2D.Double p2) {
        Line2D line = new Line2D.Double(p1, p2);

        BasicStroke stroke =
            new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 2.0f, 2.0f }, 0f);
        NonEditableGraphic shape = new NonEditableGraphic(line, stroke);
        shape.setLayerType(LayerType.ACQUIRE);
        shape.setPaint(Color.BLACK);
        shape.setLabelVisible(Boolean.FALSE);

        return shape;
    }
}
