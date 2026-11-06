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

import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.DoubleSupplier;
import javax.swing.Timer;

/**
 * The motion of a view after a pan: released with some speed, the view glides on and slows down
 * exponentially; released (or glided) beyond the {@link PanBounds}, it eases back into view.
 */
public final class PanInertia {

  /** Time constant of the slowing down: the glide covers the release speed times it. */
  static final double TIME_CONSTANT = 0.325;

  /** Speed below which a release does not glide, in view pixels per second. */
  static final double START_SPEED = 150;

  /** Speed at which a glide stops. */
  static final double STOP_SPEED = 20;

  static final double MAX_SPEED = 6000;

  /** The last part of the drag that gives the release speed. */
  static final double SAMPLE_SECONDS = 0.08;

  /** A drag held still that long before the release does not glide. */
  static final double STILL_SECONDS = 0.05;

  static final double SETTLE_SECONDS = 0.25;

  private static final int FRAME_MS = 16;

  /** The view being panned. */
  public interface Target {
    /** Pans the content by view pixels. */
    void panBy(double dx, double dy);

    /** The pan that brings the slide back in view, zero when it is (see {@link PanBounds}). */
    Point2D correction();
  }

  private enum Phase {
    NONE,
    GLIDE,
    SETTLE
  }

  private record Sample(double time, double x, double y) {}

  private final Target target;
  private final DoubleSupplier clock;
  private final Timer timer;
  private final Deque<Sample> samples = new ArrayDeque<>();

  private Phase phase = Phase.NONE;
  private double last;
  private double vx;
  private double vy;
  private double settleStart;
  private Point2D settleTotal;
  private double settleDone;

  public PanInertia(Target target) {
    this(target, () -> System.nanoTime() / 1e9);
  }

  PanInertia(Target target, DoubleSupplier clock) {
    this.target = target;
    this.clock = clock;
    this.timer = new Timer(FRAME_MS, _ -> tick());
    this.timer.setCoalesce(true);
  }

  /** A pan starts at a view point: any motion stops. */
  public void begin(double x, double y) {
    stop();
    samples.clear();
    samples.add(new Sample(clock.getAsDouble(), x, y));
  }

  /** The pan goes on to a view point. */
  public void track(double x, double y) {
    double now = clock.getAsDouble();
    samples.add(new Sample(now, x, y));
    while (samples.size() > 2 && samples.peekFirst().time() < now - SAMPLE_SECONDS) {
      samples.removeFirst();
    }
  }

  /** The pan is released: glides at the speed of its end, or comes back in view. */
  public void release() {
    double now = clock.getAsDouble();
    Sample first = samples.peekFirst();
    Sample end = samples.peekLast();
    samples.clear();
    if (first == null || end.time() - first.time() <= 0 || now - end.time() > STILL_SECONDS) {
      settle();
      return;
    }
    double dt = end.time() - first.time();
    vx = (end.x() - first.x()) / dt;
    vy = (end.y() - first.y()) / dt;
    double speed = Math.hypot(vx, vy);
    if (speed < START_SPEED) {
      settle();
      return;
    }
    if (speed > MAX_SPEED) {
      vx *= MAX_SPEED / speed;
      vy *= MAX_SPEED / speed;
    }
    start(Phase.GLIDE, now);
  }

  /** Eases the view back when the slide is out of the bounds; nothing otherwise. */
  public void settle() {
    Point2D correction = target.correction();
    if (PanBounds.isZero(correction)) {
      stop();
      return;
    }
    settleTotal = correction;
    settleDone = 0;
    settleStart = clock.getAsDouble();
    start(Phase.SETTLE, settleStart);
  }

  public void stop() {
    timer.stop();
    phase = Phase.NONE;
  }

  public boolean isRunning() {
    return phase != Phase.NONE;
  }

  /** The release speed of a glide, in view pixels per second. */
  Point2D velocity() {
    return new Point2D.Double(vx, vy);
  }

  private void start(Phase newPhase, double now) {
    phase = newPhase;
    last = now;
    if (!timer.isRunning()) {
      timer.start();
    }
  }

  void tick() {
    double now = clock.getAsDouble();
    double dt = now - last;
    last = now;
    if (phase == Phase.GLIDE) {
      // The distance covered in dt by a speed decaying with the time constant
      double decay = Math.exp(-dt / TIME_CONSTANT);
      double share = TIME_CONSTANT * (1 - decay);
      target.panBy(vx * share, vy * share);
      vx *= decay;
      vy *= decay;
      if (!PanBounds.isZero(target.correction())) {
        settle();
      } else if (Math.hypot(vx, vy) < STOP_SPEED) {
        stop();
      }
    } else if (phase == Phase.SETTLE) {
      double progress = Math.min(1.0, (now - settleStart) / SETTLE_SECONDS);
      double eased = 1 - Math.pow(1 - progress, 3);
      double step = eased - settleDone;
      settleDone = eased;
      target.panBy(settleTotal.getX() * step, settleTotal.getY() * step);
      if (progress >= 1.0) {
        stop();
      }
    }
  }
}
