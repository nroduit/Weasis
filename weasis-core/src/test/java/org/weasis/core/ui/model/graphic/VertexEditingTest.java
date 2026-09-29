/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.editor.image.GraphicMouseHandler;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;

@DisplayNameGeneration(ReplaceUnderscores.class)
class VertexEditingTest {

  private static final List<Point2D> SQUARE =
      List.of(
          new Point2D.Double(0, 0),
          new Point2D.Double(40, 0),
          new Point2D.Double(40, 40),
          new Point2D.Double(0, 40));

  private static PolylineGraphic polyline(List<Point2D> pts) throws Exception {
    PolylineGraphic graphic = new PolylineGraphic();
    graphic.buildGraphic(copy(pts));
    return graphic;
  }

  private static PolygonGraphic polygon(List<Point2D> pts) throws Exception {
    PolygonGraphic graphic = new PolygonGraphic();
    graphic.buildGraphic(copy(pts));
    return graphic;
  }

  private static List<Point2D> copy(List<Point2D> pts) {
    return pts.stream().map(p -> (Point2D) new Point2D.Double(p.getX(), p.getY())).toList();
  }

  @Test
  void an_open_path_has_one_segment_less_than_its_vertices() throws Exception {
    PolylineGraphic line = polyline(SQUARE);
    assertAll(
        () -> assertEquals(3, line.getSegmentCount()),
        () -> assertFalse(line.isClosedPath()),
        () -> assertNull(line.getSegment(3)),
        () -> assertEquals(new Point2D.Double(20, 0), line.getSegmentMidPoint(0)));
  }

  @Test
  void a_closed_path_has_a_segment_joining_the_last_vertex_to_the_first() throws Exception {
    PolygonGraphic area = polygon(SQUARE);
    assertAll(
        () -> assertTrue(area.isClosedPath()),
        () -> assertEquals(4, area.getSegmentCount()),
        () -> assertEquals(new Point2D.Double(0, 20), area.getSegmentMidPoint(3)));
  }

  @Test
  void inserting_on_a_middle_segment_keeps_the_order_of_the_path() throws Exception {
    PolylineGraphic line = polyline(SQUARE);
    int index = line.insertHandlePoint(1, new Point2D.Double(40, 20));

    assertAll(
        () -> assertEquals(2, index),
        () -> assertEquals(5, line.getPts().size()),
        () -> assertEquals(new Point2D.Double(40, 20), line.getPts().get(2)),
        () -> assertEquals(new Point2D.Double(40, 40), line.getPts().get(3)),
        () -> assertEquals(5, line.getPtsNumber()));
  }

  @Test
  void inserting_on_the_first_segment_puts_the_vertex_in_second_position() throws Exception {
    PolylineGraphic line = polyline(SQUARE);
    assertEquals(1, line.insertHandlePoint(0, new Point2D.Double(20, 0)));
    assertEquals(new Point2D.Double(20, 0), line.getPts().get(1));
  }

  @Test
  void inserting_on_the_closing_segment_appends_after_the_last_vertex() throws Exception {
    PolygonGraphic area = polygon(SQUARE);
    int index = area.insertHandlePoint(3, new Point2D.Double(0, 20));

    assertAll(
        () -> assertEquals(4, index),
        () -> assertEquals(5, area.getPts().size()),
        () -> assertEquals(new Point2D.Double(0, 20), area.getPts().get(4)),
        () -> assertEquals(new Point2D.Double(0, 0), area.getPts().getFirst()));
  }

  @Test
  void a_segment_that_is_not_one_of_the_path_inserts_nothing() throws Exception {
    PolylineGraphic line = polyline(SQUARE);
    assertAll(
        () -> assertEquals(Graphic.UNDEFINED, line.insertHandlePoint(3, new Point2D.Double(1, 1))),
        () -> assertEquals(Graphic.UNDEFINED, line.insertHandlePoint(-1, new Point2D.Double(1, 1))),
        () -> assertEquals(Graphic.UNDEFINED, line.insertHandlePoint(0, null)),
        () -> assertEquals(4, line.getPts().size()));
  }

  @Test
  void a_polyline_keeps_two_vertices_and_a_polygon_three() throws Exception {
    PolylineGraphic line = polyline(SQUARE.subList(0, 3));
    PolygonGraphic area = polygon(SQUARE.subList(0, 3));

    assertAll(
        () -> assertEquals(2, line.getMinPoints()),
        () -> assertEquals(3, area.getMinPoints()),
        () -> assertTrue(line.canRemoveHandlePoint(1)),
        () -> assertFalse(area.canRemoveHandlePoint(1)));
  }

  @Test
  void removing_at_the_minimum_leaves_the_graphic_unchanged() throws Exception {
    PolygonGraphic area = polygon(SQUARE.subList(0, 3));
    assertAll(
        () -> assertNull(area.removeHandlePoint(1, null)),
        () -> assertEquals(3, area.getPts().size()));
  }

  @Test
  void removing_a_vertex_above_the_minimum_shortens_the_path() throws Exception {
    PolylineGraphic line = polyline(SQUARE);
    Point2D removed = line.removeHandlePoint(1, null);

    assertAll(
        () -> assertEquals(new Point2D.Double(40, 0), removed),
        () -> assertEquals(3, line.getPts().size()),
        () -> assertEquals(3, line.getPtsNumber()));
  }

  @Test
  void resuming_reopens_an_open_path_only() throws Exception {
    PolylineGraphic line = polyline(SQUARE);
    PolygonGraphic area = polygon(SQUARE);

    assertAll(
        () -> assertTrue(line.resumeDrawing()),
        () -> assertEquals(Graphic.UNDEFINED, line.getPtsNumber()),
        () -> assertFalse(area.resumeDrawing()),
        () -> assertEquals(4, area.getPtsNumber()));
  }

  @Test
  void only_the_two_ends_of_an_open_path_can_be_continued() throws Exception {
    PolylineGraphic line = polyline(SQUARE);
    PolygonGraphic area = polygon(SQUARE);

    assertAll(
        () -> assertTrue(GraphicMouseHandler.isOpenEnd(line, 0)),
        () -> assertTrue(GraphicMouseHandler.isOpenEnd(line, 3)),
        () -> assertFalse(GraphicMouseHandler.isOpenEnd(line, 1)),
        // A closed outline has no end to continue from
        () -> assertFalse(GraphicMouseHandler.isOpenEnd(area, 0)),
        () -> assertFalse(GraphicMouseHandler.isOpenEnd(area, 3)));
  }

  @Test
  void a_copy_of_a_finished_path_is_editable_like_the_original() throws Exception {
    // A finished path has as many points as it declares, so the copy cannot deduce the flag
    DragGraphic copy = (DragGraphic) polyline(SQUARE).copy();

    assertAll(
        () -> assertTrue(copy.getVariablePointsNumber()),
        () -> assertTrue(copy.canRemoveHandlePoint(1)),
        () -> assertEquals(1, copy.insertHandlePoint(0, new Point2D.Double(20, 0))),
        () -> assertTrue(copy.resumeDrawing()));
  }

  @Test
  void a_draft_is_removed_although_it_is_not_a_finished_graphic() throws Exception {
    PolylineGraphic drawn = polyline(SQUARE);
    // A path still being drawn: the point count is open, so the graphic is not complete
    drawn.setPointNumber(Graphic.UNDEFINED);
    List<String> fired = new ArrayList<>();
    drawn.addPropertyChangeListener(evt -> fired.add(evt.getPropertyName()));

    drawn.fireRemoveAction();
    assertTrue(fired.isEmpty(), "a buildShape mid-creation must not drop the draft");

    drawn.fireRemoveDraftAction();
    assertEquals(List.of(Graphic.ACTION_REMOVE), fired);
  }

  @Test
  void the_visible_vertices_thin_out_when_the_path_crowds_the_screen() {
    List<Point2D> dense =
        List.of(
            new Point2D.Double(0, 0),
            new Point2D.Double(1, 0),
            new Point2D.Double(2, 0),
            new Point2D.Double(20, 0),
            new Point2D.Double(100, 0));

    double spacing = MidpointHandles.DEFAULT_MIN_SPACING;
    List<Integer> zoomedOut = MidpointHandles.visibleVertices(dense, 1.0, spacing);
    List<Integer> zoomedIn = MidpointHandles.visibleVertices(dense, 20.0, spacing);

    assertAll(
        // The ends are always drawn, the crowded vertices between them are not
        () -> assertEquals(List.of(0, 3, 4), zoomedOut),
        () -> assertEquals(List.of(0, 1, 2, 3, 4), zoomedIn));
  }

  @Test
  void a_midpoint_handle_is_hidden_on_a_segment_too_short_on_screen() throws Exception {
    PolylineGraphic line =
        polyline(
            List.of(
                new Point2D.Double(0, 0), new Point2D.Double(4, 0), new Point2D.Double(200, 0)));

    assertAll(
        () -> assertEquals(List.of(1), MidpointHandles.visibleSegments(line, 1.0)),
        () -> assertEquals(List.of(0, 1), MidpointHandles.visibleSegments(line, 10.0)));
  }

  @Test
  void the_point_inserted_from_a_segment_lands_under_the_cursor() throws Exception {
    PolylineGraphic line = polyline(SQUARE);
    Point2D onSegment = MidpointHandles.pointOnSegment(line, 0, new Point2D.Double(10, 30));
    Point2D beyondSegment = MidpointHandles.pointOnSegment(line, 0, new Point2D.Double(-50, 0));

    assertAll(
        () -> assertEquals(new Point2D.Double(10, 0), onSegment),
        // Clamped to the segment, never beyond its ends
        () -> assertEquals(new Point2D.Double(0, 0), beyondSegment));
  }
}
