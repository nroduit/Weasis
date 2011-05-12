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
import java.awt.geom.NoninvertibleTransformException;
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
 * @author Nicolas Roduit
 */

public abstract class AbstractDragGraphic implements Graphic, Cloneable {

    protected PropertyChangeSupport pcs;

    // protected Shape shape = new Rectangle();
    protected Shape shape;
    protected Shape unTransformedShape;
    protected Stroke stroke;
    protected Paint paint = Color.YELLOW;
    protected float lineThickness = 1.0f;
    protected int handleSize = 6; // ????

    protected boolean isFilled = false;
    protected boolean isSelected = false;
    protected boolean isLabelVisible = false;
    protected boolean isGraphicComplete = false;

    protected GraphicLabel graphicLabel = null;

    protected int handlePointTotalNumber;
    protected List<Point2D> handlePointList;

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public AbstractDragGraphic(int handlePointTotalNumber) {
        this.handlePointTotalNumber = handlePointTotalNumber;
        this.handlePointList = new ArrayList<Point2D>(handlePointTotalNumber);
    }

    public AbstractDragGraphic(float lineThickness, Color paint, boolean fill) {
        this(1);
        setLineThickness(lineThickness);
        setPaint(paint);
        setFilled(fill);
        updateStroke();
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Shape getShape() {
        return shape;
    }

    public Stroke getStroke() {
        return stroke;
    }

    public Paint getPaint() {
        return paint;
    }

    public float getLineThickness() {
        return lineThickness;
    }

    public int getHandleSize() {
        return handleSize;
    }

    public boolean isFilled() {
        return isFilled;
    }

    @Override
    public boolean isSelected() {
        return isSelected;
    }

    public boolean isLabelVisible() {
        return isLabelVisible;
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
        return isGraphicComplete = handlePointList.size() == handlePointTotalNumber;
    }

    @Override
    public String getDescription() {
        return "AbstractDragGraphic";
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected Area getBoundingRectAreaOfSegment(Point2D A, Point2D B) {

        double size = 2 * getHandleSize();

        Path2D path = new Path2D.Double();

        Point2D tPoint = GeomUtil.getPerpendicularPointFromLine(A, B, -size, size);
        path.moveTo(tPoint.getX(), tPoint.getY());

        tPoint = GeomUtil.getPerpendicularPointFromLine(A, B, -size, -size);
        path.lineTo(tPoint.getX(), tPoint.getY());

        tPoint = GeomUtil.getPerpendicularPointFromLine(B, A, -size, size);
        path.lineTo(tPoint.getX(), tPoint.getY());

        tPoint = GeomUtil.getPerpendicularPointFromLine(B, A, -size, -size);
        path.lineTo(tPoint.getX(), tPoint.getY());

        path.closePath();

        return new Area(path);
    }

    // protected Area getBoundingRectSegment2(Point2D A, Point2D B) {
    //
    // double halfHandleSize = getHandleSize() / 2;
    // Path2D path = new Path2D.Double();
    //
    // double dAB = A.distance(B);
    // double dxu = B.getX() - A.getX() / dAB;
    // double dyu = B.getY() - A.getY() / dAB;
    //
    // AffineTransform t1, t2, t3, t4;
    // Point2D tPoint;
    //
    // t1 = AffineTransform.getTranslateInstance(-dyu * halfHandleSize, dxu * halfHandleSize);// rot +90° CW
    // t2 = AffineTransform.getTranslateInstance(dyu * halfHandleSize, -dxu * halfHandleSize); // rot -90° CCW
    // t3 = AffineTransform.getTranslateInstance(-dxu * halfHandleSize, 0);
    // t4 = AffineTransform.getTranslateInstance(dxu * halfHandleSize, 0);
    //
    // tPoint = t1.transform(A, null);
    // tPoint = t3.transform(tPoint, tPoint);
    // path.moveTo(tPoint.getX(), tPoint.getY());
    //
    // tPoint = t2.transform(A, null);
    // tPoint = t3.transform(tPoint, tPoint);
    // path.lineTo(tPoint.getX(), tPoint.getY());
    //
    // tPoint = t2.transform(B, null);
    // tPoint = t4.transform(tPoint, tPoint);
    // path.lineTo(tPoint.getX(), tPoint.getY());
    //
    // tPoint = t1.transform(B, null);
    // tPoint = t4.transform(tPoint, tPoint);
    // path.lineTo(tPoint.getX(), tPoint.getY());
    //
    // path.closePath();
    //
    // return new Area(path);
    // }

    @Override
    public Area getArea() {
        AffineTransform transform = null;
        return getArea(transform);
    }

    public Area getArea(AffineTransform transform) {
        if (getShape() == null)
            return null;

        double flatness = 5;

        // Path2D pathBoundingShape = new Path2D.Double(Path2D.WIND_NON_ZERO); // only for debug drawing view
        Area pathBoundingArea = new Area();

        AffineTransform inverseTransform = null;
        try {
            inverseTransform = transform.createInverse();
        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
        }

        // Shape transformedShape = transform == null ? shape : transform.createTransformedShape(shape);
        // PathIterator pit = transformedShape.getPathIterator(null, flatness);
        PathIterator pit = shape.getPathIterator(transform, flatness);

        double coords[] = new double[6];
        Point2D startPoint = null, prevPoint = null, lastPoint = null;

        while (!pit.isDone()) {

            if (pit.currentSegment(coords) == PathIterator.SEG_MOVETO) {
                lastPoint = startPoint = new Point2D.Double(coords[0], coords[1]);
            } else {
                switch (pit.currentSegment(coords)) {
                    case PathIterator.SEG_LINETO:
                        lastPoint = new Point2D.Double(coords[0], coords[1]);
                        break;
                    case PathIterator.SEG_QUADTO:
                        lastPoint = new Point2D.Double(coords[2], coords[3]);
                        break;
                    case PathIterator.SEG_CUBICTO:
                        lastPoint = new Point2D.Double(coords[4], coords[5]);
                        break;
                    case PathIterator.SEG_CLOSE:
                        prevPoint = startPoint;
                        break;
                }

                Area boundingRectArea = getBoundingRectAreaOfSegment(prevPoint, lastPoint);
                pathBoundingArea.add(boundingRectArea);
                // pathBoundingShape.append(boundingRectArea, false);
            }
            prevPoint = (Point2D) lastPoint.clone();
            pit.next();
        }

        // add handles Area, necesssary ?????
        // double size = getHandleSize();
        // double halfSize = size / 2;
        //
        // Point2D.Double[] handlePtArray = new Point2D.Double[handlePointList.size()];
        // for (int i = 0; i < handlePointList.size(); i++) {
        // handlePtArray[i] = new Point2D.Double(handlePointList.get(i).getX(), handlePointList.get(i).getY());
        // }
        //
        // transform.transform(handlePtArray, 0, handlePtArray, 0, handlePtArray.length);
        // for (Point2D point : handlePtArray) {
        // Rectangle rectHandle =
        // new Rectangle((int) Math.ceil(point.getX() - halfSize), (int) Math.ceil(point.getY() - halfSize),
        // (int) Math.ceil(size), (int) Math.ceil(size));
        // // growHandles(rectHandle);
        // pathBoundingArea.add(new Area(rectHandle));
        // }

        // add label Area
        // if (isLabelVisible && graphicLabel != null) {
        // pathBoundingArea.add(new Area(transform.createTransformedShape(graphicLabel.getBound())));
        // }
        pathBoundingArea.transform(inverseTransform);

        // updateUnTransformedDrawingShape(transform);
        // if (unTransformedShape != null) {
        // pathBoundingArea.add(new Area(unTransformedShape));
        // }

        return pathBoundingArea;
    }

    public Area getArea(MouseEvent mouseEvent) {
        AffineTransform transform = getAffineTransform(mouseEvent);
        return getArea(transform);
    }

    @Override
    public Rectangle getBounds() {
        if (shape == null)
            return null;
        // return shape.getBounds2D().getBounds();
        // return getRepaintBounds();

        Rectangle bound = shape.getBounds();
        bound.grow(bound.width < 1 ? 1 : 0, bound.height < 1 ? 1 : 0);

        // if (bound.width < 1) {
        // bound.width = 1;
        // }
        // if (bound.height < 1) {
        // bound.height = 1;
        // }

        return bound;
    }

    @Override
    public Rectangle getRepaintBounds() {
        return getRepaintBounds(shape);
    }

    public Rectangle getRepaintBounds(Shape shape) {
        if (shape == null)
            return null;

        Rectangle rectangle = shape.getBounds();
        // Rectangle rectangle = shape.getBounds2D().getBounds();
        // if (unTransformedShape != null) {
        // rectangle.union(unTransformedShape.getBounds());
        // }
        growHandles(rectangle);
        return rectangle;
    }

    @Override
    public Rectangle getTransformedBounds(Shape shape, AffineTransform affineTransform) {
        if (affineTransform == null)
            return getRepaintBounds(shape);

        Rectangle rectangle = affineTransform.createTransformedShape(shape).getBounds();

        // updateUnTransformedDrawingShape(affineTransform);
        // Shape shapeTemp = affineTransform.createTransformedShape(unTransformedShape);
        // if (shapeTemp != null) {
        // rectangle = rectangle.union(shapeTemp.getBounds());
        // }
        growHandles(rectangle);

        return rectangle;

    }

    /**
     * Add 2 pixels tolerance to ensure that the graphic is correctly repainted
     */

    public void growHandles(Rectangle rectangle) {
        // int growingValue = (int) Math.floor(Math.max(handleSize, (int) Math.ceil(lineThickness)) / 2.0) + 2;
        int growingValue = 2 + (Math.max((int) Math.ceil(handleSize * 1.5), (int) Math.ceil(lineThickness)) >> 1);

        // int growingValue = 50 + Math.max(handleSize, (int) Math.ceil(lineThickness)) >> 1; // faster
        // !!!!! 50 is a workaround for unTransformedShape not correctly computed with getRepaintBound
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

    @Override
    public boolean intersects(Rectangle rectangle) {
        return getArea().intersects(rectangle);
    }

    @Override
    public boolean intersects(Rectangle rectangle, AffineTransform transform) {
        return getArea(transform).intersects(rectangle);
    }

    @Override
    public void showProperties() {
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public void setLineThickness(float f) {
        lineThickness = f;
        updateStroke();
        updateShape();
    }

    @Override
    public void setSelected(boolean flag) {
        if (isSelected != flag) {
            isSelected = flag;
            firePropertyChange("bounds", null, shape);
        }
    }

    public void setShape(Shape shape, MouseEvent mouseevent) {
        if (shape != null) {
            Shape oldShape = this.shape;
            this.shape = shape;
            firePropertyChange("bounds", oldShape, shape);
        }
    }

    public void setPaint(Paint paint1) {
        paint = paint1;
        firePropertyChange("bounds", null, shape);
    }

    public void setFilled(boolean flag) {
        if (isFilled != flag) {
            isFilled = flag;
            firePropertyChange("bounds", null, shape);
        }
    }

    public void setLabelVisible(boolean isLabelVisible) {
        this.isLabelVisible = isLabelVisible;
    }

    @Override
    public void setLabel(String[] labels, Graphics2D g2d) {
        if (isLabelVisible && g2d != null) {

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
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateShape() {
        firePropertyChange("bounds", null, shape);
    }

    protected void updateUnTransformedDrawingShape(AffineTransform transform) {

    }

    protected void updateStroke() {
        stroke = new BasicStroke(lineThickness, BasicStroke.CAP_BUTT, BasicStroke.CAP_ROUND);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void paint(Graphics2D g2d, AffineTransform transform) {
        Shape shape = getShape();

        if (shape != null) {
            g2d.setPaint(getPaint());
            g2d.setStroke(stroke);
            Shape transformedShape = transform == null ? shape : transform.createTransformedShape(shape);
            if (isFilled()) {
                g2d.fill(transformedShape);
            }

            g2d.draw(transformedShape);

            updateUnTransformedDrawingShape(transform);
            Shape shapeTest = transform.createTransformedShape(unTransformedShape);
            if (shapeTest != null) {
                g2d.draw(shapeTest);
            }

            // if (isSelected && !isGraphicComplete)
            if (isSelected) {
                paintHandles(g2d, transform);
            }

            paintLabel(g2d, transform);

            // paint transformedBounds
            // g2d.setPaint(Color.CYAN);
            // Rectangle rectangle = transform.createTransformedShape(shape).getBounds();
            // if (shapeTest != null) {
            // rectangle = rectangle.union(shapeTest.getBounds());
            // }
            // growHandles(rectangle);
            // g2d.draw(rectangle);

            // Area boundingArea = getArea(transform);
            // g2d.setPaint(Color.red);
            // g2d.draw(transform.createTransformedShape(boundingArea));

            // g2d.setPaint(Color.blue);
            // g2d.draw(transform == null ? pathBoundingShape : transform.createTransformedShape(pathBoundingShape));
            // g2d.draw(pathBoundingShape);

            // g2d.setPaint(Color.green);
            // g2d.draw(transform == null ? testEnclosingArea : transform.createTransformedShape(testEnclosingArea));
        }
    }

    protected Area testEnclosingArea = new Area();

    @Override
    public void paintLabel(Graphics2D g2d, AffineTransform transform) {
        if (isLabelVisible && graphicLabel != null) {
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

            if (!isGraphicComplete) {
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

            if (!isGraphicComplete) {
                isGraphicComplete = handlePointList.size() == handlePointTotalNumber;
            }

            int lastPointIndex = handlePointList.size() - 1;
            Point2D lastPoint = handlePointList.get(lastPointIndex);
            ListIterator<Point2D> listIt = handlePointList.listIterator(lastPointIndex);

            while (listIt.hasPrevious())
                if (lastPoint.equals(listIt.previous()))
                    return false;

            if (!isGraphicComplete) {
                handlePointList.add(new Point.Double(mouseEvent.getPoint().getX(), mouseEvent.getPoint().getY()));
                handlePointIndex = handlePointList.size() - 1;
            }

            return isGraphicComplete;
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

    protected void fireMoveAction() {
        if (pcs != null) {
            pcs.firePropertyChange("move", null, this);
        }
    }

    @Override
    public void fireRemoveAction() {
        if (pcs != null) {
            pcs.firePropertyChange("remove", null, this);
        }
    }

    public void fireRemoveAndRepaintAction() {
        if (pcs != null) {
            pcs.firePropertyChange("remove.repaint", null, this);
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
        newGraphic.graphicLabel = null;
        newGraphic.isSelected = false;
        newGraphic.handlePointList = new ArrayList<Point2D>(handlePointTotalNumber);

        return newGraphic;
    }

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

    public abstract Graphic clone(int xPos, int yPos);

}
