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
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;

/**
 * The Class AngleToolGraphic.
 * 
 * @author Nicolas Roduit
 */
public class AngleToolGraphic extends AbstractDragGraphic implements Cloneable {
    public static final Icon ICON = new ImageIcon(AngleToolGraphic.class.getResource("/icon/22x22/draw-angle.png")); //$NON-NLS-1$
    public final static int ARC_RADIUS = 14;
    public final static double PI2 = 2.0 * Math.PI;
    protected float points[];
    protected int numPoints;
    protected boolean closed;
    protected transient boolean closeShape;

    protected class PolygonDragSequence extends AbstractDragGraphic.DefaultDragSequence {

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
        public void startDrag(MouseEvent mouseevent) {
            update(mouseevent);
        }

        @Override
        public void drag(MouseEvent mouseevent) {
            int tx = mouseevent.getX() - getLastX();
            int ty = mouseevent.getY() - getLastY();
            if (tx != 0 || ty != 0) {
                Point p = needToMoveCanvas(tx, ty);
                if (p != null) {
                    tx = p.x;
                    ty = p.y;
                    move(0, tx, ty, mouseevent);
                }
                points[point + point] += tx;
                points[point + point + 1] += ty;
                updateShapeOnDrawing(mouseevent);
                update(mouseevent);
            }
        }

        @Override
        public boolean completeDrag(MouseEvent mouseevent) {
            if (mouseevent.getID() == MouseEvent.MOUSE_RELEASED) {
                if (points[0] == points[2] && points[1] == points[3]) {
                    return false;
                }
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
                    if (i == 4) {
                        createPoints = false;
                    }
                    return false;
                } else {
                    return true;
                }
            } else {
                return false;
            }
            // if (mouseevent.getID() == 501) {
            // if (mouseevent.getClickCount() == 2) {
            // if (numPoints > 3) {
            // int pointToRemove = points[0] == points[2] && points[1] == points[3] ? 4 : 2;
            // int j = (numPoints + numPoints) - pointToRemove;
            // float af1[] = new float[j];
            // System.arraycopy(points, pointToRemove == 4 ? 2 : 0, af1, 0, j);
            // numPoints -= pointToRemove / 2;
            // points = af1;
            // if (closed) {
            // points[0] = points[0] == points[2] ? points[0] - 1f : points[0];
            // points[1] = points[1] == points[3] ? points[1] - 1f : points[1];
            // }
            // closeShape = true;
            // updateShapeOnDrawing();
            // createPoints = false;
            // // comme le drag doit être annulé, pas de mise à jour dans imageDisplay
            // getLayer().getShowDrawing().oneSelectedGraphicUpdateInterface();
            // }
            // update(mouseevent);
            // return true;
            // }
            // else {
            // update(mouseevent);
            // return false;
            // }
            // }
            // else {
            // return true;
            // }
        }
    }

    public AngleToolGraphic(float lineThickness, Color paint) {
        setShape(new GeneralPath(), null);
        setPaint(paint);
        this.lineThickness = lineThickness;
        numPoints = 2;
        points = new float[numPoints * 2];
        closed = false;
        setFilled(false);
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
    // updateShapeOnDrawing(null);
    // }

    // @Override
    // public void setLayer(AbstractLayer layer) {
    // // redéfinition si le graph est effacé, on ferme le graph (remplace double clique), il faut quand même
    // // recliquer une fois pour que la dragSequence retourne false
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
            area = LineGraphic.createAreaForLine(x1, y1, x2, y2, getHandleSize());
        }
        return area;
    }

    @Override
    protected DragSequence createResizeDrag(MouseEvent mouseevent, int i) {
        return new PolygonDragSequence(mouseevent == null, i);
    }

    @Override
    public void updateLabel(Object source, Graphics2D g2d) {
        if (showLabel) {
            ImageElement image = null;
            if (source instanceof MouseEvent) {
                image = getImageElement((MouseEvent) source);
            } else if (source instanceof ImageElement) {
                image = (ImageElement) source;
            }
            if (image != null) {
                String value = DecFormater.twoDecimal(getAngleBetweenTwoSegments() * 180.0 / Math.PI);
                value = value + " °"; //$NON-NLS-1$
                setLabel(new String[] { value }, g2d);
            }
        }
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseevent) {
        GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, numPoints);
        generalpath.moveTo(points[0], points[1]);
        int i = 2;
        for (int j = numPoints * 2; i < j; i++) {
            generalpath.lineTo(points[i], points[++i]);
        }
        if (points != null && points.length == 6) {
            double segA = Math.atan2(-(points[1] - points[3]), points[0] - points[2]);
            double segB = Math.atan2(-(points[5] - points[3]), points[4] - points[2]);
            Arc2D arc =
                new Arc2D.Double(points[2] - ARC_RADIUS / 2, points[3] - ARC_RADIUS / 2, ARC_RADIUS, ARC_RADIUS, segA
                    * 180 / Math.PI, ((segB + PI2 - segA) % PI2) * 180 / Math.PI, Arc2D.OPEN);
            generalpath.append(arc, false);
        }

        setShape(generalpath, mouseevent);
        updateLabel(mouseevent, getGraphics2D(mouseevent));
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
        updateShapeOnDrawing(mouseevent);
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
            if (l1 <= k) {
                return l / 2;
            }
        }
        return -1;
    }

    @Override
    public Point getLabelPosition(Shape transformedShape) {
        Rectangle rect = transformedShape.getBounds();
        return new Point(rect.x - 3 + rect.width / 2, rect.y + 6 + rect.height / 2);
    }

    @Override
    public void paintHandles(Graphics2D graphics2d, AffineTransform transform) {
        graphics2d.setPaint(Color.black);
        int i = getHandleSize();
        int j = i / 2;
        int k = 0;

        float[] dstPts = new float[points.length];
        transform.transform(points, 0, dstPts, 0, numPoints);
        for (int l = numPoints + numPoints; k < l; k++) {
            graphics2d.fill(new Rectangle2D.Float(dstPts[k] - j, dstPts[++k] - j, i, i));
        }

        k = 0;
        graphics2d.setPaint(Color.white);
        graphics2d.setStroke(new BasicStroke(1.0f));
        for (int l = numPoints + numPoints; k < l; k++) {
            graphics2d.draw(new Rectangle2D.Float(dstPts[k] - j, dstPts[++k] - j, i, i));
        }

    }

    public double getAngleBetweenTwoSegments() {
        if (points.length == 6) {
            double segA = Math.atan2(-(points[1] - points[3]), points[0] - points[2]);
            double segB = Math.atan2(-(points[5] - points[3]), points[4] - points[2]);
            // 0 à PI => creu et PI à 2PI => pique
            return (segB + PI2 - segA) % PI2;
        }
        return 0;
    }

    @Override
    public void showProperties() {
        if (!closeShape) {
            super.showProperties();
        }
        closeShape = false;
    }

    @Override
    public Graphic clone(int i, int j) {
        AngleToolGraphic polygongraphic;
        try {
            polygongraphic = (AngleToolGraphic) super.clone();
        } catch (CloneNotSupportedException clonenotsupportedexception) {
            return null;
        }
        polygongraphic.points = new float[4];
        polygongraphic.numPoints = 2;
        polygongraphic.points[0] = polygongraphic.points[2] = i;
        polygongraphic.points[1] = polygongraphic.points[3] = j;
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
        return Messages.getString("MeasureToolBar.angle"); //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return null;
    }
}
