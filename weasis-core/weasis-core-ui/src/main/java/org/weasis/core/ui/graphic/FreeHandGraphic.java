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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.util.MouseEventDouble;

// TODO: Auto-generated Javadoc
/**
 * The Class FreeHandGraphic.
 * 
 * @author Nicolas Roduit
 */
/**
 * <p>
 * Title: JMicroVision
 * </p>
 * <p>
 * Description: Thin sections analysis
 * </p>
 * <p>
 * Copyright: Copyright (c) 2002
 * </p>
 * <p>
 * Company:
 * </p>
 * 
 * @author Nicolas Roduit
 * @version 1.0
 */
public class FreeHandGraphic extends AbstractDragGraphicOld implements Cloneable {

    private static final long serialVersionUID = 5814470994233416436L;
    public static final Icon ICON = new ImageIcon(AngleToolGraphic.class.getResource("/icon/22x22/draw-angle.png")); //$NON-NLS-1$
    private transient GeneralPath generalPath;
    protected float points[];
    protected int numPoints;
    protected boolean closed;
    private final boolean linePath;

    /**
     * The Class FreeDragSequence.
     * 
     * @author Nicolas Roduit
     */
    protected class FreeDragSequence extends AbstractDragGraphicOld.DefaultDragSequence {

        @Override
        public void startDrag(MouseEventDouble mouseevent) {
            super.startDrag(mouseevent);
            if (createPoints) {
                closed = false;
            }
        }

        @Override
        public void drag(MouseEventDouble mouseevent) {
            int tx = mouseevent.getX() - getLastX();
            int ty = mouseevent.getY() - getLastY();
            if (tx != 0 || ty != 0) {
                if (createPoints) {
                    if (true) {
                        int i = numPoints + numPoints;
                        Point p = needToMoveCanvas(tx, ty);
                        if (p != null) {
                            tx = p.x;
                            ty = p.y;
                            FreeHandGraphic.super.move(0, tx, ty, mouseevent);
                        }
                        float af[] = new float[i + 2];
                        System.arraycopy(points, 0, af, 0, i);
                        af[i] = af[i - 2] + tx;
                        af[i + 1] = af[i - 1] + ty;
                        numPoints++;
                        points = af;
                        updateShapeOnDrawing(mouseevent);
                        update(mouseevent);
                    }
                } else {
                    // resize(pointType, mouseevent.getX() - getLastX(), mouseevent.getY() - getLastY());
                    update(mouseevent);
                }
            }
        }

        @Override
        public boolean completeDrag(MouseEventDouble mouseevent) {
            if (createPoints) {
                if (numPoints > 2) {
                    int pointToRemove = points[0] == points[2] && points[1] == points[3] ? 2 : 0;
                    if (pointToRemove > 0) {
                        int j = (numPoints + numPoints) - 2;
                        float af1[] = new float[j];
                        System.arraycopy(points, 2, af1, 0, j);
                        numPoints--;
                        points = af1;
                    }
                }
                if (!linePath || numPoints == 2) {
                    // s'assurer que la forme aie bien une largeur et hauteur de 1 (autrement pas moyen de la
                    // sélectionner)
                    points[0] = points[0] == points[2] ? points[0] - 1f : points[0];
                    points[1] = points[1] == points[3] ? points[1] - 1f : points[1];
                }
                closed = true;
                updateShapeOnDrawing(mouseevent);
                updateShape();
                createPoints = false;
            }
            update(mouseevent);
            return true;
        }

        protected FreeDragSequence(boolean flag, int i) {
            super(flag, -1);
        }
    }

    public FreeHandGraphic(float lineThickness, Color paint, boolean fill, boolean linePath) {
        setShape(new GeneralPath(), null);
        setPaint(paint);
        this.lineThickness = lineThickness;
        this.linePath = linePath;
        numPoints = 2;
        points = new float[numPoints * 2];
        points[0] = 0.0F;
        points[1] = 0.0F;
        points[2] = 0.0F;
        points[3] = 100F;
        closed = false;
        setFilled(fill);
        updateStroke();
        updateShapeOnDrawing(null);
    }

    public void setClosed(boolean flag) {
        closed = flag;
        updateShape();
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isLinePath() {
        return linePath;
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
            area = createAreaForLine(x1, y1, x2, y2, (int) lineThickness);
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
    public void paintHandles(Graphics2D graphics2d, AffineTransform transform) {
        if (closed) {
            super.paintHandles(graphics2d, transform);
        }
    }

    @Override
    protected int resizeOnDrawing(int point, int j, int k, MouseEvent mouseevent) {
        return -2;
    }

    @Override
    public int getResizeCorner(MouseEvent mouseevent) {
        return -1;
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseevent) {
        generalPath = new GeneralPath();
        generalPath.reset();
        generalPath.moveTo(points[0], points[1]);
        int i = 2;
        for (int j = numPoints * 2; i < j; i++) {
            generalPath.lineTo(points[i], points[++i]);
        }
        if (isClosed() && !linePath) {
            generalPath.closePath();
        }
        setShape(generalPath, mouseevent);
    }

    @Override
    protected void updateShape() {
        super.updateShape();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        FreeHandGraphic freeGraphic = (FreeHandGraphic) super.clone();
        return freeGraphic;
    }

    @Override
    public Graphic clone(int i, int j) {
        FreeHandGraphic freeGraphic;
        try {
            freeGraphic = (FreeHandGraphic) clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }

        freeGraphic.points = new float[4];
        freeGraphic.numPoints = 2;
        freeGraphic.points[0] = freeGraphic.points[2] = i;
        freeGraphic.points[1] = freeGraphic.points[3] = j;

        freeGraphic.updateStroke();
        freeGraphic.updateShapeOnDrawing(null);
        return freeGraphic;
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
    public void updateLabel(Object source, DefaultView2d view2d) {

    }

    // délcancher lors du redimensionnement du desssin, i représente un des 9 coins du handle
    @Override
    protected DragSequence createResizeDrag(MouseEvent mouseevent, int i) {
        return new FreeDragSequence(mouseevent == null, i);
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

    @Override
    public Icon getIcon() {
        // TODO build icon
        return null;
    }

    @Override
    public String getUIName() {
        return Messages.getString("FreeHandGraphic.title"); //$NON-NLS-1$
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
