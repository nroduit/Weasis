/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.export;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sweep is what makes the turn smooth, and it needs no GL context to check: a loop has to close
 * exactly on itself, and the ping-pong has to reverse rather than jump.
 */
@DisplayName("Rotation sweep")
class RotationFrameSourceTest {

  private static final double EPSILON = 1.0e-9;
  private static final double TURN = 2 * Math.PI;

  @Test
  @DisplayName("spreads a full turn evenly over the frames")
  void fullTurn() {
    assertAll(
        () -> assertEquals(0.0, RotationFrameSource.sweepAngle(0, 36, false, TURN), EPSILON),
        () ->
            assertEquals(
                Math.toRadians(10), RotationFrameSource.sweepAngle(1, 36, false, TURN), EPSILON),
        () -> assertEquals(Math.PI, RotationFrameSource.sweepAngle(18, 36, false, TURN), EPSILON),
        () ->
            assertEquals(
                Math.toRadians(350), RotationFrameSource.sweepAngle(35, 36, false, TURN), EPSILON));
  }

  @Test
  @DisplayName("never repeats the first frame at the end of a loop")
  void loopClosesWithoutDuplicate() {
    double last = RotationFrameSource.sweepAngle(35, 36, false, TURN);
    assertTrue(last < 2 * Math.PI && last > Math.toRadians(340));
  }

  @Test
  @DisplayName("goes out to the sweep and comes back in ping-pong")
  void pingPong() {
    assertAll(
        () -> assertEquals(0.0, RotationFrameSource.sweepAngle(0, 36, true, TURN), EPSILON),
        () -> assertEquals(Math.PI, RotationFrameSource.sweepAngle(18, 36, true, TURN), EPSILON),
        () ->
            assertEquals(
                RotationFrameSource.sweepAngle(9, 36, true, TURN),
                RotationFrameSource.sweepAngle(27, 36, true, TURN),
                EPSILON));
  }

  @Test
  @DisplayName("spreads a partial sweep so the last frame lands on the angle")
  void partialSweep() {
    double arc = Math.toRadians(90);
    assertAll(
        () -> assertEquals(0.0, RotationFrameSource.sweepAngle(0, 10, false, arc), EPSILON),
        () ->
            assertEquals(
                Math.toRadians(10), RotationFrameSource.sweepAngle(1, 10, false, arc), EPSILON),
        () -> assertEquals(arc, RotationFrameSource.sweepAngle(9, 10, false, arc), EPSILON),
        () -> assertEquals(0.0, RotationFrameSource.sweepAngle(0, 1, false, arc), EPSILON));
  }

  @Test
  @DisplayName("ping-pong over a partial sweep peaks at the angle")
  void partialPingPong() {
    double arc = Math.toRadians(60);
    assertAll(
        () -> assertEquals(arc, RotationFrameSource.sweepAngle(18, 36, true, arc), EPSILON),
        () -> assertEquals(arc / 2, RotationFrameSource.sweepAngle(9, 36, true, arc), EPSILON),
        () -> assertEquals(arc / 2, RotationFrameSource.sweepAngle(27, 36, true, arc), EPSILON));
  }

  @Test
  @DisplayName("reports path-tracing convergence against the requested samples, capped at done")
  void convergence() {
    assertAll(
        () -> assertEquals(0.0, RotationFrameSource.convergence(0, 128), EPSILON),
        () -> assertEquals(0.5, RotationFrameSource.convergence(64, 128), EPSILON),
        () -> assertEquals(1.0, RotationFrameSource.convergence(129, 128), EPSILON));
  }
}
