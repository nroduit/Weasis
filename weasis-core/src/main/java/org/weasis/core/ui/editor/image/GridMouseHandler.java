/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import org.weasis.core.api.gui.layout.ConstraintSpec;
import org.weasis.core.api.gui.layout.LayoutCellManager;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.api.gui.layout.MigLayoutModel;

/**
 * Mouse handler for resizing grid cells by dragging borders. Handles mouse events to detect resize
 * borders and perform drag-resize operations.
 */
public class GridMouseHandler extends MouseAdapter {
  private static final double MIN_CELL_SIZE = 50.0;
  private static final double PRECISION_SCALE = 1000.0;

  private final LayoutCellManager<?> cellManager;
  private final JPanel grid;

  private DragState dragState;

  public GridMouseHandler(LayoutCellManager<?> cellManager, JPanel grid) {
    this.cellManager = cellManager;
    this.grid = grid;
  }

  @Override
  public void mousePressed(MouseEvent e) {
    dragState = createDragStateFromMouseEvent(e);
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    dragState = null;
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (dragState != null && isDraggingWithButton1(e)) {
      processDragResize(e.getPoint());
      updateCursorForDragDirection();
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    int cursorType = determineCursorType(e.getPoint());
    grid.setCursor(Cursor.getPredefinedCursor(cursorType));
  }

  @Override
  public void mouseExited(MouseEvent e) {
    grid.setCursor(Cursor.getDefaultCursor());
  }

  private DragState createDragStateFromMouseEvent(MouseEvent e) {
    Point clickPoint = e.getPoint();
    MigLayoutModel layout = cellManager.getLayoutModel();
    var gridSize = layout.getGridSize();

    if (gridSize.width == 0 || gridSize.height == 0) {
      return null;
    }

    ResizeBorder border = findResizeBorderAt(clickPoint);
    if (border == null) {
      return null;
    }

    Rectangle gridBounds = grid.getBounds();
    ConstraintSpec[] colSpecs =
        parseAndUpdateConstraintSpecs(layout.getColumnConstraints(), gridSize.width, true);
    ConstraintSpec[] rowSpecs =
        parseAndUpdateConstraintSpecs(layout.getRowConstraints(), gridSize.height, false);

    return new DragState(clickPoint, border, gridBounds, colSpecs, rowSpecs);
  }

  private ResizeBorder findResizeBorderAt(Point point) {
    for (var entry : cellManager.getAllEntries()) {
      Component c = entry.getComponent();
      if (c == null) continue;

      var bounds = c.getBounds();
      var cell = entry.getCell();
      if (cell.x() > 0 && isNearVerticalBorder(point, bounds)) {
        return new ResizeBorder(true, cell);
      }
      if (cell.y() > 0 && isNearHorizontalBorder(point, bounds)) {
        return new ResizeBorder(false, cell);
      }
    }
    return null;
  }

  private ConstraintSpec[] parseAndUpdateConstraintSpecs(
      String constraintString, int count, boolean isColumn) {
    ConstraintSpec[] specs = ConstraintSpec.parseAll(constraintString, count);
    Rectangle gridBounds = grid.getBounds();
    double totalSize = isColumn ? gridBounds.getWidth() : gridBounds.getHeight();

    if (totalSize <= 0) {
      return specs;
    }

    MigLayoutModel layout = cellManager.getLayoutModel();
    for (MigCell cell : layout.getCells()) {
      if (isColumn && cell.spanX() != 1) continue;
      if (!isColumn && cell.spanY() != 1) continue;

      Component c = cellManager.getComponent(cell.position());
      if (c == null) continue;

      Rectangle bounds = c.getBounds();
      int index = isColumn ? cell.x() : cell.y();
      double size = isColumn ? bounds.getWidth() : bounds.getHeight();

      if (index < specs.length) {
        double growWeight = calculateGrowWeight(size / totalSize);
        specs[index] = specs[index].withGrowWeight(growWeight);
      }
    }
    return specs;
  }

  private void processDragResize(Point currentPoint) {
    if (dragState.border.isVertical) {
      resizeColumns(currentPoint);
    } else {
      resizeRows(currentPoint);
    }
  }

  private void resizeColumns(Point currentPoint) {
    double dx = currentPoint.x - dragState.startPoint.x;
    int leftIdx = dragState.border.cell.x() - 1;
    int rightIdx = dragState.border.cell.x();

    ConstraintSpec leftSpec = dragState.colSpecs[leftIdx];
    ConstraintSpec rightSpec = dragState.colSpecs[rightIdx];

    double totalWidth = dragState.gridBounds.getWidth();
    double leftWidth = (leftSpec.growWeight() / 100.0) * totalWidth;
    double rightWidth = (rightSpec.growWeight() / 100.0) * totalWidth;

    ResizePair resized = calculateResizedWidths(leftWidth, rightWidth, dx);

    ConstraintSpec[] newSpecs = dragState.colSpecs.clone();
    newSpecs[leftIdx] = leftSpec.withGrowWeight(calculateGrowWeight(resized.first / totalWidth));
    newSpecs[rightIdx] = rightSpec.withGrowWeight(calculateGrowWeight(resized.second / totalWidth));

    applyColumnSpecs(newSpecs);
  }

  private void resizeRows(Point currentPoint) {
    double dy = currentPoint.y - dragState.startPoint.y;
    int topIdx = dragState.border.cell().y() - 1;
    int bottomIdx = dragState.border.cell().y();

    ConstraintSpec topSpec = dragState.rowSpecs[topIdx];
    ConstraintSpec bottomSpec = dragState.rowSpecs[bottomIdx];

    double totalHeight = dragState.gridBounds.getHeight();
    double topHeight = (topSpec.growWeight() / 100.0) * totalHeight;
    double bottomHeight = (bottomSpec.growWeight() / 100.0) * totalHeight;

    ResizePair resized = calculateResizedWidths(topHeight, bottomHeight, dy);

    ConstraintSpec[] newSpecs = dragState.rowSpecs.clone();
    newSpecs[topIdx] = topSpec.withGrowWeight(calculateGrowWeight(resized.first / totalHeight));
    newSpecs[bottomIdx] =
        bottomSpec.withGrowWeight(calculateGrowWeight(resized.second / totalHeight));

    applyRowSpecs(newSpecs);
  }

  private ResizePair calculateResizedWidths(double firstSize, double secondSize, double delta) {
    double newFirst = firstSize + delta;
    double newSecond = secondSize - delta;

    if (newFirst < MIN_CELL_SIZE) {
      newFirst = MIN_CELL_SIZE;
      newSecond = firstSize + secondSize - MIN_CELL_SIZE;
    } else if (newSecond < MIN_CELL_SIZE) {
      newSecond = MIN_CELL_SIZE;
      newFirst = firstSize + secondSize - MIN_CELL_SIZE;
    }

    return new ResizePair(newFirst, newSecond);
  }

  private void applyColumnSpecs(ConstraintSpec[] specs) {
    MigLayoutModel layout = cellManager.getLayoutModel();
    layout.setColumnConstraintSpecs(specs);
    layout.applyConstraintsToLayout(grid);
  }

  private void applyRowSpecs(ConstraintSpec[] specs) {
    MigLayoutModel layout = cellManager.getLayoutModel();
    layout.setRowConstraintSpecs(specs);
    layout.applyConstraintsToLayout(grid);
  }

  private int determineCursorType(Point point) {
    for (MigCell cell : cellManager.getLayoutModel().getCells()) {
      Component c = cellManager.getComponent(cell.position());
      if (c == null) continue;

      Rectangle bounds = c.getBounds();
      if (isNearVerticalBorder(point, bounds)) {
        return Cursor.E_RESIZE_CURSOR;
      }
      if (isNearHorizontalBorder(point, bounds)) {
        return Cursor.S_RESIZE_CURSOR;
      }
    }
    return Cursor.DEFAULT_CURSOR;
  }

  private boolean isNearVerticalBorder(Point point, Rectangle bounds) {
    return Math.abs(bounds.x - point.x) <= MigLayoutModel.DEFAULT_INTERCELL_GAP;
  }

  private boolean isNearHorizontalBorder(Point point, Rectangle bounds) {
    return Math.abs(bounds.y - point.y) <= MigLayoutModel.DEFAULT_INTERCELL_GAP;
  }

  private boolean isDraggingWithButton1(MouseEvent e) {
    return (e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0;
  }

  private void updateCursorForDragDirection() {
    if (dragState != null) {
      int cursorType =
          dragState.border.isVertical ? Cursor.E_RESIZE_CURSOR : Cursor.S_RESIZE_CURSOR;
      grid.setCursor(Cursor.getPredefinedCursor(cursorType));
    }
  }

  private double calculateGrowWeight(double proportion) {
    return Math.round(proportion * PRECISION_SCALE) / 10.0;
  }

  private record DragState(
      Point startPoint,
      ResizeBorder border,
      Rectangle gridBounds,
      ConstraintSpec[] colSpecs,
      ConstraintSpec[] rowSpecs) {}

  private record ResizeBorder(boolean isVertical, MigCell cell) {}

  private record ResizePair(double first, double second) {}
}
