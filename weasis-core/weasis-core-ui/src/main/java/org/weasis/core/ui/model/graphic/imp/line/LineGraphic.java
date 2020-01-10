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
import java.awt.event.KeyEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.graphic.AbstractDragGraphic;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlType(name = "line")
@XmlRootElement(name = "line")
@XmlAccessorType(XmlAccessType.NONE)
public class LineGraphic extends AbstractDragGraphic {
    private static final long serialVersionUID = -4518306673180973310L;

    public static final Integer POINTS_NUMBER = 2;

    public static final Icon ICON = new ImageIcon(LineGraphic.class.getResource("/icon/22x22/draw-line.png")); //$NON-NLS-1$

    public static final Measurement FIRST_POINT_X =
        new Measurement(Messages.getString("measure.firstx"), 1, true, true, false); //$NON-NLS-1$
    public static final Measurement FIRST_POINT_Y =
        new Measurement(Messages.getString("measure.firsty"), 2, true, true, false); //$NON-NLS-1$
    public static final Measurement LAST_POINT_X =
        new Measurement(Messages.getString("measure.lastx"), 3, true, true, false); //$NON-NLS-1$
    public static final Measurement LAST_POINT_Y =
        new Measurement(Messages.getString("measure.lasty"), 4, true, true, false); //$NON-NLS-1$
    public static final Measurement LINE_LENGTH =
        new Measurement(Messages.getString("measure.length"), 5, true, true, true); //$NON-NLS-1$
    public static final Measurement ORIENTATION =
        new Measurement(Messages.getString("measure.orientation"), 6, true, true, false); //$NON-NLS-1$
    public static final Measurement AZIMUTH =
        new Measurement(Messages.getString("measure.azimuth"), 7, true, true, false); //$NON-NLS-1$

    public static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();
    static {
        MEASUREMENT_LIST.add(FIRST_POINT_X);
        MEASUREMENT_LIST.add(FIRST_POINT_Y);
        MEASUREMENT_LIST.add(LAST_POINT_X);
        MEASUREMENT_LIST.add(LAST_POINT_Y);
        MEASUREMENT_LIST.add(LINE_LENGTH);
        MEASUREMENT_LIST.add(ORIENTATION);
        MEASUREMENT_LIST.add(AZIMUTH);
    }

    // Let AB be a simple a line segment
    protected Point2D.Double ptA;
    protected Point2D.Double ptB;

    // estimate if line segment is valid or not
    protected Boolean lineABvalid;

    public LineGraphic() {
        super(POINTS_NUMBER);
    }

    public LineGraphic(LineGraphic graphic) {
        super(graphic);
    }

    @Override
    public LineGraphic copy() {
        return new LineGraphic(this);
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

    public Point2D.Double getPtA() {
        return ptA;
    }

    public Point2D.Double getPtB() {
        return ptB;
    }

    protected Boolean getLineABvalid() {
        return lineABvalid;
    }

    public void setHandlePointList(Point2D.Double ptStart, Point2D.Double ptEnd) {
        setHandlePoint(0, ptStart == null ? null : (Point2D.Double) ptStart.clone());
        setHandlePoint(1, ptEnd == null ? null : (Point2D.Double) ptEnd.clone());
        buildShape(null);
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
    public int getKeyCode() {
        return KeyEvent.VK_D;
    }

    @Override
    public int getModifier() {
        return 0;
    }

    @Override
    public void buildShape(MouseEventDouble mouseEvent) {
        updateTool();
        Shape newShape = null;

        if (lineABvalid) {
            newShape = new Line2D.Double(ptA, ptB);
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

                if (FIRST_POINT_X.getComputed()) {
                    measVal.add(
                        new MeasureItem(FIRST_POINT_X, adapter.getXCalibratedValue(ptA.getX()), adapter.getUnit()));
                }
                if (FIRST_POINT_Y.getComputed()) {
                    measVal.add(
                        new MeasureItem(FIRST_POINT_Y, adapter.getYCalibratedValue(ptA.getY()), adapter.getUnit()));
                }
                if (LAST_POINT_X.getComputed()) {
                    measVal
                        .add(new MeasureItem(LAST_POINT_X, adapter.getXCalibratedValue(ptB.getX()), adapter.getUnit()));
                }
                if (LAST_POINT_Y.getComputed()) {
                    measVal
                        .add(new MeasureItem(LAST_POINT_Y, adapter.getYCalibratedValue(ptB.getY()), adapter.getUnit()));
                }
                if (LINE_LENGTH.getComputed()) {
                    measVal.add(
                        new MeasureItem(LINE_LENGTH, ptA.distance(ptB) * adapter.getCalibRatio(), adapter.getUnit()));
                }
                if (ORIENTATION.getComputed()) {
                    measVal.add(new MeasureItem(ORIENTATION, MathUtil.getOrientation(ptA, ptB),
                        Messages.getString("measure.deg"))); //$NON-NLS-1$
                }
                if (AZIMUTH.getComputed()) {
                    measVal.add(
                        new MeasureItem(AZIMUTH, MathUtil.getAzimuth(ptA, ptB), Messages.getString("measure.deg"))); //$NON-NLS-1$
                }
                return measVal;
            }
        }
        return Collections.emptyList();
    }

    protected void updateTool() {
        ptA = getHandlePoint(0);
        ptB = getHandlePoint(1);

        lineABvalid = ptA != null && ptB != null && !ptB.equals(ptA);
    }

    public Point2D getStartPoint() {
        updateTool();
        return ptA == null ? null : (Point2D) ptA.clone();
    }

    public Point2D getEndPoint() {
        updateTool();
        return ptB == null ? null : (Point2D) ptB.clone();
    }

    @Override
    public List<Measurement> getMeasurementList() {
        return MEASUREMENT_LIST;
    }
}
