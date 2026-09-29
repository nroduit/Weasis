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
import java.awt.Shape;
import java.awt.geom.AffineTransform;
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
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.model.graphic.AbstractDragGraphic;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * Two perpendicular axes crossing inside the first one (RECIST bidirectional measurement). The
 * second axis is placed automatically while the first is drawn; its ends can then be moved, sliding
 * the crossing point along the first axis. The longer of the two is reported as the long axis.
 */
@XmlType(name = "bidirectional")
@XmlRootElement(name = "bidirectional")
@XmlAccessorType(XmlAccessType.NONE)
public class BidirectionalGraphic extends AbstractDragGraphic {

  public static final Integer POINTS_NUMBER = 4;
  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_BIDIRECTIONAL);

  public static final Measurement LONG_AXIS =
      new Measurement("length.long", Messages.getString("measure.long_axis"), 1, true, true, true);
  public static final Measurement SHORT_AXIS =
      new Measurement(
          "length.short", Messages.getString("measure.short_axis"), 2, true, true, true);
  public static final Measurement RATIO =
      new Measurement("ratio", Messages.getString("measure.ratio"), 3, true, true, false);

  protected static final List<Measurement> MEASUREMENT_LIST = List.of(LONG_AXIS, SHORT_AXIS, RATIO);

  protected Point2D ptA;
  protected Point2D ptB;
  protected Point2D ptC;
  protected Point2D ptD;
  protected boolean lineABvalid;
  protected boolean lineCDvalid;

  /** Once the user has moved the short axis it is no longer re-centred on the long axis. */
  private boolean shortAxisCustom;

  public BidirectionalGraphic() {
    super(POINTS_NUMBER);
  }

  public BidirectionalGraphic(BidirectionalGraphic graphic) {
    super(graphic);
  }

  @Override
  public BidirectionalGraphic copy() {
    return new BidirectionalGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("measure.bidirectional");
  }

  @Override
  protected void prepareShape() throws InvalidShapeException {
    if (!isShapeValid()) {
      throw new InvalidShapeException("This shape cannot be drawn");
    }
    // A graphic rebuilt from its points (file, copy) keeps the short axis where it is
    shortAxisCustom = true;
    buildShape(null);
  }

  @Override
  public Integer moveAndResizeOnDrawing(
      Integer handlePointIndex, Double deltaX, Double deltaY, MouseEventDouble mouseEvent) {
    List<Point2D> previous = getHandlePointList();
    handlePointIndex = super.moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, mouseEvent);
    updateTool();
    if (handlePointIndex == 0 || handlePointIndex == 1) {
      if (lineABvalid) {
        if (!shortAxisCustom || ptC == null || ptD == null) {
          placeShortAxis();
        } else {
          followLongAxis(previous, handlePointIndex);
        }
      }
    } else if (handlePointIndex == 2 && lineABvalid && ptC != null && ptD != null) {
      shortAxisCustom = true;
      moveShortAxisEnd(2, 3);
    } else if (handlePointIndex == 3 && lineABvalid && ptC != null && ptD != null) {
      shortAxisCustom = true;
      moveShortAxisEnd(3, 2);
    }
    return handlePointIndex;
  }

  /** Smallest half-length of the short axis, in image pixels, so it never collapses. */
  static final double MIN_HALF_LENGTH = 1.0;

  /** Long axis over short axis when the short axis is placed automatically. */
  static final double INITIAL_AXIS_RATIO = 1.5;

  /** Centres the short axis on the long axis, with a length that stays clearly visible. */
  void placeShortAxis() {
    double half = ptA.distance(ptB) / INITIAL_AXIS_RATIO / 2;
    placeShortAxis(GeomUtil.getMidPoint(ptA, ptB), half, -half);
  }

  /** Rotates the short axis with the long axis around the handle that did not move. */
  private void followLongAxis(List<Point2D> previous, int movedIndex) {
    Point2D prevA = previous.size() > 0 ? previous.get(0) : null;
    Point2D prevB = previous.size() > 1 ? previous.get(1) : null;
    if (GeomUtil.isLineValid(prevA, prevB)) {
      double theta = GeomUtil.getAngleRad(prevA, prevB) - GeomUtil.getAngleRad(ptA, ptB);
      Point2D anchor = movedIndex == 0 ? ptB : ptA;
      AffineTransform rotate =
          AffineTransform.getRotateInstance(theta, anchor.getX(), anchor.getY());
      rotate.transform(ptC, ptC);
      rotate.transform(ptD, ptD);
    }
    placeShortAxis(GeomUtil.getPerpendicularPointToLine(ptA, ptB, ptC), sideOf(ptC), sideOf(ptD));
  }

  /**
   * The dragged end slides the crossing point along the long axis and sets its own half-length; the
   * other end keeps its half-length on the opposite side.
   */
  private void moveShortAxisEnd(int movedIndex, int otherIndex) {
    Point2D cursor = getHandlePoint(movedIndex);
    Point2D other = getHandlePoint(otherIndex);
    double otherSide = sideOf(other);
    double cursorSide = sideOf(cursor);
    double movedHalf =
        Math.signum(cursorSide) == -Math.signum(otherSide) ? cursorSide : -Math.signum(otherSide);
    Point2D crossing = GeomUtil.getPerpendicularPointToLine(ptA, ptB, cursor);
    if (movedIndex == 2) {
      placeShortAxis(crossing, movedHalf, otherSide);
    } else {
      placeShortAxis(crossing, otherSide, movedHalf);
    }
  }

  /** Signed distance of a point to the long axis; its sign tells the side. */
  private double sideOf(Point2D p) {
    return GeomUtil.getSignedDistanceToLine(ptA, ptB, p);
  }

  /**
   * Sets C and D perpendicular to the long axis through the crossing point clamped inside AB, at
   * the given signed half-lengths on opposite sides, each at least {@link #MIN_HALF_LENGTH}.
   */
  private void placeShortAxis(Point2D crossing, double halfC, double halfD) {
    double length = ptA.distance(ptB);
    double t =
        ((crossing.getX() - ptA.getX()) * (ptB.getX() - ptA.getX())
                + (crossing.getY() - ptA.getY()) * (ptB.getY() - ptA.getY()))
            / (length * length);
    t = Math.clamp(t, 0, 1);
    Point2D foot =
        new Point2D.Double(
            ptA.getX() + (ptB.getX() - ptA.getX()) * t, ptA.getY() + (ptB.getY() - ptA.getY()) * t);
    double signC = halfC >= 0 ? 1 : -1;
    double c = signC * Math.max(Math.abs(halfC), MIN_HALF_LENGTH);
    double d = -signC * Math.max(Math.abs(halfD), MIN_HALF_LENGTH);
    setHandlePoint(2, GeomUtil.getPerpendicularPointFromLine(ptA, ptB, foot, c));
    setHandlePoint(3, GeomUtil.getPerpendicularPointFromLine(ptA, ptB, foot, d));
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    updateTool();
    Shape newShape = null;
    Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 2);
    if (lineABvalid) {
      path.append(new Line2D.Double(ptA, ptB), false);
    }
    if (lineCDvalid) {
      path.append(new Line2D.Double(ptC, ptD), false);
    }
    if (path.getCurrentPoint() != null) {
      AdvancedShape shape = new AdvancedShape(this, 1);
      shape.addShape(path);
      newShape = shape;
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
        double ab = ptA.distance(ptB);
        double cd = ptC.distance(ptD);
        double longAxis = Math.max(ab, cd);
        double shortAxis = Math.min(ab, cd);
        List<MeasureItem> items = new ArrayList<>(3);
        if (LONG_AXIS.getComputed()) {
          items.add(
              new MeasureItem(LONG_AXIS, longAxis * adapter.calibrationRatio(), adapter.unit()));
        }
        if (SHORT_AXIS.getComputed()) {
          items.add(
              new MeasureItem(SHORT_AXIS, shortAxis * adapter.calibrationRatio(), adapter.unit()));
        }
        if (RATIO.getComputed() && longAxis > 0) {
          items.add(new MeasureItem(RATIO, shortAxis / longAxis, null));
        }
        return items;
      }
    }
    return Collections.emptyList();
  }

  @Override
  public boolean isShapeValid() {
    updateTool();
    return lineABvalid && lineCDvalid;
  }

  protected void updateTool() {
    ptA = getHandlePoint(0);
    ptB = getHandlePoint(1);
    ptC = getHandlePoint(2);
    ptD = getHandlePoint(3);
    lineABvalid = GeomUtil.isLineValid(ptA, ptB);
    lineCDvalid = GeomUtil.isLineValid(ptC, ptD);
  }

  @Override
  public List<Measurement> getMeasurementList() {
    return MEASUREMENT_LIST;
  }
}
