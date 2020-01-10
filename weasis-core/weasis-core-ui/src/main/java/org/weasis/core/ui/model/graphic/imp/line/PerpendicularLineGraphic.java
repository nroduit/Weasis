/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.graphic.imp.line;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.graphic.AbstractDragGraphic;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlType(name = "perpendicularLine")
@XmlRootElement(name = "perpendicularLine")
public class PerpendicularLineGraphic extends AbstractDragGraphic {
    private static final long serialVersionUID = -7056437654935777004L;

    public static final Integer POINTS_NUMBER = 4;

    public static final Icon ICON =
        new ImageIcon(PerpendicularLineGraphic.class.getResource("/icon/22x22/draw-perpendicular.png")); //$NON-NLS-1$

    public static final Measurement LINE_LENGTH =
        new Measurement(Messages.getString("measure.length"), 1, true, true, true); //$NON-NLS-1$
    public static final Measurement ORIENTATION =
        new Measurement(Messages.getString("measure.orientation"), 2, true, true, false); //$NON-NLS-1$
    public static final Measurement AZIMUTH =
        new Measurement(Messages.getString("measure.azimuth"), 3, true, true, false); //$NON-NLS-1$

    protected static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();
    static {
        MEASUREMENT_LIST.add(LINE_LENGTH);
        MEASUREMENT_LIST.add(ORIENTATION);
        MEASUREMENT_LIST.add(AZIMUTH);
    }

    // Let AB & CD two perpendicular line segments with D being the projected point C on AB
    protected Point2D.Double ptA;
    protected Point2D.Double ptB;
    protected Point2D.Double ptC;
    protected Point2D.Double ptD;

    // estimate if line segments are valid or not
    protected boolean lineABvalid;
    protected boolean lineCDvalid;

    public PerpendicularLineGraphic() {
        super(POINTS_NUMBER);
    }

    public PerpendicularLineGraphic(PerpendicularLineGraphic graphic) {
        super(graphic);
    }

    @Override
    public PerpendicularLineGraphic copy() {
        return new PerpendicularLineGraphic(this);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("measure.perpendicular"); //$NON-NLS-1$
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

        List<Point2D> prevHandlePointList = getHandlePointList();

        handlePointIndex = super.moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, mouseEvent);

        if (handlePointIndex >= 0 && handlePointIndex < getHandlePointListSize()) {
            updateTool();

            if (handlePointIndex == 0 || handlePointIndex == 1) { // drag point is A or B

                Point2D prevPtA = (!prevHandlePointList.isEmpty()) ? prevHandlePointList.get(0) : null;
                Point2D prevPtB = (prevHandlePointList.size() > 1) ? prevHandlePointList.get(1) : null;

                if (lineABvalid && GeomUtil.isLineValid(prevPtA, prevPtB) && ptC != null && ptD != null) {

                    // compute rotation from previous to actual position
                    double theta = GeomUtil.getAngleRad(prevPtA, prevPtB) - GeomUtil.getAngleRad(ptA, ptB);

                    Point2D anchor = (handlePointIndex == 0) ? ptB : ptA; // anchor is opposite point of A or B
                    AffineTransform rotate = AffineTransform.getRotateInstance(theta, anchor.getX(), anchor.getY());

                    rotate.transform(ptC, ptC);
                    rotate.transform(ptD, ptD);

                    setHandlePoint(2, ptC);
                    setHandlePoint(3, ptD);
                }

            } else if (handlePointIndex == 2) { // drag point is C

                if (lineABvalid && ptC != null) {
                    ptD = GeomUtil.getPerpendicularPointToLine(ptA, ptB, ptC);

                    setHandlePoint(3, ptD);
                }

            } else if (handlePointIndex == 3) { // drag point is D

                Point2D prevPtD = (prevHandlePointList.size() > 3) ? prevHandlePointList.get(3) : null;

                if (lineABvalid && ptD != null && prevPtD != null && ptC != null) {
                    ptD = GeomUtil.getPerpendicularPointToLine(ptA, ptB, ptD);

                    AffineTransform translate =
                        AffineTransform.getTranslateInstance(ptD.getX() - prevPtD.getX(), ptD.getY() - prevPtD.getY());
                    translate.transform(ptC, ptC);

                    setHandlePoint(2, ptC);
                    setHandlePoint(3, ptD);
                }
            }
        }

        return handlePointIndex;
    }

    @Override
    public void buildShape(MouseEventDouble mouseEvent) {

        updateTool();

        Shape newShape = null;
        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 2);

        if (lineABvalid) {
            path.append(new Line2D.Double(ptA, ptB), false);
        }

        if (lineCDvalid) {
            path.append(new Line2D.Double(ptC, ptD), false);
        }

        if (lineABvalid && lineCDvalid) {

            newShape = new AdvancedShape(this, 3);
            AdvancedShape aShape = (AdvancedShape) newShape;
            aShape.addShape(path);

            if (!ptD.equals(ptA) && !ptD.equals(ptB)) {
                // Check D is outside of AB segment
                if (MathUtil.isEqual(Math.signum(GeomUtil.getAngleDeg(ptD, ptA)),
                    Math.signum(GeomUtil.getAngleDeg(ptD, ptB)))) {
                    Point2D ptE = ptD.distance(ptA) < ptD.distance(ptB) ? ptA : ptB;
                    aShape.addShape(new Line2D.Double(ptD, ptE), getDashStroke(1.0f), true);
                }
            }

            double cornerLength = 10;
            double dMin = Math.min(ptD.distance(ptC), Math.max(ptD.distance(ptA), ptD.distance(ptB))) * 2 / 3;
            double scalingMin = cornerLength / dMin;

            Point2D f = GeomUtil.getMidPoint(ptA, ptB);
            Shape cornerShape = GeomUtil.getCornerShape(f, ptD, ptC, cornerLength);
            if (cornerShape != null) {
                aShape.addScaleInvShape(cornerShape, ptD, scalingMin, getStroke(1.0f), true);
            }

        } else if (path.getCurrentPoint() != null) {
            newShape = path;
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));

    }

    @Override
    public List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {

        if (layer != null && layer.hasContent() && isShapeValid()) {
            MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<>(3);

                if (LINE_LENGTH.getComputed()) {
                    measVal.add(
                        new MeasureItem(LINE_LENGTH, ptC.distance(ptD) * adapter.getCalibRatio(), adapter.getUnit()));
                }
                if (ORIENTATION.getComputed()) {
                    measVal.add(new MeasureItem(ORIENTATION, MathUtil.getOrientation(ptC, ptD),
                        Messages.getString("measure.deg"))); //$NON-NLS-1$
                }
                if (AZIMUTH.getComputed()) {
                    measVal.add(
                        new MeasureItem(AZIMUTH, MathUtil.getAzimuth(ptC, ptD), Messages.getString("measure.deg"))); //$NON-NLS-1$
                }
                return measVal;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isShapeValid() {
        updateTool();
        return lineABvalid && lineCDvalid;
    }

    protected void updateTool() {
        ptA = getHandlePoint(0);
        ptB = getHandlePoint(1);
        ptC = getHandlePoint(2);
        ptD = getHandlePoint(3);

        lineABvalid = ptA != null && ptB != null && !ptB.equals(ptA);
        lineCDvalid = ptC != null && ptD != null && !ptC.equals(ptD);
    }

    @Override
    public List<Measurement> getMeasurementList() {
        return MEASUREMENT_LIST;
    }
}
