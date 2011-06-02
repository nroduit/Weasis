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
import java.awt.geom.Area;
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

    /**
     * Only repaints graphics that intersects or are contained in the clip bound
     */
    @Override
    public void paint(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform, Rectangle2D bounds) {
        if (graphics == null)
            return;

        for (Graphic graphic : graphics) {
            if (bounds != null) {
                Rectangle repaintBounds = graphic.getRepaintBounds(transform);

                if (repaintBounds != null && repaintBounds.intersects(bounds))
                    graphic.paint(g2d, transform);
                else {
                    GraphicLabel graphicLabel = graphic.getGraphicLabel();
                    Rectangle2D labelBounds = (graphicLabel != null) ? graphicLabel.getBounds(transform) : null;

                    if (labelBounds != null && labelBounds.intersects(bounds))
                        graphic.paintLabel(g2d, transform);
                    // TODO would be simpler to integrate intersect check inside graphic instance
                }

            } else {
                // convention is when bounds equals null graphic is repaint
                graphic.paint(g2d, transform);
                graphic.paintLabel(g2d, transform);
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
        ArrayList<Graphic> graphicList = new ArrayList<Graphic>();

        if (graphics != null && rect != null) {

            for (Graphic graphic : graphics) {
                Rectangle selectionBounds = graphic.getBounds(transform);

                if (selectionBounds != null && selectionBounds.intersects(rect)) {
                    Area selectionArea = graphic.getArea(transform);
                    if (selectionArea != null && selectionArea.intersects(rect)) {
                        graphicList.add(graphic);
                        break;
                    }
                }

                GraphicLabel graphicLabel = graphic.getGraphicLabel();
                if (graphicLabel != null) {
                    Area selectionArea = graphicLabel.getArea(transform);
                    if (selectionArea != null && selectionArea.intersects(rect))
                        graphicList.add(graphic);
                    // Rectangle2D labelBounds = (graphicLabel != null) ? graphicLabel.getBounds(transform) : null;
                    // if (labelBounds != null && labelBounds.intersects(rect))
                    // graphicList.add(graphic);
                }
            }
        }
        return graphicList;
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
