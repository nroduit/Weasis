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
import org.joml.Quaterniondc;
import org.joml.Vector2d;
import org.joml.Vector2dc;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector4d;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.vr.DicomVolTexture;
import org.weasis.dicom.viewer3d.vr.View3d;
import org.weasis.dicom.viewer3d.vr.VolumeCanvas;

public class Camera {
  public static final String P_DEFAULT_ORIENTATION = "default.vr.orientation";
  public static final double SCALE_MAX = 6.0;
  public static final double SCALE_MIN = 1.0 / SCALE_MAX;
  // Perspective
  static final double FIELD_OF_VIEW = 45;
  static final double Z_NEAR = 0.1;
  static final double Z_FAR = 10000;
  static final double DEFAULT_ZOOM = 1;
  // The orthographic camera stands this many times farther than the perspective one
  static final double ORTHOGRAPHIC_DISTANCE_FACTOR = 3.5;
  // Tangent of half the vertical field of view: the projection takes FIELD_OF_VIEW in radians
  static final double TAN_HALF_FOV = Math.tan(FIELD_OF_VIEW * 0.5);

  /**
   * Height of the view, in logical pixels, at which the perspective camera sees with a field of
   * view of {@link #FIELD_OF_VIEW} (radians, as the projection reads it). The camera moves back as
   * the view grows taller, so that a zoom factor gives the same number of pixels per millimeter
   * whatever the size of the view: resizing the window shows more or less around the volume instead
   * of scaling it, as in the 2D views. The value makes the orthographic projection, sized with the
   * view height, match the perspective one.
   */
  static final double REFERENCE_HEIGHT =
      (Z_FAR + 1.0) * TAN_HALF_FOV / ORTHOGRAPHIC_DISTANCE_FACTOR;

  static final Vector3dc POSITION_ZERO = new Vector3d();

  private final VolumeCanvas renderer;
  private boolean isAdjusting = false;
  private final Vector3d position = new Vector3d();
  private final Quaterniond rotation = new Quaterniond();

  private final Vector3d prevMousePos = new Vector3d();

  // Zoom
  private double zoomFactor = DEFAULT_ZOOM;

  // Matrix
  public static final Matrix4d currentModelMatrix =
      new Matrix4d().rotateX(Math.toRadians(90)).translate(new Vector3d(-0.5, -0.5, -0.5));
  Matrix4d viewMatrix = null;
  Matrix4d projectionMatrix = null;
  Matrix4d viewProjectionMatrix = null;
  private boolean orthographicProjection;
  private final AxisTwist twist = new AxisTwist();

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
    return position;
  }

  public Quaterniond getRotation() {
    return rotation;
  }

  public double getZoomFactor() {
    return zoomFactor;
  }

  public double getzNear() {
    return Z_NEAR;
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

  public void set(Vector3dc position, Quaterniondc rotation, double zoom) {
    set(position, rotation, zoom, true);
  }

  public void set(Vector3dc position, Quaterniondc rotation, double zoom, boolean repaint) {
    this.position.set(position);
    this.rotation.set(rotation);
    twist.sync(this.rotation);
    updateRotationAction();

    setZoomFactor(zoom, repaint);
  }

  public void updateRotationAction() {
    EventManager.getInstance()
        .getAction(ActionW.ROTATION)
        .ifPresent(s -> s.setSliderValue(getCurrentAxisRotationInDegrees(), false));
  }

  public Matrix4d getViewMatrix() {
    if (viewMatrix == null) {
      viewMatrix =
          new Matrix4d()
              .setTranslation(new Vector3d(0, 0, -getDistance()))
              .rotate(rotation)
              .translate(position);
    }
    return new Matrix4d(viewMatrix);
  }

  public Matrix4d getProjectionMatrix() {
    if (projectionMatrix == null) {
      projectionMatrix = new Matrix4d();
      if (orthographicProjection) {
        double size = getViewHeight();
        projectionMatrix.setOrthoSymmetric(renderer.getAspectRatio() * size, size, Z_FAR, 1);
      } else {
        projectionMatrix.setPerspective(FIELD_OF_VIEW, renderer.getAspectRatio(), Z_NEAR, Z_FAR);
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

  /** Height of the view in logical pixels, the reference height while it is not laid out. */
  double getViewHeight() {
    int height = renderer.getHeight();
    return height > 0 ? height : REFERENCE_HEIGHT;
  }

  /**
   * Distance from the camera to the center of rotation. The perspective camera moves back with the
   * height of the view and the orthographic one, whose rays widen with that height, does not: both
   * keep the scale of the zoom factor when the view is resized.
   */
  double getDistance() {
    return getDistance(zoomFactor);
  }

  private double getDistance(double zoom) {
    double distance = FIELD_OF_VIEW * Z_NEAR / zoom;
    if (orthographicProjection) {
      return distance * ORTHOGRAPHIC_DISTANCE_FACTOR;
    }
    return distance * getViewHeight() / REFERENCE_HEIGHT;
  }

  /**
   * Tangent of the half vertical angle of the rays: fixed for the perspective projection, growing
   * with the view height for the orthographic one (see {@link #getProjectionMatrix()}).
   */
  private double getTanHalfAngle() {
    return orthographicProjection ? getViewHeight() / (Z_FAR + 1.0) : TAN_HALF_FOV;
  }

  /**
   * The largest zoom factor at which the volume, a box centered on the origin, fills the view as
   * much as allowed, seen from {@code position} and {@code rotation}. The content of a volume is
   * rounded and its corners are mostly air, so the fit is made on the ellipsoid inscribed in the
   * box, which must cover at most {@code coverage} of the view, while the corners of the box may
   * reach {@code cornerLimit} of it (beyond the edge when greater than one). Both are projected as
   * the renderer casts its rays: the perspective (what is near looks larger) and the aspect ratio
   * of the view are taken into account.
   *
   * @param halfExtent half of the box size along each world axis
   * @param position the camera position (the opposite of the center of rotation)
   * @param rotation the camera rotation
   * @param coverage the part of the view the ellipsoid may cover, from 0 to 1
   * @param cornerLimit the part of the view the corners of the box may reach
   * @return the zoom factor, between {@link #SCALE_MIN} and {@link #SCALE_MAX}
   */
  public double getFitZoom(
      Vector3dc halfExtent,
      Vector3dc position,
      Quaterniondc rotation,
      double coverage,
      double cornerLimit) {
    double aspect = renderer.getAspectRatio();
    if (!(aspect > 0.0) || Double.isInfinite(aspect)) {
      aspect = 1.0;
    }
    FitShape shape = new FitShape(halfExtent, position, rotation);
    double ratio = aspect;
    java.util.function.DoublePredicate fits =
        zoom ->
            shape.cornersFit(getDistance(zoom), getTanHalfAngle() * cornerLimit, ratio)
                && shape.ellipsoidFits(getDistance(zoom), getTanHalfAngle() * coverage, ratio);
    if (fits.test(SCALE_MAX)) {
      return SCALE_MAX;
    }
    if (!fits.test(SCALE_MIN)) {
      return SCALE_MIN;
    }
    // The volume only grows on the screen with the zoom: bisection in log space
    double low = java.lang.Math.log(SCALE_MIN);
    double high = java.lang.Math.log(SCALE_MAX);
    for (int i = 0; i < 40; i++) {
      double mid = (low + high) * 0.5;
      if (fits.test(java.lang.Math.exp(mid))) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return java.lang.Math.exp(low);
  }

  /**
   * The box of the volume and its inscribed ellipsoid in view axes, before the camera distance is
   * applied along z (the camera looks towards -z).
   */
  private static final class FitShape {
    private final Vector3d[] corners = new Vector3d[8];
    private final Vector3d center;
    // Rows of the ellipsoid matrix in view axes: a point is center + A u with |u| <= 1
    private final Vector3d rowX;
    private final Vector3d rowY;
    private final Vector3d rowZ;

    FitShape(Vector3dc halfExtent, Vector3dc position, Quaterniondc rotation) {
      for (int i = 0; i < corners.length; i++) {
        Vector3d c =
            new Vector3d(
                (i & 1) == 0 ? -halfExtent.x() : halfExtent.x(),
                (i & 2) == 0 ? -halfExtent.y() : halfExtent.y(),
                (i & 4) == 0 ? -halfExtent.z() : halfExtent.z());
        corners[i] = rotation.transform(c.add(position));
      }
      center = rotation.transform(new Vector3d(position));
      Vector3d ax = rotation.transform(new Vector3d(halfExtent.x(), 0, 0));
      Vector3d ay = rotation.transform(new Vector3d(0, halfExtent.y(), 0));
      Vector3d az = rotation.transform(new Vector3d(0, 0, halfExtent.z()));
      rowX = new Vector3d(ax.x, ay.x, az.x);
      rowY = new Vector3d(ax.y, ay.y, az.y);
      rowZ = new Vector3d(ax.z, ay.z, az.z);
    }

    boolean cornersFit(double distance, double tan, double aspect) {
      for (Vector3d c : corners) {
        double depth = distance - c.z;
        if (depth <= Z_NEAR) {
          return false;
        }
        if (abs(c.y) > depth * tan || abs(c.x) > depth * tan * aspect) {
          return false;
        }
      }
      return true;
    }

    boolean ellipsoidFits(double distance, double tan, double aspect) {
      double cz = center.z - distance;
      return silhouette(center.y, rowY, cz) <= tan
          && silhouette(center.x, rowX, cz) <= tan * aspect;
    }

    /**
     * Largest tangent r / -z reached by the ellipsoid along one screen axis: the planes r + k z = 0
     * through the camera tangent to it satisfy (cr + k cz)^2 = |Ar + k Az|^2.
     */
    private double silhouette(double cr, Vector3d row, double cz) {
      double a = cz * cz - rowZ.lengthSquared();
      if (a <= 0.0 || cz >= 0.0) {
        // The camera is inside the ellipsoid slab, or behind it
        return Double.POSITIVE_INFINITY;
      }
      double b = 2.0 * (cr * cz - row.dot(rowZ));
      double c = cr * cr - row.lengthSquared();
      double disc = b * b - 4.0 * a * c;
      if (disc < 0.0) {
        return Double.POSITIVE_INFINITY;
      }
      double sq = sqrt(disc);
      return java.lang.Math.max(abs((-b + sq) / (2.0 * a)), abs((-b - sq) / (2.0 * a)));
    }
  }

  /**
   * Half of the height seen on the plane through the center of rotation, in world units: the same
   * for both projections, proportional to the view height and inverse to the zoom factor.
   */
  double getHalfHeightOnCenterPlane() {
    return FIELD_OF_VIEW * Z_NEAR / zoomFactor * TAN_HALF_FOV * getViewHeight() / REFERENCE_HEIGHT;
  }

  /**
   * Logical pixels per world unit on the plane through the center of rotation at a zoom factor of
   * one. The world box of the volume spans twice its normalized texel size.
   */
  public static double getPixelsPerUnit() {
    return REFERENCE_HEIGHT / (2.0 * FIELD_OF_VIEW * Z_NEAR * TAN_HALF_FOV);
  }

  public double getFieldOfView() {
    return FIELD_OF_VIEW;
  }

  public double getFocalLength() {
    return 1.0 / tan(toRadians(getFieldOfView()) / 2.0);
  }

  public void init(Point2D p) {
    prevMousePos.x = p.getX();
    prevMousePos.y = p.getY();
    updateCameraTransform();
  }

  /**
   * Anchor the next pan/rotate delta at {@code p} without triggering a render. Used when a sync
   * event needs to align a target view's reference point before the dragging stream starts.
   */
  public void anchorMouse(Point2D p) {
    prevMousePos.x = p.getX();
    prevMousePos.y = p.getY();
  }

  public void setZoomFactor(double zoomFactor) {
    setZoomFactor(zoomFactor, true);
  }

  public void setZoomFactor(double zoomFactor, boolean repaint) {
    renderer.setActionsInView(ActionW.ZOOM.cmd(), zoomFactor);
    this.zoomFactor = Math.abs(zoomFactor);

    if (repaint) {
      updateCameraTransform();
    } else {
      resetTransformation();
    }
  }

  /** Sets the twist about the rotation axis, in the volume frame like the rotation export. */
  public void setRotation(int degree) {
    twist.apply(rotation, degree);
    updateCameraTransform();
  }

  /** Adopts an orientation coming from another view, without touching the shared slider. */
  public void setRotation(Quaterniondc rotation) {
    this.rotation.set(rotation);
    twist.sync(this.rotation);
    updateCameraTransform();
  }

  public void resetRotation() {
    rotation.set(getDefaultOrientation().rotation());
    twist.sync(rotation);
    updateRotationAction();
    updateCameraTransform();
  }

  public void resetPan() {
    position.set(getDefaultOrientation().position());
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
    moveInView(translationNDC);
    updateCameraTransform();
  }

  /** Moves the volume in the view plane by a shift in normalized device coordinates. */
  private void moveInView(Vector2dc ndc) {
    double hh = getHalfHeightOnCenterPlane();
    double hw = hh * renderer.getAspectRatio();

    Vector4d invTransform =
        new Matrix4d(getViewMatrix())
            .invert()
            .transform(new Vector4d(ndc.x() * hw, ndc.y() * hh, 0.0, 0));

    position.x += invTransform.x;
    position.y += invTransform.y;
    position.z += invTransform.z;
    resetTransformation();
  }

  /**
   * Zooms keeping in place the point shown at {@code ndc}, taken on the plane through the center of
   * rotation. After the zoom, the volume moves in the view plane until that point is back on the
   * ray the renderer casts through {@code ndc} (see {@link #rayInView}): exact for the perspective
   * and the orthographic projections, at the depth of the center of rotation.
   *
   * @param ndc the anchor in normalized device coordinates (x right, y up, from -1 to 1)
   * @param zoomFactor the new zoom factor
   */
  public void zoomAt(Vector2dc ndc, double zoomFactor) {
    Vector3d anchor = unprojectOnCenterPlane(ndc);
    setZoomFactor(zoomFactor, false);
    if (anchor != null) {
      Vector4d v = getViewMatrix().transform(new Vector4d(anchor, 1.0));
      Vector3d dir = rayInView(ndc);
      if (Math.abs(dir.z) > 1e-12) {
        // Same depth on the ray: a move in the view plane does not change the depth
        Vector3d target = dir.mul(v.z / dir.z);
        Vector4d shift =
            getViewMatrix()
                .invert()
                .transform(new Vector4d(target.x - v.x, target.y - v.y, 0.0, 0.0));
        position.add(shift.x, shift.y, shift.z);
      }
    }
    updateCameraTransform();
  }

  /**
   * Direction, in view space, of the ray the renderer casts through {@code ndc}: the inverse
   * projection of (x, y, 0, 1) without the division by w, as {@code CreateCameraRay} does. The rays
   * start at the camera, for the perspective and the orthographic projections alike.
   */
  Vector3d rayInView(Vector2dc ndc) {
    Vector4d d = getProjectionMatrix().invert().transform(new Vector4d(ndc.x(), ndc.y(), 0.0, 1.0));
    return new Vector3d(d.x, d.y, d.z);
  }

  /** The point of the plane through the center of rotation that the ray through ndc reaches. */
  Vector3d unprojectOnCenterPlane(Vector2dc ndc) {
    Vector3d dir = rayInView(ndc);
    // The view matrix maps -position to the center of rotation, on the axis of the view
    Vector4d center =
        getViewMatrix().transform(new Vector4d(-position.x, -position.y, -position.z, 1.0));
    if (Math.abs(dir.z) < 1e-12) {
      return null;
    }
    double t = center.z / dir.z;
    if (t <= 0.0) {
      return null;
    }
    Vector4d world = getViewMatrix().invert().transform(new Vector4d(dir.mul(t), 1.0));
    return new Vector3d(world.x, world.y, world.z);
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
    twist.sync(rotation);
    updateRotationAction();

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

  public Axis getRotationAxis() {
    return twist.axis();
  }

  public void setRotationAxis(Axis rotationAxis) {
    twist.setAxis(rotationAxis, rotation);
  }

  /** Twist of the orientation about the rotation axis, in radians within [-π, π]. */
  public double getCurrentAxisRotation() {
    return twist.angle();
  }

  public int getCurrentAxisRotationInDegrees() {
    return twist.degrees();
  }
}
