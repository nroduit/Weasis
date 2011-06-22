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
import org.weasis.core.ui.graphic.AbstractDragGraphic.AdvancedShape.BasicShape;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class AbstractDragGraphic.
 * 
 * @author Nicolas Roduit,Benoit Jacquemoud
 */

public abstract class AbstractDragGraphic implements Graphic, Cloneable {

    public final static int UNDEFINED = -1;

    protected PropertyChangeSupport pcs;

    protected Shape shape;

    protected int handlePointTotalNumber;
    protected List<Point2D> handlePointList;

    protected int handleSize = 6; // ????

    protected Paint colorPaint;
    protected float lineThickness;
    protected boolean filled;
    protected boolean labelVisible;
    protected GraphicLabel graphicLabel;
    protected boolean selected = false;
    protected boolean resizingOrMoving = false;

    // TODO none are transient and should be if serialized

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public AbstractDragGraphic(int handlePointTotalNumber) {
        this(handlePointTotalNumber, Color.YELLOW, 1f, true);
    }

    public AbstractDragGraphic(int handlePointTotalNumber, Color paintColor, float lineThickness, boolean labelVisible) {
        this(handlePointTotalNumber, paintColor, lineThickness, labelVisible, false);
    }

    public AbstractDragGraphic(int handlePointTotalNumber, Color paintColor, float lineThickness, boolean labelVisible,
        boolean filled) {
        this(null, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled);
    }

    public AbstractDragGraphic(List<Point2D> handlePointList, int handlePointTotalNumber, Color paintColor,
        float lineThickness, boolean labelVisible, boolean filled) {
        if (paintColor == null) {
            paintColor = Color.YELLOW;
        }

        this.handlePointTotalNumber = handlePointTotalNumber;
        this.handlePointList =
            handlePointList == null ? new ArrayList<Point2D>(handlePointTotalNumber > 0 ? handlePointTotalNumber : 10)
                : handlePointList;
        this.colorPaint = paintColor;
        this.lineThickness = lineThickness;
        this.labelVisible = labelVisible;
        this.filled = filled;
        if (handlePointList != null) {
            updateShapeOnDrawing(null);
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Shape getShape() {
        return shape;
    }

    public int getHandlePointTotalNumber() {
        return handlePointTotalNumber;
    }

    public Stroke getStroke(float lineThickness) {
        return new BasicStroke(lineThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
    }

    public Stroke getDashStroke(float lineThickness) {
        return new BasicStroke(lineThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 5.0f,
            5.0f }, 0f);
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
        return handlePointList.size() == handlePointTotalNumber;
    }

    public Point2D getHandlePoint(int index) {
        if (index < 0 || index >= handlePointList.size())
            return null;
        Point2D handlePoint = handlePointList.get(index);
        return handlePoint != null ? (Point2D) handlePointList.get(index).clone() : null;
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

        if (shape instanceof AdvancedShape) {
            ((AdvancedShape) shape).updateScalingFactor(transform);
        }

        Rectangle2D bounds = shape.getBounds2D();
        growSelection(bounds, transform);

        return bounds != null ? bounds.getBounds() : null;
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

        Rectangle2D bounds = shape.getBounds2D();
        growHandles(bounds, transform);

        return bounds != null ? bounds.getBounds() : null;
    }

    /**
     * @return Shape bounding rectangle relative to affineTransform<br>
     *         Handle points bounding rectangles are also included, knowing they have invariant size according to
     *         current view.<br>
     *         Any other invariant sized shape bounding rectangles are included if shape is instanceof AdvancedShape
     */

    @Override
    public Rectangle getTransformedBounds(Shape shape, AffineTransform transform) {
        // TODO - should be static
        Rectangle rectangle = getRepaintBounds(shape, transform);

        if (transform != null && rectangle != null) {
            rectangle = transform.createTransformedShape(rectangle).getBounds();
        }

        return rectangle;
    }

    @Override
    public Rectangle getTransformedBounds(GraphicLabel label, AffineTransform transform) {
        return (label != null) ? label.getTransformedBounds(transform).getBounds() : null;
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
        double growingSize = Math.max(handleSize, lineThickness);
        if (transform != null) {
            // growingValue = (int) Math.ceil(growingValue / transform.getScaleX()); // doesn't work with rotation
            double scalingFactor = GeomUtil.extractScalingFactor(transform);
            // growingValue = (int) Math.ceil(growingValue / scalingFactor);
            growingSize = growingSize / scalingFactor;

        }
        GeomUtil.growRectangle(rectangle, growingSize + 5);
        // rectangle.grow(growingSize, growingSize);
    }

    public void growSelection(Rectangle2D rectangle, AffineTransform transform) {
        double growingSize = handleSize;
        if (transform != null) {
            growingSize = growingSize / GeomUtil.extractScalingFactor(transform);
        }
        GeomUtil.growRectangle(rectangle, growingSize);
    }

    /**
     * @return : selected handle point index if exist, otherwise -1
     */
    @Deprecated
    public int getResizeCorner(MouseEvent mouseEvent) {
        return -1;
    }

    public int getHandlePointIndex(MouseEventDouble mouseevent) {
        if (mouseevent != null) {
            final Point2D mousePoint = mouseevent.getImageCoordinates();
            double maxHandleDistance = (handleSize * 1.5) / 2;

            AffineTransform transform = getAffineTransform(mouseevent);
            if (transform != null) {
                double scalingFactor = GeomUtil.extractScalingFactor(transform);
                maxHandleDistance = maxHandleDistance / scalingFactor;
            }

            for (int index = 0; index < handlePointList.size(); index++) {
                Point2D handlePoint = handlePointList.get(index);
                if (mousePoint != null && handlePoint != null && mousePoint.distance(handlePoint) <= maxHandleDistance)
                    return index;
            }
        }
        return -1;
    }

    public boolean isOnGraphicLabel(MouseEventDouble mouseevent) {
        if (mouseevent == null)
            return false;

        final Point2D mousePoint = mouseevent.getImageCoordinates();

        AffineTransform transform = getAffineTransform(mouseevent);
        if (transform != null && graphicLabel != null) {
            Area labelArea = graphicLabel.getArea(transform);
            if (labelArea != null && labelArea.contains(mousePoint))
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

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public void setShape(Shape newShape, MouseEvent mouseevent) {
        Shape oldShape = this.shape;
        this.shape = newShape;
        fireDrawingChanged(oldShape);
    }

    public void setLineThickness(float lineThickness) {
        if (this.lineThickness != lineThickness) {
            this.lineThickness = lineThickness;
            if (shape instanceof AdvancedShape) {
                for (BasicShape bs : ((AdvancedShape) shape).getShapeList()) {
                    bs.changelineThickness(lineThickness);
                }
            }
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
            if (this instanceof AbstractDragGraphicArea) {
                this.filled = newFilled;
                fireDrawingChanged();
            }
        }
    }

    @Override
    public void setSelected(boolean newSelected) {
        if (this.selected != newSelected) {
            this.selected = newSelected;
            fireDrawingChanged();
            fireLabelChanged();
        }
    }

    public void setLabelVisible(boolean newLabelVisible) {
        if (this.labelVisible != newLabelVisible) {
            this.labelVisible = newLabelVisible;
            fireLabelChanged();
        }
    }

    @Override
    public void setLabel(String[] labels, DefaultView2d view2d) {
        if (shape != null) {
            Rectangle2D rect;

            if (shape instanceof AdvancedShape && ((AdvancedShape) shape).shapeList.size() > 0) {
                // convention is first shape has to be the user drawing path, else should be decoration
                Shape generalPath = ((AdvancedShape) shape).shapeList.get(0).shape;
                rect = generalPath.getBounds2D();
            } else {
                rect = shape.getBounds2D();
            }

            double xPos = rect.getX() + rect.getWidth() + 3;
            double yPos = rect.getY() + rect.getHeight() * 0.5;

            this.setLabel(labels, view2d, new Point2D.Double(xPos, yPos));
        }
    }

    public void setLabel(String[] labels, DefaultView2d view2d, Point2D pos) {
        if (pos == null) {
            setLabel(labels, view2d);
        } else if (labelVisible) {
            GraphicLabel oldLabel = (graphicLabel != null) ? graphicLabel.clone() : null;

            if (labels == null || labels.length == 0) {
                graphicLabel = null;
            } else {
                if (graphicLabel == null) {
                    graphicLabel = new GraphicLabel();
                }

                graphicLabel.setLabel(view2d, pos.getX(), pos.getY(), labels);
            }
            fireLabelChanged(oldLabel);
        }
    }

    public void moveLabel(double deltaX, double deltaY) {
        if (graphicLabel != null && (deltaX != 0 || deltaY != 0)) {
            GraphicLabel oldLabel = (graphicLabel != null) ? graphicLabel.clone() : null;
            graphicLabel.move(deltaX, deltaY);
            fireLabelChanged(oldLabel);
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void updateLabel(Object source, DefaultView2d view2d) {
        this.updateLabel(source, view2d, null);
    }

    public void updateLabel(Object source, DefaultView2d view2d, Point2D pos) {
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
                List<MeasureItem> list = getMeasurements(imageElement, releasedEvent, true);

                if (list != null && list.size() > 0) {
                    List<String> labelList = new ArrayList<String>(list.size());

                    for (MeasureItem item : list) {
                        if (item.getMeasurement() != null && item.getMeasurement().isGraphicLabel()) {

                            StringBuffer buffer = new StringBuffer(item.getMeasurement().getName());
                            buffer.append(": ");

                            if (item.getValue() != null) {
                                buffer.append(DecFormater.oneDecimalUngroup(item.getValue()));
                            }

                            if (item.getUnit() != null) {
                                buffer.append(" ");
                                buffer.append(item.getUnit());
                            }
                            labelList.add(buffer.toString());
                        }
                    }

                    if (labelList.size() > 0) {
                        setLabel(labelList.toArray(new String[labelList.size()]), view2d, pos);
                    }
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
            Shape drawingShape = (transform == null) ? shape : transform.createTransformedShape(shape);

            g2d.setPaint(colorPaint);
            g2d.setStroke(getStroke(lineThickness));
            g2d.draw(drawingShape);

            if (isFilled()) {
                g2d.fill(drawingShape);
            }
        }

        // Graphics DEBUG
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
        // Graphics DEBUG

        g2d.setPaint(oldPaint);
        g2d.setStroke(oldStroke);

        if (isSelected()) {
            paintHandles(g2d, transform);
        }

        paintLabel(g2d, transform);
    }

    public boolean isResizingOrMoving() {
        return resizingOrMoving;
    }

    @Override
    public void paintLabel(Graphics2D g2d, AffineTransform transform) {
        if (labelVisible && graphicLabel != null) {
            graphicLabel.paint(g2d, transform, selected);
        }
    }

    public void paintHandles(Graphics2D g2d, AffineTransform transform) {
        if (!resizingOrMoving) {
            double size = handleSize;
            double halfSize = size / 2;

            ArrayList<Point2D> handlePts = new ArrayList<Point2D>(handlePointList.size());
            for (Point2D pt : handlePointList) {
                if (pt != null) {
                    handlePts.add(new Point2D.Double(pt.getX(), pt.getY()));
                }
            }

            Point2D.Double[] handlePtArray = handlePts.toArray(new Point2D.Double[handlePts.size()]);
            transform.transform(handlePtArray, 0, handlePtArray, 0, handlePtArray.length);

            Paint oldPaint = g2d.getPaint();
            Stroke oldStroke = g2d.getStroke();

            g2d.setPaint(Color.black);
            for (Point2D point : handlePtArray) {
                g2d.fill(new Rectangle2D.Double(point.getX() - halfSize, point.getY() - halfSize, size, size));
            }

            g2d.setPaint(Color.white);
            g2d.setStroke(new BasicStroke(1.0f));
            for (Point2D point : handlePtArray) {
                g2d.draw(new Rectangle2D.Double(point.getX() - halfSize, point.getY() - halfSize, size, size));
            }

            g2d.setPaint(oldPaint);
            g2d.setStroke(oldStroke);
        }
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

        protected DefaultDragSequence(int handlePointIndex) {
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

                updateShapeOnDrawing(mouseEvent);

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
                    updateShapeOnDrawing(mouseEvent);

                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Can be overridden to estimate what is a valid shape that can be fully computed and drawn
     * 
     * @return True when not handle points equals each another. <br>
     */
    public boolean isShapeValid() {
        if (isGraphicComplete()) {
            int lastPointIndex = handlePointList.size() - 1;

            while (lastPointIndex > 0) {
                Point2D checkPoint = handlePointList.get(lastPointIndex);
                ListIterator<Point2D> listIt = handlePointList.listIterator(lastPointIndex--);
                while (listIt.hasPrevious()) {
                    if (checkPoint != null && checkPoint.equals(listIt.previous()))
                        return false;
                }
            }
            return true;
        } else
            return false;
    }

    /**
     * @return True if last dragging point equals the previous one
     */
    protected boolean isLastPointValid() {

        Point2D lastP = getHandlePoint(handlePointList.size() - 1);
        if (lastP != null) {
            if (lastP.equals(getHandlePoint(handlePointList.size() - 2)))
                return false;
        }
        return true;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public DragSequence createMoveDrag() {
        return new DefaultDragSequence();
    }

    public DragSequence createResizeDrag(int i) {
        return new DefaultDragSequence(i);
    }

    public DragSequence createDragLabelSequence() {
        return new DragLabelSequence();
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

    protected void fireLabelChanged() {
        fireLabelChanged(null);
    }

    protected void fireLabelChanged(GraphicLabel oldLabel) {
        firePropertyChange("graphicLabel", oldLabel, graphicLabel);
    }

    protected void fireMoveAction() {
        if (isGraphicComplete()) {
            firePropertyChange("move", null, this);
        }
    }

    @Override
    public void toFront() {
        if (isGraphicComplete()) {
            firePropertyChange("toFront", null, this);
        }
    }

    @Override
    public void toBack() {
        if (isGraphicComplete()) {
            firePropertyChange("toBack", null, this);
        }
    }

    @Override
    public void fireRemoveAction() {
        if (isGraphicComplete()) {
            firePropertyChange("remove", null, this);
        }

    }

    public void fireRemoveAndRepaintAction() {
        if (isGraphicComplete()) {
            firePropertyChange("remove.repaint", null, this);
        }
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
        newGraphic.handlePointList = new ArrayList<Point2D>(handlePointTotalNumber > 0 ? handlePointTotalNumber : 10);
        // newGraphic.paintColor = paintColor; // useless
        newGraphic.graphicLabel = null;
        newGraphic.selected = false;

        return newGraphic;
    }

    public Graphic clone(double xPos, double yPos) {
        AbstractDragGraphic graphic = (AbstractDragGraphic) clone();
        graphic.setShape(new Rectangle2D.Double(xPos, yPos, 0, 0), null); // needed for selection check when used after
                                                                          // drag
        return graphic;
    };

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected int moveAndResizeOnDrawing(int handlePointIndex, double deltaX, double deltaY, MouseEventDouble mouseEvent) {
        if (handlePointIndex == -1) {
            for (Point2D point : handlePointList) {
                if (point != null) {
                    point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
                }
            }
        } else {
            Point2D point = handlePointList.get(handlePointIndex);
            if (point != null) {
                point.setLocation(mouseEvent.getImageCoordinates());
                // point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
            }
        }
        return handlePointIndex;
    }

    protected abstract void updateShapeOnDrawing(MouseEventDouble mouseevent);

    // /////////////////////////////////////////////////////////////////////////////////////////////////////
    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public interface eHandlePoint {
        Point2D get(AbstractDragGraphic graphic);
    }

    static boolean isLineValid(eHandlePoint eP1, eHandlePoint eP2, AbstractDragGraphic g) {
        if (eP1 == null || eP2 == null || g == null)
            return false;

        Point2D p1 = eP1.get(g), p2 = eP2.get(g);
        return p1 != null && p2 != null && !p2.equals(p1);
    }

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

        public class BasicShape {
            final Shape shape;
            final boolean fixedLineWidth;
            Stroke stroke;

            public BasicShape(Shape shape, Stroke stroke, boolean fixedLineWidth) {
                if (shape == null || stroke == null)
                    throw new IllegalArgumentException();
                this.shape = shape;
                this.stroke = stroke;
                this.fixedLineWidth = fixedLineWidth;
            }

            public Shape getRealShape() {
                return shape;
            }

            public void changelineThickness(float width) {
                if (!fixedLineWidth && stroke instanceof BasicStroke) {
                    BasicStroke s = (BasicStroke) stroke;
                    if (s.getLineWidth() != width) {
                        stroke =
                            new BasicStroke(width, s.getEndCap(), s.getLineJoin(), s.getMiterLimit(), s.getDashArray(),
                                s.getDashPhase());
                    }
                }
            }
        }

        /**
         * Dedicated to drawings with invariant size around anchorPoint according to the view
         */
        class InvariantShape extends BasicShape {
            final Point2D anchorPoint;
            final double scalingMin;

            public InvariantShape(Shape shape, Stroke stroke, Point2D anchorPoint, boolean fixedLineWidth) {
                this(shape, stroke, anchorPoint, 0.0, fixedLineWidth);
            }

            public InvariantShape(Shape shape, Stroke stroke, Point2D anchorPoint, double scalingMin,
                boolean fixedLineWidth) {
                super(shape, stroke, fixedLineWidth);

                if (anchorPoint == null)
                    throw new IllegalArgumentException();

                if (scalingMin < 0)
                    throw new IllegalArgumentException();

                this.anchorPoint = anchorPoint;
                this.scalingMin = scalingMin;
            }

            @Override
            public Shape getRealShape() {
                double scale = (scalingFactor < scalingMin) ? scalingMin : scalingFactor;
                return scale != 0 ? GeomUtil.getScaledShape(shape, 1 / scale, anchorPoint) : null;
            }
        }

        public AdvancedShape(int initialShapeNumber) {
            shapeList = new ArrayList<BasicShape>(initialShapeNumber);
        }

        public List<BasicShape> getShapeList() {
            return shapeList;
        }

        void addShape(Shape shape) {
            addShape(shape, getStroke(lineThickness), false);
        }

        void addShape(Shape shape, Stroke stroke, boolean fixedLineWidth) {
            shapeList.add(new BasicShape(shape, stroke, fixedLineWidth));
        }

        void addInvShape(Shape shape, Point2D anchorPoint) {
            addInvShape(shape, anchorPoint, getStroke(lineThickness), false);
        }

        void addInvShape(Shape shape, Point2D anchorPoint, double scalingMin, boolean fixedLineWidth) {
            addInvShape(shape, anchorPoint, scalingMin, getStroke(lineThickness), fixedLineWidth);
        }

        void addInvShape(Shape shape, Point2D anchorPoint, double scalingMin, Stroke stroke, boolean fixedLineWidth) {
            shapeList.add(new InvariantShape(shape, stroke, anchorPoint, scalingMin, fixedLineWidth));
        }

        void addInvShape(Shape shape, Point2D anchorPoint, Stroke stroke, boolean fixedLineWidth) {
            shapeList.add(new InvariantShape(shape, stroke, anchorPoint, fixedLineWidth));
        }

        void updateScalingFactor(double scalingFactor) {
            if (scalingFactor == 0)
                throw new IllegalArgumentException("scalingFactor cannot be zero");
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
                Shape drawingShape = item.getRealShape();

                if (drawingShape != null) {
                    if (transform != null) {
                        drawingShape = transform.createTransformedShape(drawingShape);
                    }

                    g2d.setStroke(item.stroke);
                    g2d.draw(drawingShape);

                    if (isFilled()) {
                        g2d.fill(drawingShape);
                    }
                }
            }

            g2d.setPaint(oldPaint);
            g2d.setStroke(oldStroke);
        }

        /**
         * 
         * @return a shape which is by convention the first shape in the list which is dedicated to the user tool
         *         drawing
         */
        public Shape getGeneralShape() {
            if (shapeList.size() > 0 && shapeList.get(0) != null)
                return shapeList.get(0).getRealShape();
            return null;
        }

        @Override
        public Rectangle getBounds() {
            Rectangle rectangle = null;

            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();
                Rectangle bounds = realShape != null ? realShape.getBounds() : null;

                if (bounds != null) {
                    if (rectangle == null) {
                        rectangle = bounds;
                    } else {
                        rectangle.add(bounds);
                    }
                }
            }

            return rectangle;
        }

        @Override
        public Rectangle2D getBounds2D() {
            Rectangle2D rectangle = null;

            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();
                Rectangle2D bounds = realShape != null ? realShape.getBounds2D() : null;

                if (bounds != null) {
                    if (rectangle == null) {
                        rectangle = bounds;
                    } else {
                        rectangle.add(bounds);
                    }
                }
            }
            return rectangle;
        }

        @Override
        public boolean contains(double x, double y) {
            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();
                if (realShape != null && realShape.contains(x, y))
                    return true;
            }
            return false;
        }

        @Override
        public boolean contains(Point2D p) {
            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();

                if (realShape != null && realShape.contains(p))
                    return true;
            }
            return false;
        }

        @Override
        public boolean contains(double x, double y, double w, double h) {
            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();
                if (realShape != null && realShape.contains(x, y, w, h))
                    return true;
            }
            return false;
        }

        @Override
        public boolean contains(Rectangle2D r) {
            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();

                if (realShape != null && realShape.contains(r))
                    return true;
            }
            return false;
        }

        @Override
        public boolean intersects(double x, double y, double w, double h) {
            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();
                if (realShape != null && realShape.intersects(x, y, w, h))
                    return true;
            }
            return false;
        }

        @Override
        public boolean intersects(Rectangle2D r) {
            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();
                if (realShape != null && realShape.intersects(r))
                    return true;
            }
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
                Shape realShape = item.getRealShape();
                if (realShape != null) {
                    pathShape.append(realShape, false);
                }
            }

            return pathShape;
        }

        public Area getArea(AffineTransform transform) {
            updateScalingFactor(transform);

            double growingSize = getHandleSize() * 2.0 / scalingFactor;
            Stroke boundingStroke = new BasicStroke((float) growingSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

            Area pathBoundingArea = new Area();

            for (BasicShape item : shapeList) {

                // Note : if shape is invalid, like a path with an odd number of curves, creating a new Area involves a
                // "java.lang.InternalError". Because trapping the exception is too much time consuming it's the user
                // responsibility of this not to happen

                Shape strokedArea = null;
                try {
                    Shape realShape = item.getRealShape();
                    if (realShape != null) {
                        Shape strokedShape = boundingStroke.createStrokedShape(realShape);
                        strokedArea = new Area(strokedShape);
                    }

                } catch (Throwable e) {
                    e.printStackTrace();
                    System.err.println("This graphic is not drawable, it is deleted");
                    // When the Shape cannot be drawn, the graphic is deleted.
                    fireRemoveAction();
                }
                if (strokedArea != null) {
                    pathBoundingArea.add(new Area(strokedArea));
                }
            }
            return pathBoundingArea;
        }
    }
}
