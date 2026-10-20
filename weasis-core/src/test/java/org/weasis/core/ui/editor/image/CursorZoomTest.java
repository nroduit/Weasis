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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.swing.BoundedRangeModel;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.setting.SettingEnvironment;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.ui.model.layer.imp.RenderedImageLayer;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;

/** Zoom around the pointer: the image point under it stays under it. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CursorZoomTest {

  private static final int VIEW_WIDTH = 401;
  private static final int VIEW_HEIGHT = 300;
  // The image origin is drawn at a whole pixel: the point may move by half a pixel at most
  private static final double ROUNDING = 0.5 + 1e-9;

  static Stream<Arguments> zooms() {
    List<Arguments> list = new ArrayList<>();
    for (double[] scales : new double[][] {{1.0, 2.0}, {3.3, 1.73}, {0.37, 7.9}}) {
      for (double[] anchor : new double[][] {{0, 0}, {120.0, -80.0}, {-190.5, 140.25}}) {
        for (int rotation : new int[] {0, 90, 33}) {
          for (boolean flip : new boolean[] {false, true}) {
            list.add(Arguments.of(scales[0], scales[1], anchor[0], anchor[1], rotation, flip));
          }
        }
      }
    }
    return list.stream();
  }

  @ParameterizedTest
  @MethodSource("zooms")
  void image_point_under_the_pointer_stays_under_it(
      double from, double to, double ax, double ay, int rotation, boolean flip) {
    TestPane pane = new TestPane(from, 13.37, -41.21, rotation, flip);
    Point2D anchor = new Point2D.Double(ax, ay);
    Point2D pointer = new Point2D.Double(VIEW_WIDTH * 0.5 + ax, VIEW_HEIGHT * 0.5 + ay);
    Point2D model = pane.viewToModel(pointer.getX(), pointer.getY());

    pane.zoomAt(to, anchor);
    pane.update();

    assertEquals(to, pane.getViewModel().getViewScale(), 1e-12);
    Point2D shown = pane.modelToView(model.getX(), model.getY());
    assertTrue(Math.abs(shown.getX() - pointer.getX()) <= ROUNDING, () -> "x " + shown);
    assertTrue(Math.abs(shown.getY() - pointer.getY()) <= ROUNDING, () -> "y " + shown);
  }

  @Test
  void zooming_in_and_out_around_a_point_does_not_drift() {
    TestPane pane = new TestPane(1.0, 13.37, -41.21, 33, true);
    Point2D anchor = new Point2D.Double(97.0, -53.0);
    Point2D pointer = new Point2D.Double(VIEW_WIDTH * 0.5 + 97.0, VIEW_HEIGHT * 0.5 - 53.0);
    Point2D model = pane.viewToModel(pointer.getX(), pointer.getY());
    double scale = 1.0;
    // A wheel or drag zoom: many small steps in, then back out
    for (int i = 0; i < 80; i++) {
      scale = i < 40 ? scale * 1.07 : scale / 1.07;
      pane.zoomAt(scale, anchor);
      pane.update();
      Point2D shown = pane.modelToView(model.getX(), model.getY());
      assertTrue(Math.abs(shown.getX() - pointer.getX()) <= ROUNDING, () -> "x " + shown);
      assertTrue(Math.abs(shown.getY() - pointer.getY()) <= ROUNDING, () -> "y " + shown);
    }

    assertEquals(1.0, pane.getViewModel().getViewScale(), 1e-9);
  }

  @Test
  void without_anchor_the_view_center_is_kept() {
    TestPane pane = new TestPane(1.0, 13.37, -41.21, 0, false);

    pane.zoomAt(4.0, null);

    assertEquals(13.37, pane.getViewModel().getModelOffsetX(), 1e-12);
    assertEquals(-41.21, pane.getViewModel().getModelOffsetY(), 1e-12);
  }

  @Test
  void at_the_zoom_limit_the_image_does_not_move() {
    TestPane pane = new TestPane(DefaultViewModel.SCALE_MAX, 13.37, -41.21, 0, false);

    pane.zoomAt(DefaultViewModel.SCALE_MAX * 2, new Point2D.Double(100, 100));

    assertEquals(DefaultViewModel.SCALE_MAX, pane.getViewModel().getViewScale(), 1e-12);
    assertEquals(13.37, pane.getViewModel().getModelOffsetX(), 1e-12);
    assertEquals(-41.21, pane.getViewModel().getModelOffsetY(), 1e-12);
  }

  @Test
  void pointer_is_taken_from_the_center_of_the_view() {
    JPanel view = new JPanel();
    view.setSize(400, 300);
    MouseWheelEvent wheel =
        new MouseWheelEvent(
            view,
            MouseEvent.MOUSE_WHEEL,
            0,
            0,
            300,
            100,
            0,
            false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL,
            1,
            1);

    assertEquals(new Point2D.Double(100, -50), ZoomChangeListener.getAnchor(wheel));
  }

  @Test
  void issuer_zooms_at_the_pointer_and_others_only_when_they_share_the_pan() {
    ViewCanvas<?> issuer = mock(ViewCanvas.class);
    ViewCanvas<?> other = mock(ViewCanvas.class);
    Point2D anchor = new Point2D.Double(10, 20);
    SynchEvent event = new SynchEvent(issuer, ActionW.ZOOM.cmd(), 2.0);
    event.put(ZoomChangeListener.ANCHOR_CMD, anchor);
    SynchData pan = synch(true);
    SynchData noPan = synch(false);

    assertSame(anchor, ZoomChangeListener.getAnchor(event, issuer, noPan, noPan));
    assertSame(anchor, ZoomChangeListener.getAnchor(event, other, pan, pan));
    assertNull(ZoomChangeListener.getAnchor(event, other, pan, noPan));
    assertNull(ZoomChangeListener.getAnchor(event, other, noPan, pan));
    assertNull(
        ZoomChangeListener.getAnchor(
            new SynchEvent(issuer, ActionW.ZOOM.cmd(), 2.0), issuer, pan, pan),
        "no anchor: around the center");
  }

  /** A zoom listener that keeps the events it fires; the wheel steps as in the 3D viewer. */
  private static final class RecordingZoom extends ZoomChangeListener {
    final List<SynchEvent> events = new ArrayList<>();

    RecordingZoom() {
      super(ActionW.ZOOM, 0.1, 10.0, 1.0, true, 0.3, 300);
    }

    @Override
    public void stateChanged(BoundedRangeModel model) {
      events.add(withAnchor(new SynchEvent(null, ActionW.ZOOM.cmd(), 1.0)));
    }

    @Override
    protected void wheel(MouseWheelEvent e) {
      setSliderValue(getSliderValue() - e.getWheelRotation());
    }
  }

  private static MouseWheelEvent wheelAt(JPanel view, int x, int y) {
    return new MouseWheelEvent(
        view,
        MouseEvent.MOUSE_WHEEL,
        0,
        0,
        x,
        y,
        0,
        false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL,
        1,
        1);
  }

  @Test
  void wheel_zoom_carries_the_pointer_unless_cursor_zoom_is_off() {
    JPanel view = new JPanel();
    view.setSize(400, 300);
    RecordingZoom zoom = new RecordingZoom();
    zoom.enableAction(true);
    try {
      Settings.install(SettingEnvironment.inMemory(Map.of("weasis.zoom.cursor", "true")));
      zoom.mouseWheelMoved(wheelAt(view, 300, 100));
      Settings.install(SettingEnvironment.inMemory(Map.of("weasis.zoom.cursor", "false")));
      zoom.mouseWheelMoved(wheelAt(view, 300, 100));
      // On by default
      Settings.install(SettingEnvironment.inMemory(Map.of()));
      zoom.mouseWheelMoved(wheelAt(view, 300, 100));
      // A change that does not come from the mouse
      Settings.install(SettingEnvironment.inMemory(Map.of("weasis.zoom.cursor", "true")));
      zoom.setSliderValue(zoom.getSliderValue() + 5);
    } finally {
      Settings.install(SettingEnvironment.inMemory(Map.of()));
    }

    assertEquals(4, zoom.events.size());
    Point2D pointer = new Point2D.Double(100, -50);
    assertEquals(pointer, zoom.events.get(0).getEvents().get(ZoomChangeListener.ANCHOR_CMD));
    assertNull(zoom.events.get(1).getEvents().get(ZoomChangeListener.ANCHOR_CMD), "setting off");
    assertEquals(
        pointer, zoom.events.get(2).getEvents().get(ZoomChangeListener.ANCHOR_CMD), "default");
    assertNull(zoom.events.get(3).getEvents().get(ZoomChangeListener.ANCHOR_CMD), "not the mouse");
  }

  private static SynchData synch(boolean pan) {
    return new SynchData(
        SynchData.Mode.STACK, Map.of(ActionW.PAN.cmd(), pan, ActionW.ZOOM.cmd(), true), true);
  }

  private static final class TestPane extends GraphicsPane {
    private final Image2DViewer<?> view;

    TestPane(double scale, double offsetX, double offsetY, int rotation, boolean flip) {
      super(new DefaultViewModel());
      setSize(VIEW_WIDTH, VIEW_HEIGHT);
      DefaultViewModel model = (DefaultViewModel) getViewModel();
      model.setModelArea(new Rectangle2D.Double(0, 0, 256, 192));
      model.setModelOffset(offsetX, offsetY, scale);

      view = mock(Image2DViewer.class);
      ImageLayer<?> imageLayer = mock(ImageLayer.class);
      when(view.getImageLayer()).thenReturn((ImageLayer) imageLayer);
      when(view.getActionValue(ActionW.FLIP.cmd())).thenReturn(flip);
      when(view.getActionValue(ActionW.ROTATION.cmd())).thenReturn(rotation);
      update();
    }

    void update() {
      updateAffineTransform(view, new AffineTransformOp(), mock(RenderedImageLayer.class), 0.0);
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
