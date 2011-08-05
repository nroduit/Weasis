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
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class RectangleGraphic.
 * 
 * @author Nicolas Roduit, Benoit Jacquemoud
 */
public class RectangleGraphic extends AbstractDragGraphicArea {

    public static final Icon ICON = new ImageIcon(RectangleGraphic.class.getResource("/icon/22x22/draw-rectangle.png")); //$NON-NLS-1$

    public static final Measurement AREA = new Measurement("Area", 1, true, true, true);
    public static final Measurement PERIMETER = new Measurement("Perimeter", 2, true, true, false);
    public static final Measurement TOP_LEFT_POINT_X = new Measurement("Top Left X", 3, true, true, false);
    public static final Measurement TOP_LEFT_POINT_Y = new Measurement("Top Left Y", 4, true, true, false);
    public static final Measurement CENTER_X = new Measurement("Center X", 5, true, true, false);
    public static final Measurement CENTER_Y = new Measurement("Center Y", 6, true, true, false);
    public static final Measurement WIDTH = new Measurement("Width", 7, true, true, false);
    public static final Measurement HEIGHT = new Measurement("Height", 8, true, true, false);

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public RectangleGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(8, paintColor, lineThickness, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.rect"); //$NON-NLS-1$
    }

    @Override
    protected int moveAndResizeOnDrawing(int handlePointIndex, double deltaX, double deltaY, MouseEventDouble mouseEvent) {
        if (handlePointIndex == -1) { // move shape
            for (Point2D point : handlePointList) {
                if (point != null) {
                    point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
                }
            }
        } else {
            Rectangle2D rectangle = new Rectangle2D.Double();

            rectangle
                .setFrameFromDiagonal(getHandlePoint(eHandlePoint.NW.index), getHandlePoint(eHandlePoint.SE.index));

            double x = rectangle.getX(), y = rectangle.getY();
            double w = rectangle.getWidth(), h = rectangle.getHeight();

            eHandlePoint pt = eHandlePoint.valueFromIndex(handlePointIndex);

            if (pt.equals(eHandlePoint.W) || pt.equals(eHandlePoint.NW) || pt.equals(eHandlePoint.SW)) {
                x += deltaX;
                w -= deltaX;
            }

            if (pt.equals(eHandlePoint.N) || pt.equals(eHandlePoint.NW) || pt.equals(eHandlePoint.NE)) {
                y += deltaY;
                h -= deltaY;
            }

            if (pt.equals(eHandlePoint.E) || pt.equals(eHandlePoint.NE) || pt.equals(eHandlePoint.SE)) {
                w += deltaX;
            }

            if (pt.equals(eHandlePoint.S) || pt.equals(eHandlePoint.SW) || pt.equals(eHandlePoint.SE)) {
                h += deltaY;
            }

            if (w < 0) {
                w = -w;
                x -= w;
                pt = pt.getVerticalMirror();
            }

            if (h < 0) {
                h = -h;
                y -= h;
                pt = pt.getHorizontalMirror();
            }

            handlePointIndex = pt.index;
            rectangle.setFrame(x, y, w, h);

            setHandlePointList(rectangle);
        }

        return handlePointIndex;
    }

    protected void setHandlePointList(Rectangle2D rectangle) {

        double x = rectangle.getX(), y = rectangle.getY();
        double w = rectangle.getWidth(), h = rectangle.getHeight();

        while (handlePointList.size() < handlePointTotalNumber) {
            handlePointList.add(new Point.Double());
        }

        setHandlePoint(eHandlePoint.NW.index, new Point2D.Double(x, y));
        setHandlePoint(eHandlePoint.N.index, new Point2D.Double(x + w / 2, y));
        setHandlePoint(eHandlePoint.NE.index, new Point2D.Double(x + w, y));
        setHandlePoint(eHandlePoint.E.index, new Point2D.Double(x + w, y + h / 2));
        setHandlePoint(eHandlePoint.SE.index, new Point2D.Double(x + w, y + h));
        setHandlePoint(eHandlePoint.S.index, new Point2D.Double(x + w / 2, y + h));
        setHandlePoint(eHandlePoint.SW.index, new Point2D.Double(x, y + h));
        setHandlePoint(eHandlePoint.W.index, new Point2D.Double(x, y + h / 2));
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseevent) {
        Rectangle2D rectangle = null;

        if (handlePointList.size() > 1) {
            if (!getHandlePoint(eHandlePoint.NW.index).equals(getHandlePoint(eHandlePoint.SE.index))) {
                rectangle = new Rectangle2D.Double();
                rectangle.setFrameFromDiagonal(getHandlePoint(eHandlePoint.NW.index),
                    getHandlePoint(eHandlePoint.SE.index));
            }
        }

        setShape(rectangle, mouseevent);
        updateLabel(mouseevent, getDefaultView2d(mouseevent));
    }

    @Override
    public List<MeasureItem> computeMeasurements(ImageElement imageElement, boolean releaseEvent) {

        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();
                Rectangle2D rect = new Rectangle2D.Double();

                rect.setFrameFromDiagonal(getHandlePoint(eHandlePoint.NW.index), getHandlePoint(eHandlePoint.SE.index));

                double ratio = adapter.getCalibRatio();

                if (TOP_LEFT_POINT_X.isComputed()) {
                    measVal.add(new MeasureItem(TOP_LEFT_POINT_X, adapter.getXCalibratedValue(rect.getX()), adapter
                        .getUnit()));
                }
                if (TOP_LEFT_POINT_Y.isComputed()) {
                    measVal.add(new MeasureItem(TOP_LEFT_POINT_Y, adapter.getYCalibratedValue(rect.getY()), adapter
                        .getUnit()));
                }
                if (CENTER_X.isComputed()) {
                    measVal.add(new MeasureItem(CENTER_X, adapter.getXCalibratedValue(rect.getCenterX()), adapter
                        .getUnit()));
                }
                if (CENTER_Y.isComputed()) {
                    measVal.add(new MeasureItem(CENTER_Y, adapter.getYCalibratedValue(rect.getCenterY()), adapter
                        .getUnit()));
                }
                if (WIDTH.isComputed()) {
                    measVal.add(new MeasureItem(WIDTH, ratio * rect.getWidth(), adapter.getUnit()));
                }
                if (HEIGHT.isComputed()) {
                    measVal.add(new MeasureItem(HEIGHT, ratio * rect.getHeight(), adapter.getUnit()));
                }
                if (AREA.isComputed()) {
                    Double val = rect.getWidth() * rect.getHeight() * ratio * ratio;
                    String unit = "pix".equals(adapter.getUnit()) ? adapter.getUnit() : adapter.getUnit() + "2";
                    measVal.add(new MeasureItem(AREA, val, unit));
                }
                if (PERIMETER.isComputed()) {
                    Double val = (rect.getWidth() + rect.getHeight()) * 2 * ratio;
                    measVal.add(new MeasureItem(PERIMETER, val, adapter.getUnit()));
                }

                List<MeasureItem> stats = getImageStatistics(imageElement, releaseEvent);
                if (stats != null) {
                    measVal.addAll(stats);
                }
                return measVal;
            }
        }
        return null;
    }

    @Override
    public List<Measurement> getMeasurementList() {
        List<Measurement> list = new ArrayList<Measurement>();
        list.add(TOP_LEFT_POINT_X);
        list.add(TOP_LEFT_POINT_Y);
        list.add(CENTER_X);
        list.add(CENTER_Y);
        list.add(WIDTH);
        list.add(HEIGHT);
        list.add(AREA);
        list.add(PERIMETER);
        return list;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected static enum eHandlePoint {
        NONE(-1), NW(0), SE(1), NE(2), SW(3), N(4), S(5), E(6), W(7);
        // 0 and 1 must be diagonal point of rectangle

        final int index;

        eHandlePoint(int index) {
            this.index = index;
        }

        static final Map<Integer, eHandlePoint> map = new HashMap<Integer, eHandlePoint>(eHandlePoint.values().length);

        static {
            for (eHandlePoint corner : eHandlePoint.values()) {
                map.put(corner.index, corner);
            }
        }

        static eHandlePoint valueFromIndex(int index) {
            eHandlePoint point = map.get(index);
            if (point == null) {
                throw new RuntimeException("Not a valid index for a rectangular DragGraphic : " + index);
            }
            return point;
        }

        eHandlePoint getVerticalMirror() {
            switch (this) {
                case NW:
                    return eHandlePoint.NE;
                case NE:
                    return eHandlePoint.NW;
                case W:
                    return eHandlePoint.E;
                case E:
                    return eHandlePoint.W;
                case SW:
                    return eHandlePoint.SE;
                case SE:
                    return eHandlePoint.SW;
                default:
                    return this;
            }
        }

        eHandlePoint getHorizontalMirror() {
            switch (this) {
                case NW:
                    return eHandlePoint.SW;
                case SW:
                    return eHandlePoint.NW;
                case N:
                    return eHandlePoint.S;
                case S:
                    return eHandlePoint.N;
                case NE:
                    return eHandlePoint.SE;
                case SE:
                    return eHandlePoint.NE;
                default:
                    return this;
            }
        }

    }
}
