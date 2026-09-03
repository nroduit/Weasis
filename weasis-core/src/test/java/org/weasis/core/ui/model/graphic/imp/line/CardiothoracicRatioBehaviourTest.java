/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.line;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.QuantityKind;
import org.weasis.core.ui.util.MouseEventDouble;

/** The cardiothoracic ratio measures the borders across the midline, wherever they were picked. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CardiothoracicRatioBehaviourTest {

  private static MeasurableLayer layer(double pixelSize, String unit, Object patientOrientation) {
    MeasurableLayer layer = mock(MeasurableLayer.class);
    when(layer.hasContent()).thenReturn(true);
    when(layer.getMeasurementAdapter(any()))
        .thenReturn(new MeasurementsAdapter(pixelSize, 0, 0, false, 0, unit));
    when(layer.getSourceTagValue(any())).thenReturn(patientOrientation);
    return layer;
  }

  private static CardiothoracicRatioGraphic ctr(double... xy) throws Exception {
    CardiothoracicRatioGraphic g = new CardiothoracicRatioGraphic();
    List<Point2D> points = new java.util.ArrayList<>();
    for (int i = 0; i < xy.length; i += 2) {
      points.add(new Point2D.Double(xy[i], xy[i + 1]));
    }
    g.buildGraphic(points);
    return g;
  }

  private static Optional<Double> value(List<MeasureItem> items, String key) {
    return items.stream()
        .filter(i -> i.getKey().equals(key))
        .map(i -> ((Number) i.getValue()).doubleValue())
        .findFirst();
  }

  private static List<MeasureItem> measure(CardiothoracicRatioGraphic g) {
    return g.computeMeasurements(layer(1, "pix", null), true, Unit.PIXEL); // NON-NLS
  }

  private static MouseEventDouble event(int id, double x, double y) {
    MouseEventDouble evt =
        new MouseEventDouble(new JPanel(), id, 0, 0, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON1);
    evt.setImageCoordinates(x, y);
    return evt;
  }

  @Test
  void oblique_strokes_measure_the_same_widths_as_horizontal_ones() throws Exception {
    List<MeasureItem> oblique =
        measure(ctr(80, 100, 180, 130, 20, 160, 220, 150, 120, 70, 120, 190));
    List<MeasureItem> level = measure(ctr(80, 115, 180, 115, 20, 155, 220, 155, 120, 70, 120, 190));
    assertAll(
        () -> assertEquals(100.0, value(oblique, "ctr.cardiac").orElseThrow(), 1e-9),
        () -> assertEquals(200.0, value(oblique, "ctr.thoracic").orElseThrow(), 1e-9),
        () -> assertEquals(0.5, value(oblique, "ctr.ratio").orElseThrow(), 1e-9),
        () -> assertEquals(value(level, "ctr.ratio"), value(oblique, "ctr.ratio")));
  }

  @Test
  void the_order_of_the_points_of_a_stroke_does_not_matter() throws Exception {
    List<MeasureItem> swapped =
        measure(ctr(180, 130, 80, 100, 220, 150, 20, 160, 120, 190, 120, 70));
    assertAll(
        () -> assertEquals(0.5, value(swapped, "ctr.ratio").orElseThrow(), 1e-9),
        () -> assertEquals(40.0, value(swapped, "ctr.mrd").orElseThrow(), 1e-9),
        () -> assertEquals(60.0, value(swapped, "ctr.mld").orElseThrow(), 1e-9));
  }

  @Test
  void the_ratio_is_cardiac_over_thoracic_even_above_one() throws Exception {
    List<MeasureItem> items = measure(ctr(0, 100, 300, 100, 50, 160, 250, 160, 150, 70, 150, 190));
    assertEquals(1.5, value(items, "ctr.ratio").orElseThrow(), 1e-9);
  }

  @Test
  void a_tilted_midline_changes_the_axis_the_widths_are_measured_on() throws Exception {
    // Midline at 45 degrees: a horizontal stroke of 100 projects to 100 / sqrt(2)
    List<MeasureItem> items = measure(ctr(0, 0, 100, 0, -100, 50, 200, 50, 0, -50, 100, 50));
    assertAll(
        () -> assertEquals(100 / Math.sqrt(2), value(items, "ctr.cardiac").orElseThrow(), 1e-9),
        () -> assertEquals(300 / Math.sqrt(2), value(items, "ctr.thoracic").orElseThrow(), 1e-9),
        () -> assertEquals(1.0 / 3, value(items, "ctr.ratio").orElseThrow(), 1e-9));
  }

  @Test
  void the_two_midline_distances_add_up_to_the_cardiac_width_and_need_both_sides()
      throws Exception {
    List<MeasureItem> straddling =
        measure(ctr(80, 100, 180, 130, 20, 160, 220, 150, 120, 70, 120, 190));
    List<MeasureItem> sameSide =
        measure(ctr(130, 100, 180, 130, 20, 160, 220, 150, 120, 70, 120, 190));
    assertAll(
        () ->
            assertEquals(
                value(straddling, "ctr.cardiac").orElseThrow(),
                value(straddling, "ctr.mrd").orElseThrow()
                    + value(straddling, "ctr.mld").orElseThrow(),
                1e-9),
        () -> assertTrue(value(sameSide, "ctr.mrd").isEmpty()),
        () -> assertTrue(value(sameSide, "ctr.mld").isEmpty()),
        () -> assertEquals(50.0, value(sameSide, "ctr.cardiac").orElseThrow(), 1e-9));
  }

  @Test
  void the_patient_right_is_on_the_image_left_unless_the_rows_run_to_the_right() {
    assertAll(
        () -> assertTrue(CardiothoracicRatioGraphic.isPatientRightOnImageLeft((Object) null)),
        () ->
            assertTrue(
                CardiothoracicRatioGraphic.isPatientRightOnImageLeft(new String[] {"L", "F"})),
        () -> assertTrue(CardiothoracicRatioGraphic.isPatientRightOnImageLeft("LF")), // NON-NLS
        () ->
            assertFalse(
                CardiothoracicRatioGraphic.isPatientRightOnImageLeft(new String[] {"R", "F"})),
        () -> assertFalse(CardiothoracicRatioGraphic.isPatientRightOnImageLeft(" r ")));
  }

  @Test
  void widths_follow_the_calibration_and_the_ratio_does_not() throws Exception {
    CardiothoracicRatioGraphic g = ctr(80, 100, 180, 130, 20, 160, 220, 150, 120, 70, 120, 190);
    List<MeasureItem> items =
        g.computeMeasurements(layer(0.2, "mm", null), true, Unit.MILLIMETER); // NON-NLS
    assertAll(
        () -> assertEquals(20.0, value(items, "ctr.cardiac").orElseThrow(), 1e-9),
        () -> assertEquals(40.0, value(items, "ctr.thoracic").orElseThrow(), 1e-9),
        () -> assertEquals(0.5, value(items, "ctr.ratio").orElseThrow(), 1e-9),
        () -> assertEquals(QuantityKind.RATIO, CardiothoracicRatioGraphic.RATIO.getKind()),
        () ->
            assertEquals(QuantityKind.LENGTH, CardiothoracicRatioGraphic.LEFT_DIAMETER.getKind()));
  }

  @Test
  void there_is_no_value_without_a_thoracic_width() {
    CardiothoracicRatioGraphic g = new CardiothoracicRatioGraphic();
    g.setPts(
        List.of(
            new Point2D.Double(80, 100),
            new Point2D.Double(180, 130),
            new Point2D.Double(120, 150),
            new Point2D.Double(120, 160),
            new Point2D.Double(120, 70),
            new Point2D.Double(120, 190)));
    assertAll(() -> assertFalse(g.isShapeValid()), () -> assertTrue(measure(g).isEmpty()));
  }

  @Test
  void two_strokes_complete_the_tool_with_an_upright_midline_through_the_thorax() {
    CardiothoracicRatioGraphic g = new CardiothoracicRatioGraphic();
    Draggable sequence = g.createResizeDrag();
    sequence.startDrag(event(MouseEvent.MOUSE_PRESSED, 80, 100));
    sequence.drag(event(MouseEvent.MOUSE_DRAGGED, 180, 130));
    boolean afterFirstStroke = sequence.completeDrag(event(MouseEvent.MOUSE_RELEASED, 180, 130));
    sequence.drag(event(MouseEvent.MOUSE_MOVED, 20, 160));
    boolean onSecondPress = sequence.completeDrag(event(MouseEvent.MOUSE_PRESSED, 20, 160));
    sequence.drag(event(MouseEvent.MOUSE_DRAGGED, 220, 150));
    boolean done = sequence.completeDrag(event(MouseEvent.MOUSE_RELEASED, 220, 150));
    assertAll(
        () -> assertFalse(afterFirstStroke),
        () -> assertFalse(onSecondPress),
        () -> assertTrue(done),
        () -> assertTrue(g.isGraphicComplete()),
        () -> assertEquals(120.0, g.getPts().get(4).getX(), 1e-9),
        () -> assertEquals(120.0, g.getPts().get(5).getX(), 1e-9),
        () -> assertTrue(g.getPts().get(4).getY() < 100),
        () -> assertTrue(g.getPts().get(5).getY() > 160),
        () -> assertEquals(0.5, value(measure(g), "ctr.ratio").orElseThrow(), 1e-9));
  }

  @Test
  void a_midline_moved_by_the_user_stays_where_it_is_when_a_border_is_edited() throws Exception {
    CardiothoracicRatioGraphic g = new CardiothoracicRatioGraphic();
    Draggable sequence = g.createResizeDrag();
    sequence.startDrag(event(MouseEvent.MOUSE_PRESSED, 80, 100));
    sequence.drag(event(MouseEvent.MOUSE_DRAGGED, 180, 130));
    sequence.completeDrag(event(MouseEvent.MOUSE_RELEASED, 180, 130));
    sequence.drag(event(MouseEvent.MOUSE_MOVED, 20, 160));
    sequence.completeDrag(event(MouseEvent.MOUSE_PRESSED, 20, 160));
    sequence.drag(event(MouseEvent.MOUSE_DRAGGED, 220, 150));
    sequence.completeDrag(event(MouseEvent.MOUSE_RELEASED, 220, 150));

    g.moveAndResizeOnDrawing(3, 0.0, 0.0, event(MouseEvent.MOUSE_DRAGGED, 260, 150));
    double followed = g.getPts().get(4).getX();
    g.moveAndResizeOnDrawing(4, 0.0, 0.0, event(MouseEvent.MOUSE_DRAGGED, 100, 60));
    g.moveAndResizeOnDrawing(3, 0.0, 0.0, event(MouseEvent.MOUSE_DRAGGED, 300, 150));
    assertAll(
        () -> assertEquals(140.0, followed, 1e-9),
        () -> assertEquals(new Point2D.Double(100, 60), g.getPts().get(4)));
  }
}
