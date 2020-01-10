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
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.graphic.AbstractDragGraphic;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlType(name = "polyline")
@XmlRootElement(name = "polyline")
public class PolylineGraphic extends AbstractDragGraphic {
    private static final long serialVersionUID = -4516499480176907755L;

    public static final Integer POINTS_NUMBER = UNDEFINED;

    public static final Icon ICON = new ImageIcon(PolylineGraphic.class.getResource("/icon/22x22/draw-polyline.png")); //$NON-NLS-1$

    public static final Measurement LINE_LENGTH =
        new Measurement(Messages.getString("measure.length"), 5, true, true, true); //$NON-NLS-1$

    protected static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();
    static {
        MEASUREMENT_LIST.add(LINE_LENGTH);
    }

    public PolylineGraphic() {
        super(POINTS_NUMBER);
    }

    public PolylineGraphic(PolylineGraphic graphic) {
        super(graphic);
    }

    @Override
    public PolylineGraphic copy() {
        return new PolylineGraphic(this);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.polyline"); //$NON-NLS-1$
    }

    @Override
    protected void prepareShape() throws InvalidShapeException {
        setPointNumber(pts.size());
        buildShape(null);

        if (!isShapeValid()) {
            int lastPointIndex = pts.size() - 1;
            if (lastPointIndex > 0) {
                Point2D checkPoint = pts.get(lastPointIndex);
                /*
                 * Must not have two or several points with the same position at the end of the list (two points is the
                 * convention to have a uncompleted shape when drawing)
                 */
                for (int i = lastPointIndex - 1; i >= 0; i--) {
                    if (checkPoint.equals(pts.get(i))) {
                        pts.remove(i);
                    } else {
                        break;
                    }
                }
                setPointNumber(pts.size());
            }
            if (!isShapeValid() || pts.size() < 2) {
                throw new IllegalStateException("This Polyline cannot be drawn"); //$NON-NLS-1$
            }
            buildShape(null);
        }
    }

    @Override
    public void buildShape(MouseEventDouble mouseEvent) {
        Shape newShape = null;
        Optional<Point2D.Double> firstHandlePoint = pts.stream().findFirst();

        if (firstHandlePoint.isPresent()) {
            Point2D.Double p = firstHandlePoint.get();
            Path2D polygonPath = new Path2D.Double(Path2D.WIND_NON_ZERO, pts.size());
            polygonPath.moveTo(p.getX(), p.getY());

            for (Point2D.Double pt : pts) {
                if (pt == null) {
                    break;
                }
                polygonPath.lineTo(pt.getX(), pt.getY());
            }
            newShape = polygonPath;
        }
        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public boolean isShapeValid() {
        if (!isGraphicComplete()) {
            return false;
        }

        int lastPointIndex = pts.size() - 1;

        if (lastPointIndex > 0) {
            Point2D checkPoint = pts.get(lastPointIndex);
            if (Objects.equals(checkPoint, pts.get(--lastPointIndex))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {

        if (layer != null && layer.hasContent() && isShapeValid()) {
            MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<>(5);

                double ratio = adapter.getCalibRatio();
                String unitStr = adapter.getUnit();
                // Get copy to be sure that point value are not modified any more and filter point equal to null.
                List<Point2D.Double> handlePointListcopy = new ArrayList<>(pts.size());
                for (Point2D.Double handlePt : pts) {
                    if (handlePt != null) {
                        handlePointListcopy.add((Point2D.Double) handlePt.clone());
                    }
                }

                if (LINE_LENGTH.getComputed()) {
                    Double val = (handlePointListcopy.size() > 1) ? getPerimeter(handlePointListcopy) * ratio : null;
                    measVal.add(new MeasureItem(LINE_LENGTH, val, unitStr));
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

    protected Double getPerimeter(List<Point2D.Double> handlePointList) {
        if (handlePointList.size() > 1) {
            double perimeter = 0d;
            Point2D.Double pLast = handlePointList.get(0);
            for (Point2D.Double p2 : handlePointList) {
                perimeter += pLast.distance(p2);
                pLast = p2;
            }
            return perimeter;
        }
        return null;
    }

    @Override
    public void forceToAddPoints(Integer fromPtIndex) {
        if (getVariablePointsNumber() && fromPtIndex >= 0 && fromPtIndex < pts.size()) {
            if (fromPtIndex < pts.size() - 1) {
                // Add only one point
                pts.add(fromPtIndex, getHandlePoint(fromPtIndex));
                pointNumber++;
            } else {
                // Continue to draw when it is the last point
                setPointNumber(UNDEFINED);
            }
        }
    }
}
