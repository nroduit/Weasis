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
  public Point2D removeHandlePoint(Integer index, MouseEventDouble mouseEvent) {
    // To keep a valid shape, do not remove when there are 2 points left.
    if (variablePointsNumber && pts.size() > 2 && index >= 0 && index < pts.size()) {
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
    int index = handlePointIndex == null ? UNDEFINED : handlePointIndex;
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
