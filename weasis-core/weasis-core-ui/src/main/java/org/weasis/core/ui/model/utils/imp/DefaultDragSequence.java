/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.utils.imp;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.JDialog;

import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.ui.dialog.MeasureDialog;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.AbstractDragGraphic;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.MouseEventDouble;

public class DefaultDragSequence implements Draggable {
    protected final DragGraphic graphic;
    private final Point2D lastPoint;
    private Integer handlePointIndex;

    public DefaultDragSequence(DragGraphic graphic) {
        this(graphic, Graphic.UNDEFINED);
    }

    public DefaultDragSequence(DragGraphic graphic, Integer handlePointIndex) {
        this.graphic = Objects.requireNonNull(graphic);
        this.handlePointIndex = handlePointIndex;
        this.lastPoint = new Point2D.Double();
    }

    @Override
    public void startDrag(MouseEventDouble evt) {
        graphic.setResizeOrMoving(Boolean.TRUE);

        lastPoint.setLocation(evt.getImageX(), evt.getImageY());

        if (!graphic.isGraphicComplete()) {
            if (graphic.getPts().isEmpty()) {
                graphic.getPts().add(evt.getImageCoordinates());
            }

            if (!graphic.isGraphicComplete()) {
                graphic.getPts().add(evt.getImageCoordinates());
            }

            // force index to match actual dragging point
            handlePointIndex = graphic.getPts().size() - 1;
        }
    }

    @Override
    public void drag(MouseEventDouble evt) {
        double deltaX = evt.getImageX() - lastPoint.getX();
        double deltaY = evt.getImageY() - lastPoint.getY();

        if (MathUtil.isDifferentFromZero(deltaX) || MathUtil.isDifferentFromZero(deltaY)) {
            lastPoint.setLocation(evt.getImageCoordinates());
            handlePointIndex = graphic.moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, evt);
            graphic.buildShape(evt);
            graphic.setResizeOrMoving(Boolean.TRUE);
        }
    }

    @Override
    public Boolean completeDrag(MouseEventDouble mouseEvent) {
        if (mouseEvent != null) {
            if (!graphic.isGraphicComplete()) {
                if (Objects.equals(graphic.getPtsNumber(), Graphic.UNDEFINED) && mouseEvent.getClickCount() == 2
                    && !mouseEvent.isConsumed()) {
                    List<Point2D.Double> handlePointList = graphic.getPts();
                    if (!graphic.isLastPointValid()) {
                        handlePointList.remove(handlePointList.size() - 1);
                    }
                    graphic.setPointNumber(handlePointList.size());
                } else if (graphic.isLastPointValid()) {
                    graphic.getPts().add(mouseEvent.getImageCoordinates());
                    handlePointIndex = graphic.getPts().size() - 1; // forces index to match actual dragging point
                }
            } else if (graphic.getShape() != null && graphic.isShapeValid()) {

                // The shape is not repainted because it is identical to the previous one.
                // Force to repaint the handles of the shape by setting to null.
                // Repaint also measurement labels which is entirely computed on mouse click release

                graphic.setResizeOrMoving(Boolean.FALSE);
                graphic.setShape(null, mouseEvent);
                graphic.buildShape(mouseEvent);
                if (mouseEvent.getClickCount() == 2 && !mouseEvent.isConsumed()) {
                    ViewCanvas<?> graphPane = graphic.getDefaultView2d(mouseEvent);
                    if (graphPane != null) {
                        // Do not open properties dialog for graphic with undefined points (like polyline) => double
                        // click conflict
                        boolean isEditingGraph = false;
                        Optional<Graphic> first = graphPane.getGraphicManager().getFirstGraphicIntersecting(mouseEvent);
                        if (first.isPresent() && first.get() instanceof AbstractDragGraphic) {
                            AbstractDragGraphic dragGraph = (AbstractDragGraphic) first.get();
                            if (dragGraph.getSelected() && dragGraph.getVariablePointsNumber()) {
                                List<DragGraphic> selectedDragGraphList =
                                    graphPane.getGraphicManager().getSelectedDragableGraphics();

                                if (selectedDragGraphList.size() == 1 && !dragGraph.isOnGraphicLabel(mouseEvent)
                                    && dragGraph.getHandlePointIndex(mouseEvent) >= 0) {
                                    isEditingGraph = true;
                                }
                            }
                        }

                        if (!isEditingGraph && graphPane.getGraphicManager().getSelectedGraphics().size() == 1) {
                            ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(graphPane.getJComponent());
                            final ArrayList<DragGraphic> list = new ArrayList<>();
                            list.add(graphic);
                            JDialog dialog = new MeasureDialog(graphPane, list);
                            ColorLayerUI.showCenterScreen(dialog, layer);
                            mouseEvent.consume();
                        }
                    }
                }
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }
}