/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.view;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** The scale bar and the overview mapping drawn over a slide. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class OverlayGeometryTest {

  private static final double EPS = 1e-6;

  private static void assertPoint(double x, double y, Point2D p) {
    assertEquals(x, p.getX(), 1e-6);
    assertEquals(y, p.getY(), 1e-6);
  }

  @Test
  void the_scale_bar_takes_a_round_length_below_the_width_wanted() {
    ScaleBar micro = ScaleBar.of(0.001, 160).orElseThrow();
    ScaleBar twoHundred = ScaleBar.of(0.0025, 160).orElseThrow();
    ScaleBar milli = ScaleBar.of(0.02, 160).orElseThrow();
    assertAll(
        () -> assertEquals("100 µm", micro.label()), // NON-NLS
        () -> assertEquals(100, micro.lengthPx(), EPS),
        () -> assertEquals("200 µm", twoHundred.label()), // NON-NLS
        () -> assertEquals(80, twoHundred.lengthPx(), EPS),
        () -> assertEquals("2 mm", milli.label()), // NON-NLS
        () -> assertEquals(100, milli.lengthPx(), EPS),
        () -> assertEquals(Optional.empty(), ScaleBar.of(0, 160)),
        () -> assertEquals(Optional.empty(), ScaleBar.of(Double.NaN, 160)));
  }

  @Test
  void the_overview_maps_its_corners_to_the_slide_corners() {
    OverviewMap map = OverviewMap.of(1000, 47003, 80672, 200);
    Rectangle box = map.box();
    assertAll(
        () -> assertEquals(200, box.height),
        () -> assertEquals(117, box.width),
        () -> assertEquals(1000 - 117 - OverviewMap.MARGIN, box.x),
        () -> assertPoint(0, 0, map.toModel(new Point2D.Double(box.x, box.y))),
        () ->
            assertPoint(
                47003, 80672, map.toModel(new Point2D.Double(box.getMaxX(), box.getMaxY()))),
        () -> assertTrue(map.contains(new Point2D.Double(box.getCenterX(), box.getCenterY()))),
        () -> assertFalse(map.contains(new Point2D.Double(10, 10))));
  }
}
