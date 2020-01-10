/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.graphic.imp.angle;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlType(name = "cobbAngle")
@XmlRootElement(name = "cobbAngle")
public class CobbAngleToolGraphic extends OpenAngleToolGraphic {
    private static final long serialVersionUID = -5516094184442711688L;

    public static final Integer POINTS_NUMBER = 5;

    public static final Icon ICON = new ImageIcon(CobbAngleToolGraphic.class.getResource("/icon/22x22/draw-cobb.png")); //$NON-NLS-1$

    public static final Measurement ANGLE = new Measurement(Messages.getString("measure.angle"), 1, true); //$NON-NLS-1$
    public static final Measurement COMPLEMENTARY_ANGLE =
        new Measurement(Messages.getString("measure.complement_angle"), 2, true, true, false); //$NON-NLS-1$

    protected static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();

    static {
        MEASUREMENT_LIST.add(ANGLE);
        MEASUREMENT_LIST.add(COMPLEMENTARY_ANGLE);
    }

    // Let O be center of perpendicular projections in Cobb's angle
    protected Point2D.Double ptO;

    public CobbAngleToolGraphic() {
        super(POINTS_NUMBER);
    }

    public CobbAngleToolGraphic(CobbAngleToolGraphic graphic) {
        super(graphic);
    }

    @Override
    public CobbAngleToolGraphic copy() {
        return new CobbAngleToolGraphic(this);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("measure.coobs"); //$NON-NLS-1$
    }

    @Override
    public List<Measurement> getMeasurementList() {
        return MEASUREMENT_LIST;
    }

    @Override
    public Integer moveAndResizeOnDrawing(Integer handlePointIndex, Double deltaX, Double deltaY,
        MouseEventDouble mouseEvent) {
        handlePointIndex = super.moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, mouseEvent);

        if (handlePointIndex != -1 && pts.size() >= 4) {
            updateTool();

            if (lineABvalid && lineCDvalid) {
                // Let MN be the bisector of the two line segments AB & CD, if parallel MN is the median line
                Line2D lineMN;

                if (linesParallel) {
                    lineMN = GeomUtil.getMedianLine(ptA, ptB, ptC, ptD);
                } else {
                    AffineTransform rotate =
                        AffineTransform.getRotateInstance(-Math.toRadians(angleDeg) / 2, ptP.getX(), ptP.getY());

                    Point2D ptM = (Point2D) lineABP[0].clone();
                    rotate.transform(ptM, ptM);
                    lineMN = new Line2D.Double(ptM, ptP);
                }

                if (handlePointIndex == 4 && ptO != null) {
                    ptO = GeomUtil.getPerpendicularPointToLine(lineMN, ptO);
                } else {
                    if (linesParallel) {
                        ptO = GeomUtil.getMidPoint(lineMN.getP1(), lineMN.getP1());
                    } else {
                        Point2D h1 = GeomUtil.getMidPoint(lineABP[1], GeomUtil.getMidPoint(lineABP[0], lineABP[1]));
                        Point2D h2 = GeomUtil.getMidPoint(lineCDP[1], GeomUtil.getMidPoint(lineCDP[0], lineCDP[1]));

                        Point2D o1 = GeomUtil.getPerpendicularPointToLine(lineMN, h1);
                        Point2D o2 = GeomUtil.getPerpendicularPointToLine(lineMN, h2);

                        ptO = GeomUtil.getMidPoint(o1, o2);
                    }
                }
            } else {
                ptO = null;
            }

            if (pts.size() < 5) {
                pts.add(ptO);
            } else {
                pts.set(4, ptO);
            }
        }

        return handlePointIndex;
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

        if (lineABvalid && lineCDvalid && ptO != null) {

            AdvancedShape aShape = (AdvancedShape) (newShape = new AdvancedShape(this, 10));
            aShape.addShape(path);

            double ax = ptA.getX(), ay = ptA.getY();
            double bx = ptB.getX(), by = ptB.getY();
            double cx = ptC.getX(), cy = ptC.getY();
            double dx = ptD.getX(), dy = ptD.getY();

            // Let I be the perpendicular projection of O onto AB
            // Let J be the perpendicular projection of O onto CD
            double distAB2 = Point2D.distanceSq(ax, ay, bx, by);
            double distCD2 = Point2D.distanceSq(cx, cy, dx, dy);

            double r1 = ((ay - ptO.getY()) * (ay - by) + (ax - ptO.getX()) * (ax - bx)) / distAB2;
            double r2 = ((cy - ptO.getY()) * (cy - dy) + (cx - ptO.getX()) * (cx - dx)) / distCD2;

            final Point2D ptI = new Point2D.Double(ax + r1 * (bx - ax), ay + r1 * (by - ay));
            final Point2D ptJ = new Point2D.Double(cx + r2 * (dx - cx), cy + r2 * (dy - cy));

            if (r1 < 0 || r1 > 1) {
                aShape.addShape(new Line2D.Double(r1 > 1 ? ptB : ptA, ptI), getDashStroke(1.0f), true);
            }

            if (r2 < 0 || r2 > 1) {
                aShape.addShape(new Line2D.Double(r1 > 1 ? ptD : ptC, ptJ), getDashStroke(1.0f), true);
            }

            aShape.addShape(new Line2D.Double(ptO, ptI));
            aShape.addShape(new Line2D.Double(ptO, ptJ));

            // Add angle corners where I & J lies on AB & CD line segments
            double cLength = 10;

            double cImax = (2.0 / 3.0) * Math.min(ptO.distance(ptI), Math.max(ptI.distance(ptA), ptI.distance(ptB)));
            aShape.addScaleInvShape(GeomUtil.getCornerShape(GeomUtil.getMidPoint(ptA, ptB), ptI, ptO, cLength), ptI,
                cLength / cImax, getStroke(1.0f), true);

            double cJmax = (2.0 / 3.0) * Math.min(ptO.distance(ptJ), Math.max(ptJ.distance(ptC), ptJ.distance(ptD)));
            aShape.addScaleInvShape(GeomUtil.getCornerShape(GeomUtil.getMidPoint(ptC, ptD), ptJ, ptO, cLength), ptJ,
                cLength / cJmax, getStroke(1.0f), true);

            if (!linesParallel) {

                // Let K be the point on extension of IO segment
                // Let L be the point on extension of JO segment
                double extSegLength = 32;

                Point2D ptK = GeomUtil.getColinearPointWithLength(ptI, ptO, ptO.distance(ptI) + extSegLength);
                Point2D ptL = GeomUtil.getColinearPointWithLength(ptJ, ptO, ptO.distance(ptJ) + extSegLength);

                double distOKmax = (1.0 / 2.0) * ptO.distance(ptI);
                aShape.addScaleInvShape(new Line2D.Double(ptO, ptK), ptO, extSegLength / distOKmax, false);

                double distOLmax = (1.0 / 2.0) * ptO.distance(ptJ);
                aShape.addScaleInvShape(new Line2D.Double(ptO, ptL), ptO, extSegLength / distOLmax, false);

                // Let arcAngle be the partial section of the ellipse that represents the measured angle
                double startingAngle =
                    (ptK.getY() > ptL.getY()) ? GeomUtil.getAngleDeg(ptO, ptJ) : GeomUtil.getAngleDeg(ptO, ptI);
                double angularExtent =
                    ptK.getY() > ptL.getY() ? GeomUtil.getAngleDeg(ptJ, ptO, ptK) : GeomUtil.getAngleDeg(ptI, ptO, ptL);
                angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);

                double radius = (2.0 / 3.0) * extSegLength;
                Rectangle2D arcAngleBounds =
                    new Rectangle2D.Double(ptO.getX() - radius, ptO.getY() - radius, 2 * radius, 2 * radius);

                Arc2D arcAngle = new Arc2D.Double(arcAngleBounds, startingAngle, angularExtent, Arc2D.OPEN);

                double rMax = (ptK.getY() > ptL.getY() ? distOKmax : distOLmax) * 2 / 3;
                aShape.addScaleInvShape(arcAngle, ptO, radius / rMax, false);
            }

        } else if (path.getCurrentPoint() != null) {
            newShape = path;
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    protected void updateTool() {
        super.updateTool();
        ptO = getHandlePoint(4);
    }
}
