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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.Point3;
import org.weasis.core.ui.model.graphic.SpatialAnchor;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.line.FreehandGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.util.MouseEventDouble;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PathConversionTest {

  private static final List<Point2D> TRIANGLE =
      List.of(new Point2D.Double(0, 0), new Point2D.Double(40, 0), new Point2D.Double(20, 30));

  private static MouseEventDouble click(double x, double y) {
    MouseEventDouble evt =
        new MouseEventDouble(
            new JPanel(),
            MouseEvent.MOUSE_RELEASED,
            0,
            0,
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

  @Test
  void closing_and_opening_keep_identity_style_anchor_and_points() throws Exception {
    PolylineGraphic polyline = new PolylineGraphic();
    polyline.setLayerType(LayerType.DRAW);
    polyline.setPaint(Color.RED);
    polyline.setLineThickness(3f);
    polyline.setLabelVisible(false);
    polyline.buildGraphic(TRIANGLE);
    polyline.setAnchor(
        new SpatialAnchor("1.2", new Point3(0, 0, 1), List.of(new Point3(1, 2, 3)))); // NON-NLS

    PolygonGraphic polygon = PathConversion.close(polyline);
    PolylineGraphic reopened = PathConversion.open(polygon);
    assertAll(
        () -> assertEquals(polyline.getUuid(), polygon.getUuid()),
        () -> assertEquals(polyline.getUuid(), reopened.getUuid()),
        () -> assertEquals(LayerType.DRAW, polygon.getLayerType()),
        () -> assertEquals(Color.RED, polygon.getColorPaint()),
        () -> assertEquals(3f, polygon.getLineThickness()),
        () -> assertFalse(polygon.getLabelVisible()),
        () -> assertEquals(polyline.getAnchor(), polygon.getAnchor()),
        () -> assertEquals(TRIANGLE, polygon.getPts()),
        () -> assertEquals(TRIANGLE, reopened.getPts()),
        () -> assertTrue(polygon.isGraphicComplete()),
        () -> assertTrue(reopened.isGraphicComplete()));
  }

  @Test
  void a_freehand_trace_closes_into_a_polygon_and_opens_back() throws Exception {
    FreehandGraphic freehand = new FreehandGraphic();
    freehand.buildGraphic(TRIANGLE);
    PolygonGraphic region = PathConversion.close(freehand);
    PolylineGraphic trace = PathConversion.open(region);
    assertAll(
        () -> assertEquals(freehand.getUuid(), region.getUuid()),
        () -> assertEquals(TRIANGLE, region.getPts()),
        () -> assertEquals(TRIANGLE, trace.getPts()));
  }

  @Test
  void the_closing_preview_shows_while_the_cursor_is_on_the_first_point() {
    PolylineGraphic polyline = new PolylineGraphic();
    polyline
        .getPts()
        .addAll(
            List.of(
                new Point2D.Double(10, 10),
                new Point2D.Double(60, 10),
                new Point2D.Double(60, 60),
                new Point2D.Double(12, 11)));
    polyline.buildShape(click(12, 11));
    assertTrue(polyline.getShape() instanceof AdvancedShape);
    polyline.buildShape(click(40, 40));
    assertFalse(polyline.getShape() instanceof AdvancedShape);
  }

  @Test
  void a_freehand_released_away_from_its_start_stays_an_open_line() {
    FreehandGraphic freehand = new FreehandGraphic();
    freehand
        .getPts()
        .addAll(
            List.of(
                new Point2D.Double(0, 0),
                new Point2D.Double(10, 0.2),
                new Point2D.Double(20, 0),
                new Point2D.Double(30, 30)));
    assertFalse(freehand.finishTrace(click(30, 30)));
    assertAll(
        () -> assertTrue(freehand.isGraphicComplete()),
        () -> assertEquals(3, freehand.getPts().size()),
        () -> assertEquals(new Point2D.Double(20, 0), freehand.getPts().get(1)));
  }

  @Test
  void a_click_near_the_first_point_is_detected_at_the_handle_size() {
    PolylineGraphic polyline = new PolylineGraphic();
    polyline.getPts().addAll(List.of(new Point2D.Double(10, 10), new Point2D.Double(60, 10)));
    assertAll(
        () -> assertTrue(polyline.isOnFirstPoint(click(14, 12))),
        () -> assertFalse(polyline.isOnFirstPoint(click(30, 10))),
        () -> assertFalse(polyline.isOnFirstPoint(null)));
  }

  @Test
  void closing_needs_three_real_points_and_a_view() {
    PolylineGraphic polyline = new PolylineGraphic();
    polyline
        .getPts()
        .addAll(
            List.of(
                new Point2D.Double(10, 10),
                new Point2D.Double(60, 10),
                new Point2D.Double(11, 11)));
    assertFalse(polyline.closeOnFirstPoint(click(11, 11)));
    polyline.getPts().add(2, new Point2D.Double(40, 40));
    assertFalse(polyline.closeOnFirstPoint(click(11, 11)));
    assertEquals(4, polyline.getPts().size());
  }
}
