/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.model.graphic.imp.angle;

import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
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

@XmlType(name = "openAngle")
@XmlRootElement(name = "openAngle")
public class OpenAngleToolGraphic extends AbstractDragGraphic {
    private static final long serialVersionUID = -189635138276915405L;

    public static final Integer POINTS_NUMBER = 4;

    public static final Icon ICON =
        new ImageIcon(OpenAngleToolGraphic.class.getResource("/icon/22x22/draw-open-angle.png")); //$NON-NLS-1$

    public static final Measurement ANGLE = new Measurement(Messages.getString("measure.angle"), 1, true); //$NON-NLS-1$
    public static final Measurement COMPLEMENTARY_ANGLE =
        new Measurement(Messages.getString("measure.complement_angle"), 2, true, true, false); //$NON-NLS-1$
    public static final Measurement REFLEX_ANGLE =
        new Measurement(Messages.getString("AngleToolGraphic.reflex_angle"), 3, true, true, false); //$NON-NLS-1$

    protected static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();

    static {
        MEASUREMENT_LIST.add(ANGLE);
        MEASUREMENT_LIST.add(COMPLEMENTARY_ANGLE);
        MEASUREMENT_LIST.add(REFLEX_ANGLE);
    }

    // Let AB & CD two line segments
    protected Point2D ptA;
    protected Point2D ptB;
    protected Point2D ptC;
    protected Point2D ptD;

    // Let P be the intersection point, if exist, of the two line segments AB & CD
    protected Point2D ptP;

    protected Point2D[] lineABP; // (ABP) or (BAP) or (APB) or (BPA) <= ordered array of points along AB segment.
    protected Point2D[] lineCDP; // (CDP) or (DCP) or (CPD) or (DPC) <= ordered array of points along CD segment.

    protected boolean linesParallel; // estimate if AB & CD line segments are parallel not not
    protected boolean intersectABsegment; // estimate if intersection point, if exist, is inside AB segment or not
    protected boolean intersectCDsegment; // estimate if intersection point, if exist, is inside CD segment or not

    // estimate if line segments are valid or not
    protected boolean lineABvalid;
    protected boolean lineCDvalid;

    protected double angleDeg; // smallest angle in Degrees in the range of [-180 ; 180] between AB & CD line segments

    public OpenAngleToolGraphic() {
        super(POINTS_NUMBER);
    }

    public OpenAngleToolGraphic(Integer pointsNumber) {
        super(pointsNumber);
    }

    public OpenAngleToolGraphic(OpenAngleToolGraphic graphic) {
        super(graphic);
    }

    @Override
    public OpenAngleToolGraphic copy() {
        return new OpenAngleToolGraphic(this);
    }

    @Override
    public List<Measurement> getMeasurementList() {
        return MEASUREMENT_LIST;
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("measure.open_angle"); //$NON-NLS-1$
    }

    @Override
    protected void prepareShape() throws InvalidShapeException {
        if (!isShapeValid()) {
            throw new InvalidShapeException("This shape cannot be drawn"); //$NON-NLS-1$
        }
        buildShape(null);
    }

    @Override
    public boolean isShapeValid() {
        updateTool();
        return lineABvalid && lineCDvalid;
    }

    @Override
    public void buildShape(MouseEventDouble mouseEvent) {
        updateTool();

        Shape newShape = null;
        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 6);

        if (lineABvalid) {
            path.append(new Line2D.Double(ptA, ptB), false);
        }

        if (lineCDvalid) {
            path.append(new Line2D.Double(ptC, ptD), false);
        }

        // Do not show decoration when lines are nearly parallel
        // Can cause stack overflow BUG on paint method when drawing infinite line with DashStroke
        if (lineABvalid && lineCDvalid && !linesParallel && Math.abs(angleDeg) > 0.1) {
            newShape = new AdvancedShape(this, 5);
            AdvancedShape aShape = (AdvancedShape) newShape;
            aShape.addShape(path);

            // Let arcAngle be the partial section of the ellipse that represents the measured angle
            // Let Ix,Jx points of line segments AB & CD used to compute arcAngle radius
            Point2D ptI1 = GeomUtil.getColinearPointWithRatio(lineABP[1], lineABP[0], 0.25);
            Point2D ptJ1 = GeomUtil.getColinearPointWithRatio(lineCDP[1], lineCDP[0], 0.25);
            Point2D ptI2 = GeomUtil.getColinearPointWithRatio(lineABP[0], lineABP[1], 0.25);
            Point2D ptJ2 = GeomUtil.getColinearPointWithRatio(lineCDP[0], lineCDP[1], 0.25);

            double maxRadius = Math.min(ptP.distance(ptI2), ptP.distance(ptJ2));
            double radius = Math.min(maxRadius, (ptP.distance(ptI1) + ptP.distance(ptJ1)) / 2);

            double startingAngle = GeomUtil.getAngleDeg(ptP, lineABP[0]);

            Rectangle2D arcAngleBounds =
                new Rectangle2D.Double(ptP.getX() - radius, ptP.getY() - radius, 2 * radius, 2 * radius);

            Arc2D arcAngle = new Arc2D.Double(arcAngleBounds, startingAngle, angleDeg, Arc2D.OPEN);
            aShape.addShape(arcAngle, getStroke(1.0f), true);

            if (!intersectABsegment) {
                aShape.addShape(new Line2D.Double(ptP, lineABP[1]), getDashStroke(1.0f), true);
            }

            if (!intersectCDsegment) {
                aShape.addShape(new Line2D.Double(ptP, lineCDP[1]), getDashStroke(1.0f), true);
            }

            // Let intersectPtShape be a cross line point inside a circle
            int iPtSize = 8;
            Path2D intersectPtShape = new Path2D.Double(Path2D.WIND_NON_ZERO, 5);

            Rectangle2D intersecPtBounds =
                new Rectangle2D.Double(ptP.getX() - (iPtSize / 2.0), ptP.getY() - (iPtSize / 2.0), iPtSize, iPtSize);

            intersectPtShape.append(new Line2D.Double(ptP.getX() - iPtSize, ptP.getY(), ptP.getX() - 2, ptP.getY()),
                false);
            intersectPtShape.append(new Line2D.Double(ptP.getX() + 2, ptP.getY(), ptP.getX() + iPtSize, ptP.getY()),
                false);
            intersectPtShape.append(new Line2D.Double(ptP.getX(), ptP.getY() - iPtSize, ptP.getX(), ptP.getY() - 2),
                false);
            intersectPtShape.append(new Line2D.Double(ptP.getX(), ptP.getY() + 2, ptP.getX(), ptP.getY() + iPtSize),
                false);

            intersectPtShape.append(new Arc2D.Double(intersecPtBounds, 0, 360, Arc2D.OPEN), false);

            aShape.addScaleInvShape(intersectPtShape, ptP, getStroke(0.5f), true);

        } else if (path.getCurrentPoint() != null) {
            newShape = path;
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    protected void updateTool() {
        ptA = getHandlePoint(0);
        ptB = getHandlePoint(1);
        ptC = getHandlePoint(2);
        ptD = getHandlePoint(3);

        lineABP = lineCDP = null;
        linesParallel = intersectABsegment = intersectCDsegment = false;
        angleDeg = 0.0;

        lineABvalid = ptA != null && ptB != null && !ptB.equals(ptA);
        lineCDvalid = ptC != null && ptD != null && !ptC.equals(ptD);

        if (lineABvalid && lineCDvalid) {

            double denominator = (ptB.getX() - ptA.getX()) * (ptD.getY() - ptC.getY())
                - (ptB.getY() - ptA.getY()) * (ptD.getX() - ptC.getX());

            linesParallel = MathUtil.isEqualToZero(denominator); // If denominator is zero, AB & CD are parallel

            if (!linesParallel) {

                double numerator1 = (ptA.getY() - ptC.getY()) * (ptD.getX() - ptC.getX())
                    - (ptA.getX() - ptC.getX()) * (ptD.getY() - ptC.getY());
                double numerator2 = (ptA.getY() - ptC.getY()) * (ptB.getX() - ptA.getX())
                    - (ptA.getX() - ptC.getX()) * (ptB.getY() - ptA.getY());

                double r = numerator1 / denominator; // equ1
                double s = numerator2 / denominator; // equ2

                ptP = new Point2D.Double(ptA.getX() + r * (ptB.getX() - ptA.getX()),
                    ptA.getY() + r * (ptB.getY() - ptA.getY()));

                // If 0<=r<=1 & 0<=s<=1, segment intersection exists
                // If r<0 or r>1 or s<0 or s>1, line segments intersect but not segments

                // If r>1, P is located on extension of AB
                // If r<0, P is located on extension of BA
                // If s>1, P is located on extension of CD
                // If s<0, P is located on extension of DC

                lineABP = new Point2D[3]; // order can be ABP (r>1) or BAP (r<0) or APB / BPA (0<=r<=1)
                lineCDP = new Point2D[3]; // order can be CDP (s>1) or DCP (s<0) or CPD / DPC (0<=s<=1)

                intersectABsegment = (r >= 0 && r <= 1); // means lineABP[1].equals(P)
                intersectCDsegment = (s >= 0 && s <= 1); // means lineCDP[1].equals(P)

                lineABP[0] = r >= 0 ? ptA : ptB;
                lineABP[1] = r < 0 ? ptA : r > 1 ? ptB : ptP;
                lineABP[2] = r < 0 ? ptP : r > 1 ? ptP : ptB;

                if (intersectABsegment) {
                    if (ptP.distance(lineABP[0]) < ptP.distance(lineABP[2])) {
                        Point2D switchPt = (Point2D) lineABP[2].clone();
                        lineABP[2] = (Point2D) lineABP[0].clone();
                        lineABP[0] = switchPt;
                    }
                } else if (ptP.distance(lineABP[0]) < ptP.distance(lineABP[1])) {
                    Point2D switchPt = (Point2D) lineABP[1].clone();
                    lineABP[1] = (Point2D) lineABP[0].clone();
                    lineABP[0] = switchPt;
                }

                lineCDP[0] = s >= 0 ? ptC : ptD;
                lineCDP[1] = s < 0 ? ptC : s > 1 ? ptD : ptP;
                lineCDP[2] = s < 0 ? ptP : s > 1 ? ptP : ptD;

                if (intersectCDsegment) {
                    if (ptP.distance(lineCDP[0]) < ptP.distance(lineCDP[2])) {
                        Point2D switchPt = (Point2D) lineCDP[2].clone();
                        lineCDP[2] = (Point2D) lineCDP[0].clone();
                        lineCDP[0] = switchPt;
                    }
                } else if (ptP.distance(lineCDP[0]) < ptP.distance(lineCDP[1])) {
                    Point2D switchPt = (Point2D) lineCDP[1].clone();
                    lineCDP[1] = (Point2D) lineCDP[0].clone();
                    lineCDP[0] = switchPt;
                }

                angleDeg = GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(lineABP[0], ptP, lineCDP[0]));
            }
        }
    }

    @Override
    public List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
        if (layer != null && layer.hasContent() && isShapeValid()) {
            MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<>();

                double positiveAngle = Math.abs(angleDeg);

                if (ANGLE.getComputed()) {
                    measVal.add(new MeasureItem(ANGLE, positiveAngle, Messages.getString("measure.deg"))); //$NON-NLS-1$
                }

                if (COMPLEMENTARY_ANGLE.getComputed()) {
                    measVal.add(
                        new MeasureItem(COMPLEMENTARY_ANGLE, 180.0 - positiveAngle, Messages.getString("measure.deg"))); //$NON-NLS-1$
                }
                if (REFLEX_ANGLE.getComputed()) {
                    measVal
                        .add(new MeasureItem(REFLEX_ANGLE, 360.0 - positiveAngle, Messages.getString("measure.deg"))); //$NON-NLS-1$
                }
                return measVal;
            }
        }
        return Collections.emptyList();
    }

}
