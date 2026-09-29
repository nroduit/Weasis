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

  /**
   * @deprecated ambiguous: it rotates the point list on one tool and duplicates a vertex on
   *     another. Use {@link #insertHandlePoint(int, Point2D)} or {@link #resumeDrawing()}.
   */
  @Deprecated(since = "4.8.0", forRemoval = true)
  void forceToAddPoints(Integer fromPtIndex);

  Point2D removeHandlePoint(Integer index, MouseEventDouble mouseEvent);

  /** True when the last vertex is joined back to the first, as on a polygon. */
  boolean isClosedPath();

  /** Vertices this graphic cannot go below, from the descriptor of the tool that produced it. */
  int getMinPoints();

  /** Segments of the path: one less than the vertices, and one more when the path is closed. */
  int getSegmentCount();

  /** Start and end vertex of a segment, {@code null} when the index is not one of them. */
  Point2D[] getSegment(int segmentIndex);

  /** Midpoint of a segment in image coordinates, {@code null} when it is not a segment. */
  Point2D getSegmentMidPoint(int segmentIndex);

  /** True when removing that vertex leaves a valid shape. */
  boolean canRemoveHandlePoint(Integer index);

  /** Inserts a vertex on a segment and returns its index, or {@link #UNDEFINED}. */
  int insertHandlePoint(int segmentIndex, Point2D point);

  /** Reopens an open path so the draw sequence appends to its end; false when it cannot. */
  boolean resumeDrawing();

  Integer moveAndResizeOnDrawing(
      Integer handlePointIndex, Double deltaX, Double deltaY, MouseEventDouble mouseEvent);

  Draggable createMoveDrag();

  Draggable createDragLabelSequence();

  Draggable createResizeDrag();

  Draggable createResizeDrag(Integer i);

  @Override
  DragGraphic copy();
}
