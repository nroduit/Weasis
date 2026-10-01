/*
 * Copyright (c) 2026 Weasis Team and other contributors.
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
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.graphic.AbstractDragGraphicArea;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.model.utils.imp.DefaultDragSequence;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * Circular region drawn in one gesture: the press sets the centre, the drag the radius. A click
 * without a drag drops a circle of the default radius, for a quick reading of the pixel values.
 */
@XmlType(name = "circle")
@XmlRootElement(name = "circle")
public class CircleGraphic extends AbstractDragGraphicArea {

  public static final Integer POINTS_NUMBER = 2;

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_CIRCLE_ROI);

  public static final Measurement AREA =
      new Measurement("area", Messages.getString("measure.area"), 1, true, true, true);
  public static final Measurement DIAMETER =
      new Measurement("diameter", Messages.getString("measure.diameter"), 2, true, true, false);
  public static final Measurement PERIMETER =
      new Measurement("perimeter", Messages.getString("measure.perimeter"), 3, true, true, false);
  public static final Measurement CENTER_X =
      new Measurement("center.x", Messages.getString("measure.centerx"), 4, true, true, false);
  public static final Measurement CENTER_Y =
      new Measurement("center.y", Messages.getString("measure.centery"), 5, true, true, false);
  public static final Measurement RADIUS =
      new Measurement("radius", Messages.getString("measure.radius"), 6, true, true, false);

  protected static final List<Measurement> MEASUREMENT_LIST =
      List.of(CENTER_X, CENTER_Y, RADIUS, DIAMETER, AREA, PERIMETER);

  /** Below this drag length, in screen pixels, the gesture is a click. */
  static final double CLICK_TOLERANCE = 3.0;

  /** Radius, in screen pixels, of the circle dropped by a click on an image without pixel size. */
  static final double UNCALIBRATED_RADIUS = 15.0;

  private static final double CENTER_MARK = 4.0;

  public CircleGraphic() {
    super(POINTS_NUMBER);
  }

  public CircleGraphic(CircleGraphic graphic) { // NOSONAR see initCopy()
    super(graphic);
  }

  @Override
  public CircleGraphic copy() {
    return new CircleGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("measure.circle");
  }

  public Point2D getCenter() {
    return getHandlePoint(0);
  }

  public double getRadius() {
    Point2D center = getHandlePoint(0);
    Point2D edge = getHandlePoint(1);
    return center == null || edge == null ? 0 : center.distance(edge);
  }

  public CircleGraphic buildGraphic(Point2D center, double radius) throws InvalidShapeException {
    setHandlePointList(center, new Point2D.Double(center.getX() + radius, center.getY()));
    prepareShape();
    return this;
  }

  private void setHandlePointList(Point2D center, Point2D edge) {
    pts.clear();
    pts.add(new Point2D.Double(center.getX(), center.getY()));
    pts.add(new Point2D.Double(edge.getX(), edge.getY()));
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
    return super.isShapeValid() && getRadius() > 0;
  }

  @Override
  public Draggable createResizeDrag(Integer i) {
    return isGraphicComplete() ? super.createResizeDrag(i) : new CreationSequence();
  }

  /** The centre handle moves the circle, the edge handle resizes it. */
  @Override
  public Integer moveAndResizeOnDrawing(
      Integer handlePointIndex, Double deltaX, Double deltaY, MouseEventDouble mouseEvent) {
    boolean centerDragged = handlePointIndex != null && handlePointIndex == 0;
    return super.moveAndResizeOnDrawing(
        centerDragged ? UNDEFINED : handlePointIndex, deltaX, deltaY, mouseEvent);
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    AdvancedShape newShape = null;
    Point2D center = getCenter();
    double radius = getRadius();
    if (center != null && radius > 0) {
      newShape = new AdvancedShape(this, 2);
      newShape.addShape(
          new Ellipse2D.Double(
              center.getX() - radius, center.getY() - radius, 2 * radius, 2 * radius));
      newShape.addScaleInvShape(centerMark(center), center, getStroke(1.0f), true);
    }
    setShape(newShape, mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
  }

  /** A small cross that keeps its size on screen. */
  static Shape centerMark(Point2D center) {
    Path2D cross = new Path2D.Double();
    cross.moveTo(center.getX() - CENTER_MARK, center.getY());
    cross.lineTo(center.getX() + CENTER_MARK, center.getY());
    cross.moveTo(center.getX(), center.getY() - CENTER_MARK);
    cross.lineTo(center.getX(), center.getY() + CENTER_MARK);
    return cross;
  }

  @Override
  public List<MeasureItem> computeMeasurements(
      MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
    if (layer == null || !layer.hasContent() || !isShapeValid()) {
      return Collections.emptyList();
    }
    MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);
    if (adapter == null) {
      return Collections.emptyList();
    }
    List<MeasureItem> measVal = new ArrayList<>();
    Point2D center = getCenter();
    double radius = getRadius() * adapter.calibrationRatio();
    String unit = adapter.unit();

    if (CENTER_X.getComputed()) {
      measVal.add(new MeasureItem(CENTER_X, adapter.getXCalibratedValue(center.getX()), unit));
    }
    if (CENTER_Y.getComputed()) {
      measVal.add(new MeasureItem(CENTER_Y, adapter.getYCalibratedValue(center.getY()), unit));
    }
    if (RADIUS.getComputed()) {
      measVal.add(new MeasureItem(RADIUS, radius, unit));
    }
    if (DIAMETER.getComputed()) {
      measVal.add(new MeasureItem(DIAMETER, radius * 2.0, unit));
    }
    if (AREA.getComputed()) {
      String areaUnit = Unit.PIXEL.getAbbreviation().equals(unit) ? unit : unit + "2";
      measVal.add(new MeasureItem(AREA, Math.PI * radius * radius, areaUnit));
    }
    if (PERIMETER.getComputed()) {
      measVal.add(new MeasureItem(PERIMETER, 2.0 * Math.PI * radius, unit));
    }
    List<MeasureItem> stats = getImageStatistics(layer, releaseEvent);
    if (stats != null) {
      measVal.addAll(stats);
    }
    return measVal;
  }

  @Override
  public List<Measurement> getMeasurementList() {
    return MEASUREMENT_LIST;
  }

  /** Default radius in image pixels: the preferred length, or a fixed size on screen. */
  static double defaultRadius(MeasurementsAdapter millimeters, double radiusMm, double viewScale) {
    boolean calibrated =
        millimeters != null && !Unit.PIXEL.getAbbreviation().equals(millimeters.unit());
    if (calibrated) {
      return radiusMm / millimeters.calibrationRatio();
    }
    return UNCALIBRATED_RADIUS / (viewScale > 0 ? viewScale : 1.0);
  }

  /** One press-drag-release gesture; a release on the press point drops the default circle. */
  private final class CreationSequence extends DefaultDragSequence {

    CreationSequence() {
      super(CircleGraphic.this);
    }

    @Override
    public Boolean completeDrag(MouseEventDouble mouseEvent) {
      if (mouseEvent != null && isGraphicComplete()) {
        AffineTransform transform = getAffineTransform(mouseEvent);
        double scale = transform == null ? 1.0 : GeomUtil.extractScalingFactor(transform);
        if (getRadius() * scale < CLICK_TOLERANCE) {
          Point2D center = getCenter();
          setHandlePoint(
              1, new Point2D.Double(center.getX() + clickRadius(mouseEvent, scale), center.getY()));
          buildShape(mouseEvent);
        }
      }
      return super.completeDrag(mouseEvent);
    }

    private double clickRadius(MouseEventDouble mouseEvent, double scale) {
      ViewCanvas<?> view = getDefaultView2d(mouseEvent);
      MeasurableLayer layer = view == null ? null : view.getMeasurableLayer();
      MeasurementsAdapter adapter =
          layer == null || !layer.hasContent()
              ? null
              : layer.getMeasurementAdapter(Unit.MILLIMETER);
      return defaultRadius(adapter, MeasureTool.viewSetting.getCircleRadius(), scale);
    }
  }
}
