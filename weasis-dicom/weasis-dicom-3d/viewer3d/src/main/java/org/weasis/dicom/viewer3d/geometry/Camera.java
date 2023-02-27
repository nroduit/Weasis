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
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector2d;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.ImageOrientation.Plan;
import org.weasis.dicom.viewer3d.vr.DicomVolTexture;
import org.weasis.dicom.viewer3d.vr.View3d;
import org.weasis.dicom.viewer3d.vr.VolumeCanvas;

public class Camera {
  public static final double SCALE_MAX = 6.0;
  public static final double SCALE_MIN = 1.0 / SCALE_MAX;
  static final double INITIAL_FOV = 45;
  static final double DEFAULT_ZOOM = 1;
  static final Vector3d POSITION_ZERO = new Vector3d();

  private final VolumeCanvas renderer;
  private boolean isAdjusting = false;
  private final Vector3d position = new Vector3d();
  private final Quaterniond rotation = new Quaterniond();

  private final Vector2d prevMousePos = new Vector2d();

  // Perspective
  private double zNear = 0.1;
  private double zFar = 1000;
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

  public Camera(VolumeCanvas renderer) {
    this(renderer, CameraView.INITIAL);
  }

  public Camera(VolumeCanvas renderer, View preset) {
    this.renderer = Objects.requireNonNull(renderer);
    setCameraView(preset);
  }

  public static Quaterniond getQuaternion(int x, int y, int z) {
    return new Quaterniond().rotationXYZ(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
  }

  public void setCameraView(View preset) {
    if (renderer instanceof View3d view3d) {
      DicomVolTexture volTexture = view3d.getVolTexture();
      if (volTexture != null) {
        double[] or = volTexture.getVolumeGeometry().getOrientationPatient();
        if (or != null && or.length == 6) {
          Vector3d vr = new Vector3d(or);
          Vector3d vc = new Vector3d(or[3], or[4], or[5]);
          Plan plan = ImageOrientation.getPlan(vr, vc);
          set(preset.position(), preset.rotation(plan), preset.zoom());
          return;
        }
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
        "custom",
        new Vector3d(center).negate(),
        new Quaterniond()
            .setFromNormalized(new Matrix3d(xAxis, yAxis, zAxis).transpose())
            .normalize(),
        -dir.length());
  }

  public Vector3d getPosition() {
    return position;
  }

  public Quaterniond getRotation() {
    return rotation;
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
      this.projectionMatrix = null;
      renderer.display();
    }
  }

  public void set(Vector3d position, Quaterniond rotation, double zoom) {
    set(position, rotation, zoom, true);
  }

  public void set(Vector3d position, Quaterniond rotation, double zoom, boolean repaint) {
    this.position.set(position);
    this.rotation.set(rotation);
    setZoomFactor(zoom, repaint);
  }

  public Vector2d getPrevMousePos() {
    return prevMousePos;
  }

  public Matrix4d getViewMatrix() {
    if (viewMatrix == null) {
      viewMatrix =
          new Matrix4d()
              .setTranslation(new Vector3d(0, 0, internalZoomFactor))
              .rotate(rotation)
              .translate(position);
    }

    return new Matrix4d(viewMatrix);
  }

  public Matrix4d getProjectionMatrix() {
    if (projectionMatrix == null) {
      projectionMatrix = new Matrix4d();
      if (orthographicProjection) {
        double visionSize = renderer.getSurfaceHeight() / 2.0;
        projectionMatrix.setOrthoSymmetric(
            renderer.getAspectRatio() * visionSize, visionSize, zNear, zFar);
      } else {
        projectionMatrix.setPerspective(fov, renderer.getAspectRatio(), zNear, zFar);
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

  public Vector4d getRayOrigin() {
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
    this.internalZoomFactor = -fov * zNear / this.zoomFactor;
    if (repaint) {
      updateCameraTransform();
    } else {
      resetTransformation();
    }
  }

  public void setRotation(int degree) {
    int last = (Integer) renderer.getActionValue(ActionW.ROTATION.cmd());
    renderer.setActionsInView(ActionW.ROTATION.cmd(), degree);
    rotation.rotateZ(Math.toRadians((double) last - degree));
    updateCameraTransform();
  }

  public void resetRotation() {
    renderer.setActionsInView(ActionW.ROTATION.cmd(), 0);
    rotation.set(CameraView.INITIAL.rotation());
    updateCameraTransform();
  }

  public void resetPan() {
    position.set(CameraView.INITIAL.position());
    updateCameraTransform();
  }

  public void resetAll() {
    setCameraView(CameraView.INITIAL);
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

    position.x += invTransform.x;
    position.y += invTransform.y;
    position.z += invTransform.z;
    updateCameraTransform();
  }

  public void rotate(Point2D p) {
    final Vector2i dimensions =
        new Vector2i(renderer.getSurfaceWidth(), renderer.getSurfaceHeight());
    rotation
        .set(
            new Quaterniond(ndcToArcBall(boundlessScreenCoordToNDC(p, dimensions)))
                .mul(ndcToArcBall(boundlessScreenCoordToNDC(prevMousePos, dimensions)))
                .mul(rotation))
        .normalize();

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
    return screenCoordToNDC(new Vector2d(p.getX(), p.getY()));
  }

  private Vector2d boundlessScreenCoordToNDC(Point2D p, Vector2i dimensions) {
    return boundlessScreenCoordToNDC(new Vector2d(p.getX(), p.getY()), dimensions);
  }

  private Vector2d boundlessScreenCoordToNDC(Vector2d mousePos, Vector2i dimensions) {
    final Vector2d mod = new Vector2d(mousePos);
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

  private Vector2d screenCoordToNDC(Vector2d mousePos) {
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
}
