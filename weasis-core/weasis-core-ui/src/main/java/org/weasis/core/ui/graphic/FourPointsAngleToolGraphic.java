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
public class FourPointsAngleToolGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(
        FourPointsAngleToolGraphic.class.getResource("/icon/22x22/draw-4p-angle.png")); //$NON-NLS-1$

    public final static Measurement Angle = new Measurement("Angle", true);
    public final static Measurement ComplementaryAngle = new Measurement("Compl. Angle", true);

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    Point2D A, B, C, D; // Let AB & CD two line segments which define the median line IJ
    Point2D I, J; // Let I,J be the middle points of AB & CD line segments

    Point2D E, F, G, H; // Let EF & GH two line segments which define the median line KL
    Point2D K, L; // Let K,L be the middle points of EF & GH line segments

    Point2D P; // Let P be the intersection point, if exist, of the two line segments IJ & KL

    Point2D[] IJPline; // (IJP) or (JIP) or (IPJ) or (JPI) <= ordered array of points along IJ segment.
    Point2D[] KLPline; // (KLP) or (LKP) or (KPL) or (LPK) <= ordered array of points along KL segment.

    boolean lineParallel; // estimate if IJ & KL line segments are parallel not not
    boolean intersectIJsegment; // estimate if intersection point, if exist, is inside IJ segment or not
    boolean intersectKLsegment; // estimate if intersection point, if exist, is inside KL segment or not

    boolean ABvalid, CDvalid, EFvalid, GHvalid, IJvalid, KLvalid; // estimate if line segments are valid or not

    double angleDeg; // smallest angle in Degrees in the range of [-180 ; 180] between IJ & KL line segments

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public FourPointsAngleToolGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(8, paintColor, lineThickness, labelVisible);
        init();
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return "Four Points Angle Tool";
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

        if (IJvalid)
            path.append(new Line2D.Double(I, J), false);

        if (EFvalid)
            path.append(new Line2D.Double(E, F), false);

        if (GHvalid)
            path.append(new Line2D.Double(G, H), false);

        if (KLvalid)
            path.append(new Line2D.Double(K, L), false);

        // Do not show decoration when lines are nearly parallel
        // Can cause stack overflow BUG on paint method when drawing infinite line with DashStroke
        if (IJvalid && KLvalid && !lineParallel && Math.abs(angleDeg) > 0.1) {

            AdvancedShape aShape = (AdvancedShape) (newShape = new AdvancedShape(4));
            aShape.addShape(path);

            // Let arcAngle be the partial section of the ellipse that represents the measured angle
            double startingAngle = GeomUtil.getAngleDeg(P, IJPline[0]);

            double radius = 32;
            Rectangle2D arcAngleBounds =
                new Rectangle2D.Double(P.getX() - radius, P.getY() - radius, 2 * radius, 2 * radius);

            Shape arcAngle = new Arc2D.Double(arcAngleBounds, startingAngle, angleDeg, Arc2D.OPEN);

            double rMax = Math.min(P.distance(IJPline[0]), P.distance(KLPline[0])) * 2 / 3;
            double scalingMin = radius / rMax;

            aShape.addInvShape(arcAngle, P, scalingMin, true);

            if (!intersectIJsegment)
                aShape.addShape(new Line2D.Double(P, IJPline[1]), getDashStroke(1.0f), true);

            if (!intersectKLsegment)
                aShape.addShape(new Line2D.Double(P, KLPline[1]), getDashStroke(1.0f), true);

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

                if (Angle.isComputed() || ComplementaryAngle.isComputed()) {

                    double positiveAngle = Math.abs(angleDeg);

                    if (Angle.isComputed() && (!drawOnLabel || Angle.isGraphicLabel()))
                        measVal.add(new MeasureItem(Angle, positiveAngle, "deg"));

                    if (ComplementaryAngle.isComputed() && (!drawOnLabel || ComplementaryAngle.isGraphicLabel()))
                        measVal.add(new MeasureItem(ComplementaryAngle, 180.0 - positiveAngle, "deg"));
                }
                return measVal;
            }
        }
        return null;
    }

    @Override
    protected boolean isShapeValid() {
        updateTool();
        return (ABvalid && CDvalid && EFvalid && GHvalid && IJvalid && KLvalid);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void init() {
        A = handlePointList.size() >= 1 ? handlePointList.get(0) : null;
        B = handlePointList.size() >= 2 ? handlePointList.get(1) : null;
        C = handlePointList.size() >= 3 ? handlePointList.get(2) : null;
        D = handlePointList.size() >= 4 ? handlePointList.get(3) : null;

        I = A;
        J = C;

        E = handlePointList.size() >= 5 ? handlePointList.get(4) : null;
        F = handlePointList.size() >= 6 ? handlePointList.get(5) : null;
        G = handlePointList.size() >= 7 ? handlePointList.get(6) : null;
        H = handlePointList.size() >= 8 ? handlePointList.get(7) : null;

        K = E;
        L = G;

        IJPline = KLPline = null;

        lineParallel = intersectIJsegment = intersectKLsegment = false;
        ABvalid = CDvalid = EFvalid = GHvalid = IJvalid = KLvalid = false;

        angleDeg = 0.0;
    }

    protected void updateTool() {
        init();

        if (ABvalid = (A != null && B != null && !B.equals(A)))
            I = GeomUtil.getMidPoint(A, B);

        if (CDvalid = (C != null && D != null && !C.equals(D)))
            J = GeomUtil.getMidPoint(C, D);

        IJvalid = (I != null && J != null && !I.equals(J));

        if (EFvalid = (E != null && F != null && !E.equals(F)))
            K = GeomUtil.getMidPoint(E, F);

        if (GHvalid = (G != null && H != null && !G.equals(H)))
            L = GeomUtil.getMidPoint(G, H);
        else if (G == null && EFvalid)
            L = GeomUtil.getPerpendicularPointFromLine(E, F, K, 1.0); // temporary before GHvalid

        KLvalid = (K != null && L != null && !K.equals(L));

        if (IJvalid && KLvalid) {

            double denominator =
                (J.getX() - I.getX()) * (L.getY() - K.getY()) - (J.getY() - I.getY()) * (L.getX() - K.getX());

            lineParallel = (denominator == 0); // If denominator is zero, IJ & KL are parallel

            if (!lineParallel) {

                double numerator1 =
                    (I.getY() - K.getY()) * (L.getX() - K.getX()) - (I.getX() - K.getX()) * (L.getY() - K.getY());
                double numerator2 =
                    (I.getY() - K.getY()) * (J.getX() - I.getX()) - (I.getX() - K.getX()) * (J.getY() - I.getY());

                double r = numerator1 / denominator; // equ1
                double s = numerator2 / denominator; // equ2

                P = new Point2D.Double(I.getX() + r * (J.getX() - I.getX()), I.getY() + r * (J.getY() - I.getY()));

                // If 0<=r<=1 & 0<=s<=1, segment intersection exists
                // If r<0 or r>1 or s<0 or s>1, line segments intersect but not segments

                // If r>1, P is located on extension of IJ
                // If r<0, P is located on extension of JI
                // If s>1, P is located on extension of KL
                // If s<0, P is located on extension of LK

                IJPline = new Point2D[3]; // order can be IJP (r>1) or JIP (r<0) or IPJ / JPI (0<=r<=1)
                KLPline = new Point2D[3]; // order can be KLP (s>1) or LKP (s<0) or KPL / LPK (0<=s<=1)

                intersectIJsegment = (r >= 0 && r <= 1) ? true : false; // means IJPline[1].equals(P)
                intersectKLsegment = (s >= 0 && s <= 1) ? true : false; // means KLPline[1].equals(P)

                IJPline[0] = r >= 0 ? I : J;
                IJPline[1] = r < 0 ? I : r > 1 ? J : P;
                IJPline[2] = r < 0 ? P : r > 1 ? P : J;

                if (intersectIJsegment) {
                    if (P.distance(IJPline[0]) < P.distance(IJPline[2])) {
                        Point2D switchPt = (Point2D) IJPline[2].clone();
                        IJPline[2] = (Point2D) IJPline[0].clone();
                        IJPline[0] = switchPt;
                    }
                } else if (P.distance(IJPline[0]) < P.distance(IJPline[1])) {
                    Point2D switchPt = (Point2D) IJPline[1].clone();
                    IJPline[1] = (Point2D) IJPline[0].clone();
                    IJPline[0] = switchPt;
                }

                KLPline[0] = s >= 0 ? K : L;
                KLPline[1] = s < 0 ? K : s > 1 ? L : P;
                KLPline[2] = s < 0 ? P : s > 1 ? P : L;

                if (intersectKLsegment) {
                    if (P.distance(KLPline[0]) < P.distance(KLPline[2])) {
                        Point2D switchPt = (Point2D) KLPline[2].clone();
                        KLPline[2] = (Point2D) KLPline[0].clone();
                        KLPline[0] = switchPt;
                    }
                } else if (P.distance(KLPline[0]) < P.distance(KLPline[1])) {
                    Point2D switchPt = (Point2D) KLPline[1].clone();
                    KLPline[1] = (Point2D) KLPline[0].clone();
                    KLPline[0] = switchPt;
                }

                angleDeg = GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(IJPline[0], P, KLPline[0]));
            }
        }
    }
}
