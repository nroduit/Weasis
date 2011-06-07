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
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class PolygonGraphic.
 * 
 * @author Nicolas Roduit
 */
public class PolygonGraphic extends AbstractDragGraphicOld {

    public static final Icon ICON = new ImageIcon(PolygonGraphic.class.getResource("/icon/22x22/draw-polyline.png")); //$NON-NLS-1$
    protected float points[];
    protected int numPoints;
    protected boolean closed;
    protected transient boolean closeShape;

    /**
     * The Class PolygonDragSequence.
     * 
     * @author Nicolas Roduit
     */
    protected class PolygonDragSequence extends AbstractDragGraphicOld.DefaultDragSequence {

        private int point;

        protected PolygonDragSequence() {
        }

        protected PolygonDragSequence(boolean flag, int i) {
            this();
            createPoints = flag;
            if (createPoints) {
                point = 1;
            } else {
                point = i;
            }
        }

        @Override
        public void startDrag(MouseEventDouble mouseevent) {
            update(mouseevent);
        }

        @Override
        public void drag(MouseEventDouble mouseevent) {
            int tx = mouseevent.getX() - getLastX();
            int ty = mouseevent.getY() - getLastY();
            if (tx != 0 || ty != 0) {
                Point p = needToMoveCanvas(tx, ty);
                if (p != null) {
                    tx = p.x;
                    ty = p.y;
                    PolygonGraphic.super.move(0, tx, ty, mouseevent);
                }
                points[point + point] += tx;
                points[point + point + 1] += ty;
                updateShapeOnDrawing(mouseevent);
                update(mouseevent);
            }
        }

        @Override
        public boolean completeDrag(MouseEventDouble mouseevent) {
            if (mouseevent.getID() == 502) {
                if (createPoints) {
                    int i = numPoints + numPoints;
                    float af[] = new float[i + 2];
                    System.arraycopy(points, 0, af, 0, i);
                    af[i] = mouseevent.getX();
                    af[i + 1] = mouseevent.getY();
                    point = numPoints++;
                    points = af;
                    updateShapeOnDrawing(mouseevent);
                    update(mouseevent);
                    return false;
                } else
                    return true;
            }
            if (mouseevent.getID() == 501) {
                if (mouseevent.getClickCount() == 2) {
                    if (numPoints > 3) {
                        int pointToRemove = points[0] == points[2] && points[1] == points[3] ? 4 : 2;
                        int j = (numPoints + numPoints) - pointToRemove;
                        float af1[] = new float[j];
                        System.arraycopy(points, pointToRemove == 4 ? 2 : 0, af1, 0, j);
                        numPoints -= pointToRemove / 2;
                        points = af1;
                        if (closed) {
                            points[0] = points[0] == points[2] ? points[0] - 1f : points[0];
                            points[1] = points[1] == points[3] ? points[1] - 1f : points[1];
                        }
                        closeShape = true;
                        updateShapeOnDrawing(mouseevent);
                        createPoints = false;
                        // comme le drag doit être annulé, pas de mise à jour dans imageDisplay
                        // getLayer().getShowDrawing().oneSelectedGraphicUpdateInterface();
                    }
                    update(mouseevent);
                    return true;
                } else {
                    update(mouseevent);
                    return false;
                }
            } else
                return true;
        }
    }

    public PolygonGraphic(float lineThickness, Color paint, boolean fill, boolean closedPath) {
        setShape(new GeneralPath(), null);
        setPaint(paint);
        this.lineThickness = lineThickness;
        numPoints = 2;
        points = new float[numPoints * 2];
        points[0] = 0.0F;
        points[1] = 0.0F;
        points[2] = 0.0F;
        points[3] = 100F;
        closed = closedPath;
        setFilled(fill);
        updateStroke();
        updateShapeOnDrawing(null);
    }

    public PolygonGraphic(float[] points, float lineThickness, Color paint, boolean fill, boolean closedPath) {
        setShape(new GeneralPath(), null);
        setPaint(paint);
        this.lineThickness = lineThickness;
        numPoints = 2;
        this.points = points;
        closed = closedPath;
        setFilled(fill);
        updateStroke();
        updateShapeOnDrawing(null);
    }

    public void setClosed(boolean flag) {
        closed = flag;
        updateShape();
    }

    // public void setPoints(float[] points) {
    // this.points = points;
    // this.numPoints = points.length / 2;
    // updateShapeOnDrawing(affineTransform);
    // }

    // @Override
    // public void setLayer(AbstractLayer layer) {
    // // redéfinition si le graph est deleter, on ferme le graph (remplace double clique), il faut quand même
    // // recliquer
    // // une
    // // fois pour la dragSequence retourne false
    // if (layer == null) {
    // createPoints = false;
    // }
    // super.setLayer(layer);
    // }

    public boolean isClosed() {
        return closed;
    }

    public float[] getPoints() {
        return points;
    }

    @Override
    public Area getArea() {
        Area area = new Area(getShape());
        if (area.isEmpty()) {
            float x1 = points[0];
            float y1 = points[1];
            float x2 = points[0];
            float y2 = points[1];
            for (int m = 2; m < points.length; m = m + 2) {
                if (points[m] < x1) {
                    x1 = points[m];
                }
                if (points[m + 1] < y1) {
                    y1 = points[m + 1];
                }
                if (points[m] > x2) {
                    x2 = points[m];
                }
                if (points[m + 1] > y2) {
                    y2 = points[m + 1];
                }
            }
            area = createAreaForLine(x1, y1, x2, y2, getHandleSize());
        }
        return area;
    }

    public static Area createAreaForLine(float x1, float y1, float x2, float y2, int width) {
        int i = width;
        int j = 0;
        int or = (int) MathUtil.getOrientation(x1, y1, x2, y2);
        if (or < 45 || or > 135) {
            j = i;
            i = 0;
        }
        GeneralPath generalpath = new GeneralPath();
        generalpath.moveTo(x1 - i, y1 - j);
        generalpath.lineTo(x1 + i, y1 + j);
        generalpath.lineTo(x2 + i, y2 + j);
        generalpath.lineTo(x2 - i, y2 - j);
        generalpath.lineTo(x1 - i, y1 - j);
        generalpath.closePath();
        return new Area(generalpath);
    }

    @Override
    protected DragSequence createResizeDrag(MouseEvent mouseevent, int i) {
        return new PolygonDragSequence(mouseevent == null, i);
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseevent) {
        GeneralPath generalpath = new GeneralPath();
        generalpath.moveTo(points[0], points[1]);
        int i = 2;
        for (int j = numPoints * 2; i < j; i++) {
            generalpath.lineTo(points[i], points[++i]);
        }
        if (isClosed()) {
            generalpath.closePath();
        }
        setShape(generalpath, mouseevent);
        updateShape();
    }

    @Override
    protected int resizeOnDrawing(int i, int j, int k, MouseEvent mouseevent) {
        return -2;
    }

    @Override
    public void move(int i, int j, int k, MouseEvent mouseevent) {
        Point p = needToMoveCanvas(j, k);
        if (p != null) {
            j = p.x;
            k = p.y;
            super.move(i, j, k, mouseevent);
        }
        for (int m = 0; m < points.length; m = m + 2) {
            points[m] += j;
            points[m + 1] += k;
        }
        // updateShapeOnDrawing(mouseevent);
    }

    @Override
    protected void updateShape() {
        super.updateShape();
    }

    @Override
    public void updateLabel(Object source, DefaultView2d view2d) {

    }

    @Override
    public int getResizeCorner(MouseEvent mouseevent) {
        final Point pos = mouseevent.getPoint();
        int k = getHandleSize() + 2;
        AffineTransform affineTransform = getAffineTransform(mouseevent);
        if (affineTransform != null) {
            // Enable to get a better selection of the handle with a low or high magnification zoom
            double scale = affineTransform.getScaleX();
            k = (int) Math.ceil(k / scale + 1);
        }
        int i = pos.x;
        int j = pos.y;
        int l = 0;
        for (int i1 = numPoints + numPoints; l < i1; l++) {
            int j1 = (int) points[l];
            int k1 = (int) points[++l];
            int l1 = (i - j1) * (i - j1) + (j - k1) * (j - k1);
            if (l1 <= k)
                return l / 2;
        }
        return -1;
    }

    @Override
    public void paintHandles(Graphics2D graphics2d, AffineTransform transform) {
        graphics2d.setPaint(Color.black);
        int i = getHandleSize();
        int j = i / 2;
        int k = 0;
        for (int l = numPoints + numPoints; k < l; k++) {
            graphics2d.fillRect((int) points[k] - j, (int) points[++k] - j, i, i);
        }
        k = 0;
        graphics2d.setPaint(Color.white);
        graphics2d.setStroke(new BasicStroke(1.0f));
        for (int l = numPoints + numPoints; k < l; k++) {
            graphics2d.drawRect((int) points[k] - j, (int) points[++k] - j, i, i);
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        PolygonGraphic polygongraphic = (PolygonGraphic) super.clone();
        return super.clone();
    }

    @Override
    public Graphic clone(int xPos, int yPos) {
        PolygonGraphic polygongraphic;
        try {
            polygongraphic = (PolygonGraphic) clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }

        polygongraphic.points = new float[4];
        polygongraphic.numPoints = 2;
        polygongraphic.points[0] = polygongraphic.points[2] = xPos;
        polygongraphic.points[1] = polygongraphic.points[3] = yPos;

        polygongraphic.updateStroke();
        polygongraphic.updateShapeOnDrawing(null);
        return polygongraphic;
    }

    public float getFirstX() {
        return points[0];
    }

    public float getFirstY() {
        return points[1];
    }

    public float getLastX() {
        return points[points.length - 2];
    }

    public float getLastY() {
        return points[points.length - 1];
    }

    // private Object readResolve() {
    // updateStroke();
    // updateShapeOnDrawing(affineTransform);
    // return this;
    // }

    // return area of polygon
    public double getAreaValue() {
        Rectangle2D.Float bounds = getBoundValue();
        double x = bounds.x;
        double y = bounds.y;
        double sum = 0.0;
        for (int m = 0; m < points.length - 2; m = m + 2) {
            sum = sum + ((points[m] - x) * (points[m + 3] - y)) - ((points[m + 1] - y) * (points[m + 2] - x));
        }
        return Math.abs(0.5 * sum);
    }

    // return the centroid of the polygon
    public Point2D.Double getCentroid() {
        Rectangle2D.Float bounds = getBoundValue();
        double x = bounds.x;
        double y = bounds.y;
        double cx = 0.0, cy = 0.0;
        for (int m = 0; m < points.length - 2; m = m + 2) {
            cx =
                cx + (points[m] + points[m + 2] - 2 * x)
                    * ((points[m + 1] - y) * (points[m + 2] - x) - (points[m] - x) * (points[m + 3] - y));
            cy =
                cy + (points[m + 1] + points[m + 3] - 2 * y)
                    * ((points[m + 1] - y) * (points[m + 2] - x) - (points[m] - x) * (points[m + 3] - y));
        }
        double area = getAreaValue();
        cx /= (6 * area);
        cy /= (6 * area);
        return new Point2D.Double(x + cx, y + cy);
    }

    // return bound of polygon
    public Rectangle2D.Float getBoundValue() {
        float[] rect = { Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
        for (int m = 0; m < points.length; m = m + 2) {
            if (points[m] < rect[0]) {
                rect[0] = points[m];
            }
            if (points[m + 1] < rect[1]) {
                rect[1] = points[m + 1];
            }
            if (points[m] > rect[2]) {
                rect[2] = points[m];
            }
            if (points[m + 1] > rect[3]) {
                rect[3] = points[m + 1];
            }
        }
        return new Rectangle2D.Float(rect[0], rect[1], rect[2], rect[3]);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("PolygonGraphic.title"); //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {
        // TODO Auto-generated method stub
        return null;
    }
}
