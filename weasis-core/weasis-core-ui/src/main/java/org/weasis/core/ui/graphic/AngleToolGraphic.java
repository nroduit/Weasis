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
 * 
 * @author Nicolas Roduit,Benoit Jacquemoud
 */
public class AngleToolGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(AngleToolGraphic.class.getResource("/icon/22x22/draw-angle.png")); //$NON-NLS-1$

    public final static Measurement Angle = new Measurement("Angle", true);
    public final static Measurement ComplementaryAngle = new Measurement("Compl. Angle", true);

    public final static int ARC_RADIUS = 32;

    public AngleToolGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(3, paintColor, lineThickness, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return "Angle measure";
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);

            if (handlePointList.size() >= 2) {
                AdvancedShape newShape = new AdvancedShape(2);
                Path2D generalpath = new Path2D.Double(Path2D.WIND_NON_ZERO, handlePointList.size());
                newShape.addShape(generalpath);

                Point2D P = handlePointList.get(1);

                generalpath.moveTo(A.getX(), A.getY());
                generalpath.lineTo(P.getX(), P.getY());

                if (handlePointList.size() >= 3) {
                    Point2D B = handlePointList.get(2);
                    generalpath.lineTo(B.getX(), B.getY());

                    double angularExtent = GeomUtil.getAngleDeg(A, P, B);
                    angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);
                    double startingAngle = GeomUtil.getAngleDeg(P, A);

                    // double radius = (ARC_RADIUS < rMax) ? ARC_RADIUS : rMax;
                    double radius = ARC_RADIUS;
                    Rectangle2D arcAngleBounds =
                        new Rectangle2D.Double(P.getX() - radius, P.getY() - radius, 2.0 * radius, 2.0 * radius);

                    Shape arcAngle = new Arc2D.Double(arcAngleBounds, startingAngle, angularExtent, Arc2D.OPEN);

                    double rMax = (2.0 / 3.0) * Math.min(P.distance(A), P.distance(B));
                    double scalingMin = radius / rMax;

                    newShape.addInvShape(arcAngle, (Point2D) P.clone(), scalingMin, false);

                }
                setShape(newShape, mouseEvent);
                updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
            }
        }
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent) {
        if (imageElement != null && handlePointList.size() >= 3) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();
            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();
                if (Angle.isComputed() || ComplementaryAngle.isComputed()) {
                    Point2D At = handlePointList.get(0);
                    Point2D Ot = handlePointList.get(1);
                    Point2D Bt = handlePointList.get(2);
                    double realAngle = Math.abs(GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(At, Ot, Bt)));
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
