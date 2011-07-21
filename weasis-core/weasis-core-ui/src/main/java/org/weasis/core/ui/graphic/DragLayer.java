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
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.LayerModel;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class DragLayer.
 * 
 * @author Nicolas Roduit, Benoit Jacquemoud
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

                if (repaintBounds != null && repaintBounds.intersects(bounds)) {
                    graphic.paint(g2d, transform);
                } else {
                    GraphicLabel graphicLabel = graphic.getGraphicLabel();
                    Rectangle2D labelBounds = (graphicLabel != null) ? graphicLabel.getBounds(transform) : null;

                    if (labelBounds != null && labelBounds.intersects(bounds)) {
                        graphic.paintLabel(g2d, transform);
                        // TODO would be simpler to integrate intersect check inside graphic instance
                    }
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
     * @param selectGraphic
     *            Rectangle
     * @return List
     */

    @Override
    public List<Graphic> getGraphicsSurfaceInArea(Rectangle selectGraphic, AffineTransform transform) {
        ArrayList<Graphic> selectedGraphicList = new ArrayList<Graphic>();

        if (graphics != null && graphics.size() > 0 && selectGraphic != null) {

            for (Graphic graphic : graphics) {
                Rectangle graphBounds = graphic.getBounds(transform);

                if (graphBounds != null && graphBounds.intersects(selectGraphic)) {
                    Area selectionArea = graphic.getArea(transform);

                    if (selectionArea != null && selectionArea.intersects(selectGraphic)) {
                        selectedGraphicList.add(graphic);
                        continue;
                    }
                }

                GraphicLabel graphicLabel = graphic.getGraphicLabel();
                if (graphicLabel != null && graphic.isLabelVisible()) {
                    Area selectionArea = graphicLabel.getArea(transform);

                    if (selectionArea != null && selectionArea.intersects(selectGraphic)) {
                        selectedGraphicList.add(graphic);
                    }
                }
            }
        }
        return selectedGraphicList;
    }

    @Override
    public List<Graphic> getGraphicsSurfaceInArea(Rectangle rect, AffineTransform transform, boolean firstGraphicOnly) {
        ArrayList<Graphic> selectedGraphicList = new ArrayList<Graphic>();

        if (graphics != null && graphics.size() > 0 && rect != null) {
            List<Area> selectedAreaList = new ArrayList<Area>();

            ListIterator<Graphic> graphicsIt = graphics.listIterator(graphics.size());

            while (graphicsIt.hasPrevious()) {
                Graphic selectedGraphic = graphicsIt.previous(); // starts from top level Front graphic

                if (selectedGraphic != null) {
                    Area selectedArea = null;

                    Rectangle selectionBounds = selectedGraphic.getRepaintBounds(getAffineTransform());
                    if (selectionBounds != null && selectionBounds.intersects(rect)) {
                        selectedArea = selectedGraphic.getArea(transform);
                    }

                    GraphicLabel graphicLabel = selectedGraphic.getGraphicLabel();
                    if (graphicLabel != null) {
                        Area labelArea = graphicLabel.getArea(transform);
                        if (labelArea != null) {
                            if (selectedArea != null) {
                                selectedArea.add(labelArea);
                            } else if (labelArea.intersects(rect)) {
                                selectedArea = selectedGraphic.getArea(transform);
                                selectedArea.add(labelArea);
                            }
                        }
                    }

                    if (selectedArea != null) {
                        if (firstGraphicOnly) {
                            for (Area area : selectedAreaList) {
                                selectedArea.subtract(area);// subtract any areas from front graphics already selected
                            }
                        }
                        if (selectedArea.intersects(rect)) {
                            selectedAreaList.add(selectedArea);
                            selectedGraphicList.add(selectedGraphic);
                        }
                    }
                }
            }
        }
        return selectedGraphicList;
    }

    @Override
    public List<Graphic> getGraphicsBoundsInArea(Rectangle rect) {
        ArrayList<Graphic> arraylist = new ArrayList<Graphic>();
        if (graphics != null && rect != null) {
            for (int j = graphics.size() - 1; j >= 0; j--) {
                Graphic graphic = graphics.get(j);
                Rectangle2D graphicBounds = graphic.getRepaintBounds(getAffineTransform());
                if (graphicBounds != null && graphicBounds.intersects(rect)) {
                    arraylist.add(graphic);
                }
            }
        }
        return arraylist;
    }

    /**
     */
    @Override
    public AbstractDragGraphic getGraphicContainPoint(MouseEventDouble mouseEvt) {
        final Point2D mousePt = mouseEvt.getImageCoordinates();

        if (graphics != null && mousePt != null) {

            for (int j = graphics.size() - 1; j >= 0; j--) {
                if (graphics.get(j) instanceof AbstractDragGraphic) {

                    AbstractDragGraphic dragGraph = (AbstractDragGraphic) graphics.get(j);

                    if (dragGraph.isOnGraphicLabel(mouseEvt))
                        return dragGraph;

                    // Improve speed by checking if mousePoint is inside repaintBound before checking if inside Area
                    Rectangle2D repaintBound = dragGraph.getRepaintBounds(mouseEvt);
                    if (repaintBound != null && repaintBound.contains(mousePt)) {
                        if (dragGraph.getArea(mouseEvt).contains(mousePt))
                            return dragGraph;
                    }
                }
            }
        }
        return null;
    }

}
