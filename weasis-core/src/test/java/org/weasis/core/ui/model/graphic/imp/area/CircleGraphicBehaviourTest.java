/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.util.MouseEventDouble;

/** The circle is drawn in one gesture; a click drops the default radius. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CircleGraphicBehaviourTest {

  private static MouseEventDouble event(int id, double x, double y) {
    MouseEventDouble evt =
        new MouseEventDouble(new JPanel(), id, 0, 0, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON1);
    evt.setImageCoordinates(x, y);
    return evt;
  }

  private static double value(List<MeasureItem> items, String key) {
    return items.stream()
        .filter(i -> i.getMeasurement().getKey().equals(key))
        .mapToDouble(i -> ((Number) i.getValue()).doubleValue())
        .findFirst()
        .orElseThrow();
  }

  @Test
  void press_drag_release_draws_the_circle_from_its_centre() {
    CircleGraphic g = new CircleGraphic();
    Draggable sequence = g.createResizeDrag();
    sequence.startDrag(event(MouseEvent.MOUSE_PRESSED, 50, 40));
    sequence.drag(event(MouseEvent.MOUSE_DRAGGED, 80, 80));
    boolean done = sequence.completeDrag(event(MouseEvent.MOUSE_RELEASED, 80, 80));
    Rectangle2D bounds = g.getShape().getBounds2D();
    assertAll(
        () -> assertTrue(done),
        () -> assertEquals(new Point2D.Double(50, 40), g.getCenter()),
        () -> assertEquals(50.0, g.getRadius(), 1e-9),
        () -> assertEquals(0.0, bounds.getMinX(), 1e-6),
        () -> assertEquals(100.0, bounds.getWidth(), 1e-6));
  }

  @Test
  void a_click_drops_a_circle_of_the_default_radius() {
    CircleGraphic g = new CircleGraphic();
    Draggable sequence = g.createResizeDrag();
    sequence.startDrag(event(MouseEvent.MOUSE_PRESSED, 50, 40));
    boolean done = sequence.completeDrag(event(MouseEvent.MOUSE_RELEASED, 50, 40));
    assertAll(
        () -> assertTrue(done),
        () -> assertTrue(g.isShapeValid()),
        () -> assertEquals(new Point2D.Double(50, 40), g.getCenter()),
        () -> assertEquals(CircleGraphic.UNCALIBRATED_RADIUS, g.getRadius(), 1e-9));
  }

  @Test
  void the_default_radius_is_a_length_on_calibrated_images_and_a_screen_size_otherwise() {
    MeasurementsAdapter halfMillimeterPixels =
        new MeasurementsAdapter(0.5, 0, 0, false, 0, Unit.MILLIMETER.getAbbreviation());
    MeasurementsAdapter pixels =
        new MeasurementsAdapter(1.0, 0, 0, false, 0, Unit.PIXEL.getAbbreviation());
    assertAll(
        () -> assertEquals(10.0, CircleGraphic.defaultRadius(halfMillimeterPixels, 5.0, 3.0)),
        () -> assertEquals(7.5, CircleGraphic.defaultRadius(pixels, 5.0, 2.0)),
        () -> assertEquals(15.0, CircleGraphic.defaultRadius(null, 5.0, 0.0)));
  }

  @Test
  void the_centre_handle_moves_the_circle_and_the_edge_handle_resizes_it() throws Exception {
    CircleGraphic g = new CircleGraphic().buildGraphic(new Point2D.Double(50, 40), 30);
    g.moveAndResizeOnDrawing(0, 10.0, -5.0, event(MouseEvent.MOUSE_DRAGGED, 60, 35));
    Point2D moved = g.getCenter();
    double radiusAfterMove = g.getRadius();
    g.moveAndResizeOnDrawing(1, 0.0, 0.0, event(MouseEvent.MOUSE_DRAGGED, 60, 85));
    assertAll(
        () -> assertEquals(new Point2D.Double(60, 35), moved),
        () -> assertEquals(30.0, radiusAfterMove, 1e-9),
        () -> assertEquals(50.0, g.getRadius(), 1e-9));
  }

  @Test
  void measurements_follow_the_calibration() throws Exception {
    CircleGraphic g = new CircleGraphic().buildGraphic(new Point2D.Double(50, 40), 10);
    MeasurableLayer layer = mock(MeasurableLayer.class);
    when(layer.hasContent()).thenReturn(true);
    when(layer.getMeasurementAdapter(Unit.MILLIMETER))
        .thenReturn(new MeasurementsAdapter(0.5, 0, 0, false, 0, "mm")); // NON-NLS
    List<MeasureItem> items = g.computeMeasurements(layer, false, Unit.MILLIMETER);
    assertAll(
        () -> assertEquals(5.0, value(items, "radius"), 1e-9), // NON-NLS
        () -> assertEquals(10.0, value(items, "diameter"), 1e-9), // NON-NLS
        () -> assertEquals(Math.PI * 25, value(items, "area"), 1e-9), // NON-NLS
        () -> assertEquals(Math.PI * 10, value(items, "perimeter"), 1e-9)); // NON-NLS
  }

  @Test
  void the_three_point_circle_reports_its_perimeter() throws Exception {
    ThreePointsCircleGraphic g = new ThreePointsCircleGraphic();
    g.buildGraphic(
        List.of(
            new Point2D.Double(60, 40), new Point2D.Double(50, 50), new Point2D.Double(40, 40)));
    MeasurableLayer layer = mock(MeasurableLayer.class);
    when(layer.hasContent()).thenReturn(true);
    when(layer.getMeasurementAdapter(Unit.MILLIMETER))
        .thenReturn(new MeasurementsAdapter(0.5, 0, 0, false, 0, "mm")); // NON-NLS
    List<MeasureItem> items = g.computeMeasurements(layer, false, Unit.MILLIMETER);
    assertAll(
        () -> assertEquals(5.0, value(items, "radius"), 1e-9), // NON-NLS
        () -> assertEquals(Math.PI * 10, value(items, "perimeter"), 1e-9)); // NON-NLS
  }

  @Test
  void a_circle_without_radius_is_not_valid() {
    CircleGraphic g = new CircleGraphic();
    g.setPts(List.of(new Point2D.Double(5, 5), new Point2D.Double(5, 5)));
    assertFalse(g.isShapeValid());
  }
}
