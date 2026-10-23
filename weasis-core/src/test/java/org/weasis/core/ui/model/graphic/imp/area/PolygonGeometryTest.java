/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.area;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * Area, perimeter and centroid of a polygon, whichever edge the outline iterator leaves implicit.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PolygonGeometryTest {

  private static PolygonGraphic polygon(double... coords) {
    List<Point2D> pts = new ArrayList<>();
    for (int i = 0; i < coords.length; i += 2) {
      pts.add(new Point2D.Double(coords[i], coords[i + 1]));
    }
    PolygonGraphic graphic = new PolygonGraphic();
    graphic.setPts(pts);
    return graphic;
  }

  @Test
  void a_square_has_four_edges() {
    PolygonGraphic square = polygon(100, 100, 200, 100, 200, 200, 100, 200);

    assertAll(
        () -> assertEquals(4, square.getClosedPathSegments().size()),
        () -> assertEquals(10000.0, square.getAreaValue(), 1e-9),
        () -> assertEquals(400.0, square.getPerimeter(), 1e-9),
        () -> assertEquals(new Point2D.Double(150, 150), square.getCentroid()));
  }

  @Test
  void a_triangle_with_a_horizontal_top_edge_keeps_that_edge() {
    PolygonGraphic triangle = polygon(300, 300, 350, 300, 330, 350);
    double perimeter = 50 + Math.hypot(20, 50) + Math.hypot(30, 50);

    assertAll(
        () -> assertEquals(3, triangle.getClosedPathSegments().size()),
        () -> assertEquals(1250.0, triangle.getAreaValue(), 1e-9),
        () -> assertEquals(perimeter, triangle.getPerimeter(), 1e-9),
        () -> assertEquals(980.0 / 3, triangle.getCentroid().getX(), 1e-9),
        () -> assertEquals(950.0 / 3, triangle.getCentroid().getY(), 1e-9));
  }

  @Test
  void a_repeated_first_point_adds_no_edge() {
    PolygonGraphic triangle = polygon(300, 300, 350, 300, 330, 350, 300, 300);

    assertAll(
        () -> assertEquals(3, triangle.getClosedPathSegments().size()),
        () -> assertEquals(1250.0, triangle.getAreaValue(), 1e-9));
  }

  @Test
  void an_oblique_quadrilateral_is_unchanged() {
    PolygonGraphic quad = polygon(120, 100, 200, 130, 180, 220, 90, 190);

    assertAll(
        () -> assertEquals(4, quad.getClosedPathSegments().size()),
        () -> assertEquals(8400.0, quad.getAreaValue(), 1e-9));
  }
}
