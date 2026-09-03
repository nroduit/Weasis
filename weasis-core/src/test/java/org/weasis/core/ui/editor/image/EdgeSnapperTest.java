/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.opencv.data.PlanarImage;

@DisplayNameGeneration(ReplaceUnderscores.class)
class EdgeSnapperTest {

  private static final int SIZE = 80;
  private static final double CX = 40;
  private static final double CY = 40;
  private static final double RADIUS = 25;

  /** A single-channel float image whose value is a function of (x, y). */
  private static PlanarImage image(int size, DoubleBinaryOperator value) {
    PlanarImage image = mock(PlanarImage.class);
    when(image.width()).thenReturn(size);
    when(image.height()).thenReturn(size);
    when(image.channels()).thenReturn(1);
    when(image.type()).thenReturn(CvType.CV_32FC1);
    when(image.get(anyInt(), anyInt(), any(float[].class)))
        .thenAnswer(
            inv -> {
              int row = inv.getArgument(0);
              int col = inv.getArgument(1);
              float[] data = inv.getArgument(2);
              for (int i = 0; i < data.length; i++) {
                data[i] = (float) value.applyAsDouble(col + i, row);
              }
              return data.length;
            });
    return image;
  }

  private static boolean inDisc(double x, double y) {
    return Math.hypot(x - CX, y - CY) <= RADIUS;
  }

  /** A bright disc on a dark background. */
  private static PlanarImage disc() {
    return image(SIZE, (x, y) -> inDisc(x, y) ? 1000 : 0);
  }

  private static MeasurableLayer layer(PlanarImage image) {
    MeasurableLayer layer = mock(MeasurableLayer.class);
    when(layer.hasContent()).thenReturn(true);
    when(layer.getSourceRenderedImage()).thenReturn(image);
    when(layer.getPixelMin()).thenReturn(0.0);
    when(layer.getPixelMax()).thenReturn(1000.0);
    when(layer.pixelToRealValue(any()))
        .thenAnswer(inv -> ((Number) inv.getArgument(0)).doubleValue());
    return layer;
  }

  private static double farthestFromDiscEdge(List<Point2D> path) {
    return path.stream()
        .mapToDouble(p -> Math.abs(Math.hypot(p.getX() - CX, p.getY() - CY) - RADIUS))
        .max()
        .orElse(Double.MAX_VALUE);
  }

  @Test
  void the_path_between_two_points_of_a_disc_edge_follows_the_edge() {
    EdgeSnapper snapper = new EdgeSnapper(layer(disc()));
    assertTrue(snapper.isReady());
    Point2D cursor = new Point2D.Double(CX, CY + RADIUS);
    snapper.anchorAt(new Point2D.Double(CX + RADIUS, CY));
    List<Point2D> path = snapper.pathTo(cursor);
    double worst = farthestFromDiscEdge(path);
    assertAll(
        () -> assertTrue(path.size() > 20, "path has " + path.size() + " points"),
        () -> assertEquals(new Point2D.Double(CX + RADIUS, CY), path.getFirst()),
        () -> assertTrue(path.getLast().distance(cursor) <= 2 * EdgeSnapper.SNAP_RADIUS),
        () -> assertTrue(worst <= 2.0, "farthest point from the edge: " + worst));
  }

  @Test
  void a_faint_edge_is_followed_when_the_displayed_range_shows_it() {
    // Soft-tissue disc (100 in 90) next to a much brighter bar that dominates the full range
    PlanarImage image = image(SIZE, (x, y) -> x >= 72 ? 5000 : inDisc(x, y) ? 100 : 90);
    EdgeSnapper snapper = new EdgeSnapper(layer(image), 80, 110);
    snapper.anchorAt(new Point2D.Double(CX + RADIUS, CY));
    List<Point2D> path = snapper.pathTo(new Point2D.Double(CX - RADIUS, CY));
    double worst = farthestFromDiscEdge(path);
    assertAll(
        () -> assertTrue(path.size() > 40, "path has " + path.size() + " points"),
        () -> assertTrue(worst <= 2.0, "farthest point from the edge: " + worst));
  }

  @Test
  void a_new_target_resumes_the_search_from_the_same_anchor() {
    EdgeSnapper snapper = new EdgeSnapper(layer(disc()));
    snapper.anchorAt(new Point2D.Double(CX + RADIUS, CY));
    List<Point2D> near = snapper.pathTo(new Point2D.Double(CX + 18, CY + 17));
    List<Point2D> far = snapper.pathTo(new Point2D.Double(CX, CY + RADIUS));
    assertAll(
        () -> assertTrue(far.size() > near.size()),
        () -> assertTrue(farthestFromDiscEdge(far) <= 2.0));
  }

  @Test
  void values_are_normalized_and_clamped_to_the_displayed_range() {
    MeasurableLayer layer = layer(disc());
    assertArrayEquals(
        new float[] {0f, 0f, 0.5f, 1f, 1f},
        EdgeSnapper.normalize(new float[] {-50, 100, 150, 200, 900}, layer, 100, 200));
  }

  @Test
  void the_cursor_is_pulled_onto_the_nearest_edge() {
    int w = 20;
    float[] gray = new float[w * w];
    for (int i = 0; i < gray.length; i++) {
      gray[i] = i % w >= 10 ? 1 : 0;
    }
    EdgeSnapper.Features features = EdgeSnapper.Features.of(gray, w, w);
    assertAll(
        () -> assertEquals(5 * w + 9, features.snap(7, 5, EdgeSnapper.SNAP_RADIUS)),
        () -> assertEquals(5 * w + 9, features.snap(11, 5, EdgeSnapper.SNAP_RADIUS)),
        () -> assertEquals(5 * w + 3, features.snap(3, 5, EdgeSnapper.SNAP_RADIUS)));
  }

  @Test
  void a_cursor_outside_the_window_gets_a_straight_segment() {
    PlanarImage image = disc();
    when(image.width()).thenReturn(1000);
    when(image.height()).thenReturn(1000);
    EdgeSnapper snapper = new EdgeSnapper(layer(image));
    snapper.anchorAt(new Point2D.Double(10, 10));
    List<Point2D> path = snapper.pathTo(new Point2D.Double(900, 900));
    assertEquals(List.of(new Point2D.Double(10, 10), new Point2D.Double(900, 900)), path);
  }

  @Test
  void the_window_is_clamped_to_the_image() {
    assertAll(
        () ->
            assertEquals(
                new Rectangle(0, 0, 80, 80),
                EdgeSnapper.windowAround(new java.awt.Point(5, 5), 80, 80)),
        () ->
            assertEquals(
                new Rectangle(488, 0, 512, 512),
                EdgeSnapper.windowAround(new java.awt.Point(990, 10), 1000, 1000)));
  }
}
