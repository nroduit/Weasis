/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.model.utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.media.jai.TiledImage;

import org.weasis.core.api.image.util.LayoutUtil;
import org.weasis.core.ui.model.graphic.Graphic;

public class GraphicUtil {

    private GraphicUtil() {
    }

    public static PlanarImage getGraphicAsImage(Shape shape) {
        Rectangle bound = shape.getBounds();
        TiledImage image = new TiledImage(0, 0, bound.width + 1, bound.height + 1, 0, 0,
            LayoutUtil.createBinarySampelModel(), LayoutUtil.createBinaryIndexColorModel());
        Graphics2D g2d = image.createGraphics();
        g2d.translate(-bound.x, -bound.y);
        g2d.setPaint(Color.white);
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.fill(shape);
        g2d.draw(shape);
        return image;
    }

    public static PlanarImage getGraphicsAsImage(Rectangle bound, List<Graphic> graphics2dlist) {
        TiledImage image = new TiledImage(0, 0, bound.width + 1, bound.height + 1, 0, 0,
            LayoutUtil.createBinarySampelModel(), LayoutUtil.createBinaryIndexColorModel());
        Graphics2D g2d = image.createGraphics();
        g2d.translate(-bound.x, -bound.y);
        g2d.setPaint(Color.white);
        g2d.setStroke(new BasicStroke(1.0f));
        for (Graphic graph : graphics2dlist) {
            g2d.fill(graph.getShape());
            g2d.draw(graph.getShape());
        }
        return image;
    }

}
