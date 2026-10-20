/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.area.EllipseGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.util.MouseEventDouble;

@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphicHitOrderTest {

  private static PolylineGraphic line(double y) throws Exception {
    PolylineGraphic graphic = new PolylineGraphic();
    graphic.buildGraphic(List.of(new Point2D.Double(0, y), new Point2D.Double(100, y)));
    return graphic;
  }

  /** A click in image coordinates, on a component without transform: one pixel per unit. */
  private static MouseEventDouble clickAt(double x, double y) {
    MouseEventDouble event =
        new MouseEventDouble(
            new JPanel(), MouseEvent.MOUSE_PRESSED, 0, 0, (int) x, (int) y, 0, 0, 1, false, 1);
    event.setImageCoordinates(x, y);
    return event;
  }

  @Test
  void the_nearest_outline_comes_first_even_below_another_graphic() throws Exception {
    GraphicModel model = new XmlGraphicModel();
    PolylineGraphic near = line(3);
    PolylineGraphic upper = line(0);
    model.addGraphic(near);
    model.addGraphic(upper);

    assertEquals(List.of(near, upper), model.getAllGraphicsIntersecting(clickAt(50, 2.5)));
  }

  @Test
  void outlines_at_the_same_distance_keep_the_upper_graphic_first() throws Exception {
    GraphicModel model = new XmlGraphicModel();
    PolylineGraphic lower = line(0);
    PolylineGraphic upper = line(0);
    model.addGraphic(lower);
    model.addGraphic(upper);

    assertEquals(List.of(upper, lower), model.getAllGraphicsIntersecting(clickAt(50, 1)));
  }

  @Test
  void the_selected_graphic_wins_over_a_nearer_one() throws Exception {
    GraphicModel model = new XmlGraphicModel();
    PolylineGraphic near = line(3);
    PolylineGraphic selected = line(0);
    model.addGraphic(near);
    model.addGraphic(selected);
    model.setSelectedGraphic(List.of(selected));

    assertEquals(selected, model.getFirstGraphicIntersecting(clickAt(50, 2.5)).orElse(null));
  }

  @Test
  void the_outline_distance_ignores_the_inside_of_a_closed_shape() {
    Rectangle2D square = new Rectangle2D.Double(0, 0, 40, 40);
    assertEquals(10, AbstractGraphicModel.outlineDistance(square, new Point2D.Double(10, 20), 1));
    assertEquals(
        5,
        AbstractGraphicModel.outlineDistance(
            new Line2D.Double(0, 0, 10, 0), new Point2D.Double(15, 0), 1));
  }

  /** Paints the graphic, which decides whether the axis guide of an ellipse is shown. */
  private static void paint(Graphic graphic) {
    Graphics2D g2d = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
    try {
      graphic.paint(g2d, new AffineTransform());
    } finally {
      g2d.dispose();
    }
  }

  @Test
  void the_hidden_axis_of_an_ellipse_cannot_be_clicked() throws Exception {
    GraphicModel model = new XmlGraphicModel();
    EllipseGraphic ellipse = new EllipseGraphic();
    // The axis A-B along y = 50 touches the ellipse in C, D sets its height of 200
    ellipse.buildGraphic(
        List.of(
            new Point2D.Double(0, 50),
            new Point2D.Double(100, 50),
            new Point2D.Double(50, 50),
            new Point2D.Double(50, -150)));
    model.addGraphic(ellipse);
    // Far from the handles and from the curve, which is about 20 above that point
    MouseEventDouble onAxis = clickAt(15, 50);

    paint(ellipse);
    assertTrue(model.getAllGraphicsIntersecting(onAxis).isEmpty());

    model.setSelectedGraphic(List.of(ellipse));
    paint(ellipse);
    assertEquals(List.of(ellipse), model.getAllGraphicsIntersecting(onAxis));
  }

  @Test
  void graphics_away_from_the_cursor_are_not_hit() throws Exception {
    GraphicModel model = new XmlGraphicModel();
    Graphic far = line(60);
    model.addGraphic(far);
    model.addGraphic(line(0));

    assertEquals(1, model.getAllGraphicsIntersecting(clickAt(50, 1)).size());
  }
}
