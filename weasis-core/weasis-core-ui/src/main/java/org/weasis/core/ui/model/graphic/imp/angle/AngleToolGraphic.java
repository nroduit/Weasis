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
import java.awt.event.KeyEvent;
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

@XmlType(name = "angle")
@XmlRootElement(name = "angle")
public class AngleToolGraphic extends AbstractDragGraphic {
    private static final long serialVersionUID = 1228359066740628659L;

    public static final Integer POINTS_NUMBER = 3;

    public static final Icon ICON = new ImageIcon(AngleToolGraphic.class.getResource("/icon/22x22/draw-angle.png")); //$NON-NLS-1$

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

    // Let AOB be the triangle that represents the measured angle, O being the intersection point
    Point2D ptA;
    Point2D ptO;
    Point2D ptB;

    // estimate if OA & OB line segments are colinear not not
    boolean lineColinear;

    // estimate if line segments are valid or not
    boolean lineOAvalid;
    Boolean lineOBvalid;

    // smallest angle in Degrees in the range of [-180 ; 180] between OA & OB line segments
    double angleDeg;

    public AngleToolGraphic() {
        super(POINTS_NUMBER);
    }

    public AngleToolGraphic(AngleToolGraphic graphic) {
        super(graphic);
    }

    @Override
    public AngleToolGraphic copy() {
        return new AngleToolGraphic(this);
    }

    protected void init() {
        ptA = getHandlePoint(0);
        ptO = getHandlePoint(1);
        ptB = getHandlePoint(2);

        lineColinear = false;
        lineOAvalid = lineOBvalid = false;

        angleDeg = 0d;
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
        return super.isShapeValid();
    }

    @Override
    public void buildShape(MouseEventDouble mouseEvent) {
        updateTool();

        Shape newShape = null;
        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 2);

        if (lineOAvalid) {
            path.append(new Line2D.Double(ptA, ptO), false);
        }

        if (lineOBvalid) {
            path.append(new Line2D.Double(ptO, ptB), false);
        }

        if (lineOAvalid && lineOBvalid && !lineColinear) {
            newShape = new AdvancedShape(this, 2);
            AdvancedShape aShape = (AdvancedShape) newShape;
            aShape.addShape(path);

            // Let arcAngle be the partial section of the ellipse that represents the measured angle
            double startingAngle = GeomUtil.getAngleDeg(ptO, ptA);

            double radius = 32;
            Rectangle2D arcAngleBounds =
                new Rectangle2D.Double(ptO.getX() - radius, ptO.getY() - radius, 2 * radius, 2 * radius);

            Shape arcAngle = new Arc2D.Double(arcAngleBounds, startingAngle, angleDeg, Arc2D.OPEN);

            double rMax = Math.min(ptO.distance(ptA), ptO.distance(ptB)) * 2 / 3;
            double scalingMin = radius / rMax;

            aShape.addScaleInvShape(arcAngle, ptO, scalingMin, true);

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
        return Messages.getString("measure.angle"); //$NON-NLS-1$
    }

    @Override
    public int getKeyCode() {
        return KeyEvent.VK_A;
    }

    @Override
    public int getModifier() {
        return 0;
    }

    protected void updateTool() {
        init();

        lineOAvalid = (ptA != null && ptO != null && !ptO.equals(ptA));
        lineOBvalid = (ptB != null && ptO != null && !ptO.equals(ptB));

        if (lineOAvalid && lineOBvalid) {
            angleDeg = GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(ptA, ptO, ptB));
            lineColinear = GeomUtil.lineColinear(ptO, ptA, ptO, ptB);
        }
    }
}
