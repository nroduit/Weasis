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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The first drag draws either the first side of an oblique shape or a classic upright rectangle or
 * ellipse in one go; Shift selects the mode that is not the preferred one.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class AxisAlignedCreationTest {

  private static MouseEventDouble drag(double x, double y, boolean shift) {
    MouseEventDouble evt =
        new MouseEventDouble(
            new JPanel(),
            MouseEvent.MOUSE_DRAGGED,
            0,
            shift ? InputEvent.SHIFT_DOWN_MASK : 0,
            0,
            0,
            0,
            0,
            1,
            false,
            MouseEvent.BUTTON1);
    evt.setImageCoordinates(x, y);
    return evt;
  }

  /** The drag sequence adds the start corner and the dragged point before the first move. */
  private static void startDrag(ObliqueRectangleGraphic g) {
    g.setResizeOrMoving(Boolean.TRUE);
    g.getPts().add(new Point2D.Double(10, 10));
    g.getPts().add(new Point2D.Double(10, 10));
  }

  @Test
  void shift_makes_the_drag_the_diagonal_of_an_upright_rectangle() {
    ObliqueRectangleGraphic g = new ObliqueRectangleGraphic();
    startDrag(g);
    g.moveAndResizeOnDrawing(1, 100.0, 50.0, drag(110, 60, true));
    g.buildShape(null);
    Rectangle2D bounds = g.getShape().getBounds2D();
    assertAll(
        () -> assertTrue(g.isGraphicComplete()),
        () -> assertEquals(new Point2D.Double(110, 10), g.getPts().get(1)),
        () -> assertEquals(new Point2D.Double(60, 10), g.getPts().get(2)),
        () -> assertEquals(new Point2D.Double(60, 60), g.getPts().get(3)),
        () -> assertEquals(100.0, bounds.getWidth(), 1e-9),
        () -> assertEquals(50.0, bounds.getHeight(), 1e-9));
  }

  @Test
  void the_ellipse_inherits_the_upright_creation() {
    EllipseGraphic g = new EllipseGraphic();
    startDrag(g);
    g.moveAndResizeOnDrawing(1, 100.0, 50.0, drag(110, 60, true));
    g.moveAndResizeOnDrawing(1, 0.0, 20.0, drag(110, 80, true));
    g.buildShape(null);
    Rectangle2D bounds = g.getShape().getBounds2D();
    assertAll(
        () -> assertTrue(g.isGraphicComplete()),
        () -> assertEquals(100.0, bounds.getWidth(), 1e-6),
        () -> assertEquals(70.0, bounds.getHeight(), 1e-6));
  }

  @Test
  void without_shift_the_first_drag_only_sets_the_first_side() {
    ObliqueRectangleGraphic g = new ObliqueRectangleGraphic();
    startDrag(g);
    g.moveAndResizeOnDrawing(1, 100.0, 50.0, drag(110, 60, false));
    assertAll(
        () -> assertFalse(g.isGraphicComplete()),
        () -> assertEquals(2, g.getPts().size()),
        () -> assertEquals(new Point2D.Double(110, 60), g.getPts().get(1)));
  }

  /** A rectangle for which the upright drag is the preferred mode. */
  private static ObliqueRectangleGraphic uprightByDrag() {
    return new ObliqueRectangleGraphic() {
      @Override
      protected boolean isUprightByDrag() {
        return true;
      }
    };
  }

  @Test
  void with_the_upright_preference_a_plain_drag_is_the_diagonal() {
    ObliqueRectangleGraphic g = uprightByDrag();
    startDrag(g);
    g.moveAndResizeOnDrawing(1, 100.0, 50.0, drag(110, 60, false));
    g.buildShape(null);
    Rectangle2D bounds = g.getShape().getBounds2D();
    assertAll(
        () -> assertTrue(g.isGraphicComplete()),
        () -> assertEquals(100.0, bounds.getWidth(), 1e-9),
        () -> assertEquals(50.0, bounds.getHeight(), 1e-9));
  }

  @Test
  void with_the_upright_preference_shift_draws_the_oblique_shape() {
    ObliqueRectangleGraphic g = uprightByDrag();
    startDrag(g);
    g.moveAndResizeOnDrawing(1, 100.0, 50.0, drag(110, 60, true));
    assertAll(
        () -> assertFalse(g.isGraphicComplete()),
        () -> assertEquals(new Point2D.Double(110, 60), g.getPts().get(1)));
  }

  @Test
  void shift_while_editing_a_finished_shape_keeps_the_oblique_behaviour() {
    ObliqueRectangleGraphic g = new ObliqueRectangleGraphic();
    startDrag(g);
    g.moveAndResizeOnDrawing(1, 100.0, 50.0, drag(110, 60, true));
    g.setResizeOrMoving(Boolean.FALSE);
    g.moveAndResizeOnDrawing(1, 0.0, 30.0, drag(110, 40, true));
    assertAll(
        () -> assertEquals(new Point2D.Double(110, 40), g.getPts().get(1)),
        () -> assertEquals(50.0, g.getPts().get(2).distance(g.getPts().get(3)), 1e-6));
  }
}
