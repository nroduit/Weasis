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

/** A rectangle in screen pixels: {@code x,y,width,height}, the last two positive. */
@Definition(value = "a rectangle in screen pixels: x,y,width,height")
public record ScreenRect(int x, int y, int width, int height) implements SemanticType {

  private static final String EXPECTED = "four integers x,y,width,height with a positive size";

  public ScreenRect {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException(EXPECTED);
    }
  }

  public static ScreenRect parse(String text) {
    String[] parts = text == null ? new String[0] : text.split(",");
    if (parts.length != 4) {
      throw new IllegalArgumentException(EXPECTED);
    }
    return new ScreenRect(
        SemanticType.index(parts[0], EXPECTED),
        SemanticType.index(parts[1], EXPECTED),
        SemanticType.index(parts[2], EXPECTED),
        SemanticType.index(parts[3], EXPECTED));
  }

  public Rectangle toRectangle() {
    return new Rectangle(x, y, width, height);
  }

  @Override
  public String toString() {
    return x + "," + y + "," + width + "," + height;
  }
}
