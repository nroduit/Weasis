/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.seg;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.opencv.seg.Segment;

class SegGraphicTest {

  /** Square outline from (10,10) to (30,30). */
  private static SegGraphic squareGraphic(boolean filled) {
    Path2D path = new Path2D.Double();
    path.append(new Rectangle2D.Double(10, 10, 20, 20), false);
    SegGraphic graphic = new SegGraphic(path);
    graphic.setFilled(filled);
    return graphic;
  }

  @Test
  void isOnOutlineHitsBorderOnlyWhenNotFilled() {
    SegGraphic graphic = squareGraphic(false);
    assertAll(
        () -> assertTrue(graphic.isOnOutline(new Point2D.Double(10, 20), 1), "on the left edge"),
        () -> assertTrue(graphic.isOnOutline(new Point2D.Double(11.5, 20), 2), "inside tolerance"),
        () -> assertTrue(graphic.isOnOutline(new Point2D.Double(9, 20), 2), "outside tolerance"),
        () -> assertFalse(graphic.isOnOutline(new Point2D.Double(20, 20), 1), "region interior"),
        () ->
            assertFalse(graphic.isOnOutline(new Point2D.Double(5, 20), 1), "too far from border"));
  }

  @Test
  void isOnOutlineHitsInteriorWhenFilled() {
    assertTrue(squareGraphic(true).isOnOutline(new Point2D.Double(20, 20), 1));
  }

  @Test
  void highlightWidensTheStroke() {
    SegGraphic graphic = squareGraphic(false);
    float width = ((BasicStroke) graphic.getStroke(2f)).getLineWidth();
    graphic.setHighlighted(true);
    assertEquals(
        width + SegGraphic.HIGHLIGHT_EXTRA_THICKNESS,
        ((BasicStroke) graphic.getStroke(2f)).getLineWidth());
  }

  @Test
  void contourGraphicKeepsTheRegionReference() {
    SegRegion<ImageElement> region = new SegRegion<>(1, "Liver", Color.RED);
    Segment segment =
        new Segment(
            List.of(
                new Point2D.Double(10, 10),
                new Point2D.Double(30, 10),
                new Point2D.Double(30, 30),
                new Point2D.Double(10, 30)),
            true);
    SegContour contour = new SegContour("1", List.of(segment));
    contour.setAttributes(region);

    SegGraphic graphic = contour.getSegGraphic();
    assertNotNull(graphic);
    assertAll(
        () -> assertSame(contour, graphic.getContour()),
        () -> assertSame(region, graphic.getRegion()),
        () -> assertTrue(graphic.isOnOutline(new Point2D.Double(10, 20), 1)));
  }
}
