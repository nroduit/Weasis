package org.weasis.core.ui.model.graphic;

import java.awt.geom.Point2D;

import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.util.MouseEventDouble;

public interface DragGraphic extends Graphic {
    public static final Boolean DEFAULT_RESIZE_OR_MOVING = Boolean.FALSE;

    Boolean getResizingOrMoving();

    void setResizeOrMoving(Boolean value);

    void buildShape(MouseEventDouble mouseEvent);

    void forceToAddPoints(Integer fromPtIndex);

    Point2D removeHandlePoint(Integer index, MouseEventDouble mouseEvent);

    void moveMouseOverHandlePoint(Integer handlePtIndex, MouseEventDouble event);

    Integer moveAndResizeOnDrawing(Integer handlePointIndex, Double deltaX, Double deltaY, MouseEventDouble mouseEvent);

    Draggable createMoveDrag();

    Draggable createDragLabelSequence();

    Draggable createResizeDrag();

    Draggable createResizeDrag(Integer i);

    @Override
    DragGraphic copy();

}
