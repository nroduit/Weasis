/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.line;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.IntensityProfile;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.opencv.data.PlanarImage;

/** Geometry and values of the tools added in 4.8, independent of the UI. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class NewToolsBehaviourTest {

  private static MeasurableLayer calibratedLayer(double ratio) {
    MeasurableLayer layer = mock(MeasurableLayer.class);
    when(layer.hasContent()).thenReturn(true);
    when(layer.getMeasurementAdapter(any()))
        .thenReturn(new MeasurementsAdapter(ratio, 0, 0, false, 100, "mm")); // NON-NLS
    when(layer.getPixelValueUnit()).thenReturn("HU"); // NON-NLS
    return layer;
  }

  private static MouseEventDouble drag(double x, double y) {
    return drag(x, y, 0);
  }

  private static MouseEventDouble dragShift(double x, double y) {
    return drag(x, y, InputEvent.SHIFT_DOWN_MASK);
  }

  private static MouseEventDouble drag(double x, double y, int modifiers) {
    MouseEventDouble evt =
        new MouseEventDouble(
            new JPanel(),
            MouseEvent.MOUSE_DRAGGED,
            0,
            modifiers,
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

  private static double value(List<MeasureItem> items, String key) {
    return items.stream()
        .filter(i -> key.equals(i.getKey()))
        .map(i -> ((Number) i.getValue()).doubleValue())
        .findFirst()
        .orElseThrow();
  }

  @Test
  void bidirectional_places_the_short_axis_centred_and_perpendicular() {
    BidirectionalGraphic g = new BidirectionalGraphic();
    g.setPts(List.of(new Point2D.Double(0, 0), new Point2D.Double(80, 60)));
    g.updateTool();
    g.placeShortAxis();
    List<Point2D> pts = g.getPts();
    Point2D c = pts.get(2);
    Point2D d = pts.get(3);
    double dot = (c.getX() - d.getX()) * 80 + (c.getY() - d.getY()) * 60;
    assertAll(
        () -> assertEquals(4, pts.size()),
        () -> assertTrue(g.isGraphicComplete()),
        () -> assertEquals(0.0, dot, 1e-9),
        () -> assertEquals(100.0 / BidirectionalGraphic.INITIAL_AXIS_RATIO, c.distance(d), 1e-9),
        () -> assertEquals(new Point2D.Double(40, 30), GeomUtil.getMidPoint(c, d)));
  }

  @Test
  void short_axis_follows_the_long_axis_while_it_is_drawn_from_the_palette_prototype() {
    BidirectionalGraphic drawn = new BidirectionalGraphic().copy();
    drawn.setResizeOrMoving(Boolean.TRUE);
    drawn.getPts().add(new Point2D.Double(0, 0));
    drawn.getPts().add(new Point2D.Double(0, 0));
    for (int x = 30; x <= 90; x += 30) {
      drawn.moveAndResizeOnDrawing(1, 30.0, 0.0, drag(x, 0));
    }
    List<Point2D> pts = drawn.getPts();
    assertAll(
        () -> assertEquals(4, pts.size()),
        () -> assertEquals(new Point2D.Double(45, 0), GeomUtil.getMidPoint(pts.get(2), pts.get(3))),
        () ->
            assertEquals(
                90.0 / BidirectionalGraphic.INITIAL_AXIS_RATIO,
                pts.get(2).distance(pts.get(3)),
                1e-9));
  }

  private static BidirectionalGraphic horizontal() {
    BidirectionalGraphic g = new BidirectionalGraphic();
    g.setPts(
        List.of(
            new Point2D.Double(0, 0),
            new Point2D.Double(100, 0),
            new Point2D.Double(50, 20),
            new Point2D.Double(50, -20)));
    g.updateTool();
    return g;
  }

  @Test
  void dragging_an_end_slides_the_crossing_point_and_resizes_that_half_only() {
    BidirectionalGraphic g = horizontal();
    g.moveAndResizeOnDrawing(2, 20.0, 15.0, drag(70, 35));
    assertAll(
        () -> assertEquals(new Point2D.Double(70, 35), g.getPts().get(2)),
        () -> assertEquals(new Point2D.Double(70, -20), g.getPts().get(3)));
  }

  @Test
  void the_crossing_point_stays_inside_the_long_axis() {
    BidirectionalGraphic g = horizontal();
    g.moveAndResizeOnDrawing(3, 100.0, -5.0, drag(150, -25));
    assertAll(
        () -> assertEquals(new Point2D.Double(100, 20), g.getPts().get(2)),
        () -> assertEquals(new Point2D.Double(100, -25), g.getPts().get(3)));
  }

  @Test
  void an_end_dragged_across_the_long_axis_stays_on_its_side() {
    BidirectionalGraphic g = horizontal();
    g.moveAndResizeOnDrawing(2, 0.0, -30.0, drag(50, -10));
    assertAll(
        () ->
            assertEquals(
                new Point2D.Double(50, BidirectionalGraphic.MIN_HALF_LENGTH), g.getPts().get(2)),
        () -> assertEquals(new Point2D.Double(50, -20), g.getPts().get(3)));
  }

  @Test
  void the_longer_axis_is_reported_as_the_long_one_after_resizing() throws Exception {
    BidirectionalGraphic g = new BidirectionalGraphic();
    g.buildGraphic(
        List.of(
            new Point2D.Double(0, 0),
            new Point2D.Double(40, 0),
            new Point2D.Double(20, 50),
            new Point2D.Double(20, -50)));
    List<MeasureItem> items = g.computeMeasurements(calibratedLayer(1), true, Unit.MILLIMETER);
    assertAll(
        () -> assertEquals(100.0, value(items, "length.long"), 1e-9),
        () -> assertEquals(40.0, value(items, "length.short"), 1e-9),
        () -> assertEquals(0.4, value(items, "ratio"), 1e-9));
  }

  @Test
  void bidirectional_reports_both_axes_and_their_ratio() throws Exception {
    BidirectionalGraphic g = new BidirectionalGraphic();
    g.buildGraphic(
        List.of(
            new Point2D.Double(0, 0),
            new Point2D.Double(100, 0),
            new Point2D.Double(50, 20),
            new Point2D.Double(50, -20)));
    List<MeasureItem> items = g.computeMeasurements(calibratedLayer(0.5), true, Unit.MILLIMETER);
    assertAll(
        () -> assertEquals(50.0, value(items, "length.long"), 1e-9),
        () -> assertEquals(20.0, value(items, "length.short"), 1e-9),
        () -> assertEquals(0.4, value(items, "ratio"), 1e-9));
  }

  @Test
  void parallel_lines_report_both_lengths_and_the_shorter_over_longer_ratio() throws Exception {
    ParallelLineGraphic g = new ParallelLineGraphic();
    g.buildGraphic(
        List.of(
            new Point2D.Double(40, 80),
            new Point2D.Double(140, 80),
            new Point2D.Double(10, 120),
            new Point2D.Double(210, 120),
            new Point2D.Double(90, 80),
            new Point2D.Double(110, 120)));
    List<MeasureItem> items = g.computeMeasurements(calibratedLayer(1), true, Unit.MILLIMETER);
    assertAll(
        () -> assertEquals(100.0, value(items, "length.first"), 1e-9),
        () -> assertEquals(200.0, value(items, "length.second"), 1e-9),
        () -> assertEquals(0.5, value(items, "ratio"), 1e-9),
        () -> assertEquals(40.0, value(items, "distance"), 1e-9),
        () -> assertTrue(ParallelLineGraphic.RATIO.getGraphicLabel()));
  }

  @Test
  void a_line_reports_its_horizontal_and_vertical_extent() throws Exception {
    LineGraphic g = new LineGraphic();
    g.buildGraphic(List.of(new Point2D.Double(10, 50), new Point2D.Double(40, 10)));
    List<MeasureItem> items = g.computeMeasurements(calibratedLayer(0.5), true, Unit.MILLIMETER);
    assertAll(
        () -> assertEquals(25.0, value(items, "length"), 1e-9),
        () -> assertEquals(15.0, value(items, "length.dx"), 1e-9),
        () -> assertEquals(20.0, value(items, "length.dy"), 1e-9),
        () -> assertFalse(LineGraphic.DELTA_X.getGraphicLabel()));
  }

  @Test
  void shift_snaps_a_dragged_line_end_to_the_closest_axis() {
    LineGraphic line = new LineGraphic();
    line.getPts().add(new Point2D.Double(10, 10));
    line.getPts().add(new Point2D.Double(10, 10));
    line.moveAndResizeOnDrawing(1, 100.0, 15.0, dragShift(110, 25));
    ParallelLineGraphic parallel = new ParallelLineGraphic();
    parallel.getPts().add(new Point2D.Double(10, 10));
    parallel.getPts().add(new Point2D.Double(10, 10));
    parallel.moveAndResizeOnDrawing(1, 5.0, 80.0, dragShift(15, 90));
    assertAll(
        () -> assertEquals(new Point2D.Double(110, 10), line.getPts().get(1)),
        () -> assertEquals(new Point2D.Double(10, 90), parallel.getPts().get(1)));
  }

  @Test
  void trace_simplification_keeps_corners_and_drops_collinear_points() {
    List<Point2D> trace =
        List.of(
            new Point2D.Double(0, 0),
            new Point2D.Double(1, 0.2),
            new Point2D.Double(2, 0),
            new Point2D.Double(3, 0.1),
            new Point2D.Double(10, 0),
            new Point2D.Double(10, 10),
            new Point2D.Double(0, 10));
    List<Point2D> simplified = GeomUtil.simplify(trace, 1.0);
    assertEquals(
        List.of(
            new Point2D.Double(0, 0),
            new Point2D.Double(10, 0),
            new Point2D.Double(10, 10),
            new Point2D.Double(0, 10)),
        simplified);
  }

  @Test
  void ruler_ticks_every_centimetre_on_calibrated_images_else_fifty_pixels() {
    ImageElement calibrated = mock(ImageElement.class);
    when(calibrated.getPixelSpacingUnit()).thenReturn(Unit.MILLIMETER);
    when(calibrated.getPixelSize()).thenReturn(0.5);
    ImageElement coarse = mock(ImageElement.class);
    when(coarse.getPixelSpacingUnit()).thenReturn(Unit.MILLIMETER);
    when(coarse.getPixelSize()).thenReturn(5.0);
    ImageElement uncalibrated = mock(ImageElement.class);
    when(uncalibrated.getPixelSpacingUnit()).thenReturn(Unit.PIXEL);
    assertAll(
        () -> assertEquals(20.0, RulerGraphic.tickSpacingOf(calibrated), 1e-9),
        () -> assertEquals(20.0, RulerGraphic.tickSpacingOf(coarse), 1e-9),
        () -> assertEquals(50.0, RulerGraphic.tickSpacingOf(uncalibrated), 1e-9),
        () -> assertEquals(50.0, RulerGraphic.tickSpacingOf(null), 1e-9));
  }

  @Test
  void intensity_profile_samples_one_value_per_pixel_through_every_vertex() throws Exception {
    PlanarImage image = mock(PlanarImage.class);
    when(image.width()).thenReturn(10);
    when(image.height()).thenReturn(10);
    when(image.get(anyInt(), anyInt()))
        .thenAnswer(
            inv ->
                new double[] {
                  inv.getArgument(0, Integer.class) * 10 + inv.getArgument(1, Integer.class)
                });
    MeasurableLayer layer = calibratedLayer(1);
    when(layer.getSourceRenderedImage()).thenReturn(image);
    when(layer.pixelToRealValue(any()))
        .thenAnswer(inv -> ((Number) inv.getArgument(0)).doubleValue() * 2);

    PolylineGraphic polyline = new PolylineGraphic();
    polyline.setLayerType(LayerType.MEASURE);
    polyline.buildGraphic(
        List.of(new Point2D.Double(0, 3), new Point2D.Double(4, 3), new Point2D.Double(4, 5)));
    assertTrue(IntensityProfile.isOpenPath(polyline));
    assertArrayEquals(
        new double[] {60, 62, 64, 66, 68, 88, 108},
        IntensityProfile.sample(polyline.getPts(), layer),
        1e-9);
    assertFalse(IntensityProfile.isOpenPath(new BidirectionalGraphic()));

    // Each sample knows the centre of its pixel, and how far along the path it is
    IntensityProfile.Samples samples = IntensityProfile.samples(polyline.getPts(), layer);
    assertAll(
        () -> assertEquals(7, samples.size()),
        () -> assertEquals(new Point2D.Double(0.5, 3.5), samples.positions().getFirst()),
        () -> assertEquals(new Point2D.Double(4.5, 3.5), samples.positions().get(4)),
        () -> assertEquals(new Point2D.Double(4.5, 5.5), samples.positions().getLast()),
        () -> assertEquals(0.0, samples.distanceTo(0), 1e-9),
        () -> assertEquals(4.0, samples.distanceTo(4), 1e-9),
        () -> assertEquals(6.0, samples.distanceTo(6), 1e-9));
  }
}
