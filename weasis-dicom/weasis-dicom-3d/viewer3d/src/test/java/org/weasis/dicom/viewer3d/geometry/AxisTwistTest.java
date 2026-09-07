/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.weasis.dicom.codec.geometry.ImageOrientation.Plan;

/**
 * The rotation slider reads and writes the twist about one axis. It has to cover the whole turn on
 * every axis from every preset, which a middle Euler angle cannot do, moving it must leave the rest
 * of the orientation alone, and it must not lose track of itself where the twist is undefined.
 */
@DisplayName("Axis twist")
class AxisTwistTest {

  private static final double EPSILON = 1.0e-9;

  static Stream<Arguments> presets() {
    return Stream.of(CameraView.values())
        .flatMap(
            view ->
                Stream.of(Plan.values())
                    .flatMap(
                        plan ->
                            Stream.of(Axis.values()).map(axis -> Arguments.of(view, plan, axis))));
  }

  @ParameterizedTest
  @MethodSource("presets")
  @DisplayName("reads back every degree of a full turn from every preset")
  void fullTurn(CameraView view, Plan plan, Axis axis) {
    Quaterniond rotation = new Quaterniond(view.rotation(plan));
    AxisTwist twist = new AxisTwist();
    twist.setAxis(axis, rotation);
    for (int degree = 0; degree < 360; degree++) {
      twist.apply(rotation, degree);
      assertEquals(degree, twist.degrees(), "at " + degree);
    }
  }

  @ParameterizedTest
  @MethodSource("presets")
  @DisplayName("moving the slider turns the volume by the difference, about the axis")
  void turnsByDifference(CameraView view, Plan plan, Axis axis) {
    Quaterniond rotation = new Quaterniond(view.rotation(plan));
    Quaterniond start = new Quaterniond(rotation);
    AxisTwist twist = new AxisTwist();
    twist.setAxis(axis, rotation);
    double first = twist.angle();
    twist.apply(rotation, 30);
    twist.apply(rotation, 70);
    Quaterniond expected =
        new Quaterniond(start).rotateAxis(Math.toRadians(70) - first, axis.direction());
    assertSameRotation(expected, rotation);
  }

  @Test
  @DisplayName("keeps counting on a preset whose twist is undefined")
  void undefinedTwist() {
    // BOTTOM is a half-turn about X: no twist about Z can be read from it.
    Quaterniond rotation = new Quaterniond(CameraView.BOTTOM.rotation());
    AxisTwist twist = new AxisTwist();
    twist.setAxis(Axis.Z, rotation);
    assertEquals(0, twist.degrees());
    twist.apply(rotation, 30);
    twist.apply(rotation, 70);
    assertEquals(70, twist.degrees());
    Quaterniond expected =
        new Quaterniond(CameraView.BOTTOM.rotation()).rotateZ(Math.toRadians(70));
    assertSameRotation(expected, rotation);
  }

  @ParameterizedTest
  @EnumSource(Axis.class)
  @DisplayName("moving the slider only changes the twist about the axis")
  void keepsSwing(Axis axis) {
    Vector3dc dir = axis.direction();
    Quaterniond rotation = new Quaterniond(CameraView.INITIAL.rotation());
    Quaterniond swingBefore = withoutTwist(rotation, dir);
    AxisTwist twist = new AxisTwist();
    twist.setAxis(axis, rotation);
    twist.apply(rotation, 200);
    assertSameRotation(swingBefore, withoutTwist(rotation, dir));
  }

  @Test
  @DisplayName("the twist of a pure axis rotation is its angle")
  void pureRotation() {
    Vector3dc y = Axis.Y.direction();
    assertEquals(
        Math.toRadians(150),
        AxisTwist.twistAngle(new Quaterniond().rotateY(Math.toRadians(150)), y),
        EPSILON);
    assertEquals(
        Math.toRadians(-150),
        AxisTwist.twistAngle(new Quaterniond().rotateY(Math.toRadians(210)), y),
        EPSILON);
  }

  @Test
  @DisplayName("the twist does not depend on the side of the swing")
  void twistSideIndependent() {
    Vector3dc z = Axis.Z.direction();
    Quaterniond swing = new Quaterniond().rotateX(Math.toRadians(-75));
    Quaterniond twist = new Quaterniond().rotateZ(Math.toRadians(160));
    assertEquals(
        Math.toRadians(160), AxisTwist.twistAngle(new Quaterniond(swing).mul(twist), z), EPSILON);
    assertEquals(
        Math.toRadians(160), AxisTwist.twistAngle(new Quaterniond(twist).mul(swing), z), EPSILON);
  }

  private static Quaterniond withoutTwist(Quaterniond q, Vector3dc axis) {
    return new Quaterniond(q).rotateAxis(-AxisTwist.twistAngle(q, axis), axis);
  }

  /** Compares the rotations, not the quaternions: q and -q rotate alike. */
  private static void assertSameRotation(Quaterniond expected, Quaterniond actual) {
    for (Vector3dc dir : List.of(Axis.X.direction(), Axis.Y.direction(), Axis.Z.direction())) {
      assertTrue(
          expected
              .transform(new Vector3d(dir))
              .equals(actual.transform(new Vector3d(dir)), EPSILON),
          "axis " + dir);
    }
  }
}
