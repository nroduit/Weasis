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
import java.awt.Robot;
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
import java.util.Map;
import java.util.TreeMap;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.graphic.AbstractDragGraphic.AdvancedShape.BasicShape;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.GraphicsListener;
import org.weasis.core.ui.graphic.model.Tools;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class AbstractDragGraphic.
 * 
 * @author Nicolas Roduit,Benoit Jacquemoud
 */

public abstract class AbstractDragGraphic implements Graphic {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractDragGraphic.class);

    public static final int UNDEFINED = -1;

    protected PropertyChangeSupport pcs;

    protected Shape shape;

    protected int handlePointTotalNumber;
    protected List<Point2D> handlePointList;

    protected int handleSize = 6;
    protected int selectionSize = 10;

    protected Paint colorPaint;
    protected float lineThickness;
    protected boolean filled;
    protected boolean labelVisible;
    protected GraphicLabel graphicLabel;
    protected boolean selected = false;

    private boolean resizingOrMoving = false;
    private final boolean variablePointsNumber;

    private int layerID = Tools.TEMPDRAGLAYER.getId();

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
        this.variablePointsNumber = handlePointTotalNumber == UNDEFINED;
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

    public boolean isVariablePointsNumber() {
        return variablePointsNumber;
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

    @Override
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

    public void forceToAddPoints(int fromPtIndex) {
        if (variablePointsNumber && fromPtIndex >= 0 && fromPtIndex < handlePointList.size()) {
            if (fromPtIndex < handlePointList.size() - 1) {
                List<Point2D> list = handlePointList.subList(fromPtIndex + 1, handlePointList.size());
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
            updateShapeOnDrawing(mouseEvent);
            return pt;
        }
        return null;
    }

    public Point2D getHandlePoint(int index) {
        Point2D handlePoint = null;
        if (index >= 0 && index < handlePointList.size()) {
            if ((handlePoint = handlePointList.get(index)) != null) {
                handlePoint = (Point2D) handlePoint.clone();
            }
        }
        return handlePoint;
    }

    public List<Point2D> getHandlePointList() {
        List<Point2D> handlePointListcopy = new ArrayList<Point2D>(handlePointList.size());

        for (Point2D handlePt : handlePointList) {
            handlePointListcopy.add(handlePt != null ? (Point2D) handlePt.clone() : null);
        }

        return handlePointListcopy;

    }

    public void setHandlePoint(int index, Point2D newPoint) {
        if (index >= 0 && index <= handlePointList.size()) {
            if (index == handlePointList.size()) {
                handlePointList.add(newPoint);
            } else {
                handlePointList.set(index, newPoint);
            }
        }
    }

    public int getHandlePointListSize() {
        return handlePointList.size();
    }

    @Override
    public String getDescription() {
        return "";
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

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @since v1.1.0 - new in Graphic interface
     */

    @Override
    public Area getArea(AffineTransform transform) {
        if (shape == null) {
            return new Area();
        }

        if (shape instanceof AdvancedShape) {
            return ((AdvancedShape) shape).getArea(transform);
        } else {
            double growingSize = Math.max(selectionSize, handleSize);
            growingSize = Math.max(growingSize, lineThickness);
            growingSize /= GeomUtil.extractScalingFactor(transform);

            Stroke boundingStroke = new BasicStroke((float) growingSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

            return new Area(boundingStroke.createStrokedShape(shape));
        }
    }

    public Area getArea(MouseEvent mouseEvent) {
        AffineTransform transform = getAffineTransform(mouseEvent);
        return getArea(transform);
    }

    @Override
    public boolean intersects(Rectangle rectangle, AffineTransform transform) {
        return (rectangle != null) ? getArea(transform).intersects(rectangle) : false;
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

    @Override
    public Rectangle getBounds(AffineTransform transform) {
        if (shape == null) {
            return null;
        }

        if (shape instanceof AdvancedShape) {
            ((AdvancedShape) shape).updateScalingFactor(transform);
        }

        Rectangle2D bounds = shape.getBounds2D();

        double growingSize = lineThickness / 2.0;
        growingSize /= GeomUtil.extractScalingFactor(transform);
        GeomUtil.growRectangle(bounds, growingSize);

        return (bounds != null) ? bounds.getBounds() : null;
    }

    public Rectangle getBounds(MouseEvent mouseEvent) {
        AffineTransform transform = getAffineTransform(mouseEvent);
        return getBounds(transform);
    }

    /**
     * 
     * @return Bounding rectangle which size has to be modified according to the given transform with handle drawings
     *         and lineThikness taken in consideration<br>
     *         This assumes that handle drawing size do not change with different scaling of views. Hence, real
     *         coordinates of bounding rectangle are modified consequently<br>
     * 
     * @since v1.1.0 - new in Graphic interface
     */

    public Rectangle getRepaintBounds(Shape shape, AffineTransform transform) {
        if (shape == null) {
            return null;
        }

        if (shape instanceof AdvancedShape) {
            ((AdvancedShape) shape).updateScalingFactor(transform);
        }

        Rectangle2D bounds = shape.getBounds2D();

        // Add pixel tolerance to ensure that the graphic is correctly repainted
        double growingSize = Math.max(handleSize * 1.5 / 2.0, lineThickness / 2.0) + 2;
        growingSize /= GeomUtil.extractScalingFactor(transform);
        GeomUtil.growRectangle(bounds, growingSize);

        return (bounds != null) ? bounds.getBounds() : null;
    }

    @Override
    public Rectangle getRepaintBounds(AffineTransform transform) {
        return getRepaintBounds(shape, transform);
    }

    public Rectangle getRepaintBounds(MouseEvent mouseEvent) {
        AffineTransform transform = getAffineTransform(mouseEvent);
        return getRepaintBounds(shape, transform);
    }

    /**
     * @return Shape bounding rectangle relative to affineTransform<br>
     *         Handle points bounding rectangles are also included, knowing they have invariant size according to
     *         current view.<br>
     *         Any other invariant sized shape bounding rectangles are included if shape is instanceof AdvancedShape
     */

    @Override
    public Rectangle getTransformedBounds(Shape shape, AffineTransform transform) {
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

    /**
     * @return selected handle point index if exist, otherwise -1
     */
    public int getHandlePointIndex(MouseEventDouble mouseEvent) {

        int nearestHandlePtIndex = -1;
        final Point2D mousePoint = (mouseEvent != null) ? mouseEvent.getImageCoordinates() : null;

        if (mousePoint != null && handlePointList.size() > 0) {
            double minHandleDistance = Double.MAX_VALUE;
            double maxHandleDistance = handleSize * 1.5 / GeomUtil.extractScalingFactor(getAffineTransform(mouseEvent));

            for (int index = 0; index < handlePointList.size(); index++) {
                Point2D handlePoint = handlePointList.get(index);
                double handleDistance = (handlePoint != null) ? mousePoint.distance(handlePoint) : Double.MAX_VALUE;

                if (handleDistance <= maxHandleDistance && handleDistance < minHandleDistance) {
                    minHandleDistance = handleDistance;
                    nearestHandlePtIndex = index;
                }
            }
        }
        return nearestHandlePtIndex;
    }

    public List<Integer> getHandlePointIndexList(MouseEventDouble mouseEvent) {

        Map<Double, Integer> indexByDistanceMap = null;
        final Point2D mousePoint = (mouseEvent != null) ? mouseEvent.getImageCoordinates() : null;

        if (mousePoint != null && handlePointList.size() > 0) {
            double maxHandleDistance = handleSize * 1.5 / GeomUtil.extractScalingFactor(getAffineTransform(mouseEvent));

            for (int index = 0; index < handlePointList.size(); index++) {
                Point2D handlePoint = handlePointList.get(index);
                double handleDistance = (handlePoint != null) ? mousePoint.distance(handlePoint) : Double.MAX_VALUE;

                if (handleDistance <= maxHandleDistance) {
                    if (indexByDistanceMap == null) {
                        indexByDistanceMap = new TreeMap<Double, Integer>();
                    }
                    indexByDistanceMap.put(handleDistance, index);
                }
            }
        }

        return (indexByDistanceMap != null) ? new ArrayList<Integer>(indexByDistanceMap.values()) : null;
    }

    public boolean isOnGraphicLabel(MouseEventDouble mouseevent) {
        if (mouseevent == null) {
            return false;
        }

        final Point2D mousePoint = mouseevent.getImageCoordinates();

        AffineTransform transform = getAffineTransform(mouseevent);
        if (transform != null && labelVisible && graphicLabel != null) {
            Area labelArea = graphicLabel.getArea(transform);
            if (labelArea != null && labelArea.contains(mousePoint)) {
                return true;
            }
        }
        return false;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected DefaultView2d getDefaultView2d(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof DefaultView2d) {
            return (DefaultView2d) mouseevent.getSource();
        }
        return null;
    }

    protected AffineTransform getAffineTransform(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof Image2DViewer) {
            return ((Image2DViewer) mouseevent.getSource()).getAffineTransform();
        }
        return null;
    }

    protected ImageElement getImageElement(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof Image2DViewer) {
            return ((Image2DViewer) mouseevent.getSource()).getImage();
        }
        return null;
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
                // Assuming first shape is the user drawing path, else stands for decoration
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

    public final void setLabel(String[] labels, DefaultView2d view2d, Point2D pos) {
        GraphicLabel oldLabel = (graphicLabel != null) ? graphicLabel.clone() : null;

        if (labels == null || labels.length == 0) {
            graphicLabel = null;
            fireLabelChanged(oldLabel);
        } else if (pos == null) {
            setLabel(labels, view2d);
        } else {
            if (graphicLabel == null) {
                graphicLabel = new GraphicLabel();
            }
            graphicLabel.setLabel(view2d, pos.getX(), pos.getY(), labels);
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

        boolean releasedEvent = false;
        ImageElement imageElement = null;

        if (source instanceof MouseEvent) {
            imageElement = getImageElement((MouseEvent) source);
            releasedEvent = ((MouseEvent) source).getID() == MouseEvent.MOUSE_RELEASED;

        } else if (source instanceof ImageElement) {
            imageElement = (ImageElement) source;
            // When Source is an ImageElement all the measurements are recomputed.
            releasedEvent = true;
        }

        MeasureTool measureToolListener = null;
        boolean isMultiSelection = false; // default is single selection
        AbstractLayerModel model = (view2d != null) ? view2d.getLayerModel() : null;

        if (model != null) {
            ArrayList<Graphic> selectedGraphics = model.getSelectedGraphics();
            isMultiSelection = selectedGraphics.size() > 1;

            if (selectedGraphics.size() == 1 && selectedGraphics.get(0) == this) {
                GraphicsListener[] gfxListeners = model.getGraphicSelectionListeners();
                if (gfxListeners != null) {
                    for (GraphicsListener listener : gfxListeners) {
                        if (listener instanceof MeasureTool) {
                            measureToolListener = (MeasureTool) listener;
                            break;
                        }
                    }
                }
            }
        }

        List<MeasureItem> measList = null;
        String[] labels = null;

        // If isMultiSelection is false, it should return all enable computed measurements when
        // quickComputing is enable or when releasedEvent is true
        if (labelVisible || !isMultiSelection) {
            measList = computeMeasurements(imageElement, releasedEvent);
        }

        if (labelVisible && measList != null && measList.size() > 0) {
            List<String> labelList = new ArrayList<String>(measList.size());

            for (MeasureItem item : measList) {
                if (item != null) {
                    Measurement measurement = item.getMeasurement();

                    if (measurement != null && measurement.isGraphicLabel()) {
                        StringBuilder sb = new StringBuilder();

                        String name = measurement.getName();
                        Double value = item.getValue();
                        String unit = item.getUnit();

                        if (name != null) {
                            sb.append(name).append(" : ");
                            if (value != null) {
                                sb.append(DecFormater.oneDecimalUngroup(value));
                                if (unit != null) {
                                    sb.append(" ").append(unit);
                                }
                            }
                        }
                        labelList.add(sb.toString());
                    }
                }
            }
            if (labelList.size() > 0) {
                labels = labelList.toArray(new String[labelList.size()]);
            }
        }

        setLabel(labels, view2d, pos);

        // update MeasureTool on the fly without calling again getMeasurements
        if (measureToolListener != null) {
            measureToolListener.updateMeasuredItems(isMultiSelection ? null : measList);
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

        g2d.setStroke(oldStroke);

        // // Graphics DEBUG
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
        // // Graphics DEBUG

        g2d.setPaint(oldPaint);

        if (isSelected()) {
            paintHandles(g2d, transform);
        }

        paintLabel(g2d, transform);
    }

    protected boolean isResizingOrMoving() {
        return resizingOrMoving;
    }

    @Override
    public void paintLabel(Graphics2D g2d, AffineTransform transform) {
        if (labelVisible && graphicLabel != null) {
            graphicLabel.paint(g2d, transform, selected);
        }
    }

    protected void paintHandles(Graphics2D g2d, AffineTransform transform) {
        if (!isResizingOrMoving()) {
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
        if (!isGraphicComplete()) {
            return false;
        }

        int lastPointIndex = handlePointList.size() - 1;

        while (lastPointIndex > 0) {
            Point2D checkPoint = handlePointList.get(lastPointIndex);

            ListIterator<Point2D> listIt = handlePointList.listIterator(lastPointIndex--);

            while (listIt.hasPrevious()) {
                if (checkPoint != null && checkPoint.equals(listIt.previous())) {
                    return false;
                }
            }
        }
        return true;

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

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (pcs == null) {
            pcs = new PropertyChangeSupport(this);
        }

        for (PropertyChangeListener listener : pcs.getPropertyChangeListeners()) {
            if (listener == propertychangelistener) {
                return;
            }
        }

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

    @Override
    public int getLayerID() {
        return layerID;
    }

    @Override
    public void setLayerID(int layerID) {
        this.layerID = layerID;
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
    public AbstractDragGraphic clone() {
        AbstractDragGraphic newGraphic = null;
        try {
            newGraphic = (AbstractDragGraphic) super.clone();
        } catch (CloneNotSupportedException clonenotsupportedexception) {
            return null;
        }
        newGraphic.pcs = null;
        newGraphic.shape = null;
        newGraphic.handlePointList = new ArrayList<Point2D>(handlePointTotalNumber > 0 ? handlePointTotalNumber : 10);
        newGraphic.graphicLabel = null;
        newGraphic.selected = false;

        return newGraphic;
    }

    @Override
    public Graphic deepCopy() {
        AbstractDragGraphic newGraphic = null;
        try {
            newGraphic = (AbstractDragGraphic) super.clone();
        } catch (CloneNotSupportedException clonenotsupportedexception) {
            return null;
        }
        newGraphic.pcs = null;
        newGraphic.shape = null;
        newGraphic.handlePointList = new ArrayList<Point2D>(handlePointList.size());
        for (Point2D p : handlePointList) {
            newGraphic.handlePointList.add(p != null ? (Point2D) p.clone() : null);
        }
        newGraphic.graphicLabel = null;
        newGraphic.selected = false;
        newGraphic.updateShapeOnDrawing(null);
        return newGraphic;
    };

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

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

    protected abstract void updateShapeOnDrawing(MouseEventDouble mouseEvent);

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
                if (shape == null || stroke == null) {
                    throw new IllegalArgumentException();
                }
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

                if (anchorPoint == null) {
                    throw new IllegalArgumentException();
                }

                if (scalingMin < 0) {
                    throw new IllegalArgumentException();
                }

                this.anchorPoint = (Point2D) anchorPoint.clone();
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
            if (scalingFactor == 0) {
                throw new IllegalArgumentException("scalingFactor cannot be zero");
            }
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
            if (shapeList.size() > 0 && shapeList.get(0) != null) {
                return shapeList.get(0).getRealShape();
            }
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
                if (realShape != null && realShape.contains(x, y)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean contains(Point2D p) {
            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();

                if (realShape != null && realShape.contains(p)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean contains(double x, double y, double w, double h) {
            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();
                if (realShape != null && realShape.contains(x, y, w, h)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean contains(Rectangle2D r) {
            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();

                if (realShape != null && realShape.contains(r)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean intersects(double x, double y, double w, double h) {
            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();
                if (realShape != null && realShape.intersects(x, y, w, h)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean intersects(Rectangle2D r) {
            for (BasicShape item : shapeList) {
                Shape realShape = item.getRealShape();
                if (realShape != null && realShape.intersects(r)) {
                    return true;
                }
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
