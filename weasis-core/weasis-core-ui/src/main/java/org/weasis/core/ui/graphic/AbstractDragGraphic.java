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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.DefaultView2d;

/**
 * The Class AbstractDragGraphic.
 * 
 * @author Nicolas Roduit,Benoit Jacquemoud
 */

public abstract class AbstractDragGraphic implements Graphic, Cloneable {

    protected PropertyChangeSupport pcs;

    protected Shape shape;

    protected int handlePointTotalNumber;
    protected List<Point2D> handlePointList;

    protected int handleSize = 6; // ????

    protected Paint colorPaint;
    protected float lineThickness;
    protected Stroke stroke;
    protected boolean filled;
    protected boolean labelVisible;

    protected GraphicLabel graphicLabel;

    protected boolean selected = false;
    protected boolean graphicComplete = false;

    // TODO none are transient and should be if serialized

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public AbstractDragGraphic(int handlePointTotalNumber) {
        this(handlePointTotalNumber, Color.YELLOW);
    }

    public AbstractDragGraphic(int handlePointTotalNumber, Color paintColor) {
        this(handlePointTotalNumber, paintColor, 1f);
    }

    public AbstractDragGraphic(int handlePointTotalNumber, Color paintColor, float lineThickness) {
        this(handlePointTotalNumber, paintColor, lineThickness, true);
    }

    public AbstractDragGraphic(int handlePointTotalNumber, Color paintColor, float lineThickness, boolean labelVisible) {
        this(handlePointTotalNumber, paintColor, lineThickness, labelVisible, false);
    }

    public AbstractDragGraphic(int handlePointTotalNumber, Color paintColor, float lineThickness, boolean labelVisible,
        boolean filled) {

        // TODO - assert parameters are valid

        this.handlePointTotalNumber = handlePointTotalNumber;
        this.handlePointList = new ArrayList<Point2D>(handlePointTotalNumber);

        this.colorPaint = paintColor;
        this.lineThickness = lineThickness;
        this.labelVisible = labelVisible;
        this.filled = filled;
        updateStroke(); // need lineThickness to be set
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Shape getShape() {
        return shape;
    }

    public Stroke getStroke() {
        return stroke;
    }

    public Paint getColorPaint() {
        return colorPaint;
    }

    public float getLineThickness() {
        return lineThickness;
    }

    public int getHandleSize() {
        return handleSize;
    }

    public boolean isFilled() {
        return filled;
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    public boolean isLabelVisible() {
        return labelVisible;
    }

    @Override
    public GraphicLabel getGraphicLabel() {
        return graphicLabel;
    }

    @Override
    public String[] getLabel() {
        return graphicLabel != null ? graphicLabel.getLabels() : null;
    }

    public boolean isGraphicComplete() {
        return graphicComplete = (handlePointList.size() == handlePointTotalNumber);
        // return graphicComplete;
    }

    @Override
    public String getDescription() {
        return "";
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @deprecated use getArea(AffineTransform transform) instead
     */
    @Deprecated
    @Override
    public Area getArea() {
        return new Area(getShape());

        // NOTE : called trough DefaultView2d.MouseHandler.mouseReleased ->
        // AbstractLayerModel.getSelectedAllGraphicsIntersecting -> AbstractLayerModel.getGraphicsSurfaceInArea by
        // AbstractGraphic.intersects
        // NOTE : called by AbstractGraphic.getArea(MouseEvent mouseEvent)
        // NOTE : called trough DefaultView2d.MouseHandler.mouseMoved/mousPressed/mouseReleased by
        // AbstractLayerModel.changeCursorDesign
        // NOTE : called trough DefaultView2d.MouseHandler.mousPressed -> AbstractLayerModel.getFirstGraphicIntersecting
        // by Draglayer.getGraphicContainPoint
    }

    @Override
    public Area getArea(AffineTransform transform) {
        if (shape == null)
            return new Area();
        if (shape instanceof AdvancedShape)
            return ((AdvancedShape) shape).getArea(transform);
        else {
            double growingSize = getHandleSize() * 2 / GeomUtil.extractScalingFactor(transform);
            Stroke boundingStroke = new BasicStroke((float) growingSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

            return new Area(boundingStroke.createStrokedShape(shape));
        }
    }

    public Area getArea(MouseEvent mouseEvent) {
        AffineTransform transform = getAffineTransform(mouseEvent);
        return getArea(transform);
    }

    /**
     * @deprecated Use getBounds(AffineTransform affineTransform) instead
     * 
     * @return Bounding rectangle of the drawing shape.<br>
     *         Handle points paintings not included.<br>
     *         Coordinates are given in RealCoordinates
     * 
     */
    @Deprecated
    @Override
    public Rectangle getBounds() {
        if (shape == null)
            return null;

        // NOTE : bound is not accurate with complex shape (true for rectangle or ellipse with rotation)
        Rectangle bound = shape.getBounds();
        bound.grow(bound.width < 1 ? 1 : 0, bound.height < 1 ? 1 : 0);
        return bound;

        // NOTE : called trough AbstractLayerModel.getSelectedAllGraphicsIntersecting by
        // DragLayer.getGraphicsSurfaceInArea
        // NOTE : called trough MouseListener.mouseRelease by DefaultView2d.MouseHandler.mouseReleased

        // NOTE : called in Measure2DAnalyse trough DefaultView2d.MouseHandler ->
        // AbstractLayermodel.fireGraphicSelectionChanged -> MesureTool.handle -> MesureTool.setSeletedGraphic ->
        // AbstractGraphic.getShape by Measure2DAnalyse.....()

    }

    /**
     * @param affineTransform
     *            Current transform applied to the view. Should be used to compute invariantSizedShape bounding
     *            rectangle in union with drawing shape bounding rectangle.
     * @return Bounding rectangle of all the drawing shape. Handle points paintings not included.<br>
     *         Coordinates are given in RealCoordinates. <br>
     *         null is return if shape is null
     * @since Weasis v1.1.0 - new in Graphic interface
     */

    @Override
    public Rectangle getBounds(AffineTransform transform) {
        if (shape == null)
            return null;

        if (shape instanceof AdvancedShape)
            ((AdvancedShape) shape).updateScalingFactor(transform);

        Rectangle2D bounds = shape.getBounds2D();
        growSelection(bounds, transform);

        return bounds.getBounds();
    }

    /**
     * @deprecated Use getRepaintBounds(AffineTransform affineTransform) instead
     */
    @Deprecated
    @Override
    public Rectangle getRepaintBounds() {
        return getRepaintBounds(shape);

        // NOTE : called trough AbstractLayerModel.createGraphic by AbstractLayer.addGraphic
        // NOTE : called trough AbstractLayerModel.getFirstGraphicIntersecting by AbstractLayer.getGraphicContainPoint
        // NOTE : called trough AbstractLayerModel.draw by DragLayer.paint
        // NOTE : called trough AbstractLayerModel.draw by TempLayer.paint
        // NOTE : called by DragLayer.getGraphicsBoundsInArea !!! never called - useless ???
    }

    /**
     * @deprecated Use getRepaintBounds(Shape shape,AffineTransform affineTransform) instead
     */
    @Deprecated
    public Rectangle getRepaintBounds(Shape shape) {
        if (shape == null)
            return null;

        Rectangle rectangle = shape.getBounds();
        growHandles(rectangle);
        return rectangle;

        // NOTE : called trough AbstractLayer.propertyChange("remove.repaint") -> AbstractLayer.graphicBoundsChanged by
        // AbstractGraphic.getTransformedBounds
    }

    /**
     * @param affineTransform
     * @return
     * @since Weasis v1.1.0 - new in Graphic interface
     */
    @Override
    public Rectangle getRepaintBounds(AffineTransform transform) {
        return getRepaintBounds(shape, transform);
    }

    /**
     * @param shape
     * @param affineTransform
     * @return
     */
    public Rectangle getRepaintBounds(Shape shape, AffineTransform transform) {
        if (shape == null)
            return null;

        if (shape instanceof AdvancedShape)
            ((AdvancedShape) shape).updateScalingFactor(transform);

        Rectangle2D bounds = shape.getBounds2D();
        growHandles(bounds, transform);

        return bounds != null ? bounds.getBounds() : null;
    }

    /**
     * @return Shape bounding rectangle relative to affineTransform<br>
     *         Handle points bounding rectangles are also included, knowing they have invariant size according to
     *         current view.<br>
     *         Any other invariant sized shape bounding rectangles are included if shape is instanceof DragShape
     */

    @Override
    public Rectangle getTransformedBounds(Shape shape, AffineTransform transform) {

        Rectangle rectangle = getRepaintBounds(shape, transform);
        if (transform != null && rectangle != null)
            rectangle = transform.createTransformedShape(rectangle).getBounds();

        return rectangle;
    }

    // @Override
    // public Rectangle getTransformedBounds(Shape shape, AffineTransform affineTransform) {
    // if (affineTransform == null)
    // return getRepaintBounds(shape);
    //
    // Rectangle rectangle = affineTransform.createTransformedShape(shape).getBounds();
    // growHandles(rectangle);
    //
    // return rectangle;
    //
    // // NOTE : called trough AbstractLayer.propertyChange("bounds") by AbstractLayer.graphicBoundsChanged
    // // NOTE : called trough AbstractLayer.propertyChange("remove.repaint") by AbstractLayer.removeGraphicAndRepaint
    // }

    @Deprecated
    public void growHandles(Rectangle rectangle) {
        // Add 2 pixels tolerance to ensure that the graphic is correctly repainted
        // int growingValue = (int) Math.floor(Math.max(handleSize, (int) Math.ceil(lineThickness)) / 2.0) + 2;
        int growingValue = (Math.max((int) Math.ceil(handleSize * 1.5), (int) Math.ceil(lineThickness)) + 1) >> 1;
        rectangle.grow(growingValue, growingValue);
    }

    /**
     * @param rectangle
     *            Bounding rectangle which size has to be modified according to the given transform with handle drawings
     *            and lineThikness taken in consideration<br>
     *            This assumes that handle drawing size do not change with different scaling of views. Hence, real
     *            coordinates of bounding rectangle are modified consequently<br>
     * @param transform
     */
    public void growHandles(Rectangle2D rectangle, AffineTransform transform) {
        // int growingValue = (Math.max((int) Math.ceil(handleSize * 1.5), (int) Math.ceil(lineThickness)) + 1) >> 1;
        double growingSize = Math.max(handleSize * 1.5, lineThickness) / 2;// >> 1;
        if (transform != null) {
            // growingValue = (int) Math.ceil(growingValue / transform.getScaleX()); // doesn't work with rotation
            double scalingFactor = GeomUtil.extractScalingFactor(transform);
            // growingValue = (int) Math.ceil(growingValue / scalingFactor);
            growingSize = growingSize / scalingFactor;

        }
        GeomUtil.growRectangle(rectangle, growingSize);
        // rectangle.grow(growingSize, growingSize);
    }

    public void growSelection(Rectangle2D rectangle, AffineTransform transform) {
        double growingSize = handleSize;
        if (transform != null)
            growingSize = growingSize / GeomUtil.extractScalingFactor(transform);
        GeomUtil.growRectangle(rectangle, growingSize);
    }

    /**
     * @return : selected handle point index if exist, otherwise -1
     */
    @Deprecated
    public int getResizeCorner(MouseEvent mouseEvent) {
        return getHandlePointIndex(mouseEvent);
    }

    public int getHandlePointIndex(MouseEvent mouseevent) {
        final Point mousePoint = mouseevent.getPoint();
        // System.out.println("mousePoint : " + mousePoint);

        double maxHandleDistance = (handleSize * 1.5) / 2;

        AffineTransform transform = getAffineTransform(mouseevent);
        if (transform != null) {
            double scalingFactor = GeomUtil.extractScalingFactor(transform);
            maxHandleDistance = maxHandleDistance / scalingFactor;
        }

        for (int index = 0; index < handlePointList.size(); index++) {
            Point2D point = handlePointList.get(index);
            // System.out.println("point : " + mousePoint.distance(point));
            // System.out.println("distance : " + mousePoint.distance(point) + " - maxHandleDistance : "
            // + maxHandleDistance);
            if (mousePoint.distance(point) <= maxHandleDistance)
                return index;
        }

        return -1;
    }

    public boolean isOnGraphicLabel(MouseEvent mouseevent) {
        final Point mousePoint = mouseevent.getPoint();

        AffineTransform transform = getAffineTransform(mouseevent);
        if (transform != null && graphicLabel != null) {
            Area selectionArea = graphicLabel.getArea(transform);
            if (selectionArea != null && selectionArea.contains(mousePoint))
                return true;
        }
        return false;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected DefaultView2d getDefaultView2d(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof DefaultView2d)
            return (DefaultView2d) mouseevent.getSource();
        return null;
    }

    protected AffineTransform getAffineTransform(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof Image2DViewer)
            return ((Image2DViewer) mouseevent.getSource()).getAffineTransform();
        return null;
    }

    protected ImageElement getImageElement(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof Image2DViewer)
            return ((Image2DViewer) mouseevent.getSource()).getImage();
        return null;
    }

    @Deprecated
    @Override
    public boolean intersects(Rectangle rectangle) {
        return getArea().intersects(rectangle);
        // NOTE : called trough AbstractLayerModel.getSelectedAllGraphicsIntersecting by
        // Draglayer.getGraphicsSurfaceInArea
    }

    @Deprecated
    @Override
    public boolean intersects(Rectangle rectangle, AffineTransform transform) {
        return getArea(transform).intersects(rectangle);
    }

    @Override
    public void showProperties() {
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public void setShape(Shape newShape, MouseEvent mouseevent) {
        if (this.shape == null || newShape == null || !this.shape.equals(newShape)) {
            Shape oldShape = this.shape;
            this.shape = newShape;
            fireDrawingChanged(oldShape);
        }
    }

    public void setLineThickness(float lineThickness) {
        if (this.lineThickness != lineThickness) {
            this.lineThickness = lineThickness;
            updateStroke();
            fireDrawingChanged();
        }
    }

    public void setPaint(Color newPaintColor) {
        if (this.colorPaint == null || newPaintColor == null || !this.colorPaint.equals(newPaintColor)) {
            this.colorPaint = newPaintColor;
            fireDrawingChanged();
        }
    }

    public void setFilled(boolean newFilled) {
        if (this.filled != newFilled) {
            this.filled = newFilled;
            fireDrawingChanged();
        }
    }

    @Override
    public void setSelected(boolean newSelected) {
        if (this.selected != newSelected) {
            this.selected = newSelected;
            fireDrawingChanged();
        }
    }

    public void setLabelVisible(boolean newLabelVisible) {
        if (this.labelVisible != newLabelVisible) {
            this.labelVisible = newLabelVisible;
            fireDrawingChanged();
        }
    }

    @Override
    public void setLabel(String[] labels, DefaultView2d view2d) {
        if (shape != null) {
            Rectangle rect = shape.getBounds();
            int xPos = rect.x + rect.width;
            int yPos = (int) Math.ceil(rect.y + rect.height * 0.5);
            this.setLabel(labels, view2d, xPos, yPos);
        }
    }

    public void setLabel(String[] labels, DefaultView2d view2d, int xPos, int yPos) {
        if (view2d == null)
            return;
        // throw new RuntimeException();

        if (labelVisible) {
            Rectangle2D oldBounds = (graphicLabel != null) ? graphicLabel.getLabelBounds() : null;
            Rectangle2D newBounds = null;

            if (labels == null || labels.length == 0)
                graphicLabel = null;
            else {
                if (graphicLabel == null)
                    graphicLabel = new GraphicLabel();

                graphicLabel.setLabel(view2d, xPos, yPos, labels);
                newBounds = graphicLabel.getLabelBounds();
            }
            if (oldBounds == null || newBounds == null || !oldBounds.equals(newBounds))
                firePropertyChange("graphicLabel", oldBounds, newBounds);
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateStroke() {
        stroke = new BasicStroke(lineThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
    }

    @Override
    public void updateLabel(Object source, DefaultView2d view2d) {
        if (labelVisible) {
            boolean releasedEvent = true;
            ImageElement imageElement = null;
            if (source instanceof MouseEvent) {
                imageElement = getImageElement((MouseEvent) source);
                releasedEvent = ((MouseEvent) source).getID() == MouseEvent.MOUSE_RELEASED;
            } else if (source instanceof ImageElement) {
                imageElement = (ImageElement) source;
            }
            if (imageElement != null) {
                List<MeasureItem> list = getMeasurements(imageElement, releasedEvent);
                if (list != null) {
                    String[] labels = new String[list.size()];
                    for (int i = 0; i < labels.length; i++) {
                        MeasureItem m = list.get(i);
                        StringBuffer buffer = new StringBuffer(m.getMeasurement().getName());
                        buffer.append(": ");
                        if (m.getValue() != null) {
                            buffer.append(DecFormater.oneDecimalUngroup(m.getValue()));
                        }
                        if (m.getUnit() != null) {
                            buffer.append(" ");
                            buffer.append(m.getUnit());
                        }
                        labels[i] = buffer.toString();
                    }
                    setLabel(labels, view2d);
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void paint(Graphics2D g2d, AffineTransform transform) {

        Paint oldPaint = g2d.getPaint();
        Stroke oldStroke = g2d.getStroke();

        if (shape instanceof AdvancedShape) {
            ((AdvancedShape) shape).paint(g2d, transform);
        } else if (shape != null) {
            Shape drawingShape = transform == null ? shape : transform.createTransformedShape(shape);

            g2d.setPaint(colorPaint);
            g2d.setStroke(stroke);
            g2d.draw(drawingShape);
            if (isFilled())
                g2d.fill(drawingShape);
        }

        // if (isSelected && !isGraphicComplete)
        if (isSelected())
            paintHandles(g2d, transform);

        paintLabel(g2d, transform);

        // if (transform != null) {
        // g2d.setPaint(Color.CYAN);
        // g2d.draw(transform.createTransformedShape(getBounds(transform)));
        // }
        // if (transform != null) {
        // g2d.setPaint(Color.RED);
        // g2d.draw(transform.createTransformedShape(getArea(transform)));
        // }
        // if (transform != null) {
        // g2d.setPaint(Color.BLUE);
        // g2d.draw(transform.createTransformedShape(getRepaintBounds(transform)));
        // }

        // }

        g2d.setPaint(oldPaint);
        g2d.setStroke(oldStroke);
    }

    @Override
    public void paintLabel(Graphics2D g2d, AffineTransform transform) {
        if (labelVisible && graphicLabel != null)
            graphicLabel.paint(g2d, transform, selected);
    }

    public void paintHandles(Graphics2D g2d, AffineTransform transform) {

        double size = handleSize;
        double halfSize = size / 2;

        Point2D.Double[] handlePtArray = new Point2D.Double[handlePointList.size()];
        for (int i = 0; i < handlePointList.size(); i++)
            handlePtArray[i] = new Point2D.Double(handlePointList.get(i).getX(), handlePointList.get(i).getY());

        transform.transform(handlePtArray, 0, handlePtArray, 0, handlePtArray.length);

        g2d.setPaint(Color.black);
        for (Point2D point : handlePtArray)
            g2d.fill(new Rectangle2D.Double(point.getX() - halfSize, point.getY() - halfSize, size, size));

        g2d.setPaint(Color.white);
        g2d.setStroke(new BasicStroke(1.0f));
        for (Point2D point : handlePtArray)
            g2d.draw(new Rectangle2D.Double(point.getX() - halfSize, point.getY() - halfSize, size, size));
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected class DefaultDragSequence implements DragSequence {

        protected int lastX;
        protected int lastY;
        protected int handlePointIndex;

        protected DefaultDragSequence() {
            this(-1); // -1 stands for moving current graphic
        }

        protected DefaultDragSequence(int handlePointIndex) {
            this.handlePointIndex = handlePointIndex;
        }

        @Override
        public void startDrag(MouseEvent mouseEvent) {
            lastX = mouseEvent.getX();
            lastY = mouseEvent.getY();

            if (!isGraphicComplete()) {
                if (handlePointList.isEmpty())
                    handlePointList.add(new Point.Double(mouseEvent.getPoint().getX(), mouseEvent.getPoint().getY()));

                if (!isGraphicComplete())
                    handlePointList.add(new Point.Double(mouseEvent.getPoint().getX(), mouseEvent.getPoint().getY()));

                handlePointIndex = handlePointList.size() - 1; // override index to match actual dragging point
            }
        }

        @Override
        public void drag(MouseEvent mouseEvent) {
            int deltaX = mouseEvent.getX() - lastX;
            int deltaY = mouseEvent.getY() - lastY;

            if (deltaX != 0 || deltaY != 0) {
                lastX = mouseEvent.getX();
                lastY = mouseEvent.getY();

                handlePointIndex = moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, mouseEvent);
                updateShapeOnDrawing(mouseEvent);
            }
        }

        @Override
        public boolean completeDrag(MouseEvent mouseEvent) {

            if (!isGraphicComplete()) {

                // check if last dragging point is valid
                int lastPointIndex = handlePointList.size() - 1;
                Point2D lastPoint = handlePointList.get(lastPointIndex);
                if (lastPointIndex > 0) {
                    Point2D previousPoint = handlePointList.get(lastPointIndex - 1);
                    if (lastPoint.equals(previousPoint))
                        return false;
                }

                // if last dragging point do not equals previous one means DragSequence can keep continue on next point
                handlePointList.add(new Point.Double(mouseEvent.getPoint().getX(), mouseEvent.getPoint().getY()));
                handlePointIndex = handlePointList.size() - 1; // forces index to match actual dragging point

                return false;
            }

            boolean shapeValid = (shape != null && isShapeValid());

            if (shapeValid)
                updateShapeOnDrawing(mouseEvent);
            // repaint measurement labels which are entirely computed on mouse click release

            return shapeValid;
        }
    }

    /**
     * Check that not handle points equals with another. <br>
     * Can be overridden to assert what is a valid shape that can be fully computed
     * 
     * @return
     */
    protected boolean isShapeValid() {
        int lastPointIndex = handlePointList.size() - 1;
        while (lastPointIndex > 0) {
            Point2D checkPoint = handlePointList.get(lastPointIndex);
            ListIterator<Point2D> listIt = handlePointList.listIterator(lastPointIndex--);
            while (listIt.hasPrevious())
                if (checkPoint.equals(listIt.previous()))
                    return false;
        }
        return true;
    }

    public DragSequence createDragSequence(DragSequence dragsequence, MouseEvent mouseevent) {
        int i = 1;

        if (mouseevent != null && (dragsequence != null || (i = getHandlePointIndex(mouseevent)) == -1))
            return createMoveDrag(dragsequence, mouseevent);
        else
            return createResizeDrag(mouseevent, i);

    }

    protected DragSequence createMoveDrag(DragSequence dragsequence, MouseEvent mouseevent) {
        return new DefaultDragSequence();
    }

    protected DragSequence createResizeDrag(MouseEvent mouseevent, int i) {
        return new DefaultDragSequence(i);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (pcs == null) {
            pcs = new PropertyChangeSupport(this);
        }

        for (PropertyChangeListener listener : pcs.getPropertyChangeListeners())
            if (listener == propertychangelistener)
                return;

        pcs.addPropertyChangeListener(propertychangelistener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (pcs != null) {
            pcs.removePropertyChangeListener(propertychangelistener);
        }
    }

    protected void firePropertyChange(String s, Object obj, Object obj1) {
        if (pcs != null) {
            pcs.firePropertyChange(s, obj, obj1);
        }
    }

    protected void firePropertyChange(String s, int i, int j) {
        if (pcs != null) {
            pcs.firePropertyChange(s, i, j);
        }
    }

    protected void firePropertyChange(String s, boolean flag, boolean flag1) {
        if (pcs != null) {
            pcs.firePropertyChange(s, flag, flag1);
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void fireDrawingChanged() {
        fireDrawingChanged(null);
    }

    protected void fireDrawingChanged(Shape oldShape) {
        firePropertyChange("bounds", oldShape, shape);

        Rectangle2D labelBounds = (graphicLabel != null) ? graphicLabel.getLabelBounds() : null;
        firePropertyChange("graphicLabel", null, labelBounds);

        // firePropertyChange("graphicLabel", null, graphicLabel.getLabelBounds());
    }

    protected void fireMoveAction() {
        firePropertyChange("move", null, this);
    }

    @Override
    public void fireRemoveAction() {
        firePropertyChange("remove", null, this);
    }

    public void fireRemoveAndRepaintAction() {
        firePropertyChange("remove.repaint", null, this);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return getUIName();
    }

    @Override
    public Object clone() {
        AbstractDragGraphic newGraphic = null;

        try {
            newGraphic = (AbstractDragGraphic) super.clone();
        } catch (CloneNotSupportedException clonenotsupportedexception) {
            return null;
        }

        newGraphic.pcs = null;
        newGraphic.shape = null;
        newGraphic.handlePointList = new ArrayList<Point2D>(handlePointTotalNumber);

        // newGraphic.paintColor = paintColor; // useless
        updateStroke();

        newGraphic.graphicLabel = null;
        newGraphic.selected = false;
        newGraphic.graphicComplete = false;

        return newGraphic;
    }

    // public abstract Graphic clone(int xPos, int yPos); // ???
    public Graphic clone(int xPos, int yPos) {
        AbstractDragGraphic graphic = (AbstractDragGraphic) clone();
        graphic.setShape(new Rectangle(xPos, yPos, 0, 0), null); // needed for selection check when used after drag
        return graphic;
    };

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    @Deprecated
    protected int resizeOnDrawing(int handlePointIndex, int deltaX, int deltaY, MouseEvent mouseEvent) {
        return moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, mouseEvent);
    };

    protected int moveAndResizeOnDrawing(int handlePointIndex, int deltaX, int deltaY, MouseEvent mouseEvent) {
        if (handlePointIndex == -1) {
            for (Point2D point : handlePointList)
                point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
        } else {
            handlePointList.get(handlePointIndex).setLocation(mouseEvent.getPoint());
        }
        return handlePointIndex;
    }

    protected abstract void updateShapeOnDrawing(MouseEvent mouseevent);

    // /////////////////////////////////////////////////////////////////////////////////////////////////////
    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public class AdvancedShape implements Shape {

        /**
         * First element should be considered as the main shape used for drawing of the main features of graphic.<br>
         * For instance, this first shape defines measurement areas or path lines. Other shape are usually dedicated to
         * decorative drawings, with or without invariant size according to the view.
         */
        protected List<BasicShape> shapeList;

        protected double scalingFactor = 1;

        class BasicShape {
            Shape shape;
            Stroke stroke;

            public BasicShape(Shape shape, Stroke stroke) {
                if (shape == null || stroke == null)
                    throw new IllegalArgumentException();
                this.shape = shape;
                this.stroke = stroke;
            }

            public Shape getRealShape() {
                return shape;
            }
        }

        /**
         * Dedicated to drawings with invariant size around anchorPoint according to the view
         */
        class InvariantShape extends BasicShape {
            Point2D anchorPoint;
            double scalingMin = 0;

            public InvariantShape(Shape shape, Stroke stroke, Point2D anchorPoint) {
                super(shape, stroke);
                if (anchorPoint == null)
                    throw new IllegalArgumentException();
                this.anchorPoint = anchorPoint;
            }

            public InvariantShape(Shape shape, Stroke stroke, Point2D anchorPoint, double scalingMin) {
                this(shape, stroke, anchorPoint);
                if (scalingMin < 0)
                    throw new IllegalArgumentException();
                this.scalingMin = scalingMin;
            }

            @Override
            public Shape getRealShape() {
                double scale = scalingFactor < scalingMin ? scalingMin : scalingFactor;
                return GeomUtil.getScaledShape(shape, 1 / scale, anchorPoint);
            }
        }

        public AdvancedShape(int initialShapeNumber) {
            shapeList = new ArrayList<BasicShape>(initialShapeNumber);
        }

        void addShape(Shape shape) {
            addShape(shape, getStroke());
        }

        void addShape(Shape shape, Stroke stroke) {
            shapeList.add(new BasicShape(shape, stroke));
        }

        void addInvShape(Shape shape, Point2D anchorPoint) {
            addInvShape(shape, anchorPoint, getStroke());
        }

        void addInvShape(Shape shape, Point2D anchorPoint, double scalingMin) {
            addInvShape(shape, anchorPoint, scalingMin, getStroke());
        }

        void addInvShape(Shape shape, Point2D anchorPoint, double scalingMin, Stroke stroke) {
            shapeList.add(new InvariantShape(shape, stroke, anchorPoint, scalingMin));
        }

        void addInvShape(Shape shape, Point2D anchorPoint, Stroke stroke) {
            shapeList.add(new InvariantShape(shape, stroke, anchorPoint));
        }

        void updateScalingFactor(double scalingFactor) {
            this.scalingFactor = scalingFactor;
        }

        public void updateScalingFactor(AffineTransform transform) {
            updateScalingFactor(GeomUtil.extractScalingFactor(transform));
        }

        public void paint(Graphics2D g2d, AffineTransform transform) {
            updateScalingFactor(transform);

            Paint oldPaint = g2d.getPaint();
            Stroke oldStroke = g2d.getStroke();

            g2d.setPaint(getColorPaint());

            for (BasicShape item : shapeList) {
                Shape drawingShape =
                    (transform == null) ? item.getRealShape() : transform.createTransformedShape(item.getRealShape());

                g2d.setStroke(item.stroke);
                g2d.draw(drawingShape);

                if (isFilled())
                    g2d.fill(drawingShape);
            }
            g2d.setPaint(oldPaint);
            g2d.setStroke(oldStroke);
        }

        /**
         * 
         * @return generalShape which is by convention the first shape of the list and is dedicated to the measure
         */
        public Shape getGeneralShape() {
            if (shapeList.size() > 0)
                return shapeList.get(0).getRealShape();
            return null;
        }

        @Override
        public Rectangle getBounds() {
            Rectangle rectangle = null;

            for (BasicShape item : shapeList) {
                if (rectangle == null)
                    rectangle = item.getRealShape().getBounds();
                else
                    rectangle.add(item.getRealShape().getBounds());
            }

            return rectangle;
        }

        @Override
        public Rectangle2D getBounds2D() {
            Rectangle2D rectangle = null;

            for (BasicShape item : shapeList) {
                if (rectangle == null)
                    rectangle = item.getRealShape().getBounds2D();
                else
                    rectangle.add(item.getRealShape().getBounds2D());
            }
            return rectangle;
        }

        @Override
        public boolean contains(double x, double y) {
            for (BasicShape item : shapeList)
                if (item.getRealShape().contains(x, y))
                    return true;

            return false;
        }

        @Override
        public boolean contains(Point2D p) {
            for (BasicShape item : shapeList)
                if (item.getRealShape().contains(p))
                    return true;

            return false;
        }

        @Override
        public boolean contains(double x, double y, double w, double h) {
            for (BasicShape item : shapeList)
                if (item.getRealShape().contains(x, y, w, h))
                    return true;

            return false;
        }

        @Override
        public boolean contains(Rectangle2D r) {
            for (BasicShape item : shapeList)
                if (item.getRealShape().contains(r))
                    return true;

            return false;
        }

        @Override
        public boolean intersects(double x, double y, double w, double h) {
            for (BasicShape item : shapeList)
                if (item.getRealShape().intersects(x, y, w, h))
                    return true;

            return false;
        }

        @Override
        public boolean intersects(Rectangle2D r) {
            for (BasicShape item : shapeList)
                if (item.getRealShape().intersects(r))
                    return true;

            return false;
        }

        @Override
        public PathIterator getPathIterator(AffineTransform at) {
            if (at != null)
                updateScalingFactor(at);
            return getFullPathShape().getPathIterator(at);
        }

        @Override
        public PathIterator getPathIterator(AffineTransform at, double flatness) {
            if (at != null)
                updateScalingFactor(at);
            return getFullPathShape().getPathIterator(at, flatness);
        }

        private Path2D getFullPathShape() {
            Path2D pathShape = new Path2D.Double(Path2D.WIND_NON_ZERO);

            for (BasicShape item : shapeList)
                pathShape.append(item.getRealShape(), false);

            return pathShape;
        }

        public Area getArea(AffineTransform transform) {
            updateScalingFactor(transform);

            double growingSize = getHandleSize() * 2.0 / scalingFactor;
            Stroke boundingStroke = new BasicStroke((float) growingSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

            Area pathBoundingArea = new Area();

            for (BasicShape item : shapeList) {

                // Shape strokedShape = boundingStroke.createStrokedShape(item.getRealShape());

                // Note : if shape is invalid, like a path with an odd number of curves, creating a new Area involves a
                // "java.lang.InternalError". Because trapping the exception is too much time consuming it's the user
                // responsibility of this not to happen
                // Area strokedArea = new Area(strokedShape);
                Shape strokedArea = null;
                try {
                    Shape strokedShape = boundingStroke.createStrokedShape(item.getRealShape());
                    strokedArea = new Area(strokedShape);
                } catch (Throwable e) {
                    System.err.println(e);
                }
                if (strokedArea != null)
                    pathBoundingArea.add(new Area(strokedArea));

            }
            return pathBoundingArea;
        }
    }
}
