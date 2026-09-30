/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import java.awt.Rectangle;

/** A rectangle in the canvas pixels of a pane: {@code x,y,width,height}, the last two positive. */
public record CanvasRect(int x, int y, int width, int height) implements SemanticType {

  private static final String EXPECTED = "four integers x,y,width,height with a positive size";

  public CanvasRect {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException(EXPECTED);
    }
  }

  public static CanvasRect parse(String text) {
    int[] values = SemanticType.integers(text, 4, EXPECTED);
    return new CanvasRect(values[0], values[1], values[2], values[3]);
  }

  public Rectangle toRectangle() {
    return new Rectangle(x, y, width, height);
  }

  @Override
  public String toString() {
    return x + "," + y + "," + width + "," + height;
  }
}
