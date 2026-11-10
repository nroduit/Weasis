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

import java.awt.geom.Rectangle2D;

/**
 * A rectangle in image pixels: {@code x,y,width,height}, the last two positive. The values are
 * decimal, as the graphics of a view are.
 */
@Definition(value = "a rectangle in image pixels: x,y,width,height")
public record ImageRect(double x, double y, double width, double height) implements SemanticType {

  private static final String EXPECTED = "four numbers x,y,width,height with a positive size";

  public ImageRect {
    if (!(width > 0) || !(height > 0)) {
      throw new IllegalArgumentException(EXPECTED);
    }
  }

  public static ImageRect parse(String text) {
    double[] values = SemanticType.decimals(text, 4, EXPECTED);
    return new ImageRect(values[0], values[1], values[2], values[3]);
  }

  public Rectangle2D toRectangle() {
    return new Rectangle2D.Double(x, y, width, height);
  }

  @Override
  public String toString() {
    return x + "," + y + "," + width + "," + height;
  }
}
