/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.GraphicLabel;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AnnotationLabelAlignmentTest {

  private static final double G = GraphicLabel.GROWING_BOUND;

  private static AnnotationGraphic graphic() {
    AnnotationGraphic g = new AnnotationGraphic();
    g.setLabelWidth(80.0);
    g.setLabelHeight(12.0);
    return g;
  }

  @Test
  void label_is_centred_on_the_box_point_by_default() {
    Rectangle2D b = graphic().buildLabelBounds(new Point2D.Double(100, 50), 2);

    assertEquals(100, b.getCenterX(), 1e-9);
    assertEquals(50, b.getCenterY(), 1e-9);
    assertEquals(80 + 4 * G, b.getWidth(), 1e-9);
    assertEquals(24 + 4 * G, b.getHeight(), 1e-9);
  }

  @Test
  void label_starts_at_the_box_point_when_aligned_top_left() {
    AnnotationGraphic g = graphic();
    g.setLabelAlignment(0, 0);
    Rectangle2D b = g.buildLabelBounds(new Point2D.Double(100, 50), 1);

    assertEquals(100, b.getMinX(), 1e-9);
    assertEquals(50, b.getMinY(), 1e-9);
  }

  @Test
  void label_ends_at_the_box_point_when_aligned_bottom_right() {
    AnnotationGraphic g = graphic();
    g.setLabelAlignment(1, 1);
    Rectangle2D b = g.buildLabelBounds(new Point2D.Double(100, 50), 1);

    assertEquals(100, b.getMaxX(), 1e-9);
    assertEquals(50, b.getMaxY(), 1e-9);
  }

  @Test
  void copy_keeps_the_alignment() {
    AnnotationGraphic g = graphic();
    g.setLabelAlignment(1, 0);
    AnnotationGraphic copy = g.copy();

    assertEquals(1.0, copy.getLabelAlignX());
    assertEquals(0.0, copy.getLabelAlignY());
  }
}
