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

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.LayerModel;

/**
 * The Class DragLayer.
 * 
 * @author Nicolas Roduit
 */
public class DragLayer extends AbstractLayer {

    private static final long serialVersionUID = 8576601524359423997L;

    public DragLayer(LayerModel canvas1, int drawMode) {
        super(canvas1, drawMode);
    }

    @Override
    public void paint(Graphics2D g2, AffineTransform transform, AffineTransform inverseTransform, Rectangle2D bound) {
        if (graphics != null) {
            for (int i = 0; i < graphics.size(); i++) {
                Graphic graphic = graphics.get(i);
                // Rectangle repaintBounds = graphic.getRepaintBounds();
                Rectangle repaintBounds = graphic.getRepaintBounds(transform);
                // only repaints graphics that intersects or are contained in the clip bound
                if (bound == null || repaintBounds != null && bound.intersects(repaintBounds)) {
                    graphic.paint(g2, transform);
                } else {
                    if (graphic.getGraphicLabel() != null) {
                        Rectangle2D labelBound = graphic.getGraphicLabel().getLabelBound();
                        if (labelBound != null) {
                            // As the size of the graphicLabel does not change with the zoom, it requires an inverse
                            // transform for
                            // comparing in real coordinates
                            Point2D.Double p = new Point2D.Double(labelBound.getWidth(), labelBound.getHeight());
                            inverseTransform.transform(p, p);
                            Rectangle rect =
                                new Rectangle((int) labelBound.getX(), (int) labelBound.getY(), (int) Math.ceil(p.x),
                                    (int) Math.ceil(p.y));
                            if (bound.intersects(rect)) {
                                graphic.paintLabel(g2, transform);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * getGraphicsInArea
     * 
     * @param rect
     *            Rectangle
     * @return List
     */
    @Override
    public java.util.List getGraphicsSurfaceInArea(Rectangle rect, AffineTransform transform) {
        ArrayList arraylist = new ArrayList();
        if (graphics != null) {
            for (int j = graphics.size() - 1; j >= 0; j--) {
                Graphic graphic = graphics.get(j);
                // optimisation : d'abord check si le rectangle est dans le bounding box (beaucoup plus rapide que de
                // checker sur shape directement)
                // if (graphic.getBounds().intersects(rect)) {
                if (graphic.getBounds(transform).intersects(rect)) {
                    // if (graphic.intersects(rect)) {
                    if (graphic.intersects(rect, transform)) {
                        arraylist.add(graphic);
                    }
                }
            }
        }
        return arraylist;
    }

    @Override
    public java.util.List getGraphicsBoundsInArea(Rectangle rect) {
        ArrayList arraylist = new ArrayList();
        if (graphics != null) {
            for (int j = graphics.size() - 1; j >= 0; j--) {
                Graphic graphic = graphics.get(j);
                // if (graphic.getRepaintBounds().intersects(rect)) {
                if (graphic.getRepaintBounds(getAffineTransform()).intersects(rect)) {
                    arraylist.add(graphic);
                }
            }
        }
        return arraylist;
    }

    /**
     * getGraphicsSurfaceInArea
     * 
     * @param pos
     *            Point
     * @return List
     */
    @Override
    public Graphic getGraphicContainPoint(MouseEvent mouseevent) {
        AbstractDragGraphic selectedGraphic = null;
        if (graphics != null) {
            final Point pos = mouseevent.getPoint();
            for (int j = graphics.size() - 1; j >= 0; j--) {
                AbstractDragGraphic graphic = (AbstractDragGraphic) graphics.get(j);
                // optimisation : d'abord check si le rectangle est dans le bounding box (beaucoup plus rapide que de
                // checker sur shape directement)
                // if (graphic.getRepaintBounds().contains(pos)) {
                if (graphic.getRepaintBounds(getAffineTransform()).contains(pos)) {
                    // if (graphic.getArea().contains(pos) || graphic.getResizeCorner(mouseevent) != -1) {
                    if (graphic.getArea(mouseevent).contains(pos) || graphic.getHandlePointIndex(mouseevent) != -1) {
                        if (selectedGraphic == null || !graphic.isSelected()) {
                            selectedGraphic = graphic;
                        } else if (graphic.isSelected()) {
                            break;
                        }
                    }
                }
            }
        }
        return selectedGraphic;
    }
    // previous version
    // public Graphic getGraphicContainPoint(MouseEvent mouseevent) {
    // if (graphics != null) {
    // final Point pos = mouseevent.getPoint();
    // for (int j = graphics.size() - 1; j >= 0; j--) {
    // AbstractDragGraphic graphic = (AbstractDragGraphic) graphics.get(j);
    // // optimisation : d'abord check si le rectangle est dans le bounding box (beaucoup plus rapide que de
    // // checker
    // // sur shape directement)
    // if (graphic.getRepaintBounds().contains(pos)) {
    // if (graphic.getArea().contains(pos) || graphic.getResizeCorner(mouseevent) != -1) {
    // return graphic;
    // }
    // }
    // }
    // }
    // return null;
    // }

    // REMOVED by btja
    // seems never to be used instead use AbstractDragGraphic.getUIName or AbstractDragGraphic.getDescription
    /*
     * public static String getDrawinType(AbstractDragGraphic graphic) { if (graphic instanceof LineGraphic) { return
     * "Segment"; //$NON-NLS-1$ } else if (graphic instanceof CircleGraphic) { return "Ellipse"; //$NON-NLS-1$ } else if
     * (graphic instanceof RectangleGraphic) { return "Rectangle"; //$NON-NLS-1$ } else if (graphic instanceof
     * PolygonGraphic) { return "Polygon"; //$NON-NLS-1$ } else { return "FreeHand"; //$NON-NLS-1$ } }
     */
}
