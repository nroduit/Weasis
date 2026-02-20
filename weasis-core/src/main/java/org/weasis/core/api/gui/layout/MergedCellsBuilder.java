/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.layout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent builder for creating MigLayoutModel with merged cells. Provides a convenient API for
 * defining rectangular merged cells in a grid layout.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * MigLayoutModel layout = new MergedCellsBuilder(3, 3, "layout-id", "My Layout", "ViewerClass")
 *     .addMergedCell(0, 0, 2, 2)  // Top-left 2x2 merged cell
 *     .addCell(0, 2)              // Single cell at (0, 2)
 *     .build();
 * }</pre>
 */
public class MergedCellsBuilder {
  private final int rows;
  private final int cols;
  private final String id;
  private final String title;
  private final String defaultClass;
  private final Map<Integer, int[]> mergedCells = new LinkedHashMap<>();

  /**
   * Creates a builder for a grid layout with merged cells.
   *
   * @param rows number of rows in the grid
   * @param cols number of columns in the grid
   * @param id unique layout identifier
   * @param title display title
   * @param defaultClass default component class name
   */
  public MergedCellsBuilder(int rows, int cols, String id, String title, String defaultClass) {
    if (rows < 1 || cols < 1) {
      throw new IllegalArgumentException("Grid dimensions must be positive");
    }
    this.rows = rows;
    this.cols = cols;
    this.id = Objects.requireNonNull(id, "id cannot be null");
    this.title = Objects.requireNonNull(title, "title cannot be null");
    this.defaultClass = Objects.requireNonNull(defaultClass, "defaultClass cannot be null");
  }

  /**
   * Adds a merged cell at the specified position.
   *
   * @param row starting row (0-based)
   * @param col starting column (0-based)
   * @param spanX number of columns to span
   * @param spanY number of rows to span
   * @return this builder for chaining
   * @throws IllegalArgumentException if parameters are invalid or cell exceeds boundaries
   */
  public MergedCellsBuilder addMergedCell(int row, int col, int spanX, int spanY) {
    validatePosition(row, col);
    validateSpan(spanX, spanY);
    validateBoundaries(row, col, spanX, spanY);

    int position = row * cols + col;
    mergedCells.put(position, new int[] {spanX, spanY});
    return this;
  }

  /**
   * Adds a single cell at the specified position.
   *
   * @param row cell row (0-based)
   * @param col cell column (0-based)
   * @return this builder for chaining
   */
  public MergedCellsBuilder addCell(int row, int col) {
    return addMergedCell(row, col, 1, 1);
  }

  /**
   * Builds the MigLayoutModel with the configured merged cells.
   *
   * @return configured MigLayoutModel
   */
  public MigLayoutModel build() {
    return new MigLayoutModel(id, title, rows, cols, defaultClass, mergedCells);
  }

  private void validatePosition(int row, int col) {
    if (row < 0 || row >= rows) {
      throw new IllegalArgumentException(
          "Row must be between 0 and %d, got: %d".formatted(rows - 1, row));
    }
    if (col < 0 || col >= cols) {
      throw new IllegalArgumentException(
          "Column must be between 0 and %d, got: %d".formatted(cols - 1, col));
    }
  }

  private void validateSpan(int spanX, int spanY) {
    if (spanX < 1 || spanY < 1) {
      throw new IllegalArgumentException(
          "Span dimensions must be positive, got: [%d, %d]".formatted(spanX, spanY));
    }
  }

  private void validateBoundaries(int row, int col, int spanX, int spanY) {
    if (row + spanY > rows || col + spanX > cols) {
      throw new IllegalArgumentException(
          "Merged cell at (%d,%d) with span [%d,%d] exceeds grid boundaries [%d,%d]"
              .formatted(row, col, spanX, spanY, cols, rows));
    }
  }
}
