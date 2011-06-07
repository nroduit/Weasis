/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Benoit Jacquemoud
 ******************************************************************************/
package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.MouseEventDouble;

public class LineGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(LineGraphic.class.getResource("/icon/22x22/draw-line.png")); //$NON-NLS-1$

    public final static Measurement FirstPointX = new Measurement("First point X", true, true, false);
    public final static Measurement FirstPointY = new Measurement("First point Y", true, true, false);
    public final static Measurement LastPointX = new Measurement("Last point X", true, true, false);
    public final static Measurement LastPointY = new Measurement("Last point Y", true, true, false);
    public final static Measurement LineLength = new Measurement("Line length", true, true, true);
    public final static Measurement Orientation = new Measurement("Orientation", true, true, false);
    public final static Measurement Azimuth = new Measurement("Azimuth", true, true, false);
    public final static Measurement ColorRGB = new Measurement("Color (RGB)", true, true, false);

    public LineGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(2, paintColor, lineThickness, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.line"); //$NON-NLS-1$
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {
        GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, handlePointList.size());

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);
            generalpath.moveTo(A.getX(), A.getY());

            if (handlePointList.size() >= 2) {
                Point2D B = handlePointList.get(1);
                generalpath.lineTo(B.getX(), B.getY());
            }
        }

        setShape(generalpath, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public LineGraphic clone() {
        return (LineGraphic) super.clone();
    }

    @Override
    public Graphic clone(double xPos, double yPos) {
        LineGraphic newGraphic = clone();
        newGraphic.updateShapeOnDrawing(null);
        return newGraphic;
    }

    public Double getSegmentLength(double scalex, double scaley) {
        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);
            if (handlePointList.size() >= 2) {
                Point2D B = handlePointList.get(1);
                return Point2D.distance(scalex * A.getX(), scaley * A.getY(), scalex * B.getX(), scaley * B.getY());
            }
        }
        return null;
    }

    public Double getSegmentOrientation() {
        Point2D p1 = getStartPoint();
        Point2D p2 = getEndPoint();
        if (p1 == null || p2 == null)
            return null;
        return MathUtil.getOrientation(p1.getX(), p1.getY(), p2.getX(), p2.getY());

    }

    public Double getSegmentAzimuth() {
        Point2D p1 = getStartPoint();
        Point2D p2 = getEndPoint();
        if (p1 == null || p2 == null)
            return null;
        return MathUtil.getAzimuth(p1.getX(), p1.getY(), p2.getX(), p2.getY());
    }

    public Point2D getStartPoint() {
        if (handlePointList.size() >= 1)
            return (Point2D) handlePointList.get(0).clone();
        return null;
    }

    public Point2D getEndPoint() {
        if (handlePointList.size() > 1)
            return (Point2D) handlePointList.get(1).clone();
        return null;
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {
        if (imageElement != null && handlePointList.size() >= 2) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();
            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();
                if (FirstPointX.isComputed() || FirstPointY.isComputed()) {
                    Point2D p = getStartPoint();
                    if (FirstPointX.isComputed() && (!drawOnLabel || FirstPointX.isGraphicLabel())) {
                        Double val =
                            releaseEvent || FirstPointX.isQuickComputing() ? adapter.getXCalibratedValue(p.getX())
                                : null;
                        measVal.add(new MeasureItem(FirstPointX, val, adapter.getUnit()));
                    }
                    if (FirstPointY.isComputed() && (!drawOnLabel || FirstPointY.isGraphicLabel())) {
                        Double val =
                            releaseEvent || FirstPointY.isQuickComputing() ? adapter.getXCalibratedValue(p.getY())
                                : null;
                        measVal.add(new MeasureItem(FirstPointY, val, adapter.getUnit()));
                    }
                }
                if (LastPointX.isComputed() || LastPointY.isComputed()) {
                    Point2D p = getEndPoint();
                    if (LastPointX.isComputed() && (!drawOnLabel || LastPointX.isGraphicLabel())) {
                        Double val =
                            releaseEvent || LastPointX.isQuickComputing() ? adapter.getXCalibratedValue(p.getX())
                                : null;
                        measVal.add(new MeasureItem(LastPointX, val, adapter.getUnit()));
                    }
                    if (LastPointY.isComputed() && (!drawOnLabel || LastPointY.isGraphicLabel())) {
                        Double val =
                            releaseEvent || LastPointY.isQuickComputing() ? adapter.getXCalibratedValue(p.getY())
                                : null;
                        measVal.add(new MeasureItem(LastPointY, val, adapter.getUnit()));
                    }
                }

                if (LineLength.isComputed() && (!drawOnLabel || LineLength.isGraphicLabel())) {
                    Double val =
                        releaseEvent || LineLength.isQuickComputing() ? getSegmentLength(adapter.getCalibRatio(),
                            adapter.getCalibRatio()) : null;
                    measVal.add(new MeasureItem(LineLength, val, adapter.getUnit()));
                }
                if (Orientation.isComputed() && (!drawOnLabel || Orientation.isGraphicLabel())) {
                    Double val = releaseEvent || Orientation.isQuickComputing() ? getSegmentOrientation() : null;
                    measVal.add(new MeasureItem(Orientation, val, "deg"));
                }
                if (Azimuth.isComputed() && (!drawOnLabel || Azimuth.isGraphicLabel())) {
                    Double val = releaseEvent || Azimuth.isQuickComputing() ? getSegmentAzimuth() : null;
                    measVal.add(new MeasureItem(Azimuth, val, "deg"));
                }
                return measVal;
            }
        }
        return null;
    }
}
