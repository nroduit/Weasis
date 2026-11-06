/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.view;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** Snapping of the zoom to the native scales, bounds of the pan and inertia. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SlideNavigationTest {

  private static final double EPS = 1e-9;
  private static final double[] SCALES = {1.0, 0.25, 0.0625};

  @Test
  void a_zoom_snaps_near_a_native_scale_when_coming_nearer_or_passing_it() {
    assertAll(
        () -> assertEquals(1.0, ScaleSnap.snap(0.9, 1.035, SCALES), EPS, "passing"),
        () -> assertEquals(1.0, ScaleSnap.snap(0.9, 0.95, SCALES), EPS, "coming nearer"),
        () -> assertEquals(0.25, ScaleSnap.snap(0.3, 0.26, SCALES), EPS),
        () -> assertEquals(0.8, ScaleSnap.snap(0.7, 0.8, SCALES), EPS, "too far"),
        () -> assertEquals(1.15, ScaleSnap.snap(1.0, 1.15, SCALES), EPS, "a notch away"),
        () -> assertEquals(2.0, ScaleSnap.snap(1.0, 2.0, SCALES), EPS));
  }

  @Test
  void small_steps_leave_a_native_scale_without_being_held() {
    double scale = 1.0;
    for (int i = 0; i < 10; i++) {
      scale = ScaleSnap.snap(scale, scale * 1.014, SCALES);
    }
    assertEquals(Math.pow(1.014, 10), scale, EPS);
    assertEquals(1.0, ScaleSnap.snap(1.03, 1.03 / 1.014, SCALES), EPS, "and come back to it");
  }

  @Test
  void the_bounds_keep_half_the_view_covered_or_the_whole_slide_shown() {
    Rectangle2D view = new Rectangle2D.Double(0, 0, 1000, 800);
    assertAll(
        // Larger than the view, covering it: nothing to do; a quarter only: back to the half
        () -> assertPoint(0, 0, PanBounds.correction(rect(-3000, -2000, 5000, 5000), view)),
        () -> assertPoint(250, 0, PanBounds.correction(rect(-4750, -100, 5000, 5000), view)),
        () -> assertPoint(0, -200, PanBounds.correction(rect(-100, 600, 5000, 5000), view)),
        // Smaller than half the view: shown whole
        () -> assertPoint(50, 0, PanBounds.correction(rect(-50, 100, 300, 200), view)),
        () -> assertPoint(-100, -50, PanBounds.correction(rect(800, 650, 300, 200), view)),
        () -> assertPoint(0, 0, PanBounds.correction(rect(100, 100, 300, 200), view)),
        // Completely away
        () -> assertPoint(1500, 0, PanBounds.correction(rect(-2000, 0, 1000, 800), view)));
  }

  /** A view panned in pixels, whose slide spans [x, x + 2000] in a view 1000 wide. */
  private static final class FakeView implements PanInertia.Target {
    double x;
    int pans;

    @Override
    public void panBy(double dx, double dy) {
      x += dx;
      pans++;
    }

    @Override
    public Point2D correction() {
      return PanBounds.correction(rect(x, 0, 2000, 1000), rect(0, 0, 1000, 1000));
    }
  }

  private double now;

  private PanInertia inertia(FakeView view) {
    return new PanInertia(view, () -> now);
  }

  private void dragTo(PanInertia inertia, double x, double dt) {
    now += dt;
    inertia.track(x, 0);
  }

  @Test
  void a_fast_release_glides_on_and_slows_down() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          FakeView view = new FakeView();
          view.x = -500;
          PanInertia inertia = inertia(view);
          inertia.begin(500, 0);
          dragTo(inertia, 490, 0.1); // older than the samples kept
          dragTo(inertia, 480, 0.02);
          dragTo(inertia, 470, 0.02);
          now += 0.01;
          inertia.release();
          double speed = inertia.velocity().getX();
          double travel = 0;
          for (int i = 0; i < 200 && inertia.isRunning(); i++) {
            double before = view.x;
            now += 0.016;
            inertia.tick();
            travel += view.x - before;
          }
          double expected = speed * PanInertia.TIME_CONSTANT;
          double total = travel;
          assertAll(
              () -> assertEquals(-500, speed, 1e-6, "from the last 80 ms"),
              () -> assertFalse(inertia.isRunning()),
              () -> assertTrue(Math.abs(total - expected) < 0.05 * -expected, total + ""));
        });
  }

  @Test
  void a_slow_or_held_release_does_not_glide() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          FakeView view = new FakeView();
          view.x = -500;
          PanInertia inertia = inertia(view);
          inertia.begin(500, 0);
          dragTo(inertia, 400, 0.02);
          now += PanInertia.STILL_SECONDS + 0.01;
          inertia.release();
          assertFalse(inertia.isRunning(), "held still before the release");
          inertia.begin(500, 0);
          dragTo(inertia, 498, 0.05);
          inertia.release();
          assertFalse(inertia.isRunning(), "slow");
          assertEquals(0, view.pans);
        });
  }

  @Test
  void a_release_beyond_the_bounds_eases_back_and_a_glide_stops_at_them() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          FakeView view = new FakeView();
          view.x = 800; // a fifth of the view covered, the half is required
          PanInertia inertia = inertia(view);
          inertia.begin(500, 0);
          inertia.release();
          assertTrue(inertia.isRunning());
          now += 0.1;
          inertia.tick();
          double midway = view.x;
          now += PanInertia.SETTLE_SECONDS;
          inertia.tick();
          assertAll(
              () -> assertTrue(midway < 800 && midway > 500, "eased: " + midway),
              () -> assertEquals(500, view.x, 1e-6),
              () -> assertFalse(inertia.isRunning()));

          // A glide to the right: stops and comes back when the slide reaches the bound
          view.x = 300;
          inertia.begin(0, 0);
          dragTo(inertia, 40, 0.02);
          dragTo(inertia, 80, 0.02);
          inertia.release();
          for (int i = 0; i < 200 && inertia.isRunning(); i++) {
            now += 0.016;
            inertia.tick();
          }
          assertEquals(500, view.x, 1e-6);
        });
  }

  private static Rectangle2D rect(double x, double y, double w, double h) {
    return new Rectangle2D.Double(x, y, w, h);
  }

  private static void assertPoint(double x, double y, Point2D p) {
    assertEquals(x, p.getX(), 1e-6);
    assertEquals(y, p.getY(), 1e-6);
  }
}
