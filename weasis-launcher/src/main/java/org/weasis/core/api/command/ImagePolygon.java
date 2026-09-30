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
import java.util.ArrayList;
import java.util.List;

/** An open or closed outline in image pixels: {@code x1,y1,x2,y2,…}, two points at least. */
public record ImagePolygon(List<Point2D> points) implements SemanticType {

  private static final String EXPECTED = "x,y pairs in image pixels, two points at least";

  public ImagePolygon {
    if (points == null || points.size() < 2) {
      throw new IllegalArgumentException(EXPECTED);
    }
    points = List.copyOf(points);
  }

  public static ImagePolygon parse(String text) {
    double[] values = SemanticType.decimals(text, -1, EXPECTED);
    if (values.length < 4 || values.length % 2 != 0) {
      throw new IllegalArgumentException(EXPECTED);
    }
    List<Point2D> points = new ArrayList<>(values.length / 2);
    for (int i = 0; i < values.length; i += 2) {
      points.add(new Point2D.Double(values[i], values[i + 1]));
    }
    return new ImagePolygon(points);
  }

  @Override
  public String toString() {
    StringBuilder text = new StringBuilder();
    for (Point2D point : points) {
      if (!text.isEmpty()) {
        text.append(',');
      }
      text.append(point.getX()).append(',').append(point.getY());
    }
    return text.toString();
  }
}
