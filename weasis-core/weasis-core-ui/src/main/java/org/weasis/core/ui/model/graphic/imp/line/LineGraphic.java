/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.line;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.graphic.AbstractDragGraphic;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.util.MathUtil;

@XmlType(name = "line")
@XmlRootElement(name = "line")
@XmlAccessorType(XmlAccessType.NONE)
public class LineGraphic extends AbstractDragGraphic {

  public static final Integer POINTS_NUMBER = 2;

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_LINE);

  public static final Measurement FIRST_POINT_X =
      new Measurement(Messages.getString("measure.firstx"), 1, true, true, false);
  public static final Measurement FIRST_POINT_Y =
      new Measurement(Messages.getString("measure.firsty"), 2, true, true, false);
  public static final Measurement LAST_POINT_X =
      new Measurement(Messages.getString("measure.lastx"), 3, true, true, false);
  public static final Measurement LAST_POINT_Y =
      new Measurement(Messages.getString("measure.lasty"), 4, true, true, false);
  public static final Measurement LINE_LENGTH =
      new Measurement(Messages.getString("measure.length"), 5, true, true, true);
  public static final Measurement ORIENTATION =
      new Measurement(Messages.getString("measure.orientation"), 6, true, true, false);
  public static final Measurement AZIMUTH =
      new Measurement(Messages.getString("measure.azimuth"), 7, true, true, false);

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
  protected Point2D ptA;
  protected Point2D ptB;

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
      throw new InvalidShapeException("This shape cannot be drawn");
    }
    buildShape(null);
  }

  @Override
  public boolean isShapeValid() {
    updateTool();
    return super.isShapeValid();
  }

  public Point2D getPtA() {
    return ptA;
  }

  public Point2D getPtB() {
    return ptB;
  }

  protected Boolean getLineABvalid() {
    return lineABvalid;
  }

  public void setHandlePointList(Point2D ptStart, Point2D ptEnd) {
    setHandlePoint(0, ptStart == null ? null : (Point2D) ptStart.clone());
    setHandlePoint(1, ptEnd == null ? null : (Point2D) ptEnd.clone());
    buildShape(null);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("MeasureToolBar.line");
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
  public List<MeasureItem> computeMeasurements(
      MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
    if (layer != null && layer.hasContent() && isShapeValid()) {
      MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

      if (adapter != null) {
        ArrayList<MeasureItem> measVal = new ArrayList<>();

        if (FIRST_POINT_X.getComputed()) {
          measVal.add(
              new MeasureItem(
                  FIRST_POINT_X, adapter.getXCalibratedValue(ptA.getX()), adapter.getUnit()));
        }
        if (FIRST_POINT_Y.getComputed()) {
          measVal.add(
              new MeasureItem(
                  FIRST_POINT_Y, adapter.getYCalibratedValue(ptA.getY()), adapter.getUnit()));
        }
        if (LAST_POINT_X.getComputed()) {
          measVal.add(
              new MeasureItem(
                  LAST_POINT_X, adapter.getXCalibratedValue(ptB.getX()), adapter.getUnit()));
        }
        if (LAST_POINT_Y.getComputed()) {
          measVal.add(
              new MeasureItem(
                  LAST_POINT_Y, adapter.getYCalibratedValue(ptB.getY()), adapter.getUnit()));
        }
        if (LINE_LENGTH.getComputed()) {
          measVal.add(
              new MeasureItem(
                  LINE_LENGTH, ptA.distance(ptB) * adapter.getCalibRatio(), adapter.getUnit()));
        }
        if (ORIENTATION.getComputed()) {
          measVal.add(
              new MeasureItem(
                  ORIENTATION,
                  MathUtil.getOrientation(ptA, ptB),
                  Messages.getString("measure.deg")));
        }
        if (AZIMUTH.getComputed()) {
          measVal.add(
              new MeasureItem(
                  AZIMUTH, MathUtil.getAzimuth(ptA, ptB), Messages.getString("measure.deg")));
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
