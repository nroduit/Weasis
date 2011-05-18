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
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.media.jai.PlanarImage;
import javax.media.jai.iterator.RectIter;
import javax.media.jai.iterator.RectIterFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.Messages;

/**
 * The Class RectangleGraphic.
 * 
 * @author Nicolas Roduit, Benoit Jacquemoud
 */
public class RectangleGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(RectangleGraphic.class.getResource("/icon/22x22/draw-rectangle.png")); //$NON-NLS-1$

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
    protected void updateStroke() {
        stroke = new BasicStroke(lineThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseevent) {
        Rectangle2D rectangle = new Rectangle2D.Double();

        if (handlePointList.size() > 1) {
            rectangle.setFrameFromDiagonal(handlePointList.get(eHandlePoint.NW.index),
                handlePointList.get(eHandlePoint.SE.index));
        }

        setShape(rectangle, mouseevent);
        updateLabel(mouseevent, getGraphics2D(mouseevent));
    }

    @Override
    protected int moveAndResizeOnDrawing(int handlePointIndex, int deltaX, int deltaY, MouseEvent mouseEvent) {
        if (handlePointIndex == -1) {
            for (Point2D point : handlePointList) {
                point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
            }
        } else {
            Rectangle2D rectangle = new Rectangle2D.Double();

            // if (!isGraphicComplete) {
            // handlePointList.get(handlePointIndex).setLocation(mouseEvent.getPoint());
            // rectangle.setFrameFromDiagonal(handlePointList.get(0), handlePointList.get(1));
            // } else {

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
            // }

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

    protected double getGraphicArea(double scaleX, double scaleY) {
        Rectangle2D rectangle = new Rectangle2D.Double();
        rectangle.setFrameFromDiagonal(handlePointList.get(eHandlePoint.NW.index),
            handlePointList.get(eHandlePoint.SE.index));

        return rectangle.getWidth() * scaleX * rectangle.getHeight() * scaleY;
    }

    @Override
    public void updateLabel(Object source, Graphics2D g2d) {
        if (labelVisible) {
            ImageElement imageElement = null;
            if (source instanceof MouseEvent) {
                imageElement = getImageElement((MouseEvent) source);
            } else if (source instanceof ImageElement) {
                imageElement = (ImageElement) source;
            }
            if (imageElement != null) {
                ArrayList<String> list = new ArrayList<String>(5);
                Unit unit = imageElement.getPixelSpacingUnit();
                list.add(Messages.getString("RectangleGraphic.area") //$NON-NLS-1$
                    + DecFormater.oneDecimalUngroup(getGraphicArea(imageElement.getPixelSizeX(),
                        imageElement.getPixelSizeY())) + " " + unit.getAbbreviation() + "2"); //$NON-NLS-1$ //$NON-NLS-2$

                PlanarImage image = imageElement.getImage();
                try {
                    ArrayList<Integer> pList = getValueFromArea(image);
                    if (pList != null && pList.size() > 0) {
                        int band = image.getSampleModel().getNumBands();
                        if (band == 1) {
                            // Hounsfield = pixelValue * rescale slope + intercept value
                            Float slope = (Float) imageElement.getTagValue(TagW.RescaleSlope);
                            Float intercept = (Float) imageElement.getTagValue(TagW.RescaleIntercept);
                            double min = Double.MAX_VALUE;
                            double max = -Double.MAX_VALUE;
                            double sum = 0;
                            for (Integer val : pList) {
                                if (val < min) {
                                    min = val;
                                }
                                if (val > max) {
                                    max = val;
                                }
                                sum += val;
                            }

                            double mean = sum / pList.size();

                            double stdv = 0.0D;
                            for (Integer val : pList) {
                                if (val < min) {
                                    min = val;
                                }
                                if (val > max) {
                                    max = val;
                                }
                                stdv += (val - mean) * (val - mean);
                            }

                            stdv = Math.sqrt(stdv / (pList.size() - 1.0));

                            if (slope != null || intercept != null) {
                                slope = slope == null ? 1.0f : slope;
                                intercept = intercept == null ? 0.0f : intercept;
                                mean = mean * slope + intercept;
                                stdv = stdv * slope + intercept;
                                min = min * slope + intercept;
                                max = max * slope + intercept;
                            }
                            String hu =
                                imageElement.getPixelValueUnit() == null ? "" : " " + imageElement.getPixelValueUnit(); //$NON-NLS-1$ //$NON-NLS-2$
                            list.add(Messages.getString("RectangleGraphic.max") + DecFormater.oneDecimalUngroup(max) + hu); //$NON-NLS-1$
                            list.add(Messages.getString("RectangleGraphic.min") + DecFormater.oneDecimalUngroup(min) + hu); //$NON-NLS-1$
                            list.add(Messages.getString("RectangleGraphic.std") + DecFormater.oneDecimalUngroup(stdv) + hu); //$NON-NLS-1$
                            list.add(Messages.getString("RectangleGraphic.mean") + DecFormater.oneDecimalUngroup(mean) + hu); //$NON-NLS-1$
                        } else {
                            // message.append("R=" + c[0] + " G=" + c[1] + " B=" + c[2]);
                        }
                    }
                } catch (ArrayIndexOutOfBoundsException ex) {
                }

                setLabel(list.toArray(new String[list.size()]), g2d);
            }
        }
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

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    static protected enum eHandlePoint {
        NONE(-1), NW(0), SE(1), NE(2), SW(3), N(4), S(5), E(6), W(7);
        // 0 and 1 must be diagonal point of rectangle

        final int index;

        eHandlePoint(int index) {
            this.index = index;
        }

        final static Map<Integer, eHandlePoint> map = new HashMap<Integer, eHandlePoint>(eHandlePoint.values().length);

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
