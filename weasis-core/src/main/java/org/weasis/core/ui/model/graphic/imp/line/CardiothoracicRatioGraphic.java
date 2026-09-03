/*
 * Copyright (c) 2026 Weasis Team and other contributors.
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
import java.awt.Stroke;
import java.awt.geom.Line2D;
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
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.model.graphic.AbstractDragGraphic;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.bean.QuantityKind;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * Cardiothoracic ratio on a chest radiograph: the transverse cardiac diameter over the internal
 * thoracic diameter. A first stroke joins the two heart borders, a second one the two inner rib
 * margins; each end is placed where its border is widest, at its own height. Only the position of
 * the borders across the midline counts: the widths are their projections on the axis perpendicular
 * to the midline, which can be tilted to follow a rotated patient.
 */
@XmlType(name = "ctr")
@XmlRootElement(name = "ctr")
@XmlAccessorType(XmlAccessType.NONE)
public class CardiothoracicRatioGraphic extends AbstractDragGraphic {

  public static final Integer POINTS_NUMBER = 6;
  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_CTR);

  public static final Measurement RATIO =
      new Measurement("ctr.ratio", Messages.getString("measure.ctr"), 1, true, true, true);
  public static final Measurement CARDIAC_WIDTH = length("ctr.cardiac", "measure.ctr_cardiac", 2);
  public static final Measurement THORACIC_WIDTH =
      length("ctr.thoracic", "measure.ctr_thoracic", 3);
  public static final Measurement RIGHT_DIAMETER = length("ctr.mrd", "measure.ctr_mrd", 4);
  public static final Measurement LEFT_DIAMETER = length("ctr.mld", "measure.ctr_mld", 5);

  protected static final List<Measurement> MEASUREMENT_LIST =
      List.of(RATIO, CARDIAC_WIDTH, THORACIC_WIDTH, RIGHT_DIAMETER, LEFT_DIAMETER);

  /** Part of the thoracic width by which the midline extends beyond the two strokes. */
  static final double MIDLINE_MARGIN = 0.15;

  private static final int HEART_1 = 0;
  private static final int HEART_2 = 1;
  private static final int THORAX_1 = 2;
  private static final int THORAX_2 = 3;
  private static final int MIDLINE_1 = 4;
  private static final int MIDLINE_2 = 5;
  private static final double TICK = 5.0;

  /** Once the user has moved the midline it no longer follows the thoracic stroke. */
  private boolean midlineCustom;

  public CardiothoracicRatioGraphic() {
    super(POINTS_NUMBER);
  }

  public CardiothoracicRatioGraphic(CardiothoracicRatioGraphic graphic) {
    super(graphic);
  }

  private static Measurement length(String key, String message, int id) {
    return new Measurement(
        key, Messages.getString(message), id, true, true, false, QuantityKind.LENGTH);
  }

  @Override
  public CardiothoracicRatioGraphic copy() {
    return new CardiothoracicRatioGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("measure.ctr_tool");
  }

  @Override
  protected void prepareShape() throws InvalidShapeException {
    if (!isShapeValid()) {
      throw new InvalidShapeException("This shape cannot be drawn");
    }
    // A graphic rebuilt from its points (file, copy) keeps the midline where it is
    midlineCustom = true;
    buildShape(null);
  }

  @Override
  public Integer moveAndResizeOnDrawing(
      Integer handlePointIndex, Double deltaX, Double deltaY, MouseEventDouble mouseEvent) {
    int index = super.moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, mouseEvent);
    if (index == MIDLINE_1 || index == MIDLINE_2) {
      midlineCustom = true;
    } else if (index >= 0 && !midlineCustom && pts.size() > THORAX_2) {
      placeMidline();
    }
    return index;
  }

  /**
   * Midline parallel to the image columns through the middle of the thoracic stroke, long enough to
   * cross both strokes.
   */
  void placeMidline() {
    Point2D t1 = getHandlePoint(THORAX_1);
    Point2D t2 = getHandlePoint(THORAX_2);
    if (t1 == null || t2 == null) {
      return;
    }
    double x = (t1.getX() + t2.getX()) / 2;
    double top = Double.MAX_VALUE;
    double bottom = -Double.MAX_VALUE;
    for (int i = HEART_1; i <= THORAX_2; i++) {
      top = Math.min(top, getHandlePoint(i).getY());
      bottom = Math.max(bottom, getHandlePoint(i).getY());
    }
    double margin = Math.max(1.0, Math.abs(t2.getX() - t1.getX()) * MIDLINE_MARGIN);
    setHandlePoint(MIDLINE_1, new Point2D.Double(x, top - margin));
    setHandlePoint(MIDLINE_2, new Point2D.Double(x, bottom + margin));
  }

  @Override
  public boolean isShapeValid() {
    Axes axes = axes();
    return axes != null && axes.thoracicWidth() > 0;
  }

  /** The measurement frame, or {@code null} while the graphic is not complete. */
  Axes axes() {
    if (pts.size() < POINTS_NUMBER || pts.contains(null)) {
      return null;
    }
    Point2D m1 = pts.get(MIDLINE_1);
    Point2D m2 = pts.get(MIDLINE_2);
    double length = m1.distance(m2);
    if (length <= 0) {
      return null;
    }
    double vx = (m2.getX() - m1.getX()) / length;
    double vy = (m2.getY() - m1.getY()) / length;
    // u is perpendicular to the midline and points to the right of the image
    double ux = -vy;
    double uy = vx;
    if (ux < 0) {
      ux = -ux;
      uy = -uy;
    }
    return new Axes(m1, ux, uy, vx, vy, pts);
  }

  /**
   * Frame of the measurement: {@code v} along the midline, {@code u} across it toward the right of
   * the image; every border point has an abscissa across ({@code a}) and an ordinate along it.
   */
  record Axes(Point2D origin, double ux, double uy, double vx, double vy, List<Point2D> points) {

    double across(int index) {
      Point2D p = points.get(index);
      return (p.getX() - origin.getX()) * ux + (p.getY() - origin.getY()) * uy;
    }

    double along(int index) {
      Point2D p = points.get(index);
      return (p.getX() - origin.getX()) * vx + (p.getY() - origin.getY()) * vy;
    }

    Point2D at(double across, double along) {
      return new Point2D.Double(
          origin.getX() + across * ux + along * vx, origin.getY() + across * uy + along * vy);
    }

    double cardiacWidth() {
      return Math.abs(across(HEART_2) - across(HEART_1));
    }

    double thoracicWidth() {
      return Math.abs(across(THORAX_2) - across(THORAX_1));
    }

    /** True when the heart borders lie on opposite sides of the midline. */
    boolean straddles() {
      return across(HEART_1) * across(HEART_2) < 0;
    }

    /** Distance from the midline to the heart border on the left of the image. */
    double imageLeftDiameter() {
      return Math.abs(Math.min(across(HEART_1), across(HEART_2)));
    }

    /** Distance from the midline to the heart border on the right of the image. */
    double imageRightDiameter() {
      return Math.abs(Math.max(across(HEART_1), across(HEART_2)));
    }
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    AdvancedShape shape = null;
    Axes axes = axes();
    if (axes != null) {
      shape = new AdvancedShape(this, 8);
      shape.addShape(new Line2D.Double(pts.get(MIDLINE_1), pts.get(MIDLINE_2)), dash(), true);
      addDimension(shape, axes, HEART_1, HEART_2);
      addDimension(shape, axes, THORAX_1, THORAX_2);
    } else {
      Path2D strokes = new Path2D.Double();
      appendStroke(strokes, HEART_1, HEART_2);
      appendStroke(strokes, THORAX_1, THORAX_2);
      if (strokes.getCurrentPoint() != null) {
        shape = new AdvancedShape(this, 1);
        shape.addShape(strokes);
      }
    }
    setShape(shape, mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
  }

  private Stroke dash() {
    return getDashStroke(1.0f);
  }

  private void appendStroke(Path2D path, int first, int second) {
    Point2D a = getHandlePoint(first);
    Point2D b = getHandlePoint(second);
    if (GeomUtil.isLineValid(a, b)) {
      path.append(new Line2D.Double(a, b), false);
    }
  }

  /**
   * Dimension line across the midline at the mean height of the two borders, with end ticks and a
   * dashed guide from each border point to its end.
   */
  private void addDimension(AdvancedShape shape, Axes axes, int first, int second) {
    double along = (axes.along(first) + axes.along(second)) / 2;
    Point2D end1 = axes.at(axes.across(first), along);
    Point2D end2 = axes.at(axes.across(second), along);
    shape.addShape(new Line2D.Double(end1, end2));
    for (Point2D end : List.of(end1, end2)) {
      Line2D tick =
          new Line2D.Double(
              end.getX() - TICK * axes.vx(),
              end.getY() - TICK * axes.vy(),
              end.getX() + TICK * axes.vx(),
              end.getY() + TICK * axes.vy());
      shape.addScaleInvShape(tick, end, getStroke(lineThickness), false);
    }
    shape.addShape(new Line2D.Double(pts.get(first), end1), dash(), true);
    shape.addShape(new Line2D.Double(pts.get(second), end2), dash(), true);
  }

  @Override
  public List<MeasureItem> computeMeasurements(
      MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
    Axes axes = axes();
    if (layer == null || !layer.hasContent() || axes == null || axes.thoracicWidth() <= 0) {
      return Collections.emptyList();
    }
    MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);
    if (adapter == null) {
      return Collections.emptyList();
    }
    double ratio = adapter.calibrationRatio();
    String unit = adapter.unit();
    List<MeasureItem> items = new ArrayList<>(MEASUREMENT_LIST.size());
    if (RATIO.getComputed()) {
      items.add(new MeasureItem(RATIO, axes.cardiacWidth() / axes.thoracicWidth(), null));
    }
    if (CARDIAC_WIDTH.getComputed()) {
      items.add(new MeasureItem(CARDIAC_WIDTH, axes.cardiacWidth() * ratio, unit));
    }
    if (THORACIC_WIDTH.getComputed()) {
      items.add(new MeasureItem(THORACIC_WIDTH, axes.thoracicWidth() * ratio, unit));
    }
    if (axes.straddles()) {
      boolean rightOnImageLeft = isPatientRightOnImageLeft(layer);
      double right = rightOnImageLeft ? axes.imageLeftDiameter() : axes.imageRightDiameter();
      double left = rightOnImageLeft ? axes.imageRightDiameter() : axes.imageLeftDiameter();
      if (RIGHT_DIAMETER.getComputed()) {
        items.add(new MeasureItem(RIGHT_DIAMETER, right * ratio, unit));
      }
      if (LEFT_DIAMETER.getComputed()) {
        items.add(new MeasureItem(LEFT_DIAMETER, left * ratio, unit));
      }
    }
    return items;
  }

  /**
   * The patient's right is on the left of the image unless Patient Orientation (0020,0020) says
   * that the rows run toward the patient's right. Without the attribute the usual display of a
   * frontal chest radiograph is assumed, which is what the attribute states on a conformant image.
   */
  static boolean isPatientRightOnImageLeft(MeasurableLayer layer) {
    TagW tag = TagW.get("PatientOrientation"); // NON-NLS
    return isPatientRightOnImageLeft(tag == null ? null : layer.getSourceTagValue(tag));
  }

  static boolean isPatientRightOnImageLeft(Object patientOrientation) {
    String rows =
        switch (patientOrientation) {
          case String[] values when values.length > 0 -> values[0];
          case String text -> text;
          case null, default -> null;
        };
    return rows == null || !rows.trim().toUpperCase().startsWith("R"); // NON-NLS
  }

  @Override
  public List<Measurement> getMeasurementList() {
    return MEASUREMENT_LIST;
  }
}
