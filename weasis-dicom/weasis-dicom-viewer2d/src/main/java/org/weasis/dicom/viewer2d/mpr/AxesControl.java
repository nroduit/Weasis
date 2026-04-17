/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

public class AxesControl {
  public static final double RAD90D = Math.toRadians(90);
  private static final Quaterniond CORONAL_ROTATION = new Quaterniond().rotateX(-RAD90D);
  private static final Quaterniond SAGITTAL_ROTATION =
      new Quaterniond().rotateY(RAD90D).rotateZ(RAD90D);

  private final PropertyChangeSupport changeSupport;
  private final Set<SliceCanvas> watchingCanvases;
  private final EnumMap<Plane, Double> canvasRotationOffset;
  private final MprController controller;

  private Vector3d center;
  private boolean hidden;
  private Quaterniond globalRotation;

  public AxesControl(MprController controller) {
    this.controller = Objects.requireNonNull(controller);
    this.hidden = false;
    this.changeSupport = new PropertyChangeSupport(this);
    this.canvasRotationOffset = new EnumMap<>(Plane.class);
    this.watchingCanvases = new HashSet<>();
    reset();
  }

  public void addPropertyChangeListener(PropertyChangeListener changeListener) {
    for (PropertyChangeListener listener : changeSupport.getPropertyChangeListeners()) {
      if (listener == changeListener) {
        return;
      }
    }
    changeSupport.addPropertyChangeListener(changeListener);
  }

  public void removePropertyChangeListener(PropertyChangeListener changeListener) {
    changeSupport.removePropertyChangeListener(changeListener);
  }

  public Vector3d getCenter() {
    return new Vector3d(center);
  }

  protected int getSliceSize() {
    var volume = controller.getVolume();
    return volume == null ? 1 : volume.getSliceSize();
  }

  public double getCenterAlongAxis(SliceCanvas c) {
    double halfSlice = getSliceSize() / 2.0;
    Vector3d axis = getRotatedCanvasAxis(c.getPlane());
    return new Vector3d(center).sub(halfSlice, halfSlice, halfSlice).dot(axis) + halfSlice;
  }

  public void setCenter(Vector3d center) {
    this.center = new Vector3d(center);
  }

  public void setCenterAlongAxis(SliceCanvas c, double value) {
    double halfSlice = getSliceSize() / 2.0;
    Vector3d axis = getRotatedCanvasAxis(c.getPlane());
    double currentDepth = new Vector3d(center).sub(halfSlice, halfSlice, halfSlice).dot(axis);
    double targetDepth = value - halfSlice;
    double delta = targetDepth - currentDepth;
    center.add(new Vector3d(axis).mul(delta));
  }

  public boolean isHidden() {
    return hidden;
  }

  public void setHidden(boolean hidden) {
    if (this.hidden != hidden) {
      this.hidden = hidden;
      refreshAllCanvases();
    }
  }

  public void reset() {
    double halfSlice = getSliceSize() / 2.0;
    this.center = new Vector3d(halfSlice, halfSlice, halfSlice);
    setGlobalRotation(new Quaterniond());
    canvasRotationOffset.put(Plane.AXIAL, 0.0);
    canvasRotationOffset.put(Plane.CORONAL, 0.0);
    canvasRotationOffset.put(Plane.SAGITTAL, 0.0);
  }

  public void resetListeners() {
    for (PropertyChangeListener listener : changeSupport.getPropertyChangeListeners()) {
      changeSupport.removePropertyChangeListener(listener);
    }
  }

  public AxisAngle4d getRotationAsAxisAngle() {
    return new AxisAngle4d(globalRotation);
  }

  public Quaterniond getViewRotation(Plane plane) {
    Quaterniond all = getGlobalRotation();
    double offset = -getRotationOffset(plane);
    return switch (plane) {
      case AXIAL -> all.rotateZ(offset);
      case CORONAL -> all.rotateY(-offset);
      case SAGITTAL -> all.rotateX(offset);
    };
  }

  public Quaterniond getGlobalRotation() {
    return new Quaterniond(globalRotation);
  }

  public void setGlobalRotation(Quaterniond globalRotation) {
    Quaterniond old = this.globalRotation;
    if (globalRotation == null) {
      this.globalRotation = null;
    } else {
      this.globalRotation = new Quaterniond(globalRotation);
    }

    changeSupport.firePropertyChange("rotation", old, globalRotation); // NON-NLS
  }

  public void addWatchingCanvas(SliceCanvas imageCanvas) {
    watchingCanvases.add(imageCanvas);
  }

  public void refreshAllCanvases() {
    for (SliceCanvas c : controller.getMprViews()) {
      if (c != null) {
        c.getJComponent().repaint();
      }
    }

    for (SliceCanvas c : watchingCanvases) {
      c.getJComponent().repaint();
    }
  }

  public AxisAngle4d getAxisRotationForPlane(SliceCanvas imageCanvas) {
    Quaterniond rotation = getRotationForSlice(imageCanvas.getPlane());
    return new AxisAngle4d(rotation.invert());
  }

  public AxisAngle4d getAxisRotationForCanvas(SliceCanvas imageCanvas) {
    Quaterniond var3 = getQuatRotationForCanvas(imageCanvas);
    return new AxisAngle4d(var3);
  }

  /**
   * Gets the rotation quaternion based on the slice orientation.
   *
   * @param orientation The slice orientation.
   * @return The predefined quaternion specific to the given slice orientation.
   */
  public static Quaterniond getRotationForSlice(Plane orientation) {
    return switch (orientation) {
      case CORONAL -> new Quaterniond(CORONAL_ROTATION);
      case SAGITTAL -> new Quaterniond(SAGITTAL_ROTATION);
      default -> new Quaterniond();
    };
  }

  public Quaterniond getQuatRotationForCanvas(SliceCanvas c) {
    Quaterniond rotation = getRotationForSlice(c.getPlane());
    return new Quaterniond(globalRotation).mul(rotation).invert();
  }

  public Vector3d getCanvasAxis(Plane type) {
    Vector3d p;
    if (type == Plane.AXIAL) {
      p = new Vector3d(0.0, 0.0, 1.0);
    } else if (type == Plane.CORONAL) {
      p = new Vector3d(0.0, 1.0, 0.0);
    } else {
      p = new Vector3d(1.0, 0.0, 0.0);
    }

    return p;
  }

  public Vector3d getRotatedCanvasAxis(Plane type) {
    Vector3d axis = getCanvasAxis(type);
    new Matrix3d().set(globalRotation).transform(axis);
    return axis;
  }

  public Vector3d getCenterForCanvas(SliceCanvas imageCanvas) {
    return getCenterForCanvas(imageCanvas, center);
  }

  public Vector3d getCenterForCanvas(SliceCanvas imageCanvas, Vector3d pt) {
    if (imageCanvas == null) {
      return new Vector3d(pt);
    }
    double halfSlice = getSliceSize() / 2.0;
    Vector3d adjustedCenter = new Vector3d(pt).sub(halfSlice, halfSlice, halfSlice);
    applyRotationMatrix(adjustedCenter, imageCanvas);
    adjustedCenter.add(new Vector3d(halfSlice, halfSlice, halfSlice));
    return adjustedCenter;
  }

  private void applyRotationMatrix(Vector3d vector, SliceCanvas canvas) {
    Plane plane = canvas.getPlane();

    // The forward transform (display→texture) applies rotations in this order:
    //   AXIAL:    R(viewRot)
    //   CORONAL:  R(viewRot) · Rx(-90°) · S(1,-1,1)
    //   SAGITTAL: R(viewRot) · Ry(90°) · Rz(90°)
    // The inverse (texture→display) is therefore:
    //   AXIAL:    R(viewRot)⁻¹
    //   CORONAL:  S(1,-1,1) · Rx(90°) · R(viewRot)⁻¹
    //   SAGITTAL: Rz(-90°) · Ry(-90°) · R(viewRot)⁻¹

    // Step 1: apply inverse of the full view rotation (global rotation + per-plane offset)
    Quaterniond viewRotation = getViewRotation(plane);
    new Matrix3d().set(viewRotation).invert().transform(vector);

    // Step 2: apply inverse of the plane-specific base rotation
    Quaterniond planeRotation = getRotationForSlice(plane);
    new Matrix3d().set(planeRotation).invert().transform(vector);

    // Step 3: apply the coronal Y-flip (S(1,-1,1) is its own inverse)
    if (plane == Plane.CORONAL) {
      vector.y = -vector.y;
    }
  }

  public void rotate(Quaterniond rotation, Plane type, double offset) {
    canvasRotationOffset.put(type, offset);
    setGlobalRotation(rotation);
  }

  public double getRotationOffset(Plane type) {
    Double val = canvasRotationOffset.get(type);
    return val == null ? 0.0 : val;
  }

  protected void setRotationOffset(Plane type, double offset) {
    canvasRotationOffset.put(type, offset);
  }

  public void rotateAroundAxis(Plane plane, double angleRadians) {
    double axisAngle = getRotationOffset(plane);
    double planeAngle;
    if (plane == Plane.CORONAL) {
      planeAngle = axisAngle - angleRadians;
    } else {
      planeAngle = angleRadians - axisAngle;
    }
    Vector3d axis = plane.getDirection();
    Quaterniond q = new Quaterniond().fromAxisAngleRad(axis, planeAngle);
    globalRotation.mul(q);
    setRotationOffset(plane, angleRadians);
  }
}
