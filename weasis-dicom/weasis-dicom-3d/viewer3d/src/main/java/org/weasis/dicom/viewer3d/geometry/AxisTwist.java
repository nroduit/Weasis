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

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import org.weasis.core.api.gui.util.GeomUtil;

/**
 * State of the rotation slider: the twist of the camera orientation about one axis of the volume.
 *
 * <p>The angle is kept explicitly rather than re-read from the quaternion on every access, because
 * a pure half-turn about a perpendicular axis (several presets) has no defined twist; the value
 * read there is a valid but arbitrary choice, and slider moves are relative to it.
 */
final class AxisTwist {

  private Axis axis = Axis.Z;
  private double angle;

  Axis axis() {
    return axis;
  }

  void setAxis(Axis axis, Quaterniondc rotation) {
    this.axis = axis;
    sync(rotation);
  }

  /** Radians within [-π, π]. */
  double angle() {
    return angle;
  }

  /** Degrees within [0, 360), as the slider shows them. */
  int degrees() {
    int degrees = (int) Math.round(Math.toDegrees(angle));
    return degrees < 0 ? (degrees + 360) % 360 : degrees;
  }

  /** Re-reads the twist after a change of orientation that did not come from the slider. */
  void sync(Quaterniondc rotation) {
    angle = twistAngle(rotation, axis.direction());
  }

  /**
   * Turns {@code rotation} about the axis so that its twist is {@code degree}, keeping the swing.
   */
  void apply(Quaterniond rotation, int degree) {
    double target = GeomUtil.normalizeAngle(Math.toRadians(degree));
    rotation.rotateAxis(target - angle, axis.direction()).normalize();
    angle = target;
  }

  /**
   * Twist of {@code q} about the unit {@code axis}, in radians within [-π, π]; the same whether the
   * swing is applied before or after it, and 0 when undefined.
   */
  static double twistAngle(Quaterniondc q, Vector3dc axis) {
    double s = q.x() * axis.x() + q.y() * axis.y() + q.z() * axis.z();
    return GeomUtil.normalizeAngle(2.0 * Math.atan2(s, q.w()));
  }
}
