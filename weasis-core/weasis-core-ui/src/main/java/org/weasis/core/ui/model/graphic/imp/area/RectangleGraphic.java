/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.graphic.imp.area;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.graphic.AbstractDragGraphicArea;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlType(name = "rectangle")
@XmlRootElement(name = "rectangle")
public class RectangleGraphic extends AbstractDragGraphicArea {
    private static final long serialVersionUID = -2862114022989550927L;

    public static final Integer POINTS_NUMBER = 8;

    public static final Icon ICON = new ImageIcon(RectangleGraphic.class.getResource("/icon/22x22/draw-rectangle.png")); //$NON-NLS-1$

    public static final Measurement AREA = new Measurement(Messages.getString("measure.area"), 1, true, true, true); //$NON-NLS-1$
    public static final Measurement PERIMETER =
        new Measurement(Messages.getString("measure.perimeter"), 2, true, true, false); //$NON-NLS-1$
    public static final Measurement TOP_LEFT_POINT_X =
        new Measurement(Messages.getString("measure.topx"), 3, true, true, false); //$NON-NLS-1$
    public static final Measurement TOP_LEFT_POINT_Y =
        new Measurement(Messages.getString("measure.topy"), 4, true, true, false); //$NON-NLS-1$
    public static final Measurement CENTER_X =
        new Measurement(Messages.getString("measure.centerx"), 5, true, true, false); //$NON-NLS-1$
    public static final Measurement CENTER_Y =
        new Measurement(Messages.getString("measure.centery"), 6, true, true, false); //$NON-NLS-1$
    public static final Measurement WIDTH = new Measurement(Messages.getString("measure.width"), 7, true, true, false); //$NON-NLS-1$
    public static final Measurement HEIGHT =
        new Measurement(Messages.getString("measure.height"), 8, true, true, false); //$NON-NLS-1$

    protected static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();
    static {
        MEASUREMENT_LIST.add(TOP_LEFT_POINT_X);
        MEASUREMENT_LIST.add(TOP_LEFT_POINT_Y);
        MEASUREMENT_LIST.add(CENTER_X);
        MEASUREMENT_LIST.add(CENTER_Y);
        MEASUREMENT_LIST.add(WIDTH);
        MEASUREMENT_LIST.add(HEIGHT);
        MEASUREMENT_LIST.add(AREA);
        MEASUREMENT_LIST.add(PERIMETER);
    }

    public RectangleGraphic() {
        super(POINTS_NUMBER);
    }

    public RectangleGraphic(RectangleGraphic graphic) {
        super(graphic);
    }

    @Override
    public RectangleGraphic copy() {
        return new RectangleGraphic(this);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.rect"); //$NON-NLS-1$
    }

    public RectangleGraphic buildGraphic(Rectangle2D rectangle) throws InvalidShapeException {
        Rectangle2D r = Optional.ofNullable(rectangle).orElseThrow(() -> new InvalidShapeException("Rectangle2D is null!")); //$NON-NLS-1$
        setHandlePointList(r);
        prepareShape();
        return this;
    }

    @Override
    protected void prepareShape() throws InvalidShapeException {

        if (!isShapeValid()) {
            throw new InvalidShapeException("This shape cannot be drawn"); //$NON-NLS-1$
        }
        buildShape(null);
    }

    @Override
    public Integer moveAndResizeOnDrawing(Integer handlePointIndex, Double deltaX, Double deltaY,
        MouseEventDouble mouseEvent) {
        if (Objects.equals(handlePointIndex, UNDEFINED)) { // move shape
            for (Point2D point : pts) {
                if (point != null) {
                    point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
                }
            }
        } else {
            Rectangle2D rectangle = new Rectangle2D.Double();

            rectangle.setFrameFromDiagonal(getHandlePoint(eHandlePoint.NW.index),
                getHandlePoint(eHandlePoint.SE.index));

            double x = rectangle.getX();
            double y = rectangle.getY();
            double w = rectangle.getWidth();
            double h = rectangle.getHeight();

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

    @Override
    public void buildShape(MouseEventDouble mouseevent) {
        Rectangle2D rectangle = null;

        if (pts.size() > 1) {
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
    public List<Measurement> getMeasurementList() {
        return MEASUREMENT_LIST;
    }

    @Override
    public List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {

        if (layer != null && layer.hasContent() && isShapeValid()) {
            MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<>();
                Rectangle2D rect = new Rectangle2D.Double();

                rect.setFrameFromDiagonal(getHandlePoint(eHandlePoint.NW.index), getHandlePoint(eHandlePoint.SE.index));

                double ratio = adapter.getCalibRatio();

                if (TOP_LEFT_POINT_X.getComputed()) {
                    measVal.add(
                        new MeasureItem(TOP_LEFT_POINT_X, adapter.getXCalibratedValue(rect.getX()), adapter.getUnit()));
                }
                if (TOP_LEFT_POINT_Y.getComputed()) {
                    measVal.add(
                        new MeasureItem(TOP_LEFT_POINT_Y, adapter.getYCalibratedValue(rect.getY()), adapter.getUnit()));
                }
                if (CENTER_X.getComputed()) {
                    measVal.add(
                        new MeasureItem(CENTER_X, adapter.getXCalibratedValue(rect.getCenterX()), adapter.getUnit()));
                }
                if (CENTER_Y.getComputed()) {
                    measVal.add(
                        new MeasureItem(CENTER_Y, adapter.getYCalibratedValue(rect.getCenterY()), adapter.getUnit()));
                }
                if (WIDTH.getComputed()) {
                    measVal.add(new MeasureItem(WIDTH, ratio * rect.getWidth(), adapter.getUnit()));
                }
                if (HEIGHT.getComputed()) {
                    measVal.add(new MeasureItem(HEIGHT, ratio * rect.getHeight(), adapter.getUnit()));
                }
                if (AREA.getComputed()) {
                    Double val = rect.getWidth() * rect.getHeight() * ratio * ratio;
                    String unit = "pix".equals(adapter.getUnit()) ? adapter.getUnit() : adapter.getUnit() + "2"; //$NON-NLS-1$ //$NON-NLS-2$
                    measVal.add(new MeasureItem(AREA, val, unit));
                }
                if (PERIMETER.getComputed()) {
                    Double val = (rect.getWidth() + rect.getHeight()) * 2 * ratio;
                    measVal.add(new MeasureItem(PERIMETER, val, adapter.getUnit()));
                }

                List<MeasureItem> stats = getImageStatistics(layer, releaseEvent);
                if (stats != null) {
                    measVal.addAll(stats);
                }
                return measVal;
            }
        }
        return Collections.emptyList();
    }

    protected void setHandlePointList(Rectangle2D rectangle) {

        double x = rectangle.getX();
        double y = rectangle.getY();
        double w = rectangle.getWidth();
        double h = rectangle.getHeight();

        while (pts.size() < pointNumber) {
            pts.add(new Point2D.Double());
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

    public enum eHandlePoint {
        NONE(-1), NW(0), SE(1), NE(2), SW(3), N(4), S(5), E(6), W(7);
        // 0 and 1 must be diagonal point of rectangle

        static final Map<Integer, eHandlePoint> map = new HashMap<>(eHandlePoint.values().length);
        static {
            for (eHandlePoint corner : eHandlePoint.values()) {
                map.put(corner.index, corner);
            }
        }
        public final int index;

        eHandlePoint(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        static eHandlePoint valueFromIndex(int index) {
            return Optional.ofNullable(map.get(index))
                .orElseThrow(() -> new RuntimeException("Not a valid index for a rectangular DragGraphic : " + index)); //$NON-NLS-1$
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
