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

import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;
import javax.swing.Timer;

/**
 * Animates the zoom of a view towards a target scale with an exponential ease-out, as OpenSeadragon
 * does. The scale moves in log space, so zooming in and out feel symmetric and successive wheel
 * notches add up to the target of a running animation. Each frame zooms the view at the anchor of
 * the gesture ({@link GraphicsPane#zoomAt}), which keeps the point under the cursor in place
 * whatever the rotation or the flip.
 */
public final class SmoothZoomAnimator {

  /** Shape of the ease-out curve (OpenSeadragon spring stiffness). */
  static final double STIFFNESS = 6.5;

  /** Duration of an animation segment, restarted by each new wheel notch. */
  public static final double DURATION_SECONDS = 0.3;

  /** Zoom factor of one wheel notch. */
  public static final double STEP = 1.15;

  private static final double NORMALIZATION = 1.0 / (1.0 - Math.exp(-STIFFNESS));
  private static final int FRAME_MS = 16;

  /** The view being zoomed: its scale, its zoom at an anchor and its scale limits. */
  public interface Target {
    double getScale();

    /** Clamps a scale to what the view allows. */
    double clampScale(double scale);

    /**
     * @param anchor offset from the center of the view in view pixels, null for the center
     */
    void zoomTo(double scale, Point2D anchor);

    /**
     * The scale a relative zoom (wheel, double click) goes to: {@code to} unless the view prefers a
     * scale close to it.
     *
     * @param from the scale the zoom starts from
     * @param to the scale asked for
     */
    default double snapScale(double from, double to) {
      return to;
    }
  }

  private final Target target;
  private final Timer timer;
  private final DoubleUnaryOperator clock;

  private double duration = DURATION_SECONDS;
  private double wheelStep = STEP;
  private double startLog;
  private double currentLog;
  private double targetLog;
  private double segmentStart;
  private Point2D anchor;
  private Runnable then;
  private Runnable idleAction;
  private boolean applying;

  public SmoothZoomAnimator(Target target) {
    this(target, _ -> System.nanoTime() / 1e9);
  }

  SmoothZoomAnimator(Target target, DoubleUnaryOperator clock) {
    this.target = target;
    this.clock = clock;
    this.timer = new Timer(FRAME_MS, _ -> tick());
    this.timer.setCoalesce(true);
  }

  /**
   * Adds the notches of a wheel event to the target.
   *
   * @param anchor offset from the center of the view in view pixels, null for the center
   */
  public void wheel(MouseWheelEvent e, Point2D anchor) {
    // Negative rotation is away from the user: zoom in
    zoomBy(Math.pow(wheelStep, -e.getPreciseWheelRotation()), anchor);
  }

  /** The length of an animation segment, in seconds; zero reaches the target at the next frame. */
  public void setDuration(double seconds) {
    duration = Math.max(0.0, seconds);
  }

  /** The zoom factor of one wheel notch, above one. */
  public void setWheelStep(double factor) {
    wheelStep = Math.max(1.001, factor);
  }

  /** Multiplies the target scale by a factor, keeping the point at the anchor in place. */
  public void zoomBy(double factor, Point2D anchor) {
    double base = isAnimating() ? targetLog : Math.log(target.getScale());
    double from = Math.exp(base);
    start(target.snapScale(from, from * factor), anchor, null);
  }

  /**
   * Animates to a scale.
   *
   * @param anchor offset from the center of the view in view pixels, null for the center
   * @param then run once the scale is reached, null for nothing
   */
  public void animateTo(double scale, Point2D anchor, Runnable then) {
    start(scale, anchor, then);
  }

  /** Runs an action each time an animation reaches its scale, after its end action. */
  public void setIdleAction(Runnable action) {
    this.idleAction = action;
  }

  /** Whether an animation runs. */
  public boolean isAnimating() {
    return timer.isRunning();
  }

  /** The scale the running animation goes to. */
  public Optional<Double> targetScale() {
    return isAnimating() ? Optional.of(Math.exp(targetLog)) : Optional.empty();
  }

  /** The anchor of the running animation, null for the center of the view. */
  public Point2D anchor() {
    return anchor == null ? null : new Point2D.Double(anchor.getX(), anchor.getY());
  }

  /** Whether the zoom being applied comes from this animator, as opposed to another action. */
  public boolean isApplying() {
    return applying;
  }

  /** Stops where it is, without running the end action. */
  public void stop() {
    timer.stop();
    then = null;
  }

  private void start(double scale, Point2D newAnchor, Runnable newThen) {
    double current = Math.log(target.getScale());
    startLog = isAnimating() ? currentLog : current;
    currentLog = startLog;
    targetLog = Math.log(target.clampScale(scale));
    anchor = newAnchor;
    then = newThen;
    segmentStart = clock.applyAsDouble(0);
    if (!timer.isRunning()) {
      timer.start();
    }
  }

  void tick() {
    double elapsed = clock.applyAsDouble(0) - segmentStart;
    boolean done = elapsed >= duration;
    currentLog = done ? targetLog : startLog + (targetLog - startLog) * fraction(elapsed, duration);
    applying = true;
    try {
      target.zoomTo(Math.exp(currentLog), anchor);
    } finally {
      applying = false;
    }
    if (done) {
      timer.stop();
      Runnable end = then;
      then = null;
      if (end != null) {
        end.run();
      }
      if (idleAction != null && !isAnimating()) {
        idleAction.run();
      }
    }
  }

  /** The eased share of the way done after {@code elapsed} seconds, from 0 to 1. */
  static double fraction(double elapsed) {
    return fraction(elapsed, DURATION_SECONDS);
  }

  static double fraction(double elapsed, double duration) {
    double progress = duration <= 0 ? 1.0 : Math.min(1.0, elapsed / duration);
    return (1.0 - Math.exp(-progress * STIFFNESS)) * NORMALIZATION;
  }
}
