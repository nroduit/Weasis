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

    public final static double ARC_RADIUS = 24.0;

    public FourPointsAngleToolGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(8, paintColor, lineThickness, labelVisible);
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

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);

            if (handlePointList.size() >= 2) {
                AdvancedShape newShape = new AdvancedShape(4);
                Path2D generalpath = new Path2D.Double(Path2D.WIND_NON_ZERO, handlePointList.size());
                newShape.addShape(generalpath);

                Point2D B = handlePointList.get(1);

                generalpath.moveTo(A.getX(), A.getY());
                generalpath.lineTo(B.getX(), B.getY());

                Point2D I = GeomUtil.getMidPoint(A, B);

                if (handlePointList.size() >= 3) {
                    Point2D C = handlePointList.get(2);
                    Point2D J;

                    if (handlePointList.size() < 4) {
                        J = C;
                    } else {
                        Point2D D = handlePointList.get(3);

                        generalpath.moveTo(C.getX(), C.getY());
                        generalpath.lineTo(D.getX(), D.getY());

                        J = GeomUtil.getMidPoint(C, D);

                        if (handlePointList.size() >= 5) {
                            Point2D E = handlePointList.get(4);

                            if (handlePointList.size() >= 6) {
                                Point2D F = handlePointList.get(5);

                                generalpath.moveTo(E.getX(), E.getY());
                                generalpath.lineTo(F.getX(), F.getY());

                                Point2D K = GeomUtil.getMidPoint(E, F);
                                Point2D L;

                                if (handlePointList.size() < 7) {
                                    L = GeomUtil.getPerpendicularPointFromLine(E, F, K, 1.0);
                                } else {
                                    Point2D G = handlePointList.get(6);

                                    if (handlePointList.size() < 8) {
                                        L = G;
                                    } else {
                                        Point2D H = handlePointList.get(7);

                                        generalpath.moveTo(G.getX(), G.getY());
                                        generalpath.lineTo(H.getX(), H.getY());

                                        L = GeomUtil.getMidPoint(G, H);
                                    }

                                    generalpath.append(new Line2D.Double(K, L), false);
                                }

                                Point2D P = GeomUtil.getIntersectPoint(I, J, K, L);

                                if (P != null) {
                                    newShape.addShape(new Line2D.Double(J, P), getDashStroke(1.0f), true);
                                    newShape.addShape(new Line2D.Double(K, P), getDashStroke(1.0f), true);

                                    double startingAngle = GeomUtil.getAngleDeg(P, J);
                                    double angularExtent = GeomUtil.getAngleDeg(J, P, K);
                                    angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);

                                    double radius = ARC_RADIUS;
                                    Rectangle2D arcAngleBounds =
                                        new Rectangle2D.Double(P.getX() - radius, P.getY() - radius, 2 * radius,
                                            2 * radius);

                                    Shape arcAngle =
                                        new Arc2D.Double(arcAngleBounds, startingAngle, angularExtent, Arc2D.OPEN);

                                    double rMax = Math.min(P.distance(I), P.distance(J));
                                    rMax = (2.0 / 3.0) * Math.min(rMax, Math.min(P.distance(K), P.distance(L)));

                                    newShape.addInvShape(arcAngle, (Point2D) P.clone(), radius / rMax,
                                        getDashStroke(1.0f), true);
                                }
                            }
                        }
                    }

                    generalpath.append(new Line2D.Double(I, J), false);
                }
                setShape(newShape, mouseEvent);
                updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
            }
        }
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent) {
        if (imageElement != null && handlePointList.size() >= 8) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();
            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();
                if (Angle.isComputed()) {
                    Point2D A = handlePointList.get(0);
                    Point2D B = handlePointList.get(1);
                    Point2D C = handlePointList.get(2);
                    Point2D D = handlePointList.get(3);
                    Point2D E = handlePointList.get(4);
                    Point2D F = handlePointList.get(5);
                    Point2D G = handlePointList.get(6);
                    Point2D H = handlePointList.get(7);

                    Point2D I = GeomUtil.getMidPoint(A, B);
                    Point2D J = GeomUtil.getMidPoint(C, D);
                    Point2D K = GeomUtil.getMidPoint(E, F);
                    Point2D L = GeomUtil.getMidPoint(G, H);

                    Point2D P = GeomUtil.getIntersectPoint(I, J, K, L);

                    double realAngle = Math.abs(GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(J, P, K)));

                    if (Angle.isComputed() && (releaseEvent || Angle.isGraphicLabel())) {
                        measVal.add(new MeasureItem(Angle, realAngle, "deg"));
                    }
                    if (ComplementaryAngle.isComputed() && (releaseEvent || ComplementaryAngle.isGraphicLabel())) {
                        measVal.add(new MeasureItem(ComplementaryAngle, 180.0 - realAngle, "deg"));
                    }
                }
                return measVal;
            }
        }
        return null;
    }
}
