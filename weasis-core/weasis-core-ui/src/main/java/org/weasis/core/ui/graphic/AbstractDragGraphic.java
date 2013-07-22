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
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Robot;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class AbstractDragGraphic.
 * 
 * @author Nicolas Roduit,Benoit Jacquemoud
 */

public abstract class AbstractDragGraphic extends BasicGraphic {

    private boolean resizingOrMoving = false;

    public AbstractDragGraphic(int handlePointTotalNumber) {
        this(handlePointTotalNumber, Color.YELLOW, 1f, true);
    }

    public AbstractDragGraphic(int handlePointTotalNumber, Paint paintColor, float lineThickness, boolean labelVisible) {
        this(handlePointTotalNumber, paintColor, lineThickness, labelVisible, false);
    }

    public AbstractDragGraphic(int handlePointTotalNumber, Paint paintColor, float lineThickness, boolean labelVisible,
        boolean filled) {
        this(null, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled);
    }

    public AbstractDragGraphic(List<Point2D.Double> handlePointList, int handlePointTotalNumber, Paint paintColor,
        float lineThickness, boolean labelVisible, boolean filled) {
        super(handlePointList, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled);
    }

    public AbstractDragGraphic(List<Point2D.Double> handlePointList, int handlePointTotalNumber, Paint paintColor,
        float lineThickness, boolean labelVisible, boolean filled, int classID) {
        super(handlePointList, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled, classID);
    }

    protected abstract void buildShape(MouseEventDouble mouseEvent);

    @Override
    protected void buildShape() {
        if (isShapeValid()) {
            buildShape(null);
        }
    }

    @Override
    protected void paintHandles(Graphics2D g2d, AffineTransform transform) {
        if (!isResizingOrMoving()) {
            super.paintHandles(g2d, transform);
        }
    }

    public void forceToAddPoints(int fromPtIndex) {
        if (variablePointsNumber && fromPtIndex >= 0 && fromPtIndex < handlePointList.size()) {
            if (fromPtIndex < handlePointList.size() - 1) {
                List<Point2D.Double> list = handlePointList.subList(fromPtIndex + 1, handlePointList.size());
                for (int i = 0; i <= fromPtIndex; i++) {
                    list.add(handlePointList.get(i));
                }
                handlePointList = list;
            }
            handlePointTotalNumber = UNDEFINED;
        }
    }

    public Point2D removeHandlePoint(int index, MouseEventDouble mouseEvent) {
        // To keep a valid shape, do not remove when there are 2 points left.
        if (variablePointsNumber && handlePointList.size() > 2 && index >= 0 && index < handlePointList.size()) {
            Point2D pt = handlePointList.remove(index);
            handlePointTotalNumber = handlePointList.size();
            buildShape(mouseEvent);
            return pt;
        }
        return null;
    }

    /**
     * Adjust the mouse cursor at the center of the handle point
     * 
     * @param handlePtIndex
     * @param event
     */
    public void moveMouseOverHandlePoint(int handlePtIndex, MouseEventDouble event) {
        DefaultView2d<?> graphPane = getDefaultView2d(event);

        if (graphPane != null) {
            Point2D handlePt = null;

            if (handlePtIndex >= 0 && handlePtIndex < handlePointList.size()) {
                handlePt = handlePointList.get(handlePtIndex);
            }

            if (handlePt != null) {
                Point mousePt = graphPane.getMouseCoordinatesFromImage(handlePt.getX(), handlePt.getY());

                if (event.getX() != mousePt.x || event.getY() != mousePt.y) {
                    try {
                        event.translatePoint(mousePt.x - event.getX(), mousePt.y - event.getY());
                        event.setImageCoordinates(handlePt);
                        SwingUtilities.convertPointToScreen(mousePt, graphPane);
                        new Robot().mouseMove(mousePt.x, mousePt.y);
                    } catch (Exception doNothing) {
                    }
                }
            }
        }

    }

    protected boolean isResizingOrMoving() {
        return resizingOrMoving;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected class DragLabelSequence implements DragSequence {

        protected final Point2D lastPoint;

        protected DragLabelSequence() {
            this.lastPoint = new Point2D.Double();
        }

        @Override
        public void startDrag(MouseEventDouble mouseEvent) {
            lastPoint.setLocation(mouseEvent.getImageX(), mouseEvent.getImageY());
        }

        @Override
        public void drag(MouseEventDouble mouseEvent) {
            double deltaX = mouseEvent.getImageX() - lastPoint.getX();
            double deltaY = mouseEvent.getImageY() - lastPoint.getY();

            if (deltaX != 0.0 || deltaY != 0.0) {
                lastPoint.setLocation(mouseEvent.getImageX(), mouseEvent.getImageY());
                moveLabel(deltaX, deltaY);
            }
        }

        @Override
        public boolean completeDrag(MouseEventDouble mouseEvent) {
            return true;
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected class DefaultDragSequence implements DragSequence {

        protected final Point2D lastPoint;
        protected int handlePointIndex;

        protected DefaultDragSequence() {
            this(-1); // -1 stands for moving current graphic
        }

        protected DefaultDragSequence(int handlePointIndex) { // /////////////////////////////////////////////////////////////////////////////////////////////////////
            this.handlePointIndex = handlePointIndex;
            this.lastPoint = new Point2D.Double();
        }

        @Override
        public void startDrag(MouseEventDouble mouseEvent) {
            resizingOrMoving = true;

            lastPoint.setLocation(mouseEvent.getImageX(), mouseEvent.getImageY());

            if (!isGraphicComplete()) {
                if (handlePointList.isEmpty()) {
                    handlePointList.add(mouseEvent.getImageCoordinates());
                }

                if (!isGraphicComplete()) {
                    handlePointList.add(mouseEvent.getImageCoordinates());
                }

                handlePointIndex = handlePointList.size() - 1; // force index to match actual dragging point
            }
        }

        @Override
        public void drag(MouseEventDouble mouseEvent) {
            double deltaX = mouseEvent.getImageX() - lastPoint.getX();
            double deltaY = mouseEvent.getImageY() - lastPoint.getY();

            if (deltaX != 0.0 || deltaY != 0.0) {

                lastPoint.setLocation(mouseEvent.getImageCoordinates());
                handlePointIndex = moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, mouseEvent);

                buildShape(mouseEvent);

                resizingOrMoving = true;
            }
        }

        @Override
        public boolean completeDrag(MouseEventDouble mouseEvent) {
            if (mouseEvent != null) {

                if (!isGraphicComplete()) {
                    if (handlePointTotalNumber == UNDEFINED && mouseEvent.getClickCount() == 2) {
                        if (!isLastPointValid()) {
                            handlePointList.remove(handlePointList.size() - 1);
                        }

                        handlePointTotalNumber = handlePointList.size();

                    } else if (isLastPointValid()) {
                        handlePointList.add(mouseEvent.getImageCoordinates());
                        handlePointIndex = handlePointList.size() - 1; // forces index to match actual dragging point
                    }
                } else if (shape != null && isShapeValid()) {

                    // The shape is not repainted because it is identical to the previous one.
                    // Force to repaint the handles of the shape by setting to null.
                    // Repaint also measurement labels which is entirely computed on mouse click release

                    resizingOrMoving = false;
                    shape = null;
                    buildShape(mouseEvent);
                    if (mouseEvent.getClickCount() == 2) {
                        DefaultView2d<?> graphPane = getDefaultView2d(mouseEvent);
                        if (graphPane != null) {
                            // Do not open properties dialog for graphic with undefined points (like polyline) => double
                            // click conflict
                            boolean isEditingGraph = false;
                            Graphic firstGraphicIntersecting =
                                graphPane.getLayerModel().getFirstGraphicIntersecting(mouseEvent);
                            if (firstGraphicIntersecting == AbstractDragGraphic.this && isSelected()
                                && isVariablePointsNumber()) {
                                AbstractDragGraphic dragGraph = (AbstractDragGraphic) firstGraphicIntersecting;
                                List<AbstractDragGraphic> selectedDragGraphList =
                                    graphPane.getLayerModel().getSelectedDragableGraphics();

                                if (selectedDragGraphList != null && selectedDragGraphList.size() == 1
                                    && !dragGraph.isOnGraphicLabel(mouseEvent)
                                    && dragGraph.getHandlePointIndex(mouseEvent) >= 0) {
                                    isEditingGraph = true;
                                }
                            }
                            if (!isEditingGraph) {
                                final ArrayList<AbstractDragGraphic> list = new ArrayList<AbstractDragGraphic>();
                                list.add(AbstractDragGraphic.this);
                                JDialog dialog = new MeasureDialog(graphPane, list);
                                WinUtil.adjustLocationToFitScreen(dialog, mouseEvent.getLocationOnScreen());
                                dialog.setVisible(true);
                            }
                        }
                    }
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * @return False if last dragging point equals the previous one
     */
    protected final boolean isLastPointValid() {

        Point2D lastPt = handlePointList.size() > 0 ? handlePointList.get(handlePointList.size() - 1) : null;
        Point2D previousPt = handlePointList.size() > 1 ? handlePointList.get(handlePointList.size() - 2) : null;

        return lastPt == null || !lastPt.equals(previousPt);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public DragSequence createMoveDrag() {
        return new DefaultDragSequence();
    }

    public DragSequence createResizeDrag() {
        return createResizeDrag(0);
    }

    public DragSequence createResizeDrag(int i) {
        return new DefaultDragSequence(i);
    }

    public DragSequence createDragLabelSequence() {
        return new DragLabelSequence();
    }

    protected int moveAndResizeOnDrawing(int handlePointIndex, double deltaX, double deltaY, MouseEventDouble mouseEvent) {
        if (handlePointIndex == -1) {
            for (Point2D point : handlePointList) {
                if (point != null) {
                    point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
                }
            }
        } else if (handlePointIndex >= 0 && handlePointIndex < handlePointList.size()) {
            Point2D point = handlePointList.get(handlePointIndex);
            if (point != null) {
                point.setLocation(mouseEvent.getImageCoordinates());
                // point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
            }
        }
        return handlePointIndex;
    }

}
