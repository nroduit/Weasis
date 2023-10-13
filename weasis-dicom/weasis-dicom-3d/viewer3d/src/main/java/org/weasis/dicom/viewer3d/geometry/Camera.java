/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.geometry;

import static org.joml.Math.abs;
import static org.joml.Math.sqrt;
import static org.joml.Math.tan;
import static org.joml.Math.toRadians;

import java.awt.geom.Point2D;
import java.util.Objects;
import org.joml.Math;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector2d;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.vr.DicomVolTexture;
import org.weasis.dicom.viewer3d.vr.RenderingType;
import org.weasis.dicom.viewer3d.vr.View3d;
import org.weasis.dicom.viewer3d.vr.VolumeCanvas;

public class Camera {
  public static final String P_DEFAULT_ORIENTATION = "default.vr.orientation";
  public static final double SCALE_MAX = 6.0;
  public static final double SCALE_MIN = 1.0 / SCALE_MAX;
  static final double INITIAL_FOV = 45;
  static final double DEFAULT_ZOOM = 1;
  static final Vector3d POSITION_ZERO = new Vector3d();

  private final VolumeCanvas renderer;
  private boolean isAdjusting = false;
  private final Vector3d position = new Vector3d();
  private final Quaterniond rotation = new Quaterniond();
  public static final ViewData DEFAULT_SLICE_VIEW =
      new ViewData(
          RenderingType.SLICE.getTitle(),
          new Vector3d(0, 0, 0),
          getQuaternion(0, 0, 180),
          DEFAULT_ZOOM);
  private final Vector3d slicePosition = new Vector3d(DEFAULT_SLICE_VIEW.position());
  private final Quaterniond sliceRotation = new Quaterniond(DEFAULT_SLICE_VIEW.rotation());

  private final Vector3d prevMousePos = new Vector3d();
  // Perspective
  private double zNear = 0.1;
  private double zFar = 10000;
  private double fov = INITIAL_FOV;

  // Zoom
  private double zoomFactor = DEFAULT_ZOOM;
  private double internalZoomFactor = -fov * zNear / DEFAULT_ZOOM;

  // Matrix
  public static final Matrix4d currentModelMatrix =
      new Matrix4d().rotateX(Math.toRadians(90)).translate(new Vector3d(-0.5, -0.5, -0.5));
  Matrix4d viewMatrix = null;
  Matrix4d projectionMatrix = null;
  Matrix4d viewProjectionMatrix = null;
  private boolean orthographicProjection;
  private boolean sliceMode;
  private Axis rotationAxis = Axis.Z;

  public Camera(VolumeCanvas renderer) {
    this(renderer, getDefaultOrientation());
  }

  public Camera(VolumeCanvas renderer, View preset) {
    this.renderer = Objects.requireNonNull(renderer);
    setCameraView(preset);
  }

  public static CameraView getDefaultOrientation() {
    String orientation =
        GuiUtils.getUICore().getSystemPreferences().getProperty(P_DEFAULT_ORIENTATION);
    if (StringUtil.hasText(orientation)) {
      return CameraView.getCameraView(orientation);
    }
    return CameraView.INITIAL;
  }

  public static Quaterniond getQuaternion(int x, int y, int z) {
    return new Quaterniond().rotationXYZ(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
  }

  public void setCameraView(View preset) {
    if (renderer instanceof View3d view3d) {
      DicomVolTexture volTexture = view3d.getVolTexture();
      if (volTexture != null) {
        set(preset.position(), preset.rotation(volTexture.getSlicePlan()), preset.zoom());
        return;
      }
    }
    set(preset.position(), preset.rotation(), preset.zoom());
  }

  public Camera(VolumeCanvas renderer, Vector3d eye, Vector3d viewCenter, Vector3d upDir) {
    this(renderer, presetFromLookAt(eye, viewCenter, upDir));
  }

  public static View presetFromLookAt(Vector3d eye, Vector3d center, Vector3d up) {
    if (up.y() == 1 && eye.get(1) == 0) {
      eye.y += 1.0e-10f;
    }
    final Vector3d dir = new Vector3d(center).sub(eye);
    final Vector3d zAxis = new Vector3d(dir).normalize();

    final Vector3d xAxis = new Vector3d(zAxis).cross(new Vector3d(up).normalize()).normalize();
    final Vector3d yAxis = new Vector3d(xAxis).cross(zAxis).normalize();
    xAxis.set(new Vector3d(zAxis).cross(yAxis)).normalize();
    return new ViewData(
        "custom", // NON-NLS
        new Vector3d(center).negate(),
        new Quaterniond()
            .setFromNormalized(new Matrix3d(xAxis, yAxis, zAxis).transpose())
            .normalize(),
        -dir.length());
  }

  public Vector3d getPosition() {
    return sliceMode ? slicePosition : position;
  }

  public Quaterniond getRotation() {
    return sliceMode ? sliceRotation : rotation;
  }

  public double getZoomFactor() {
    return zoomFactor;
  }

  public double getzNear() {
    return zNear;
  }

  public boolean isOrthographicProjection() {
    return orthographicProjection;
  }

  public void setOrthographicProjection(boolean orthographicProjection) {
    if (this.orthographicProjection != orthographicProjection) {
      this.orthographicProjection = orthographicProjection;
      resetTransformation();
      setZoomFactor(getZoomFactor());
    }
  }

  public void set(Vector3d position, Quaterniond rotation, double zoom) {
    set(position, rotation, zoom, true);
  }

  public void set(Vector3d position, Quaterniond rotation, double zoom, boolean repaint) {
    if (sliceMode) {
      this.slicePosition.set(position);
      this.sliceRotation.set(rotation);
    } else {
      this.position.set(position);
      this.rotation.set(rotation);
      updateRotationAction();
    }
    setZoomFactor(zoom, repaint);
  }

  public void updateRotationAction() {
    EventManager.getInstance()
        .getAction(ActionW.ROTATION)
        .ifPresent(s -> s.setSliderValue(getCurrentAxisRotationInDegrees(), false));
  }

  public Matrix4d getViewMatrix() {
    if (viewMatrix == null) {
      if (sliceMode) {
        viewMatrix = new Matrix4d().rotate(sliceRotation);
      } else {
        viewMatrix =
            new Matrix4d()
                .setTranslation(new Vector3d(0, 0, internalZoomFactor))
                .rotate(rotation)
                .translate(position);
      }
    }
    return new Matrix4d(viewMatrix);
  }

  public Matrix4d getProjectionMatrix() {
    if (projectionMatrix == null) {
      projectionMatrix = new Matrix4d();
      if (!sliceMode) {
        if (orthographicProjection) {
          double size = renderer.getSurfaceHeight();
          projectionMatrix.setOrthoSymmetric(renderer.getAspectRatio() * size, size, zFar, 1);
        } else {
          projectionMatrix.setPerspective(fov, renderer.getAspectRatio(), zNear, zFar);
        }
      }
    }
    return new Matrix4d(projectionMatrix);
  }

  public Matrix4d getViewProjectionMatrix() {
    if (viewProjectionMatrix == null) {
      viewProjectionMatrix = new Matrix4d(getProjectionMatrix()).mul(getViewMatrix());
    }
    return new Matrix4d(viewProjectionMatrix);
  }

  public Vector4d getLightOrigin() {
    // Vector3d lightPosition = new Vector3d(0, 0, 0);
    // Matrix4d lightMatrix = new Matrix4d().translate(lightPosition);
    //
    // Matrix4d viewInverse = getViewMatrix().invert();
    // lightMatrix.mul(viewInverse);
    // Vector3d lightTranslation = new Vector3d();
    // lightMatrix.getTranslation(lightTranslation);
    // return new Vector4d(lightTranslation, 1);
    return new Vector4d(0, 0, 0, 1).mul(getViewMatrix().invert());
  }

  public boolean isAdjusting() {
    return isAdjusting;
  }

  public void setAdjusting(boolean adjusting) {
    isAdjusting = adjusting;
  }

  public double getFieldOfView() {
    return fov;
  }

  public double getFocalLength() {
    return 1.0 / tan(toRadians(getFieldOfView()) / 2.0);
  }

  public void init(Point2D p) {
    prevMousePos.x = p.getX();
    prevMousePos.y = p.getY();
    updateCameraTransform();
  }

  public void setZoomFactor(double zoomFactor) {
    setZoomFactor(zoomFactor, true);
  }

  public void setZoomFactor(double zoomFactor, boolean repaint) {
    renderer.setActionsInView(ActionW.ZOOM.cmd(), zoomFactor);
    this.zoomFactor = Math.abs(zoomFactor);
    if (!sliceMode) {
      double ratio = this.zoomFactor;
      if (orthographicProjection) {
        ratio /= 3.5;
      }
      this.internalZoomFactor = -fov * zNear / ratio;
    }
    if (repaint) {
      updateCameraTransform();
    } else {
      resetTransformation();
    }
  }

  public void setRotation(int degree) {
    if (sliceMode) {
      int last = (Integer) renderer.getActionValue(ActionW.ROTATION.cmd());
      renderer.setActionsInView(ActionW.ROTATION.cmd(), degree);
      sliceRotation.rotateAxis(Math.toRadians((double) last - degree), 0, 0, 1);
    } else {
      Vector3d angles = getEulerAnglesXYZ();
      if (rotationAxis == Axis.X) {
        if (degree >= 180) {
          angles.x = Math.toRadians(degree - 360.0);
        } else {
          angles.x = Math.toRadians(degree);
        }
      } else if (rotationAxis == Axis.Y) {
        if (degree >= 270) {
          angles.y = Math.toRadians(degree - 360.0);
        } else if (degree > 90) {
          angles.y = Math.toRadians(180.0 - degree);
        } else {
          angles.y = Math.toRadians(degree);
        }
      } else {
        if (degree >= 180) {
          angles.z = Math.toRadians(degree - 360.0);
        } else {
          angles.z = Math.toRadians(degree);
        }
      }
      Quaterniond quat = new Quaterniond().rotationXYZ(angles.x, angles.y, angles.z);
      rotation.set(quat);
    }
    updateCameraTransform();
  }

  public void resetRotation() {
    if (sliceMode) {
      renderer.setActionsInView(ActionW.ROTATION.cmd(), 0);
      sliceRotation.set(DEFAULT_SLICE_VIEW.rotation());
    } else {
      rotation.set(getDefaultOrientation().rotation());
      updateRotationAction();
    }
    updateCameraTransform();
  }

  public void resetPan() {
    if (sliceMode) {
      slicePosition.set(DEFAULT_SLICE_VIEW.position());
    } else {
      position.set(getDefaultOrientation().position());
    }
    updateCameraTransform();
  }

  public void resetAll() {
    setCameraView(getDefaultOrientation());
  }

  public void translate(Point2D p) {
    final Vector2d translationNDC =
        new Vector2d(screenCoordToNDC(p)).sub(screenCoordToNDC(prevMousePos));
    prevMousePos.x = p.getX();
    prevMousePos.y = p.getY();
    double hh = abs(internalZoomFactor) * tan(getFieldOfView() * 0.5);
    double hw = hh * renderer.getAspectRatio();

    Vector4d invTransform =
        new Matrix4d(getViewMatrix())
            .invert()
            .transform(new Vector4d(translationNDC.x() * hw, translationNDC.y() * hh, 0.0, 0));

    if (sliceMode) {
      slicePosition.x += invTransform.x;
      slicePosition.y += invTransform.y;
      slicePosition.z += invTransform.z;
    } else {
      position.x += invTransform.x;
      position.y += invTransform.y;
      position.z += invTransform.z;
    }
    updateCameraTransform();
  }

  public void rotate(Point2D p) {
    final Vector2i dimensions =
        new Vector2i(renderer.getSurfaceWidth(), renderer.getSurfaceHeight());

    if (sliceMode) {
      //      Vector3d currPos = new Vector3d(boundlessScreenCoordToNDC(p, dimensions), 0);
      //      Vector3d prevPos = new Vector3d(boundlessScreenCoordToNDC(prevMousePos, dimensions),
      // 0);
      //      double angle = prevPos.angle(currPos);
      //      Vector3d axis = prevPos.cross(currPos, new Vector3d());
      //      Quaterniond deltaRotation = new Quaterniond().rotateAxis(angle, axis);
      //      sliceRotation.mul(deltaRotation).normalize();

      sliceRotation
          .set(
              new Quaterniond(ndcToArcBall(boundlessScreenCoordToNDC(p, dimensions)))
                  .mul(ndcToArcBall(boundlessScreenCoordToNDC(prevMousePos, dimensions)))
                  .mul(sliceRotation))
          .normalize();
    } else {
      rotation
          .set(
              new Quaterniond(ndcToArcBall(boundlessScreenCoordToNDC(p, dimensions)))
                  .mul(ndcToArcBall(boundlessScreenCoordToNDC(prevMousePos, dimensions)))
                  .mul(rotation))
          .normalize();
      updateRotationAction();
    }

    prevMousePos.x = p.getX();
    prevMousePos.y = p.getY();
    updateCameraTransform();
  }

  public void updateCameraTransform() {
    resetTransformation();
    renderer.display();
  }

  public void resetTransformation() {
    viewMatrix = null;
    projectionMatrix = null;
    viewProjectionMatrix = null;
  }

  private Vector2d screenCoordToNDC(Point2D p) {
    return screenCoordToNDC(new Vector3d(p.getX(), p.getY(), 0));
  }

  private Vector2d boundlessScreenCoordToNDC(Point2D p, Vector2i dimensions) {
    return boundlessScreenCoordToNDC(new Vector3d(p.getX(), p.getY(), 0), dimensions);
  }

  private Vector2d boundlessScreenCoordToNDC(Vector3d mousePos, Vector2i dimensions) {
    final Vector3d mod = new Vector3d(mousePos);
    mod.x = mod.x() % (dimensions.x());
    mod.y = mod.y() % (dimensions.y());
    if (mousePos.x < 0) {
      mod.x = dimensions.x - abs(mod.x);
    }
    if (mousePos.y < 0) {
      mod.y = dimensions.y - abs(mod.y);
    }
    return screenCoordToNDC(mod);
  }

  private Vector2d screenCoordToNDC(Vector3d mousePos) {
    return new Vector2d(
        mousePos.x() * 2.0 / renderer.getSurfaceWidth() - 1.0,
        1.0 - 2.0 * mousePos.y() / renderer.getSurfaceHeight());
  }

  public static Quaterniond ndcToArcBall(Vector2d p) {
    double dist = p.dot(p);
    if (dist <= 1.0) {
      // Inside the sphere
      return new Quaterniond(p.x(), p.y(), sqrt(1.0 - dist), 0.0);
    } else {
      // Outside the sphere
      final Vector2d proj = new Vector2d(p).normalize();
      return new Quaterniond(proj.x(), proj.y(), 0.0, 0.0);
    }
  }

  public void setSliceMode(boolean sliceMode) {
    if (this.sliceMode != sliceMode) {
      this.sliceMode = sliceMode;
      if (sliceMode) {
        setCameraView(DEFAULT_SLICE_VIEW);
      }
      updateCameraTransform();
    }
  }

  public Axis getRotationAxis() {
    return rotationAxis;
  }

  public void setRotationAxis(Axis rotationAxis) {
    this.rotationAxis = rotationAxis;
  }

  public Vector3d getEulerAnglesXYZ() {
    Vector3d angles = new Vector3d();
    rotation.getEulerAnglesXYZ(angles);
    return angles;
  }

  public double getCurrentAxisRotation() {
    Vector3d angles = getEulerAnglesXYZ();
    if (rotationAxis == Axis.X) {
      return angles.x;
    } else if (rotationAxis == Axis.Y) {
      return angles.y;
    } else {
      return angles.z;
    }
  }

  public int getCurrentAxisRotationInDegrees() {
    int rotationAngle = (int) Math.round(Math.toDegrees(getCurrentAxisRotation()));
    if (rotationAngle < 0) {
      rotationAngle = (rotationAngle + 360) % 360;
    }
    return rotationAngle;
  }
}
