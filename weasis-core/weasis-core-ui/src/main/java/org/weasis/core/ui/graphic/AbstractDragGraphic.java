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
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.graphic.model.GraphicsPane;

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
        return graphicLabel != null ? graphicLabel.getLabel() : null;
    }

    public boolean isGraphicComplete() {
        // return isGraphicComplete = (handlePointList.size() == handlePointTotalNumber);
        return graphicComplete;
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

        // NOTE : bound is not accurate with complex shape (it is true for rectangle or ellipse with rotation)
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

        if (shape instanceof AdvancedShape) {
            ((AdvancedShape) shape).updateScalingFactor(transform);
        }

        return shape.getBounds();
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

        if (shape instanceof AdvancedShape) {
            ((AdvancedShape) shape).updateScalingFactor(transform);
        }

        Rectangle rectangle = shape.getBounds();
        growHandles(rectangle, transform);

        return rectangle;
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
        if (transform != null && rectangle != null) {
            rectangle = transform.createTransformedShape(rectangle).getBounds();
        }

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
     *            Bounding rectangle which size has to be modified according to the given transform for the view when
     *            taking handle drawings in consideration<br>
     *            This assumes that handle drawing size do not change with different scaling of views. Hence, real
     *            coordinates of bounding rectangle have modified consequently<br>
     * @param transform
     */
    public void growHandles(Rectangle rectangle, AffineTransform transform) {
        int growingValue = (Math.max((int) Math.ceil(handleSize * 1.5), (int) Math.ceil(lineThickness)) + 1) >> 1;
        if (transform != null) {
            growingValue = (int) Math.ceil(growingValue / transform.getScaleX());
        }
        rectangle.grow(growingValue, growingValue);
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
        int handleSize = getHandleSize() + 2;

        // Enable to get a better selection of the handle with a low or high magnification zoom
        AffineTransform affineTransform = getAffineTransform(mouseevent);
        if (affineTransform != null) {
            handleSize = (int) Math.ceil(handleSize / affineTransform.getScaleX());
        }

        for (int index = 0; index < handlePointList.size(); index++) {
            Point2D point = handlePointList.get(index);
            if (mousePoint.distance(point) <= handleSize)
                return index;
        }
        return -1;

    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected Graphics2D getGraphics2D(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof GraphicsPane)
            return (Graphics2D) ((GraphicsPane) mouseevent.getSource()).getGraphics();
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
    public void setLabel(String[] labels, Graphics2D g2d) {
        if (labelVisible && g2d != null && shape != null)
            if (labels == null || labels.length == 0) {
                graphicLabel = null;
            } else {
                if (graphicLabel == null) {
                    graphicLabel = new GraphicLabel();
                }

                Rectangle oldBound = graphicLabel.getBound();
                graphicLabel.setLabel(labels, g2d);

                Rectangle rect = shape.getBounds();
                graphicLabel.setLabelPosition(rect.x + rect.width, (int) Math.ceil(rect.y + rect.height * 0.5));

                firePropertyChange("graphicLabel", oldBound, graphicLabel);
            }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateStroke() {
        stroke = new BasicStroke(lineThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
    }

    @Override
    public void updateLabel(Object source, Graphics2D g2d) {
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void paint(Graphics2D g2d, AffineTransform transform) {

        // if (shape != null) {

        if (shape instanceof AdvancedShape) {
            ((AdvancedShape) shape).paint(g2d, transform);
        } else if (shape != null) {
            g2d.setPaint(colorPaint);
            g2d.setStroke(stroke);
            Shape drawingShape = transform == null ? shape : transform.createTransformedShape(shape);
            if (isFilled()) {
                g2d.fill(drawingShape);
            }
            g2d.draw(drawingShape);
        }
        // if (isSelected && !isGraphicComplete)
        if (isSelected()) {
            paintHandles(g2d, transform);
        }

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
    }

    @Override
    public void paintLabel(Graphics2D g2d, AffineTransform transform) {
        if (labelVisible && graphicLabel != null) {
            graphicLabel.paint(g2d, transform);
        }
    }

    public void paintHandles(Graphics2D graphics2d, AffineTransform transform) {

        double size = getHandleSize();
        double halfSize = size / 2;

        Point2D.Double[] handlePtArray = new Point2D.Double[handlePointList.size()];
        for (int i = 0; i < handlePointList.size(); i++) {
            handlePtArray[i] = new Point2D.Double(handlePointList.get(i).getX(), handlePointList.get(i).getY());
        }

        transform.transform(handlePtArray, 0, handlePtArray, 0, handlePtArray.length);

        graphics2d.setPaint(Color.black);
        for (Point2D point : handlePtArray) {
            graphics2d.fill(new Rectangle2D.Double(point.getX() - halfSize, point.getY() - halfSize, size, size));
        }

        graphics2d.setPaint(Color.white);
        graphics2d.setStroke(new BasicStroke(1.0f));
        for (Point2D point : handlePtArray) {
            graphics2d.draw(new Rectangle2D.Double(point.getX() - halfSize, point.getY() - halfSize, size, size));
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected class DefaultDragSequence implements DragSequence {

        protected int lastX;
        protected int lastY;
        protected int handlePointIndex; // -1 stands for moving current graphic

        protected DefaultDragSequence() {
            this(-1);
        }

        protected DefaultDragSequence(int handlePointIndex) {
            this.handlePointIndex = handlePointIndex;
        }

        @Override
        public void startDrag(MouseEvent mouseEvent) {
            lastX = mouseEvent.getX();
            lastY = mouseEvent.getY();

            if (!graphicComplete) {
                if (handlePointList.isEmpty()) {
                    handlePointList.add(new Point.Double(mouseEvent.getPoint().getX(), mouseEvent.getPoint().getY()));
                }
                if (handlePointList.size() < handlePointTotalNumber) {
                    handlePointList.add(new Point.Double(mouseEvent.getPoint().getX(), mouseEvent.getPoint().getY()));
                }

                handlePointIndex = handlePointList.size() - 1; // forces index to match actual dragging point
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

            if (!graphicComplete) {
                graphicComplete = handlePointList.size() == handlePointTotalNumber;
            }

            int lastPointIndex = handlePointList.size() - 1;
            Point2D lastPoint = handlePointList.get(lastPointIndex);
            ListIterator<Point2D> listIt = handlePointList.listIterator(lastPointIndex);

            while (listIt.hasPrevious())
                if (lastPoint.equals(listIt.previous()))
                    return false;

            if (!graphicComplete) {
                handlePointList.add(new Point.Double(mouseEvent.getPoint().getX(), mouseEvent.getPoint().getY()));
                handlePointIndex = handlePointList.size() - 1;
            }
            boolean validShape = graphicComplete && shape != null;
            if (validShape) {
                updateShapeOnDrawing(mouseEvent);
            }
            return validShape;
        }
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
            for (Point2D point : handlePointList) {
                point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
            }
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

                // System.out.println("scalingFactor : " + scalingFactor);
                // System.out.println("scalingMin : " + scalingMin);
                // System.out.println("transformedScale : " + scale);
                // System.out.println("final scale : " + scalingFactor / scale);
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

        void addInvShape(Shape shape, Point2D anchorPoint, double scalingMax) {
            addInvShape(shape, anchorPoint, scalingMax, getStroke());
        }

        void addInvShape(Shape shape, Point2D anchorPoint, double scalingMax, Stroke stroke) {
            shapeList.add(new InvariantShape(shape, stroke, anchorPoint, scalingMax));
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

            g2d.setPaint(getColorPaint());

            for (BasicShape item : shapeList) {
                Shape drawingShape =
                    (transform == null) ? item.getRealShape() : transform.createTransformedShape(item.getRealShape());

                g2d.setStroke(item.stroke);
                g2d.draw(drawingShape);

                if (isFilled()) {
                    g2d.fill(drawingShape);
                }
            }
        }

        @Override
        public Rectangle getBounds() {
            Rectangle rectangle = null;

            for (BasicShape item : shapeList) {
                if (rectangle == null) {
                    rectangle = item.getRealShape().getBounds();
                } else {
                    rectangle.add(item.getRealShape().getBounds());
                }
            }

            return rectangle;
        }

        @Override
        public Rectangle2D getBounds2D() {
            Rectangle2D rectangle = null;

            for (BasicShape item : shapeList) {
                if (rectangle == null) {
                    rectangle = item.getRealShape().getBounds2D();
                } else {
                    rectangle.add(item.getRealShape().getBounds2D());
                }
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
            if (at != null) {
                updateScalingFactor(at);
            }
            return getFullPathShape().getPathIterator(at);
        }

        @Override
        public PathIterator getPathIterator(AffineTransform at, double flatness) {
            if (at != null) {
                updateScalingFactor(at);
            }
            return getFullPathShape().getPathIterator(at, flatness);
        }

        private Path2D getFullPathShape() {
            Path2D pathShape = new Path2D.Double(Path2D.WIND_NON_ZERO);

            for (BasicShape item : shapeList) {
                pathShape.append(item.getRealShape(), false);
            }

            return pathShape;
        }

        public Area getArea(AffineTransform transform) {
            updateScalingFactor(transform);

            double growingSize = getHandleSize() * 2 / scalingFactor;
            Stroke boundingStroke = new BasicStroke((float) growingSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

            Area pathBoundingArea = new Area();

            for (BasicShape item : shapeList) {
                pathBoundingArea.add(new Area(boundingStroke.createStrokedShape(item.getRealShape())));
            }

            return pathBoundingArea;
        }
    }
}
