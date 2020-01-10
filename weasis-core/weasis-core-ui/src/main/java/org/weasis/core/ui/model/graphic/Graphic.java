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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.weasis.core.api.gui.util.GUIEntry;
import org.weasis.core.api.gui.util.KeyActionValue;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.Copyable;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.UUIDable;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlJavaTypeAdapter(AbstractGraphic.Adapter.class)
public interface Graphic extends UUIDable, GUIEntry, KeyActionValue, Copyable<Graphic> {
    static final Color DEFAULT_COLOR = Color.YELLOW;
    static final Integer DEFAULT_POINT_NUMBER = 1;
    static final Float DEFAULT_LINE_THICKNESS = 1f;
    static final Boolean DEFAULT_LABEL_VISISIBLE = Boolean.TRUE;
    static final Boolean DEFAULT_FILLED = Boolean.FALSE;
    static final Boolean DEFAULT_SELECTED = Boolean.FALSE;
    static final Integer DEFAULT_PTS_SIZE = 10;

    static final String ACTION_TO_FRONT = "toFront"; //$NON-NLS-1$
    static final String ACTION_TO_BACK = "toBack"; //$NON-NLS-1$
    static final String ACTION_REMOVE = "remove"; //$NON-NLS-1$
    static final String ACTION_REMOVE_REPAINT = "remove.repaint"; //$NON-NLS-1$

    static final Integer HANDLE_SIZE = 6;
    static final Integer SELECTION_SIZE = 10;
    static final Integer UNDEFINED = -1;

    default List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
        return Collections.emptyList();
    }

    default List<Measurement> getMeasurementList() {
        return Collections.emptyList();
    }

    /**
     * @param affineTransform
     *            Current transform applied to the view. Should be used to compute invariantSizedShape bounding
     *            rectangle in union with drawing shape bounding rectangle.
     * @return Bounding rectangle of all the drawing shape. Handle points paintings not included.<br>
     *         Coordinates are given in RealCoordinates. <br>
     *         Null is return if shape is Null
     *
     * @since v1.1.0 - new in Graphic interface
     */
    Rectangle getBounds(AffineTransform transform);

    /**
     * @return Shape bounding rectangle relative to affineTransform<br>
     *         Handle points bounding rectangles are also included, knowing they have invariant size according to
     *         current view.<br>
     *         Any other invariant sized shape bounding rectangles are included if shape is instanceof AdvancedShape
     */
    Rectangle getTransformedBounds(Shape shape, AffineTransform transform);

    /**
     * Returns the line thickness value (default value: <b>1</b>).
     *
     * @return The line thickness value
     */
    Float getLineThickness();

    /**
     * Returns a build Stroke object regarding the line thickness value.
     *
     * @param lineThickness
     *            Line thickness
     * @return New Stroke object with defined thickness
     */
    Stroke getStroke(Float lineThickness);

    /**
     * Returns graphic's color (Default value: <b>Color.YELLOW</b>)
     *
     * @return Graphic's color
     */
    Paint getColorPaint();

    /**
     * Returns <b>TRUE</b> if the graphic is filled, <b>FALSE</b> otherwise (Default value: <b>FALSE</b>)
     *
     * @return <b>TRUE</b> or <b>FALSE</b>
     */
    Boolean getFilled();

    /**
     * Returns the Handle size (Default value: <b>6</b>)
     *
     * @return The Handle size
     */
    Integer getHandleSize();

    Graphic deepCopy();

    void buildShape();

    Area getArea(AffineTransform transform);

    void setLabel(String[] label, ViewCanvas<?> view2d);

    void setSelected(Boolean flag);

    Boolean getSelected();

    void paint(Graphics2D g2, AffineTransform transform);

    void paintLabel(Graphics2D g2, AffineTransform transform);

    void updateLabel(Object source, ViewCanvas<?> view2d);

    Rectangle getRepaintBounds(AffineTransform transform);

    GraphicLabel getGraphicLabel();

    Boolean getLabelVisible();

    ViewCanvas getDefaultView2d(MouseEvent mouseEvent);

    Boolean intersects(Rectangle rectangle, AffineTransform transform);

    void addPropertyChangeListener(PropertyChangeListener propertychangelistener);

    void removePropertyChangeListener(PropertyChangeListener propertychangelistener);

    void fireRemoveAction();

    void toFront();

    void toBack();

    boolean isShapeValid();

    /**
     * @return False if last dragging point equals the previous one
     */
    default Boolean isLastPointValid() {
        List<Point2D.Double> pts = getPts();
        int size = pts.size();
        Point2D lastPt = size > 0 ? pts.get(size - 1) : null;
        Point2D previousPt = size > 1 ? pts.get(size - 2) : null;

        return lastPt == null || !lastPt.equals(previousPt);
    }

    Shape getShape();

    Rectangle getTransformedBounds(GraphicLabel label, AffineTransform transform);

    void moveLabel(Double deltaX, Double deltaY);

    Boolean isGraphicComplete();

    List<Point2D.Double> getPts();

    Integer getPtsNumber();

    boolean isOnGraphicLabel(MouseEventDouble mouseEvent);

    int getHandlePointIndex(MouseEventDouble mouseEvent);

    Rectangle getRepaintBounds(MouseEvent mouseEvent);

    Area getArea(MouseEvent mouseEvent);

    void fireRemoveAndRepaintAction();

    void setLineThickness(Float lineThickness);

    void setPaint(Color newPaintColor);

    void setPointNumber(Integer pointNumber);

    /**
     * Set the list of points. Do not use this method when building a graphic programmatically, use
     * buildGraphic(List<Point2D.Double> pts) instead.
     *
     * @param pts
     */
    void setPts(List<Point2D.Double> pts);

    void setLabelVisible(Boolean labelVisible);

    void setFilled(Boolean filled);

    void setShape(Shape newShape, MouseEvent mouseEvent);

    Boolean getVariablePointsNumber();

    void setVariablePointsNumber(Boolean variablePointsNumber);

    LayerType getLayerType();

    void removeAllPropertyChangeListener();

    void setLayer(GraphicLayer layer);

    GraphicLayer getLayer();

    /**
     * This is the method for building a new graphic with a list of points. This method is an adapter as the constructor
     * must have no parameter for serialization.
     *
     * @param pts
     * @return
     * @throws InvalidShapeException
     */
    Graphic buildGraphic(List<Point2D.Double> pts) throws InvalidShapeException;

    void setLayerType(LayerType layerType);

    void setClassID(Integer classID);

    Integer getClassID();

    void setLabel(GraphicLabel label);
}
