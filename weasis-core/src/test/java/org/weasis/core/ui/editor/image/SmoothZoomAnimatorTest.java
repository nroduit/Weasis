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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** The eased zoom: target, accumulation, anchor, end and stop. Runs on the event thread. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SmoothZoomAnimatorTest {

  /** A view whose scale is the last one applied, limited to [0.01, 4]. */
  private static final class FakeView implements SmoothZoomAnimator.Target {
    double scale = 1.0;
    double snapTo;
    final List<Point2D> anchors = new ArrayList<>();

    @Override
    public double snapScale(double from, double to) {
      return snapTo > 0 ? snapTo : to;
    }

    @Override
    public double getScale() {
      return scale;
    }

    @Override
    public double clampScale(double s) {
      return Math.clamp(s, 0.01, 4.0);
    }

    @Override
    public void zoomTo(double s, Point2D anchor) {
      scale = s;
      anchors.add(anchor);
    }
  }

  private double now;

  private SmoothZoomAnimator animator(FakeView view) {
    return new SmoothZoomAnimator(view, _ -> now);
  }

  private static void onEdt(Runnable test) throws Exception {
    SwingUtilities.invokeAndWait(test);
  }

  @Test
  void the_easing_goes_from_zero_to_one_and_slows_down() {
    double half = SmoothZoomAnimator.DURATION_SECONDS / 2;
    assertAll(
        () -> assertEquals(0.0, SmoothZoomAnimator.fraction(0), 1e-12),
        () ->
            assertEquals(
                1.0, SmoothZoomAnimator.fraction(SmoothZoomAnimator.DURATION_SECONDS), 1e-12),
        () -> assertTrue(SmoothZoomAnimator.fraction(half) > 0.9, "front-loaded"),
        () -> assertEquals(1.0, SmoothZoomAnimator.fraction(10), 1e-12));
  }

  @Test
  void notches_add_up_to_the_target_and_the_anchor_follows_every_frame() throws Exception {
    onEdt(
        () -> {
          FakeView view = new FakeView();
          SmoothZoomAnimator a = animator(view);
          Point2D anchor = new Point2D.Double(30, -20);
          a.zoomBy(2.0, anchor);
          now += 0.05;
          a.tick();
          double midway = view.scale;
          a.zoomBy(2.0, anchor); // a second notch during the animation
          assertEquals(4.0, a.targetScale().orElseThrow(), 1e-9);
          now += SmoothZoomAnimator.DURATION_SECONDS;
          a.tick();
          assertAll(
              () -> assertTrue(midway > 1.0 && midway < 2.0, "eased: " + midway),
              () -> assertEquals(4.0, view.scale, 1e-9),
              () -> assertFalse(a.isAnimating()),
              () -> assertTrue(view.anchors.stream().allMatch(anchor::equals)));
        });
  }

  @Test
  void a_zero_duration_reaches_the_target_at_the_first_tick_and_the_notch_is_configurable()
      throws Exception {
    onEdt(
        () -> {
          FakeView view = new FakeView();
          SmoothZoomAnimator a = animator(view);
          a.setDuration(0);
          a.setWheelStep(2.0);
          a.wheel(wheelEvent(-1), null);
          now += 0.001;
          a.tick();
          assertAll(
              () -> assertEquals(2.0, view.scale, 1e-9),
              () -> assertFalse(a.isAnimating()),
              () -> assertEquals(1.0, SmoothZoomAnimator.fraction(0, 0), 1e-12));
        });
  }

  private static java.awt.event.MouseWheelEvent wheelEvent(int rotation) {
    return new java.awt.event.MouseWheelEvent(
        new javax.swing.JPanel(),
        java.awt.event.MouseEvent.MOUSE_WHEEL,
        0,
        0,
        0,
        0,
        0,
        false,
        java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
        1,
        rotation);
  }

  @Test
  void the_target_is_clamped_and_the_end_action_runs_once() throws Exception {
    onEdt(
        () -> {
          FakeView view = new FakeView();
          SmoothZoomAnimator a = animator(view);
          AtomicInteger ends = new AtomicInteger();
          a.animateTo(100.0, null, ends::incrementAndGet);
          now += 1.0;
          a.tick();
          a.tick();
          assertAll(
              () -> assertEquals(4.0, view.scale, 1e-9),
              () -> assertEquals(1, ends.get()),
              () -> assertSame(null, view.anchors.getFirst()));
        });
  }

  @Test
  void a_relative_zoom_goes_to_the_scale_the_view_snaps_to_and_the_idle_action_follows()
      throws Exception {
    onEdt(
        () -> {
          FakeView view = new FakeView();
          view.snapTo = 2.0;
          SmoothZoomAnimator a = animator(view);
          List<String> order = new ArrayList<>();
          a.setIdleAction(() -> order.add("idle"));
          a.zoomBy(1.9, null);
          assertEquals(2.0, a.targetScale().orElseThrow(), 1e-9);
          a.stop();
          a.animateTo(0.5, null, () -> order.add("end"));
          assertEquals(0.5, a.targetScale().orElseThrow(), 1e-9, "an absolute zoom: no snap");
          now += 1.0;
          a.tick();
          assertEquals(List.of("end", "idle"), order);
        });
  }

  @Test
  void stopping_leaves_the_scale_and_skips_the_end_action() throws Exception {
    onEdt(
        () -> {
          FakeView view = new FakeView();
          SmoothZoomAnimator a = animator(view);
          AtomicInteger ends = new AtomicInteger();
          a.animateTo(0.25, null, ends::incrementAndGet);
          now += 0.05;
          a.tick();
          double stoppedAt = view.scale;
          a.stop();
          assertAll(
              () -> assertFalse(a.isAnimating()),
              () -> assertTrue(a.targetScale().isEmpty()),
              () -> assertEquals(0, ends.get()),
              () -> assertTrue(stoppedAt < 1.0 && stoppedAt > 0.25));
        });
  }
}
