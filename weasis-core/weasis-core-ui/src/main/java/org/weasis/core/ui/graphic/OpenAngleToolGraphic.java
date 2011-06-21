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
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Benoit Jacquemoud
 */
public class OpenAngleToolGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(
        OpenAngleToolGraphic.class.getResource("/icon/22x22/draw-open-angle.png")); //$NON-NLS-1$

    public final static Measurement Angle = new Measurement("Angle", true);
    public final static Measurement ComplementaryAngle = new Measurement("Compl. Angle", true);

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    protected Point2D A, B, C, D; // Let AB & CD two line segments

    protected Point2D P; // Let P be the intersection point, if exist, of the two line segments AB & CD

    protected Point2D[] ABPline; // (ABP) or (BAP) or (APB) or (BPA) <= ordered array of points along AB segment.
    protected Point2D[] CDPline; // (CDP) or (DCP) or (CPD) or (DPC) <= ordered array of points along CD segment.

    protected boolean lineParallel; // estimate if AB & CD line segments are parallel not not
    protected boolean intersectABsegment; // estimate if intersection point, if exist, is inside AB segment or not
    protected boolean intersectCDsegment; // estimate if intersection point, if exist, is inside CD segment or not

    protected boolean ABvalid, CDvalid; // estimate if line segments are valid or not

    protected double angleDeg; // smallest angle in Degrees in the range of [-180 ; 180] between AB & CD line segments

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public OpenAngleToolGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        this(4, lineThickness, paintColor, labelVisible);
    }

    protected OpenAngleToolGraphic(int handlePointTotalNumber, float lineThickness, Color paintColor,
        boolean labelVisible) {
        super(handlePointTotalNumber, paintColor, lineThickness, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return "Open Angle";
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {

        updateTool();

        Shape newShape = null;
        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 6);

        if (ABvalid)
            path.append(new Line2D.Double(A, B), false);

        if (CDvalid)
            path.append(new Line2D.Double(C, D), false);

        // Do not show decoration when lines are nearly parallel
        // Can cause stack overflow BUG on paint method when drawing infinite line with DashStroke
        if (ABvalid && CDvalid && !lineParallel && Math.abs(angleDeg) > 0.1) {

            AdvancedShape aShape = (AdvancedShape) (newShape = new AdvancedShape(5));
            aShape.addShape(path);

            // Let arcAngle be the partial section of the ellipse that represents the measured angle
            // Let Ix,Jx points of line segments AB & CD used to compute arcAngle radius
            Point2D I1 = GeomUtil.getColinearPointWithRatio(ABPline[1], ABPline[0], 0.25);
            Point2D J1 = GeomUtil.getColinearPointWithRatio(CDPline[1], CDPline[0], 0.25);
            Point2D I2 = GeomUtil.getColinearPointWithRatio(ABPline[0], ABPline[1], 0.25);
            Point2D J2 = GeomUtil.getColinearPointWithRatio(CDPline[0], CDPline[1], 0.25);

            double maxRadius = Math.min(P.distance(I2), P.distance(J2));
            double radius = Math.min(maxRadius, (P.distance(I1) + P.distance(J1)) / 2);

            double startingAngle = GeomUtil.getAngleDeg(P, ABPline[0]);

            Rectangle2D arcAngleBounds =
                new Rectangle2D.Double(P.getX() - radius, P.getY() - radius, 2 * radius, 2 * radius);

            Arc2D arcAngle = new Arc2D.Double(arcAngleBounds, startingAngle, angleDeg, Arc2D.OPEN);
            aShape.addShape(arcAngle);

            if (!intersectABsegment)
                aShape.addShape(new Line2D.Double(P, ABPline[1]), getDashStroke(1.0f), true);

            if (!intersectCDsegment)
                aShape.addShape(new Line2D.Double(P, CDPline[1]), getDashStroke(1.0f), true);

            // Let intersectPtShape be a cross lines inside a circle
            int iPtSize = 8;
            Path2D intersectPtShape = new Path2D.Double(Path2D.WIND_NON_ZERO, 5);

            Rectangle2D intersecPtBounds =
                new Rectangle2D.Double(P.getX() - (iPtSize / 2.0), P.getY() - (iPtSize / 2.0), iPtSize, iPtSize);

            intersectPtShape.append(new Line2D.Double(P.getX() - iPtSize, P.getY(), P.getX() - 2, P.getY()), false);
            intersectPtShape.append(new Line2D.Double(P.getX() + 2, P.getY(), P.getX() + iPtSize, P.getY()), false);
            intersectPtShape.append(new Line2D.Double(P.getX(), P.getY() - iPtSize, P.getX(), P.getY() - 2), false);
            intersectPtShape.append(new Line2D.Double(P.getX(), P.getY() + 2, P.getX(), P.getY() + iPtSize), false);

            intersectPtShape.append(new Arc2D.Double(intersecPtBounds, 0, 360, Arc2D.OPEN), false);

            aShape.addInvShape(intersectPtShape, P, getStroke(0.5f), true);

        } else if (path.getCurrentPoint() != null)
            newShape = path;

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {

        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>(2);

                double positiveAngle = Math.abs(angleDeg);

                if (Angle.isComputed() && (!drawOnLabel || Angle.isGraphicLabel()))
                    measVal.add(new MeasureItem(Angle, positiveAngle, "deg"));

                if (ComplementaryAngle.isComputed() && (!drawOnLabel || ComplementaryAngle.isGraphicLabel()))
                    measVal.add(new MeasureItem(ComplementaryAngle, 180.0 - positiveAngle, "deg"));

                return measVal;
            }
        }
        return null;
    }

    @Override
    protected boolean isShapeValid() {
        updateTool();
        return (ABvalid && CDvalid);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateTool() {

        A = handlePointList.size() >= 1 ? handlePointList.get(0) : null;
        B = handlePointList.size() >= 2 ? handlePointList.get(1) : null;
        C = handlePointList.size() >= 3 ? handlePointList.get(2) : null;
        D = handlePointList.size() >= 4 ? handlePointList.get(3) : null;

        ABPline = CDPline = null;

        lineParallel = intersectABsegment = intersectCDsegment = false;

        angleDeg = 0.0;

        ABvalid = (A != null && B != null && !B.equals(A));
        CDvalid = (C != null && D != null && !C.equals(D));

        if (ABvalid && CDvalid) {

            double denominator =
                (B.getX() - A.getX()) * (D.getY() - C.getY()) - (B.getY() - A.getY()) * (D.getX() - C.getX());

            lineParallel = (denominator == 0); // If denominator is zero, AB & CD are parallel

            if (!lineParallel) {

                double numerator1 =
                    (A.getY() - C.getY()) * (D.getX() - C.getX()) - (A.getX() - C.getX()) * (D.getY() - C.getY());
                double numerator2 =
                    (A.getY() - C.getY()) * (B.getX() - A.getX()) - (A.getX() - C.getX()) * (B.getY() - A.getY());

                double r = numerator1 / denominator; // equ1
                double s = numerator2 / denominator; // equ2

                P = new Point2D.Double(A.getX() + r * (B.getX() - A.getX()), A.getY() + r * (B.getY() - A.getY()));

                // If 0<=r<=1 & 0<=s<=1, segment intersection exists
                // If r<0 or r>1 or s<0 or s>1, line segments intersect but not segments

                // If r>1, P is located on extension of AB
                // If r<0, P is located on extension of BA
                // If s>1, P is located on extension of CD
                // If s<0, P is located on extension of DC

                ABPline = new Point2D[3]; // order can be ABP (r>1) or BAP (r<0) or APB / BPA (0<=r<=1)
                CDPline = new Point2D[3]; // order can be CDP (s>1) or DCP (s<0) or CPD / DPC (0<=s<=1)

                intersectABsegment = (r >= 0 && r <= 1) ? true : false; // means ABPline[1].equals(P)
                intersectCDsegment = (s >= 0 && s <= 1) ? true : false; // means CDPline[1].equals(P)

                ABPline[0] = r >= 0 ? A : B;
                ABPline[1] = r < 0 ? A : r > 1 ? B : P;
                ABPline[2] = r < 0 ? P : r > 1 ? P : B;

                if (intersectABsegment) {
                    if (P.distance(ABPline[0]) < P.distance(ABPline[2])) {
                        Point2D switchPt = (Point2D) ABPline[2].clone();
                        ABPline[2] = (Point2D) ABPline[0].clone();
                        ABPline[0] = switchPt;
                    }
                } else if (P.distance(ABPline[0]) < P.distance(ABPline[1])) {
                    Point2D switchPt = (Point2D) ABPline[1].clone();
                    ABPline[1] = (Point2D) ABPline[0].clone();
                    ABPline[0] = switchPt;
                }

                CDPline[0] = s >= 0 ? C : D;
                CDPline[1] = s < 0 ? C : s > 1 ? D : P;
                CDPline[2] = s < 0 ? P : s > 1 ? P : D;

                if (intersectCDsegment) {
                    if (P.distance(CDPline[0]) < P.distance(CDPline[2])) {
                        Point2D switchPt = (Point2D) CDPline[2].clone();
                        CDPline[2] = (Point2D) CDPline[0].clone();
                        CDPline[0] = switchPt;
                    }
                } else if (P.distance(CDPline[0]) < P.distance(CDPline[1])) {
                    Point2D switchPt = (Point2D) CDPline[1].clone();
                    CDPline[1] = (Point2D) CDPline[0].clone();
                    CDPline[0] = switchPt;
                }

                angleDeg = GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(ABPline[0], P, CDPline[0]));
            }
        }
    }

}
