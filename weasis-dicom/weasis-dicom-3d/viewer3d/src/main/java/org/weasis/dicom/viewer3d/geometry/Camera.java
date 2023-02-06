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

import java.awt.event.MouseEvent;
import java.util.Objects;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.dicom.viewer3d.vr.DicomVolTexture;
import org.weasis.dicom.viewer3d.vr.View3d;
import org.weasis.dicom.viewer3d.vr.VolumeCanvas;

public class Camera {
  static final float INITIAL_FOV = 45f;
  static final float DEFAULT_ZOOM = -4f;
  static final Vector3f POSITION_ZERO = new Vector3f();

  private final VolumeCanvas renderer;
  private boolean isAdjusting = false;
  private final Vector3f position = new Vector3f();
  private final Quaternionf rotation = new Quaternionf();
  private float zoomFactor = -1;
  private final Vector2f prevMousePos = new Vector2f();

  // Perspective
  float zNear = .1f;
  float zFar = 1000f;
  float fov = INITIAL_FOV;

  // Matrix
  public static final Matrix4f currentModelMatrix =
      new Matrix4f()
          .rotateX((float) Math.toRadians(90))
          .translate(new Vector3f(-0.5f, -0.5f, -0.5f));
  Matrix4f viewMatrix = null;
  Matrix4f projectionMatrix = null;
  Matrix4f viewProjectionMatrix = null;

  public Camera(VolumeCanvas renderer) {
    this(renderer, CameraView.INITIAL);
  }

  public Camera(VolumeCanvas renderer, View preset) {
    this.renderer = Objects.requireNonNull(renderer);
    setCameraView(preset);
  }

  public void setCameraView(View preset) {
    if (renderer instanceof View3d view3d) {
      DicomVolTexture volTexture = view3d.getVolTexture();
      if (volTexture != null) {
        //        double[] or = volTexture.getVolumeGeometry().getOrientationPatient();
        //        if (or != null && or.length == 6) {
        //          Vector3d vr = new Vector3d(or);
        //          Vector3d vc = new Vector3d(or[3], or[4], or[5]);
        //          Quaterniond quaternionCol = getRotationFromTo(vr, new Vector3d(1.0, 0.0, 0.0),
        // vc);
        //          quaternionCol =
        //              getRotationFromTo(vc, new Vector3d(0.0, 1.0, 0.0), vr)
        //                  .mul(quaternionCol)
        //                  .mul(preset.rotation());
        //          set(preset.position(), quaternionCol, preset.zoom());
        //          return;
        //        }
      }
    }
    set(preset.position(), preset.rotation(), preset.zoom());
  }

  public Camera(VolumeCanvas renderer, Vector3f eye, Vector3f viewCenter, Vector3f upDir) {
    this(renderer, presetFromLookAt(eye, viewCenter, upDir));
  }

  public static View presetFromLookAt(Vector3f eye, Vector3f center, Vector3f up) {
    if (up.y() == 1 && eye.get(1) == 0) {
      eye.y += 1.0e-10f;
    }
    final Vector3f dir = new Vector3f(center).sub(eye);
    final Vector3f zAxis = new Vector3f(dir).normalize();

    final Vector3f xAxis = new Vector3f(zAxis).cross(new Vector3f(up).normalize()).normalize();
    final Vector3f yAxis = new Vector3f(xAxis).cross(zAxis).normalize();
    xAxis.set(new Vector3f(zAxis).cross(yAxis)).normalize();
    return new ViewData(
        "custom",
        new Vector3f(center).negate(),
        new Quaterniond()
            .setFromNormalized(new Matrix3f(xAxis, yAxis, zAxis).transpose())
            .normalize(),
        -dir.length());
  }

  public Vector3f getPosition() {
    return position;
  }

  public Quaternionf getRotation() {
    return rotation;
  }

  public float getZoomFactor() {
    return zoomFactor;
  }

  public void set(Vector3f position, Quaterniond rotation, float zoom) {
    this.rotation.set(rotation);
    this.zoomFactor = zoom;
    this.position.set(position);
    updateCameraTransform();
  }

  private void setFromPreset(View preset) {
    set(preset.position(), preset.rotation(), preset.zoom());
  }

  public Vector2f getPrevMousePos() {
    return prevMousePos;
  }

  public Matrix4f getViewMatrix() {
    if (viewMatrix == null) {
      viewMatrix =
          new Matrix4f()
              .setTranslation(new Vector3f(0, 0, zoomFactor))
              .rotate(rotation)
              .translate(position);
    }

    return new Matrix4f(viewMatrix);
  }

  public Matrix4f getProjectionMatrix() {
    if (projectionMatrix == null) {
      projectionMatrix = new Matrix4f().setPerspective(fov, renderer.getAspectRatio(), zNear, zFar);
    }
    return new Matrix4f(projectionMatrix);
  }

  public Matrix4f getViewProjectionMatrix() {
    if (viewProjectionMatrix == null) {
      viewProjectionMatrix = new Matrix4f(getProjectionMatrix()).mul(getViewMatrix());
    }
    return new Matrix4f(viewProjectionMatrix);
  }

  public Vector4f getRayOrigin() {
    return new Vector4f(0f, 0f, 0f, 1f).mul(getViewMatrix().invert());
  }

  public boolean isAdjusting() {
    return isAdjusting;
  }

  public void setAdjusting(boolean adjusting) {
    isAdjusting = adjusting;
  }

  public float getFieldOfView() {
    return fov;
  }

  public float getFocalLength() {
    return 1.0f / tan(toRadians(getFieldOfView()) / 2f);
  }

  public void init(MouseEvent e) {
    prevMousePos.x = e.getX();
    prevMousePos.y = e.getY();
    updateCameraTransform();
  }

  protected void zoom(float delta) {
    if (zoomFactor + delta >= 0) {
      return;
    }
    setZoomFactor(zoomFactor + delta);
  }

  public void setZoomFactor(float zoomFactor) {
    renderer.setActionsInView(ActionW.ZOOM.cmd(), (double) zoomFactor);
    this.zoomFactor = zoomFactor;
    updateCameraTransform();
  }

  public void setRotation(int degree) {
    int last = (Integer) renderer.getActionValue(ActionW.ROTATION.cmd());
    renderer.setActionsInView(ActionW.ROTATION.cmd(), degree);
    rotation.rotateZ((float) Math.toRadians(last - degree));
    updateCameraTransform();
  }

  public void translate(MouseEvent e) {
    final Vector2f translationNDC =
        new Vector2f(screenCoordToNDC(e)).sub(screenCoordToNDC(prevMousePos));
    prevMousePos.x = e.getX();
    prevMousePos.y = e.getY();
    final float hh = abs(zoomFactor) * tan(getFieldOfView() * 0.5f);
    final float hw = hh * renderer.getAspectRatio();

    final Vector4f invTransform =
        new Matrix4f(getViewMatrix())
            .invert()
            .transform(new Vector4f(translationNDC.x() * hw, translationNDC.y() * hh, 0.0f, 0));

    position.x += invTransform.x;
    position.y += invTransform.y;
    position.z += invTransform.z;
    updateCameraTransform();
  }

  public void rotate(MouseEvent e) {
    final Vector2i dimensions =
        new Vector2i(renderer.getSurfaceWidth(), renderer.getSurfaceHeight());
    rotation
        .set(
            new Quaternionf(ndcToArcBall(boundlessScreenCoordToNDC(e, dimensions)))
                .mul(ndcToArcBall(boundlessScreenCoordToNDC(prevMousePos, dimensions)))
                .mul(rotation))
        .normalize();

    prevMousePos.x = e.getX();
    prevMousePos.y = e.getY();
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

  private Vector2f screenCoordToNDC(MouseEvent e) {
    return screenCoordToNDC(new Vector2f(e.getX(), e.getY()));
  }

  private Vector2f boundlessScreenCoordToNDC(MouseEvent e, Vector2i dimensions) {
    return boundlessScreenCoordToNDC(new Vector2f(e.getX(), e.getY()), dimensions);
  }

  private Vector2f boundlessScreenCoordToNDC(Vector2f mousePos, Vector2i dimensions) {
    final Vector2f mod = new Vector2f(mousePos);
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

  private Vector2f screenCoordToNDC(Vector2f mousePos) {
    return new Vector2f(
        mousePos.x() * 2.0f / renderer.getSurfaceWidth() - 1.0f,
        1.0f - 2.0f * mousePos.y() / renderer.getSurfaceHeight());
  }

  public static Quaternionf ndcToArcBall(Vector2f p) {
    final float dist = p.dot(p);
    if (dist <= 1.0f) {
      // Inside the sphere
      return new Quaternionf(p.x(), p.y(), sqrt(1.0f - dist), 0.0f);
    } else {
      // Outside the sphere
      final Vector2f proj = new Vector2f(p).normalize();
      return new Quaternionf(proj.x(), proj.y(), 0.0f, 0.0f);
    }
  }
}
