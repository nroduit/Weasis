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

import java.util.Objects;
import org.weasis.core.api.util.Copyable;
import org.weasis.core.util.StringUtil;

/**
 * Represents a cell in the MigLayout grid.
 *
 * @param position position in layout order
 * @param type component type class name
 * @param constraints MigLayout cell constraints (should not contain "span" as it's auto-generated)
 * @param x grid x position
 * @param y grid y position
 * @param spanX number of columns to span
 * @param spanY number of rows to span
 */
public record MigCell(
    int position, String type, String constraints, int x, int y, int spanX, int spanY)
    implements Copyable<MigCell> {

  public MigCell {
    Objects.requireNonNull(type, "type cannot be null");
    if (spanX < 1 || spanY < 1) {
      throw new IllegalArgumentException("Span values must be positive");
    }
  }

  /** Gets the full MigLayout constraints string for this cell, including span if needed. */
  public String getFullConstraints() {
    StringBuilder sb = new StringBuilder();

    if ((spanX > 1 || spanY > 1) && !hasSpanConstraint()) {
      sb.append("span ").append(spanX);
      if (spanY > 1) {
        sb.append(" ").append(spanY);
      }
    }

    // Add custom constraints
    if (StringUtil.hasText(constraints)) {
      if (!sb.isEmpty()) {
        sb.append(", ");
      }
      sb.append(constraints);
    }

    return sb.toString();
  }

  /** Checks if the constraints string already contains a span directive. */
  private boolean hasSpanConstraint() {
    return StringUtil.hasText(constraints) && constraints.toLowerCase().contains("span");
  }

  @Override
  public MigCell copy() {
    return new MigCell(position, type, constraints, x, y, spanX, spanY);
  }
}
