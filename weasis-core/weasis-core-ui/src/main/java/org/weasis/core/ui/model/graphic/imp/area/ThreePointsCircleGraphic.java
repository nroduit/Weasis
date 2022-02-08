/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.area;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.graphic.AbstractDragGraphicArea;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlType(name = "threePointsCircle")
@XmlRootElement(name = "threePointsCircle")
public class ThreePointsCircleGraphic extends AbstractDragGraphicArea {

  public static final Integer POINTS_NUMBER = 3;

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_CIRCLE);

  public static final Measurement AREA =
      new Measurement(Messages.getString("measure.area"), 1, true, true, true);
  public static final Measurement DIAMETER =
      new Measurement(Messages.getString("measure.diameter"), 2, true, true, false);
  public static final Measurement PERIMETER =
      new Measurement(Messages.getString("measure.perimeter"), 3, true, true, false);
  public static final Measurement CENTER_X =
      new Measurement(Messages.getString("measure.centerx"), 4, true, true, false);
  public static final Measurement CENTER_Y =
      new Measurement(Messages.getString("measure.centery"), 5, true, true, false);
  public static final Measurement RADIUS =
      new Measurement(Messages.getString("measure.radius"), 6, true, true, false);

  protected static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();

  static {
    MEASUREMENT_LIST.add(CENTER_X);
    MEASUREMENT_LIST.add(CENTER_Y);
    MEASUREMENT_LIST.add(RADIUS);
    MEASUREMENT_LIST.add(DIAMETER);
    MEASUREMENT_LIST.add(AREA);
    MEASUREMENT_LIST.add(PERIMETER);
  }

  protected Point2D centerPt; // Let O be the center of the three point interpolated circle
  protected Double radiusPt; // circle radius

  public ThreePointsCircleGraphic() {
    super(POINTS_NUMBER);
  }

  public ThreePointsCircleGraphic(ThreePointsCircleGraphic graphic) {
    super(graphic);
  }

  @Override
  public ThreePointsCircleGraphic copy() {
    return new ThreePointsCircleGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("measure.three_pt_angle");
  }

  @Override
  protected void prepareShape() throws InvalidShapeException {
    if (!isShapeValid()) {
      throw new InvalidShapeException("This shape cannot be drawn");
    }
    buildShape(null);
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    updateTool();
    Shape newShape = null;

    if (Objects.nonNull(centerPt) && !Objects.equals(radiusPt, 0d)) {
      newShape =
          new Ellipse2D.Double(
              centerPt.getX() - radiusPt, centerPt.getY() - radiusPt, 2 * radiusPt, 2 * radiusPt);
    }

    setShape(newShape, mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
  }

  /** Force displaying handles even during resizing or moving sequences */
  @Override
  public Boolean getResizingOrMoving() {
    return Boolean.FALSE;
  }

  @Override
  public List<MeasureItem> computeMeasurements(
      MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {

    if (layer != null && layer.hasContent() && isShapeValid()) {
      MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

      if (adapter != null) {
        ArrayList<MeasureItem> measVal = new ArrayList<>();

        double ratio = adapter.getCalibRatio();

        if (CENTER_X.getComputed()) {
          measVal.add(
              new MeasureItem(
                  CENTER_X, adapter.getXCalibratedValue(centerPt.getX()), adapter.getUnit()));
        }
        if (CENTER_Y.getComputed()) {
          measVal.add(
              new MeasureItem(
                  CENTER_Y, adapter.getYCalibratedValue(centerPt.getY()), adapter.getUnit()));
        }
        if (RADIUS.getComputed()) {
          measVal.add(new MeasureItem(RADIUS, ratio * radiusPt, adapter.getUnit()));
        }
        if (DIAMETER.getComputed()) {
          measVal.add(new MeasureItem(DIAMETER, ratio * radiusPt * 2.0, adapter.getUnit()));
        }
        if (AREA.getComputed()) {
          String unit =
              "pix".equals(adapter.getUnit()) // NON-NLS
                  ? adapter.getUnit()
                  : adapter.getUnit() + "2";
          measVal.add(new MeasureItem(AREA, Math.PI * radiusPt * radiusPt * ratio * ratio, unit));
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

  @Override
  public boolean isShapeValid() {
    updateTool();
    return super.isShapeValid() && centerPt != null && radiusPt < 50000;
  }

  protected void updateTool() {
    Point2D ptA = getHandlePoint(0);

    centerPt = GeomUtil.getCircleCenter(pts);
    radiusPt = (centerPt != null && ptA != null) ? centerPt.distance(ptA) : 0;
  }
}
