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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.ui.model.layer.imp.RenderedImageLayer;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;

/**
 * The image raster, the graphics and the mouse share one mapping between the model and the view:
 * the raster is drawn by Java2D at a whole pixel, so the view origin of the image must be one.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphicsPaneGeometryTest {

  private static final int VIEW_WIDTH = 401;
  private static final int VIEW_HEIGHT = 300;
  private static final double EPS = 1e-9;

  static Stream<Arguments> views() {
    List<Arguments> list = new ArrayList<>();
    for (double scale : new double[] {0.37, 1.0, 1.73, 3.3, 7.9}) {
      for (double[] offset : new double[][] {{0, 0}, {13.37, -41.21}, {-97.6, 70.45}}) {
        for (int rotation : new int[] {0, 90, 33}) {
          for (boolean flip : new boolean[] {false, true}) {
            list.add(Arguments.of(scale, offset[0], offset[1], rotation, flip));
          }
        }
      }
    }
    return list.stream();
  }

  @ParameterizedTest
  @MethodSource("views")
  void image_origin_is_a_whole_pixel(
      double scale, double offsetX, double offsetY, int rotation, boolean flip) {
    TestPane pane = new TestPane(scale, offsetX, offsetY, rotation, flip);

    Point2D clip = pane.getClipViewCoordinatesOffset();
    Rectangle2D bounds = pane.getImageViewBounds();
    assertAll(
        () -> assertEquals(Math.rint(clip.getX()), clip.getX()),
        () -> assertEquals(Math.rint(clip.getY()), clip.getY()),
        () -> assertEquals(Math.rint(bounds.getX()), bounds.getX()),
        () -> assertEquals(Math.rint(bounds.getY()), bounds.getY()));
  }

  @ParameterizedTest
  @MethodSource("views")
  void raster_matrix_places_the_image_where_the_graphics_are(
      double scale, double offsetX, double offsetY, int rotation, boolean flip) {
    TestPane pane = new TestPane(scale, offsetX, offsetY, rotation, flip);

    // OpenCV order: a, b, tx, c, d, ty. The raster is drawn at the clip offset, like the graphics.
    List<Double> m = pane.node.getParam(AffineTransformOp.P_AFFINE_MATRIX, List.class);
    for (double[] p : new double[][] {{0, 0}, {255.5, 0}, {17.25, 191.75}, {128, 96}}) {
      Point2D expected = pane.getAffineTransform().transform(new Point2D.Double(p[0], p[1]), null);
      double x = m.get(0) * p[0] + m.get(1) * p[1] + m.get(2);
      double y = m.get(3) * p[0] + m.get(4) * p[1] + m.get(5);
      assertEquals(expected.getX(), x, EPS);
      assertEquals(expected.getY(), y, EPS);
    }
  }

  @ParameterizedTest
  @MethodSource("views")
  void view_and_mouse_mappings_agree(
      double scale, double offsetX, double offsetY, int rotation, boolean flip) {
    TestPane pane = new TestPane(scale, offsetX, offsetY, rotation, flip);

    for (int[] v : new int[][] {{0, 0}, {200, 150}, {400, 299}, {37, 251}}) {
      Point2D fromView = pane.viewToModel((double) v[0], (double) v[1]);
      Point2D fromMouse = pane.getImageCoordinatesFromMouse(v[0], v[1]);
      Point2D back = pane.modelToView(fromView.getX(), fromView.getY());
      Point mouse = pane.getMouseCoordinatesFromImage(fromMouse.getX(), fromMouse.getY());
      assertAll(
          () -> assertEquals(fromMouse.getX(), fromView.getX(), EPS),
          () -> assertEquals(fromMouse.getY(), fromView.getY(), EPS),
          () -> assertEquals(v[0], back.getX(), EPS),
          () -> assertEquals(v[1], back.getY(), EPS),
          () -> assertEquals(new Point(v[0], v[1]), mouse));
    }
  }

  @ParameterizedTest
  @MethodSource("views")
  void rounding_moves_the_view_centre_by_half_a_pixel_at_most(
      double scale, double offsetX, double offsetY, int rotation, boolean flip) {
    TestPane pane = new TestPane(scale, offsetX, offsetY, 0, false);

    Rectangle2D area = pane.getViewModel().getModelArea();
    Point2D centre = pane.modelToView(area.getCenterX() + offsetX, area.getCenterY() + offsetY);
    assertTrue(Math.abs(centre.getX() - VIEW_WIDTH * 0.5) <= 0.5 + EPS, () -> "x " + centre);
    assertTrue(Math.abs(centre.getY() - VIEW_HEIGHT * 0.5) <= 0.5 + EPS, () -> "y " + centre);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final class TestPane extends GraphicsPane {
    final AffineTransformOp node = new AffineTransformOp();

    TestPane(double scale, double offsetX, double offsetY, int rotation, boolean flip) {
      super(new DefaultViewModel());
      setSize(VIEW_WIDTH, VIEW_HEIGHT);
      DefaultViewModel model = (DefaultViewModel) getViewModel();
      model.setModelArea(new Rectangle2D.Double(0, 0, 256, 192));
      model.setModelOffset(offsetX, offsetY, scale);

      Image2DViewer view = mock(Image2DViewer.class);
      ImageLayer imageLayer = mock(ImageLayer.class);
      when(view.getImageLayer()).thenReturn(imageLayer);
      when(view.getActionValue(ActionW.FLIP.cmd())).thenReturn(flip);
      when(view.getActionValue(ActionW.ROTATION.cmd())).thenReturn(rotation);
      updateAffineTransform(view, node, mock(RenderedImageLayer.class), 0.0);
    }

    @Override
    public double getRealWorldViewScale() {
      return 1.0;
    }

    @Override
    public double adjustViewScale(double viewScale) {
      return viewScale;
    }
  }
}
