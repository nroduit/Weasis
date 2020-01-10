/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.graphic.imp.area;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlType(name = "ellipse")
@XmlRootElement(name = "ellipse")
public class EllipseGraphic extends RectangleGraphic {
    private static final long serialVersionUID = 3296060775738751236L;

    public static final Icon ICON = new ImageIcon(EllipseGraphic.class.getResource("/icon/22x22/draw-eclipse.png")); //$NON-NLS-1$

    public static final Measurement AREA = new Measurement(Messages.getString("measure.area"), 1, true, true, true); //$NON-NLS-1$
    public static final Measurement PERIMETER =
        new Measurement(Messages.getString("measure.perimeter"), 2, true, true, false); //$NON-NLS-1$
    public static final Measurement CENTER_X =
        new Measurement(Messages.getString("measure.centerx"), 3, true, true, false); //$NON-NLS-1$
    public static final Measurement CENTER_Y =
        new Measurement(Messages.getString("measure.centery"), 4, true, true, false); //$NON-NLS-1$
    public static final Measurement WIDTH = new Measurement(Messages.getString("measure.width"), 5, true, true, false); //$NON-NLS-1$
    public static final Measurement HEIGHT =
        new Measurement(Messages.getString("measure.height"), 6, true, true, false); //$NON-NLS-1$

    protected static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();
    static {
        MEASUREMENT_LIST.add(CENTER_X);
        MEASUREMENT_LIST.add(CENTER_Y);
        MEASUREMENT_LIST.add(AREA);
        MEASUREMENT_LIST.add(PERIMETER);
        MEASUREMENT_LIST.add(WIDTH);
        MEASUREMENT_LIST.add(HEIGHT);
    }

    public EllipseGraphic() {
        super();
    }

    public EllipseGraphic(EllipseGraphic graphic) {
        super(graphic);
    }

    @Override
    public EllipseGraphic copy() {
        return new EllipseGraphic(this);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.ellipse"); //$NON-NLS-1$
    }

    @Override
    public void buildShape(MouseEventDouble mouseevent) {
        Rectangle2D rectangle = new Rectangle2D.Double();
        rectangle.setFrameFromDiagonal(getHandlePoint(eHandlePoint.NW.index), getHandlePoint(eHandlePoint.SE.index));

        setShape(new Ellipse2D.Double(rectangle.getX(), rectangle.getY(), rectangle.getWidth(), rectangle.getHeight()),
            mouseevent);
        updateLabel(mouseevent, getDefaultView2d(mouseevent));
    }

    @Override
    public List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {

        if (layer != null && layer.hasContent() && isShapeValid()) {
            MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<>();
                Rectangle2D rect = new Rectangle2D.Double();

                rect.setFrameFromDiagonal(getHandlePoint(eHandlePoint.NW.index), getHandlePoint(eHandlePoint.SE.index));

                double ratio = adapter.getCalibRatio();

                if (CENTER_X.getComputed()) {
                    measVal.add(
                        new MeasureItem(CENTER_X, adapter.getXCalibratedValue(rect.getCenterX()), adapter.getUnit()));
                }
                if (CENTER_Y.getComputed()) {
                    measVal.add(
                        new MeasureItem(CENTER_Y, adapter.getYCalibratedValue(rect.getCenterY()), adapter.getUnit()));
                }

                if (WIDTH.getComputed()) {
                    measVal.add(new MeasureItem(WIDTH, ratio * rect.getWidth(), adapter.getUnit()));
                }
                if (HEIGHT.getComputed()) {
                    measVal.add(new MeasureItem(HEIGHT, ratio * rect.getHeight(), adapter.getUnit()));
                }

                if (AREA.getComputed()) {
                    Double val = Math.PI * rect.getWidth() * ratio * rect.getHeight() * ratio / 4.0;
                    String unit = "pix".equals(adapter.getUnit()) ? adapter.getUnit() : adapter.getUnit() + "2"; //$NON-NLS-1$ //$NON-NLS-2$
                    measVal.add(new MeasureItem(AREA, val, unit));
                }
                if (PERIMETER.getComputed()) {
                    double a = ratio * rect.getWidth() / 2.0;
                    double b = ratio * rect.getHeight() / 2.0;
                    Double val = 2.0 * Math.PI * Math.sqrt((a * a + b * b) / 2.0);
                    measVal.add(new MeasureItem(PERIMETER, val, adapter.getUnit()));
                }

                List<MeasureItem> stats = getImageStatistics(layer, releaseEvent);
                if (stats != null) {
                    measVal.addAll(stats);
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
}
