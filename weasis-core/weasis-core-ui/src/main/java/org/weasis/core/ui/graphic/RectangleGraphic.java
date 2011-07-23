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

    public static final Measurement Area = new Measurement("Area", true, true, true);
    public static final Measurement Perimeter = new Measurement("Perimeter", true, true, false);
    public static final Measurement TopLeftPointX = new Measurement("Top Left X", true, true, false);
    public static final Measurement TopLeftPointY = new Measurement("Top Left Y", true, true, false);
    public static final Measurement CenterX = new Measurement("Center X", true, true, false);
    public static final Measurement CenterY = new Measurement("Center Y", true, true, false);
    public static final Measurement Width = new Measurement("Width", true, true, false);
    public static final Measurement Height = new Measurement("Height", true, true, false);

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

            rectangle.setFrameFromDiagonal(handlePointList.get(eHandlePoint.NW.index),
                handlePointList.get(eHandlePoint.SE.index));

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

        handlePointList.get(eHandlePoint.NW.index).setLocation(new Point2D.Double(x, y));
        handlePointList.get(eHandlePoint.N.index).setLocation(new Point2D.Double(x + w / 2, y));
        handlePointList.get(eHandlePoint.NE.index).setLocation(new Point2D.Double(x + w, y));
        handlePointList.get(eHandlePoint.E.index).setLocation(new Point2D.Double(x + w, y + h / 2));
        handlePointList.get(eHandlePoint.SE.index).setLocation(new Point2D.Double(x + w, y + h));
        handlePointList.get(eHandlePoint.S.index).setLocation(new Point2D.Double(x + w / 2, y + h));
        handlePointList.get(eHandlePoint.SW.index).setLocation(new Point2D.Double(x, y + h));
        handlePointList.get(eHandlePoint.W.index).setLocation(new Point2D.Double(x, y + h / 2));
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseevent) {
        Rectangle2D rectangle = null;

        if (handlePointList.size() > 1) {
            if (!handlePointList.get(eHandlePoint.NW.index).equals(handlePointList.get(eHandlePoint.SE.index))) {
                rectangle = new Rectangle2D.Double();
                rectangle.setFrameFromDiagonal(handlePointList.get(eHandlePoint.NW.index),
                    handlePointList.get(eHandlePoint.SE.index));
            }
        }

        setShape(rectangle, mouseevent);
        updateLabel(mouseevent, getDefaultView2d(mouseevent));
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {

        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();
                Rectangle2D rect = new Rectangle2D.Double();

                rect.setFrameFromDiagonal(handlePointList.get(eHandlePoint.NW.index),
                    handlePointList.get(eHandlePoint.SE.index));

                double ratio = adapter.getCalibRatio();

                if (TopLeftPointX.isComputed() && (!drawOnLabel || TopLeftPointX.isGraphicLabel())) {
                    Double val =
                        releaseEvent || TopLeftPointX.isQuickComputing() ? adapter.getXCalibratedValue(rect.getX())
                            : null;
                    measVal.add(new MeasureItem(TopLeftPointX, val, adapter.getUnit()));
                }
                if (TopLeftPointY.isComputed() && (!drawOnLabel || TopLeftPointY.isGraphicLabel())) {
                    Double val =
                        releaseEvent || TopLeftPointY.isQuickComputing() ? adapter.getYCalibratedValue(rect.getY())
                            : null;
                    measVal.add(new MeasureItem(TopLeftPointY, val, adapter.getUnit()));
                }
                if (CenterX.isComputed() && (!drawOnLabel || CenterX.isGraphicLabel())) {
                    Double val =
                        releaseEvent || CenterX.isQuickComputing() ? adapter.getXCalibratedValue(rect.getCenterX())
                            : null;
                    measVal.add(new MeasureItem(CenterX, val, adapter.getUnit()));
                }
                if (CenterY.isComputed() && (!drawOnLabel || CenterY.isGraphicLabel())) {
                    Double val =
                        releaseEvent || CenterY.isQuickComputing() ? adapter.getYCalibratedValue(rect.getCenterY())
                            : null;
                    measVal.add(new MeasureItem(CenterY, val, adapter.getUnit()));
                }
                if (Width.isComputed() && (!drawOnLabel || Width.isGraphicLabel())) {
                    Double val = releaseEvent || Width.isQuickComputing() ? ratio * rect.getWidth() : null;
                    measVal.add(new MeasureItem(Width, val, adapter.getUnit()));
                }
                if (Height.isComputed() && (!drawOnLabel || Height.isGraphicLabel())) {
                    Double val = releaseEvent || Height.isQuickComputing() ? ratio * rect.getHeight() : null;
                    measVal.add(new MeasureItem(Height, val, adapter.getUnit()));
                }
                if (Area.isComputed() && (!drawOnLabel || Area.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || Area.isQuickComputing()) {
                        val = rect.getWidth() * rect.getHeight() * ratio * ratio;
                    }

                    String unit = "pix".equals(adapter.getUnit()) ? adapter.getUnit() : adapter.getUnit() + "2";
                    measVal.add(new MeasureItem(Area, val, unit));
                }
                if (Perimeter.isComputed() && (!drawOnLabel || Perimeter.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || Perimeter.isQuickComputing()) {
                        val = (rect.getWidth() + rect.getHeight()) * 2 * ratio;
                    }

                    measVal.add(new MeasureItem(Perimeter, val, adapter.getUnit()));
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
            if (point == null)
                throw new RuntimeException("Not a valid index for a rectangular DragGraphic : " + index);
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
