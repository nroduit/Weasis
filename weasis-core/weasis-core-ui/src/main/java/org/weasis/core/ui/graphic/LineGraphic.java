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
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;

/**
 * The Class LineGraphic.
 * 
 * @author Nicolas Roduit
 */
public class LineGraphic extends AbstractDragGraphic implements Cloneable {

    private static final long serialVersionUID = -6023305795757471234L;
    public static final Icon ICON = new ImageIcon(LineGraphic.class.getResource("/icon/22x22/draw-line.png")); //$NON-NLS-1$
    private float x1, y1, x2, y2;

    /**
     * The Class LineDragSequence.
     * 
     * @author Nicolas Roduit
     */
    protected class LineDragSequence extends AbstractDragGraphic.DefaultDragSequence {

        @Override
        public void startDrag(MouseEvent mouseevent) {
            update(mouseevent);
        }

        @Override
        public void drag(MouseEvent mouseevent) {
            int i = mouseevent.getX();
            int j = mouseevent.getY();
            if (i - getLastX() != 0 || j - getLastY() != 0) {
                switch (pointType) {
                    case -1:
                        move(pointType, i - getLastX(), j - getLastY(), mouseevent);
                        break;
                    default:
                        resizeOnDrawing(pointType, i - getLastX(), j - getLastY(), mouseevent);
                }
                update(mouseevent);
            }
        }

        @Override
        public boolean completeDrag(MouseEvent mouseevent) {
            if (x1 == x2 && y1 == y2) {
                return false;
            }
            if (createPoints) {
                updateShape();
                createPoints = false;
            }
            update(mouseevent);
            return true;
        }

        protected LineDragSequence() {
            this(false, -1);
        }

        protected LineDragSequence(boolean flag, int i) {
            pointType = i;
            // toujours action update dans toolbar
            createPoints = flag;
        }

        private final int pointType;
    }

    public LineGraphic(float lineThickness, Color paint) {
        setPaint(paint);
        this.lineThickness = lineThickness;
        updateStroke();
        updateShapeOnDrawing(null);
    }

    public LineGraphic(float lineThickness, Color paint, float x1, float y1, float x2, float y2) {
        setPaint(paint);
        this.lineThickness = lineThickness;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        updateStroke();
        updateShapeOnDrawing(null);
    }

    @Override
    protected DragSequence createResizeDrag(MouseEvent mouseevent, int i) {
        return new LineDragSequence((mouseevent == null), i);
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseevent) {
        setShape(new Line2D.Double(x1, y1, x2, y2), mouseevent);
        updateLabel(mouseevent, getGraphics2D(mouseevent));
    }

    @Override
    protected DragSequence createMoveDrag(DragSequence dragsequence, MouseEvent mouseevent) {
        return new LineDragSequence();
    }

    @Override
    public DragSequence createDragSequence(DragSequence dragsequence, MouseEvent mouseevent) {
        int i = 6;
        if (mouseevent != null && (dragsequence != null || (i = getResizeCorner(mouseevent)) == -1)) {
            return createMoveDrag(dragsequence, mouseevent);
        } else {
            return createResizeDrag(mouseevent, i);
        }
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
                Unit unit = image.getPixelSpacingUnit();
                String value = DecFormater.twoDecimal(getSegmentLength(image.getPixelSizeX(), image.getPixelSizeY()));
                value = value + " " + unit.getAbbreviation(); //$NON-NLS-1$
                setLabel(new String[] { value }, g2d);
            }
        }
    }

    @Override
    public void move(int i, int j, int k, MouseEvent mouseevent) {
        Point p = needToMoveCanvas(j, k);
        if (p != null) {
            j = p.x;
            k = p.y;
            super.move(i, p.x, p.y, mouseevent);
        }
        x1 += j;
        y1 += k;
        x2 += j;
        y2 += k;
        updateShapeOnDrawing(mouseevent);
    }

    @Override
    public Area getArea() {
        return createAreaForLine(x1, y1, x2, y2, getHandleSize() / 2);
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
    public int getResizeCorner(MouseEvent mouseevent) {
        final Point pos = mouseevent.getPoint();
        int k = getHandleSize() + 2;
        AffineTransform affineTransform = getAffineTransform(mouseevent);
        if (affineTransform != null) {
            // Enable to get a better selection of the handle with a low or high magnification zoom
            double scale = affineTransform.getScaleX();
            k = (int) Math.ceil(k / scale + 1);
        }
        float i = pos.x;
        float j = pos.y;
        float dist = (i - x1) * (i - x1) + (j - y1) * (j - y1);
        if (dist < k) {
            int or = (int) getSegmentOrientation();
            if (or > 45 && or < 135) {
                return 2;
            } else {
                return 0;
            }
        }
        dist = (i - x2) * (i - x2) + (j - y2) * (j - y2);
        if (dist < k) {
            int or = (int) getSegmentOrientation();
            if (or > 45 && or < 135) {
                return 6;
            } else {
                return 4;
            }
        }
        return -1;
    }

    @Override
    public int getHandleSize() {
        return 16;
    }

    @Override
    public void paintHandles(Graphics2D g2d, AffineTransform transform) {
        g2d.setPaint(Color.black);
        g2d.setStroke(stroke);

        Point2D.Float point1 = new Point2D.Float(x1, y1);
        Point2D.Float point2 = new Point2D.Float(x2, y2);
        transform.transform(point1, point1);
        transform.transform(point2, point2);

        double j = getHandleSize() / 2.0;
        double teta = Math.atan2(point1.getY() - point2.getY(), point1.getX() - point2.getX());
        // utilise arctan2 pour lever l'ambiguité 180 - degrés
        double p1 = j * Math.cos(Math.PI / 2 + teta);
        double p2 = j * Math.sin(Math.PI / 2 + teta);

        Shape line1 = new Line2D.Double(point1.getX() + p1, point1.getY() + p2, point1.getX() - p1, point1.getY() - p2);
        Shape line2 = new Line2D.Double(point2.getX() + p1, point2.getY() + p2, point2.getX() - p1, point2.getY() - p2);

        g2d.setStroke(new BasicStroke(1.0F));
        g2d.setColor(Color.black);
        g2d.draw(line1);
        g2d.draw(line2);
        float dash[] = { 2F };
        g2d.setStroke(new BasicStroke(1.0F, 0, 0, 2F, dash, 0));
        g2d.setPaint(Color.white);
        g2d.draw(line1);
        g2d.draw(line2);
    }

    @Override
    protected int resizeOnDrawing(int i, int tx, int ty, MouseEvent mouseevent) {
        Point p = needToMoveCanvas(tx, ty);
        if (p != null) {
            tx = p.x;
            ty = p.y;
            super.move(i, tx, ty, null);
        }
        if (i == 2 || i == 0) {
            x1 += tx;
            y1 += ty;
        } else if (i == 4 || i == 6) {
            x2 += tx;
            y2 += ty;
        }
        updateShapeOnDrawing(mouseevent);
        return i;
    }

    @Override
    public Graphic clone(int i, int j) {
        LineGraphic lineGraphic;
        try {
            lineGraphic = (LineGraphic) super.clone();
        } catch (CloneNotSupportedException clonenotsupportedexception) {
            return null;
        }
        lineGraphic.x1 = i;
        lineGraphic.x2 = i;
        lineGraphic.y1 = j;
        lineGraphic.y2 = j;
        lineGraphic.updateStroke();
        lineGraphic.updateShapeOnDrawing(null);
        return lineGraphic;
    }

    @Override
    public boolean isFilled() {
        return false;
    }

    public double getSegmentLength() {
        return Point2D.distance(x1, y1, x2, y2);
    }

    public double getSegmentLength(double scalex, double scaley) {
        return Point2D.distance(scalex * x1, scaley * y1, scalex * x2, scaley * y2);
    }

    public double getSegmentOrientation() {
        return MathUtil.getOrientation(x1, y1, x2, y2);
    }

    public double getSegmentAzimuth() {
        return MathUtil.getAzimuth(x1, y1, x2, y2);
    }

    public Point2D getStartPoint() {
        return new Point2D.Float(x1, y1);
    }

    public Point2D getEndPoint() {
        return new Point2D.Float(x2, y2);
    }

    /*
     * public ArrayList<ChainPoint> getCoordinates() { return RasterizeGraphicsToCoord.rasterizeSegment((int)x1,
     * (int)y1, (int)x2, (int)y2); }
     */
    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.line"); //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return null;
    }

}
