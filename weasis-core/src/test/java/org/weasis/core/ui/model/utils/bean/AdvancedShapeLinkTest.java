/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.BasicStroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.utils.bean.AdvancedShape.LinkArrowShape;
import org.weasis.core.ui.model.utils.bean.AdvancedShape.LinkSegmentToInvariantShape;

/** A label aligned on its top left corner, at the box point (100, 50), linked to (200, 150). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class AdvancedShapeLinkTest {

  private static final Point2D BOX = new Point2D.Double(100, 50);
  private static final Point2D ANCHOR = new Point2D.Double(200, 150);
  private static final Rectangle2D LABEL = new Rectangle2D.Double(100, 50, 80, 20);

  private final AdvancedShape shape = new AdvancedShape(new AnnotationGraphic(), 3);
  private final LinkSegmentToInvariantShape link =
      shape.addLinkSegmentToInvariantShape(
          new Line2D.Double(BOX, ANCHOR), BOX, LABEL, new BasicStroke(), false);
  private final LinkArrowShape arrow = shape.addLinkArrow(link, 15, 8, new BasicStroke(), false);
  private final AdvancedShape.InvariantShape frame =
      shape.addAllInvShape(LABEL, BOX, new BasicStroke(), false);

  private static Point2D onScreen(AffineTransform t, Point2D p) {
    return t.transform(p, null);
  }

  private static void assertPoint(double x, double y, Point2D p) {
    assertEquals(x, p.getX(), 1e-9);
    assertEquals(y, p.getY(), 1e-9);
  }

  @Test
  void link_leaves_the_label_from_the_edge_facing_the_anchor() {
    AffineTransform t = new AffineTransform(2, 0, 0, 2, 10, 20);
    shape.setAffineTransform(t);

    // On screen the label is (210, 120, 80, 20) and the anchor (410, 320): the line from the
    // center of the label (250, 130) leaves it by its bottom edge
    Line2D line = (Line2D) link.getRealShape();
    assertPoint(250 + 160 * 10 / 190.0, 140, onScreen(t, line.getP1()));
    assertPoint(200, 150, line.getP2());
  }

  @Test
  void arrow_points_along_the_link() {
    AffineTransform t = new AffineTransform(2, 0, 0, 2, 10, 20);
    shape.setAffineTransform(t);

    Line2D line = (Line2D) link.getRealShape();
    Point2D start = onScreen(t, line.getP1());
    Point2D tip = onScreen(t, line.getP2());
    double len = start.distance(tip);
    // The head is drawn from the center of its base, 15 pixels before the tip along the link
    double[] base = new double[6];
    t.createTransformedShape(arrow.getRealShape()).getPathIterator(null).currentSegment(base);
    assertEquals(tip.getX() - 15 * (tip.getX() - start.getX()) / len, base[0], 1e-9);
    assertEquals(tip.getY() - 15 * (tip.getY() - start.getY()) / len, base[1], 1e-9);
  }

  @Test
  void label_frame_stays_right_and_below_the_box_point_in_a_flipped_view() {
    AffineTransform t = new AffineTransform(-2, 0, 0, 2, 500, 20);
    shape.setAffineTransform(t);

    Point2D box = onScreen(t, BOX);
    Rectangle2D drawn = t.createTransformedShape(frame.getRealShape()).getBounds2D();
    assertEquals(box.getX(), drawn.getMinX(), 1e-9);
    assertEquals(box.getY(), drawn.getMinY(), 1e-9);
    assertEquals(80, drawn.getWidth(), 1e-9);
    assertEquals(20, drawn.getHeight(), 1e-9);
  }

  @Test
  void label_frame_keeps_its_orientation_in_a_rotated_view() {
    AffineTransform t = AffineTransform.getRotateInstance(Math.PI / 2);
    t.scale(1.5, 1.5);
    shape.setAffineTransform(t);

    Point2D box = onScreen(t, BOX);
    Rectangle2D drawn = t.createTransformedShape(frame.getRealShape()).getBounds2D();
    assertEquals(box.getX(), drawn.getMinX(), 1e-9);
    assertEquals(box.getY(), drawn.getMinY(), 1e-9);
    assertEquals(80, drawn.getWidth(), 1e-9);
  }
}
