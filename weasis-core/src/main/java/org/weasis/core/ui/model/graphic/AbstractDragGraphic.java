/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import jakarta.xml.bind.annotation.XmlTransient;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.model.utils.imp.DefaultDragSequence;
import org.weasis.core.ui.model.utils.imp.DragLabelSequence;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlTransient
public abstract class AbstractDragGraphic extends AbstractGraphic implements DragGraphic {

  private Boolean resizingOrMoving = DEFAULT_RESIZE_OR_MOVING;

  protected AbstractDragGraphic(Integer pointNumber) {
    super(pointNumber);
  }

  protected AbstractDragGraphic(AbstractGraphic graphic) {
    super(graphic);
  }

  @Override
  public void setResizeOrMoving(Boolean value) {
    this.resizingOrMoving = Optional.ofNullable(value).orElse(DEFAULT_RESIZE_OR_MOVING);
  }

  @Override
  public void buildShape() {
    if (isShapeValid()) {
      buildShape(null);
    }
  }

  @Override
  protected void paintHandles(Graphics2D g2d, AffineTransform transform) {
    if (!getResizingOrMoving()) {
      super.paintHandles(g2d, transform);
    }
  }

  @Override
  public Boolean getResizingOrMoving() {
    return Optional.ofNullable(resizingOrMoving).orElse(Boolean.FALSE);
  }

  /**
   * @deprecated Rotating the point list to resume a drawing silently reorders the vertices of a
   *     closed outline. Use {@link #insertHandlePoint(int, Point2D)} to add a vertex and {@link
   *     #resumeDrawing()} to continue an open path.
   */
  @Deprecated(since = "4.8.0", forRemoval = true)
  @Override
  public void forceToAddPoints(Integer fromPtIndex) {
    if (variablePointsNumber && fromPtIndex >= 0 && fromPtIndex < pts.size()) {
      if (fromPtIndex < pts.size() - 1) {
        List<Point2D> list = pts.subList(fromPtIndex + 1, pts.size());
        for (int i = 0; i <= fromPtIndex; i++) {
          list.add(pts.get(i));
        }
        pts = list;
      }
      pointNumber = UNDEFINED;
    }
  }

  @Override
  public boolean isClosedPath() {
    return false;
  }

  @Override
  public int getMinPoints() {
    return GraphicRegistry.getInstance().minPointsOf(this);
  }

  /** Number of segments: one less than the vertices, and one more on a closed path. */
  @Override
  public int getSegmentCount() {
    int size = pts.size();
    if (size < 2) {
      return 0;
    }
    return isClosedPath() ? size : size - 1;
  }

  /**
   * The two vertices of a segment, {@code null} when the index is not a segment of this path. The
   * last segment of a closed path is the one joining the last vertex back to the first.
   */
  @Override
  public Point2D[] getSegment(int segmentIndex) {
    if (segmentIndex < 0 || segmentIndex >= getSegmentCount()) {
      return null;
    }
    Point2D start = pts.get(segmentIndex);
    Point2D end = pts.get((segmentIndex + 1) % pts.size());
    return start == null || end == null ? null : new Point2D[] {start, end};
  }

  @Override
  public boolean canRemoveHandlePoint(Integer index) {
    return variablePointsNumber
        && index != null
        && index >= 0
        && index < pts.size()
        && pts.size() > getMinPoints();
  }

  /**
   * Inserts a vertex on the given segment and returns its index, or {@link #UNDEFINED} when the
   * segment does not belong to this path. The vertex of the closing segment of a closed path is
   * appended after the last one, so the outline keeps its order.
   */
  @Override
  public int insertHandlePoint(int segmentIndex, Point2D point) {
    if (!variablePointsNumber || point == null || segmentIndex < 0) {
      return UNDEFINED;
    }
    Point2D[] segment = getSegment(segmentIndex);
    if (segment == null) {
      return UNDEFINED;
    }
    int index = segmentIndex + 1;
    pts.add(index, new Point2D.Double(point.getX(), point.getY()));
    if (!Objects.equals(pointNumber, UNDEFINED)) {
      pointNumber = pts.size();
    }
    return index;
  }

  /** Midpoint of a segment in image coordinates, {@code null} when it is not a segment. */
  @Override
  public Point2D getSegmentMidPoint(int segmentIndex) {
    Point2D[] segment = getSegment(segmentIndex);
    if (segment == null) {
      return null;
    }
    return new Point2D.Double(
        (segment[0].getX() + segment[1].getX()) / 2.0,
        (segment[0].getY() + segment[1].getY()) / 2.0);
  }

  /** Reopens the point count so the draw sequence appends to the end of an open path. */
  @Override
  public boolean resumeDrawing() {
    if (!variablePointsNumber || isClosedPath() || pts.size() < 2) {
      return false;
    }
    setPointNumber(UNDEFINED);
    return true;
  }

  @Override
  public Point2D removeHandlePoint(Integer index, MouseEventDouble mouseEvent) {
    if (canRemoveHandlePoint(index)) {
      Point2D pt = pts.remove(index.intValue());
      pointNumber = pts.size();
      buildShape(mouseEvent);
      return pt;
    }
    return null;
  }

  @Override
  public Draggable createMoveDrag() {
    return new DefaultDragSequence(this);
  }

  @Override
  public Draggable createResizeDrag() {
    return createResizeDrag(UNDEFINED);
  }

  @Override
  public Draggable createResizeDrag(Integer i) {
    return new DefaultDragSequence(this, i);
  }

  @Override
  public Draggable createDragLabelSequence() {
    return new DragLabelSequence(this);
  }

  @Override
  public Integer moveAndResizeOnDrawing(
      Integer handlePointIndex, Double deltaX, Double deltaY, MouseEventDouble mouseEvent) {
    int index = Objects.requireNonNullElse(handlePointIndex, UNDEFINED);
    if (index == UNDEFINED) {
      pts.stream()
          .filter(Objects::nonNull)
          .forEach(p -> p.setLocation(p.getX() + deltaX, p.getY() + deltaY));
    } else if (index >= 0 && index < pts.size()) {
      Point2D point = pts.get(index);
      Optional.ofNullable(point).ifPresent(p -> p.setLocation(mouseEvent.getImageCoordinates()));
    }
    return index;
  }
}
