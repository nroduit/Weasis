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
  private static final double HALF_DIMENSION = 0.5;
  public static final double RAD90D = Math.toRadians(90);
  private static final Quaterniond CORONAL_ROTATION = new Quaterniond().rotateX(-RAD90D);
  private static final Quaterniond SAGITTAL_ROTATION =
      new Quaterniond().rotateY(RAD90D).rotateZ(RAD90D);
  private static final Vector3d CENTER_VECTOR =
      new Vector3d(HALF_DIMENSION, HALF_DIMENSION, HALF_DIMENSION);

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

  public Vector3d getUnnormalizedPosition(Vector3d p) {
    return new Vector3d(p).mul(getSliceSize());
  }

  protected int getSliceSize() {
    Volume<?> volume = controller.getVolume();
    return volume == null ? 1 : volume.getSliceSize();
  }

  public double getCenterAlongAxis(SliceCanvas c) {
    return center.get(c.getPlane().axisIndex()) * getSliceSize();
  }

  public void setCenter(Vector3d center) {
    this.center = new Vector3d(center);
  }

  public void setCenterAlongAxis(SliceCanvas c, double value) {
    center.setComponent(c.getPlane().axisIndex(), value / getSliceSize());
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
    this.center = new Vector3d(HALF_DIMENSION, HALF_DIMENSION, HALF_DIMENSION);
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
      if (type == Plane.SAGITTAL) {
        throw new IllegalArgumentException("Canvas3D type not supported");
      }

      p = new Vector3d(-1.0, 0.0, 0.0);
    }

    return p;
  }

  public Vector3d getRotatedCanvasAxis(Plane type) {
    Vector3d axis = getCanvasAxis(type);
    new Matrix3d().set(globalRotation).transform(axis);
    return axis;
  }

  public Vector3d getCenterForCanvas(SliceCanvas imageCanvas) {
    return getCenterForCanvas(imageCanvas, false);
  }

  public Vector3d getCenterForCanvas(SliceCanvas imageCanvas, boolean applySpatialMultiplier) {
    return getCenterForCanvas(imageCanvas, center, applySpatialMultiplier);
  }

  public Vector3d getCenterForCanvas(
      SliceCanvas imageCanvas, Vector3d pt, boolean applySpatialMultiplier) {
    if (imageCanvas == null) {
      return new Vector3d(pt);
    }
    Vector3d adjustedCenter = new Vector3d(pt).sub(CENTER_VECTOR);
    if (applySpatialMultiplier) {
      Volume<?> volume = controller.getVolume();
      if (volume != null) {
        Vector3d multiplier = volume.getSpatialMultiplier();
        adjustedCenter.x *= multiplier.x;
        adjustedCenter.y *= multiplier.y;
        adjustedCenter.z *= multiplier.z;
      }
    }
    applyRotationMatrix(adjustedCenter, imageCanvas);
    adjustedCenter.add(CENTER_VECTOR);
    return adjustedCenter;
  }

  private void applyRotationMatrix(Vector3d vector, SliceCanvas canvas) {
    Quaterniond rotation = getRotationForSlice(canvas.getPlane());
    Matrix3d rotationMatrix = new Matrix3d().set(rotation).invert();
    rotationMatrix.transform(vector);
    if (canvas.getPlane() == Plane.CORONAL) {
      vector.y = -vector.y;
    }
  }

  public Vector3d getCenterForCustomRotation(Quaterniond rotation) {
    Vector3d vCenter = new Vector3d(this.center).sub(CENTER_VECTOR);
    Volume<?> v = controller.getVolume();
    if (v != null) {
      //      Vector3d dimensionMultiplier = v.getSpatialMultiplier();
      //      vCenter.x *= dimensionMultiplier.x;
      //      vCenter.y *= dimensionMultiplier.y;
      //      vCenter.z *= dimensionMultiplier.z;
      Matrix3d var4 = new Matrix3d().set(new Quaterniond(rotation).invert());
      var4.transform(vCenter);
    }
    vCenter.add(CENTER_VECTOR);
    return vCenter;
  }

  public Vector3d getGlobalPositionForLocalPosition(SliceCanvas canvas, Vector3d position) {
    Volume<?> v = controller.getVolume();
    if (v == null) {
      return new Vector3d(position);
    }
    Vector3d p = new Vector3d(position).sub(CENTER_VECTOR);
    Matrix3d var3 = new Matrix3d().set(getAxisRotationForPlane(canvas));
    var3.invert().transform(p);
    p.add(CENTER_VECTOR);
    return p;
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
