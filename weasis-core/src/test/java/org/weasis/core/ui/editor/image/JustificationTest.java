/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.editor.image.Justification.Horizontal;
import org.weasis.core.ui.editor.image.Justification.Vertical;

class JustificationTest {

  /** The image origin in the view for an offset, as GraphicsPane computes it. */
  private static double originX(double view, double image, double offset, double scale) {
    return (view - image) * 0.5 - offset * scale;
  }

  @Test
  void aJustifiedImagePutsItsEdgeOnTheEdgeOfTheView() {
    double scale = 0.25;
    Point2D left = Justification.of("LEFT", null).modelOffset(800, 600, 300, 600, scale);
    Point2D right = Justification.of("right", null).modelOffset(800, 600, 300, 600, scale);
    Point2D bottom = Justification.of(null, "BOTTOM").modelOffset(800, 600, 800, 400, scale);
    assertAll(
        () -> assertEquals(0.0, originX(800, 300, left.getX(), scale), 1e-9, "left edge"),
        () -> assertEquals(500.0, originX(800, 300, right.getX(), scale), 1e-9, "right edge"),
        () -> assertEquals(0.0, left.getY()),
        () -> assertEquals(200.0, originX(600, 400, bottom.getY(), scale), 1e-9, "bottom edge"),
        () ->
            assertEquals(
                -200.0,
                originX(
                    800,
                    1000,
                    Justification.of("RIGHT", null).modelOffset(800, 600, 1000, 600, scale).getX(),
                    scale),
                1e-9,
                "a larger image shows its right side"));
  }

  @Test
  void unknownValuesCenter() {
    assertAll(
        () -> assertTrue(Justification.of(null, null).isCentered()),
        () -> assertTrue(Justification.of("SIDEWAYS", "").isCentered()), // NON-NLS
        () ->
            assertEquals(
                new Justification(Horizontal.LEFT, Vertical.TOP),
                Justification.fromJson(Justification.of("left", "top").toJson())), // NON-NLS
        () ->
            assertEquals(
                new Point2D.Double(), Justification.CENTER.modelOffset(800, 600, 300, 600, 0.5)));
  }
}
