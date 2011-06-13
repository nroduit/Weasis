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
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;

import javax.media.jai.PlanarImage;
import javax.media.jai.iterator.RectIter;
import javax.media.jai.iterator.RectIterFactory;

import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.graphic.model.GraphicsPane;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class AbstractDragGraphic.
 * 
 * @author Nicolas Roduit
 */
public abstract class AbstractDragGraphicOld implements Graphic, Cloneable {

    protected float lineThickness;
    protected Paint paint;
    protected boolean filled;
    // private transient AbstractLayer layer;
    protected transient PropertyChangeSupport pcs;
    protected transient boolean selected;
    protected transient boolean showLabel;
    protected transient boolean createPoints = true;
    protected transient Stroke stroke;
    // protected transient AffineTransform affineTransform;
    // protected transient Shape transformedShape;
    protected transient Shape shape;
    private transient String[] labels;

    protected transient Double labelHeight = null;

    protected transient GraphicLabel graphicLabel = null;

    /**
     * The Class DefaultDragSequence.
     * 
     * @author Nicolas Roduit
     */
    protected class DefaultDragSequence implements DragSequence {

        @Override
        public void startDrag(MouseEventDouble mouseevent) {
            update(mouseevent);
        }

        @Override
        public void drag(MouseEventDouble mouseevent) {
            int deltaX = mouseevent.getX() - getLastX();
            int deltaY = mouseevent.getY() - getLastY();

            if (deltaX != 0 || deltaY != 0) {
                boundingPointIndex = resizeOnDrawing(getType(), deltaX, deltaY, mouseevent);
                updateShapeOnDrawing(mouseevent); // moved here from resizeOnDrawing calls in subclass by BTJA
                update(mouseevent);
            }
        }

        @Override
        public boolean completeDrag(MouseEventDouble mouseevent) {
            if (createPoints) {
                // closed = true;
                updateShape(); // should not be necessary till it's called in updateshape on drawing
                createPoints = false;
            }

            // FIXME - why do we need to update ??? seems that update is useless ...
            int deltaX = mouseevent.getX() - getLastX();
            int deltaY = mouseevent.getY() - getLastY();
            if (deltaX != 0 || deltaY != 0) {
                System.out.println();
            }

            update(mouseevent);
            return true;
        }

        protected int getType() {
            return boundingPointIndex;
        }

        protected int getLastX() {
            return lastX;
        }

        protected int getLastY() {
            return lastY;
        }

        protected void update(int x, int y) {
            lastX = x;
            lastY = y;
        }

        protected void update(MouseEvent mouseevent) {
            lastX = mouseevent.getX();
            lastY = mouseevent.getY();
        }

        protected int lastX;
        protected int lastY;
        protected int boundingPointIndex;

        protected DefaultDragSequence() {
            this(false, -1);
        }

        protected DefaultDragSequence(boolean flag, int i) {
            // createPoints = flag; // should not be decided here if drag Graphic is finished
            // handle point type
            boundingPointIndex = i;
        }

    }

    public AbstractDragGraphicOld() {
        this.showLabel = true;
    }

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

    // seems never to be used
    // protected ImageLayer getImageLayer(MouseEvent mouseevent) {
    // if (mouseevent != null && mouseevent.getSource() instanceof Image2DViewer) {
    // return ((Image2DViewer) mouseevent.getSource()).getImageLayer();
    // }
    // return null;
    // }
    @Override
    public void toFront() {
        firePropertyChange("toFront", null, this);
    }

    @Override
    public void toBack() {
        firePropertyChange("toBack", null, this);

    }

    @Override
    public void setSelected(boolean flag) {
        if (selected != flag) {
            selected = flag;
            // firePropertyChange("selected", !flag, flag);
            firePropertyChange("bounds", null, shape); //$NON-NLS-1$
        }
    }

    @Override
    public String toString() {
        return getUIName();
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    public void setShape(Shape shape, MouseEvent mouseevent) {
        if (shape != null) {
            Shape oldShape = this.shape;
            this.shape = shape;
            // Fire event to repaint the old shape and the new shape of the graphic
            firePropertyChange("bounds", oldShape, shape); //$NON-NLS-1$
        }
    }

    protected void buildLabelBound(DefaultView2d view2d) {
        if (showLabel && labels != null && view2d != null) {

            Rectangle2D longestBound = null;
            for (String l : labels) {
                Font defaultFont = view2d.getEventManager().getViewSetting().getFont();
                Rectangle2D bound =
                    defaultFont.getStringBounds(l, ((Graphics2D) view2d.getGraphics()).getFontRenderContext());
                // Find out the longest labels
                if (longestBound == null || bound.getWidth() > longestBound.getWidth()) {
                    longestBound = bound;
                }
            }
            if (longestBound == null) {
                graphicLabel = null;
                labelHeight = null;
            } else {
                labelHeight = longestBound.getHeight();
                Rectangle rect = shape.getBounds();
                if (graphicLabel == null) {
                    graphicLabel = new GraphicLabel();

                }
                // Rectangle oldBound = graphicLabel.getBound();
                // graphicLabel.setLabelBound(rect.x + rect.width, rect.y + rect.height * 0.5,
                // longestBound.getWidth() + 6, labelHeight * labels.length + 6);
                //                firePropertyChange("graphicLabel", oldBound, graphicLabel); //$NON-NLS-1$
            }
        }
    }

    @Override
    public GraphicLabel getGraphicLabel() {
        return graphicLabel;
    }

    @Override
    public Shape getShape() {
        return shape;
    }

    public int getHandleSize() {
        // if (layer.getSettingsData() == null) {
        return 6;
        // }
        // else {
        // return layer.getSettingsData().getDis_handleDisplaySize();
        // }

    }

    public void setPaint(Paint paint1) {
        paint = paint1;
        // firePropertyChange("paint", paint2, paint1);
        firePropertyChange("bounds", null, shape); //$NON-NLS-1$
    }

    public Paint getPaint() {
        return paint;
    }

    public void setFilled(boolean flag) {
        if (filled != flag) {
            filled = flag;
            firePropertyChange("bounds", null, shape); //$NON-NLS-1$
            // firePropertyChange("selected", !flag, flag);
        }
    }

    public boolean isFilled() {
        return filled;
    }

    protected void updateShape() {
        firePropertyChange("bounds", null, shape); //$NON-NLS-1$
        // firePropertyChange("shape", null, shape);
    }

    // public void repaint() {
    // AbstractLayer layer1 = getLayer();
    // if (layer1 != null) {
    // layer1.repaint(getAllShapeRepaintBounds());
    // }
    // }

    protected void updateStroke() {
        stroke = new BasicStroke(lineThickness, BasicStroke.CAP_BUTT, BasicStroke.CAP_ROUND);
    }

    public float getLineThickness() {
        return lineThickness;
    }

    public Point getLabelPosition(Shape shape) {
        if (labelHeight != null) {
            Rectangle rect = shape.getBounds();
            return new Point(rect.x + 3 + rect.width, (int) (rect.y + 6 + rect.height / 2 - labelHeight * labels.length
                * 0.5));
        }
        return null;
    }

    @Override
    public void paint(Graphics2D g2d, AffineTransform transform) {
        Shape shape = getShape();
        if (shape != null) {
            g2d.setPaint(getPaint());
            g2d.setStroke(stroke);
            Shape transformedShape = transform == null ? shape : transform.createTransformedShape(shape);
            if (isFilled()) {
                // draw filled shape
                g2d.fill(transformedShape);
            }
            // draw outline shape
            g2d.draw(transformedShape);
            if (selected && !createPoints) {
                paintHandles(g2d, transform);
            }
            paintLabel(g2d, transform);
        }
    }

    @Override
    public void paintLabel(Graphics2D g2d, AffineTransform transform) {
        if (showLabel && labels != null) {
            if (graphicLabel != null && labelHeight != null) {
                // Rectangle2D labelBound = graphicLabel.getLabelBounds();
                Rectangle2D labelBound = graphicLabel.getBounds(transform);
                Point2D.Double p = new Point2D.Double(labelBound.getX(), labelBound.getY());
                transform.transform(p, p);
                // p.x += graphicLabel.getOffsetX();
                // p.y += graphicLabel.getOffsetY();
                for (int i = 0; i < labels.length; i++) {
                    paintFontOutline(g2d, labels[i], (float) (p.x + 3), (float) (p.y + labelHeight * (i + 1)));
                }
                // Test, show bound to repaint
                // g2d.drawRect((int) p.x, (int) p.y, (int) labelBound.getWidth(), (int) labelBound.getHeight());
            }
        }
    }

    public void paintFontOutline(Graphics2D g2, String str, float x, float y) {
        g2.setPaint(Color.BLACK);
        g2.drawString(str, x - 1f, y - 1f);
        g2.drawString(str, x - 1f, y);
        g2.drawString(str, x - 1f, y + 1f);
        g2.drawString(str, x, y - 1f);
        g2.drawString(str, x, y + 1f);
        g2.drawString(str, x + 1f, y - 1f);
        g2.drawString(str, x + 1f, y);
        g2.drawString(str, x + 1f, y + 1f);
        g2.setPaint(Color.WHITE);
        g2.drawString(str, x, y);
    }

    protected Stroke getStroke() {
        return stroke;
    }

    /*
     * (non-Javadoc) draw the handles, when the graphic is selected
     * 
     * @see org.weasis.core.ui.graphic.Graphic#paintHandles(java.awt.Graphics2D, java.awt.geom.AffineTransform)
     */
    public void paintHandles(Graphics2D graphics2d, AffineTransform transform) {
        Rectangle rect = getBounds();
        graphics2d.setPaint(Color.black);
        int numPoints = 8;
        int i = getHandleSize();
        int j = i / 2;
        float x = rect.x;
        float y = rect.y;
        float w = x + rect.width;
        float h = y + rect.height;
        float mw = x + rect.width / 2.0f;
        float mh = y + rect.height / 2.0f;

        float[] dstPts = new float[] { x, y, mw, y, w, y, x, mh, w, mh, x, h, mw, h, w, h };
        if (transform != null) {
            transform.transform(dstPts, 0, dstPts, 0, numPoints);
        }
        int k = 0;
        for (int l = numPoints * 2; k < l; k++) {
            graphics2d.fill(new Rectangle2D.Float(dstPts[k] - j, dstPts[++k] - j, i, i));
        }

        k = 0;
        graphics2d.setPaint(Color.white);
        graphics2d.setStroke(new BasicStroke(1.0f));
        for (int l = numPoints * 2; k < l; k++) {
            graphics2d.draw(new Rectangle2D.Float(dstPts[k] - j, dstPts[++k] - j, i, i));
        }
    }

    public void setLineThickness(float f) {
        lineThickness = f;
        updateStroke();
        updateShape();
    }

    @Override
    public boolean intersects(Rectangle rectangle) {
        return getArea().intersects(rectangle);
    }

    @Override
    public boolean intersects(Rectangle rectangle, AffineTransform transform) {
        return getArea(transform).intersects(rectangle);
    }

    /*
     * return the rectangle that corresponds to the bounding box of the graphic (when is selected it is the bounding box
     * of handles)
     */
    @Override
    public Rectangle getRepaintBounds() {
        return getRepaintBounds(shape);
    }

    public Rectangle getRepaintBounds(Shape shape) {
        if (shape == null)
            return null;
        Rectangle rectangle = shape.getBounds();
        growHandles(rectangle);
        return rectangle;
    }

    public void growHandles(Rectangle rectangle) {
        int i = getHandleSize();
        int thick = (int) Math.ceil(lineThickness);
        if (thick > i) {
            i = thick;
        }
        // Add 2 pixels tolerance to ensure that the graphic is correctly repainted
        i += 4;
        rectangle.width += i;
        rectangle.height += i;
        i /= 2;
        rectangle.x -= i;
        rectangle.y -= i;
    }

    public Rectangle getTransformedBounds(AffineTransform affineTransform) {
        return getTransformedBounds(shape, affineTransform);
    }

    @Override
    public Rectangle getTransformedBounds(Shape shape, AffineTransform affineTransform) {
        // Get the bounds of the transformed shape + the handles.
        if (affineTransform == null)
            return getRepaintBounds(shape);
        Rectangle rectangle = affineTransform.createTransformedShape(shape).getBounds();
        growHandles(rectangle);
        return rectangle;
    }

    @Override
    public Rectangle getTransformedBounds(GraphicLabel label, AffineTransform transform) {
        return (label != null) ? label.getTransformedBounds(transform).getBounds() : null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.core.ui.graphic.Graphic#getBounds()
     */
    @Override
    public Rectangle getBounds() {
        return getBounds(null);
    }

    @Override
    public Rectangle getBounds(AffineTransform affineTransform) {
        Shape shape1 = getShape();
        if (shape1 == null)
            return null;
        // bound is not accurate with complex shape (it is true for rectangle or ellipse with rotation)
        Rectangle bound = shape1.getBounds();
        if (bound.width < 1) {
            bound.width = 1;
        }
        if (bound.height < 1) {
            bound.height = 1;
        }
        return bound;
    }

    public DragSequence createDragSequence(DragSequence dragsequence, MouseEvent mouseevent) {
        int i = 1;

        if (mouseevent != null && (dragsequence != null || (i = getResizeCorner(mouseevent)) == -1))
            return createMoveDrag(dragsequence, mouseevent);
        else
            // let drag the handle i to resize the graphic
            return createResizeDrag(mouseevent, i);
    }

    // Start a drag sequence to move the graphic
    protected DragSequence createMoveDrag(DragSequence dragsequence, MouseEvent mouseevent) {
        return new DefaultDragSequence();
    }

    protected DragSequence createResizeDrag(MouseEvent mouseevent, int i) {
        // let drag the handle i to resize the graphic
        return new DefaultDragSequence((mouseevent == null), i);
    }

    @Override
    public Area getArea() {
        return new Area(getShape());
    }

    public Area getArea(MouseEvent mouseevent) {
        return getArea();
    }

    @Override
    public Area getArea(AffineTransform transform) {
        return getArea();
    }

    public int getResizeCorner(MouseEvent mouseevent) {
        // return the selected handle point position
        /*
         * 1 2 3 8 4 7 6 5
         */
        final Point pos = mouseevent.getPoint();
        Rectangle rect = getBounds();
        int k = getHandleSize() + 2;
        AffineTransform affineTransform = getAffineTransform(mouseevent);
        if (affineTransform != null) {
            // Enable to get a better selection of the handle with a low or high magnification zoom
            double scale = affineTransform.getScaleX();
            k = (int) Math.ceil(k / scale + 1);
        }

        int l = k / 2;
        int i = pos.x - rect.x + l;
        int j = pos.y - rect.y + l;
        int i1 = -1;
        if (i >= 0) {
            if (i < k) {
                i1 = 1;
            } else if (i >= rect.width / 2 && i < rect.width / 2 + k) {
                i1 = 2;
            } else if (i >= rect.width && i < rect.width + k) {
                i1 = 3;
            }
        }
        if (i1 != -1 && j >= 0) {
            if (j >= rect.height / 2 && j < rect.height / 2 + k) {
                if (i1 == 2)
                    return -1;
                i1 = i1 == 1 ? 0 : 4;
            } else if (j >= rect.height && j < rect.height + k) {
                i1 = i1 == 1 ? 7 : i1 == 2 ? 6 : 5;
            } else if (j >= k) {
                i1 = -1;
            }
        } else {
            i1 = -1;
        }
        return i1;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        AbstractDragGraphicOld abstractGraphic = null;
        try {
            abstractGraphic = (AbstractDragGraphicOld) super.clone();
        } catch (CloneNotSupportedException clonenotsupportedexception) {
            return null; // never happen
        }
        abstractGraphic.pcs = null;
        abstractGraphic.selected = false;
        abstractGraphic.filled = filled;

        return abstractGraphic;
    }

    // protected abstract int resizeOnDrawing(int i, int j, int k, MouseEvent mouseevent);
    protected abstract int resizeOnDrawing(int boundingPointIndex, int deltaX, int deltaY, MouseEvent mouseEvent);

    public abstract Graphic clone(int xPos, int yPos);

    protected abstract void updateShapeOnDrawing(MouseEvent mouseevent);

    public void move(int i, int j, int k, MouseEvent mouseevent) {
        // WeasisWin.getInstance().getImageCanvas().moveOrigin(j, k);
    }

    public Point needToMoveCanvas(int x, int y) {
        // WeasisWin imageFrame = WeasisWin.getInstance();
        // if (imageFrame.getProjectSettingsData().isDis_autoMove()) {
        // return null;
        // // return imageFrame.getImageCanvas().stepToMoveCanvas(getRepaintBounds(), x, y);
        // }
        // else {
        return null;
        // }
    }

    // moved
    // protected int adjustBoundsForResize(Rectangle rectangle, int i, int j, int k) {
    // switch (i) {
    // // handle points
    // /*
    // * 1=NW 2=N 3=NE 0=W 4=E 7=SW 6=S 5=SE
    // */
    // case 0:
    // rectangle.x += j;
    // rectangle.width -= j;
    // break;
    // case 1:
    // rectangle.x += j;
    // rectangle.y += k;
    // rectangle.width -= j;
    // rectangle.height -= k;
    // break;
    // case 2:
    // rectangle.y += k;
    // rectangle.height -= k;
    // break;
    // case 3:
    // rectangle.width += j;
    // rectangle.y += k;
    // rectangle.height -= k;
    // break;
    // case 4:
    // rectangle.width += j;
    // break;
    // case 5:
    // rectangle.width += j;
    // rectangle.height += k;
    // break;
    // case 6:
    // rectangle.height += k;
    // break;
    // case 7:
    // rectangle.x += j;
    // rectangle.height += k;
    // rectangle.width -= j;
    // break;
    // }
    // int l = 0;
    // if (rectangle.width < 0) {
    // rectangle.x += rectangle.width;
    // rectangle.width = -rectangle.width;
    // l |= 1;
    // }
    // if (rectangle.height < 0) {
    // rectangle.y += rectangle.height;
    // rectangle.height = -rectangle.height;
    // l |= 2;
    // }
    // return l;
    // }

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
            pcs.firePropertyChange("move", null, this); //$NON-NLS-1$
        }
    }

    @Override
    public void fireRemoveAction() {
        if (pcs != null) {
            pcs.firePropertyChange("remove", null, this); //$NON-NLS-1$
        }
    }

    public void fireRemoveAndRepaintAction() {
        if (pcs != null) {
            pcs.firePropertyChange("remove.repaint", null, this); //$NON-NLS-1$
        }
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (pcs == null) {
            pcs = new PropertyChangeSupport(this);
        }
        // Do not add if already exists
        for (PropertyChangeListener listener : pcs.getPropertyChangeListeners()) {
            if (listener == propertychangelistener)
                return;
        }
        pcs.addPropertyChangeListener(propertychangelistener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (pcs != null) {
            pcs.removePropertyChangeListener(propertychangelistener);
        }
    }

    public boolean isCreatePoints() {
        return createPoints;
    }

    @Override
    public String[] getLabel() {
        return labels;
    }

    @Override
    public void setLabel(String[] label, DefaultView2d view2d) {
        this.labels = label;
        buildLabelBound(view2d);
    }

    protected ArrayList<Integer> getValueFromArea(PlanarImage imageData) {
        if (imageData == null || shape == null)
            return null;
        Area area = new Area(shape);
        Rectangle bound = area.getBounds();
        bound = imageData.getBounds().intersection(bound);
        if (bound.width == 0 || bound.height == 0)
            return null;
        RectIter it;
        try {
            it = RectIterFactory.create(imageData, bound);
        } catch (Exception ex) {
            it = null;
        }
        ArrayList<Integer> list = null;

        if (it != null) {
            int band = imageData.getSampleModel().getNumBands();
            list = new ArrayList<Integer>();
            int[] c = { 0, 0, 0 };
            it.startBands();
            it.startLines();
            int y = bound.y;
            while (!it.finishedLines()) {
                it.startPixels();
                int x = bound.x;
                while (!it.finishedPixels()) {
                    if (shape.contains(x, y)) {
                        it.getPixel(c);
                        for (int i = 0; i < band; i++) {
                            list.add(c[i]);
                        }
                    }
                    it.nextPixel();
                    x++;
                }
                it.nextLine();
                y++;
            }
        }
        return list;
    }

    @Override
    public Rectangle getRepaintBounds(AffineTransform transform) {
        // TODO Auto-generated method stub
        return null;
    }
}
