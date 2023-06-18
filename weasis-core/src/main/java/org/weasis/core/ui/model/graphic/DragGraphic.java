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

import java.awt.geom.Point2D;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.util.MouseEventDouble;

public interface DragGraphic extends Graphic {
  Boolean DEFAULT_RESIZE_OR_MOVING = Boolean.FALSE;

  Boolean getResizingOrMoving();

  void setResizeOrMoving(Boolean value);

  void buildShape(MouseEventDouble mouseEvent);

  void forceToAddPoints(Integer fromPtIndex);

  Point2D removeHandlePoint(Integer index, MouseEventDouble mouseEvent);

  void moveMouseOverHandlePoint(Integer handlePtIndex, MouseEventDouble event);

  Integer moveAndResizeOnDrawing(
      Integer handlePointIndex, Double deltaX, Double deltaY, MouseEventDouble mouseEvent);

  Draggable createMoveDrag();

  Draggable createDragLabelSequence();

  Draggable createResizeDrag();

  Draggable createResizeDrag(Integer i);

  @Override
  DragGraphic copy();
}
