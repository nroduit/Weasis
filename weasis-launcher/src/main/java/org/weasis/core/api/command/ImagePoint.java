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

import java.awt.geom.Point2D;

/** A point in image pixels, origin at the top-left corner of the image: {@code x,y}. */
public record ImagePoint(double x, double y) implements SemanticType {

  private static final String EXPECTED = "two numbers x,y in image pixels";

  public static ImagePoint parse(String text) {
    double[] values = SemanticType.decimals(text, 2, EXPECTED);
    return new ImagePoint(values[0], values[1]);
  }

  public Point2D toPoint() {
    return new Point2D.Double(x, y);
  }

  @Override
  public String toString() {
    return x + "," + y;
  }
}
