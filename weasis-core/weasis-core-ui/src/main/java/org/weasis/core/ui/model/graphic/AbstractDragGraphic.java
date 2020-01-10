/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.graphic;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Robot;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.SwingUtilities;
import javax.xml.bind.annotation.XmlTransient;

import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.model.utils.imp.DefaultDragSequence;
import org.weasis.core.ui.model.utils.imp.DragLabelSequence;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlTransient
public abstract class AbstractDragGraphic extends AbstractGraphic implements DragGraphic {
    private static final long serialVersionUID = 4694941331227706591L;

    private Boolean resizingOrMoving = DEFAULT_RESIZE_OR_MOVING;

    public AbstractDragGraphic(Integer pointNumber) {
        super(pointNumber);
    }

    public AbstractDragGraphic(AbstractGraphic graphic) {
        super(graphic);
    }

    @Override
    public void setResizeOrMoving(Boolean value) {
        this.resizingOrMoving = Optional.ofNullable(value).orElse(DEFAULT_RESIZE_OR_MOVING);
    }

    @Override
    public void buildShape() {
        if (isShapeValid()) {
            buildShape(null);
        }
    }

    @Override
    protected void paintHandles(Graphics2D g2d, AffineTransform transform) {
        if (!getResizingOrMoving()) {
            super.paintHandles(g2d, transform);
        }
    }

    @Override
    public Boolean getResizingOrMoving() {
        return Optional.ofNullable(resizingOrMoving).orElse(Boolean.FALSE);
    }

    @Override
    public void forceToAddPoints(Integer fromPtIndex) {
        if (variablePointsNumber && fromPtIndex >= 0 && fromPtIndex < pts.size()) {
            if (fromPtIndex < pts.size() - 1) {
                List<Point2D.Double> list = pts.subList(fromPtIndex + 1, pts.size());
                for (int i = 0; i <= fromPtIndex; i++) {
                    list.add(pts.get(i));
                }
                pts = list;
            }
            pointNumber = UNDEFINED;
        }
    }

    @Override
    public Point2D removeHandlePoint(Integer index, MouseEventDouble mouseEvent) {
        // To keep a valid shape, do not remove when there are 2 points left.
        if (variablePointsNumber && pts.size() > 2 && index >= 0 && index < pts.size()) {
            Point2D pt = pts.remove(index.intValue());
            pointNumber = pts.size();
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
    @Override
    public void moveMouseOverHandlePoint(Integer handlePtIndex, MouseEventDouble event) {
        ViewCanvas<?> graphPane = getDefaultView2d(event);

        if (graphPane != null) {
            Point2D handlePt = null;

            if (handlePtIndex >= 0 && handlePtIndex < pts.size()) {
                handlePt = pts.get(handlePtIndex);
            }

            if (handlePt != null) {
                Point mousePt = graphPane.getMouseCoordinatesFromImage(handlePt.getX(), handlePt.getY());

                if (mousePt != null && (event.getX() != mousePt.x || event.getY() != mousePt.y)) {
                    try {
                        event.translatePoint(mousePt.x - event.getX(), mousePt.y - event.getY());
                        event.setImageCoordinates(handlePt);
                        SwingUtilities.convertPointToScreen(mousePt, graphPane.getJComponent());
                        new Robot().mouseMove(mousePt.x, mousePt.y);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
            }
        }
    }

    @Override
    public Draggable createMoveDrag() {
        return new DefaultDragSequence(this);
    }

    @Override
    public Draggable createResizeDrag() {
        return createResizeDrag(UNDEFINED);
    }

    @Override
    public Draggable createResizeDrag(Integer i) {
        return new DefaultDragSequence(this, i);
    }

    @Override
    public Draggable createDragLabelSequence() {
        return new DragLabelSequence(this);
    }

    @Override
    public Integer moveAndResizeOnDrawing(Integer handlePointIndex, Double deltaX, Double deltaY,
        MouseEventDouble mouseEvent) {
        if (Objects.equals(handlePointIndex, UNDEFINED)) {
            pts.stream().filter(Objects::nonNull).forEach(p -> p.setLocation(p.getX() + deltaX, p.getY() + deltaY));
        } else if (handlePointIndex >= 0 && handlePointIndex < pts.size()) {
            Point2D point = pts.get(handlePointIndex);
            Optional.ofNullable(point).ifPresent(p -> p.setLocation(mouseEvent.getImageCoordinates()));
        }
        return handlePointIndex;
    }
}
