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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.vr.VolumeCanvas;

/** Zoom around a point of the view: the volume point under it stays under it. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CameraZoomTest {

  /**
   * Distance from a world point to the ray the renderer casts through uv, written as in {@code
   * CreateCameraRay} of vrFunctions.glsl (the inverses of the view and projection matrices).
   */
  private static double distanceToRay(Camera camera, Vector2d uv, Vector3d world) {
    Matrix4d cameraToWorld = camera.getViewMatrix().invert();
    Matrix4d inverseProjection = camera.getProjectionMatrix().invert();
    Vector4d o = cameraToWorld.transform(new Vector4d(0, 0, 0, 1));
    Vector4d d = inverseProjection.transform(new Vector4d(uv.x, uv.y, 0, 1));
    Vector4d dw = cameraToWorld.transform(new Vector4d(d.x, d.y, d.z, 0));
    Vector3d dir = new Vector3d(dw.x, dw.y, dw.z).normalize();
    Vector3d toPoint = new Vector3d(world).sub(o.x, o.y, o.z);
    return new Vector3d(toPoint).sub(new Vector3d(dir).mul(toPoint.dot(dir))).length();
  }

  @ParameterizedTest
  @CsvSource({
    // ndc x, ndc y, zoom before, zoom after, orthographic
    "0.5, 0.25, 1.0, 2.0, false",
    "-0.8, 0.6, 1.0, 0.5, false",
    "0.3, -0.9, 2.5, 6.0, false",
    "0, 0, 1.0, 3.0, false",
    "0.5, 0.25, 1.0, 2.0, true",
    "-0.7, -0.4, 3.0, 1.2, true",
  })
  void volume_point_under_the_anchor_stays_under_it(
      double x, double y, double from, double to, boolean orthographic) {
    VolumeCanvas renderer = mock(VolumeCanvas.class);
    when(renderer.getAspectRatio()).thenReturn(1.6);
    when(renderer.getSurfaceWidth()).thenReturn(800);
    when(renderer.getSurfaceHeight()).thenReturn(500);
    try (MockedStatic<EventManager> em = mockStatic(EventManager.class)) {
      em.when(EventManager::getInstance).thenReturn(mock(EventManager.class));
      Camera camera = new Camera(renderer, CameraView.INITIAL);
      camera.setOrthographicProjection(orthographic);
      camera.setZoomFactor(from, false);
      Vector2d anchor = new Vector2d(x, y);
      Vector3d world = camera.unprojectOnCenterPlane(anchor);
      assertNotNull(world);
      assertEquals(0.0, distanceToRay(camera, anchor, world), 1e-9, "on the ray before");

      camera.zoomAt(anchor, to);

      assertEquals(to, camera.getZoomFactor(), 1e-12);
      double scale = world.length() + 1.0;
      assertEquals(0.0, distanceToRay(camera, anchor, world) / scale, 1e-7, "on the ray after");
    }
  }

  @ParameterizedTest
  @CsvSource({
    // view height, zoom, orthographic
    "400, 1.0, false",
    "1500, 1.0, false",
    "700, 2.5, false",
    "400, 1.0, true",
    "1500, 1.0, true",
    "700, 2.5, true",
  })
  void zoom_factor_is_a_pixel_scale_whatever_the_view_height(
      int height, double zoom, boolean orthographic) {
    VolumeCanvas renderer = mock(VolumeCanvas.class);
    when(renderer.getAspectRatio()).thenReturn(1.6);
    when(renderer.getHeight()).thenReturn(height);
    when(renderer.getSurfaceHeight()).thenReturn(height);
    try (MockedStatic<EventManager> em = mockStatic(EventManager.class)) {
      em.when(EventManager::getInstance).thenReturn(mock(EventManager.class));
      Camera camera = new Camera(renderer, CameraView.INITIAL);
      camera.setOrthographicProjection(orthographic);
      camera.setZoomFactor(zoom, false);
      Vector3d center = camera.unprojectOnCenterPlane(new Vector2d(0, 0));
      Vector3d top = camera.unprojectOnCenterPlane(new Vector2d(0, 1));
      assertNotNull(center);
      assertNotNull(top);
      // From the center to the top edge: half of the view height in pixels
      double pixelsPerUnit = height / 2.0 / top.distance(center);
      assertEquals(zoom * Camera.getPixelsPerUnit(), pixelsPerUnit, 1e-6 * pixelsPerUnit);
      assertEquals(top.distance(center), camera.getHalfHeightOnCenterPlane(), 1e-9);
    }
  }

  @ParameterizedTest
  @CsvSource({
    // half extent x, y, z, rotation about x and y in degrees, width, height, orthographic
    "1.0, 1.0, 1.0, 0, 0, 800, 600, false",
    "0.4, 1.0, 0.7, 90, 0, 400, 900, false",
    "1.0, 0.6, 0.3, 30, 45, 1200, 500, false",
    "1.0, 0.2, 0.2, 0, 0, 900, 900, false",
    "1.0, 1.0, 1.0, 0, 0, 800, 600, true",
    "0.4, 1.0, 0.7, 30, 45, 400, 900, true",
  })
  void best_fit_brings_the_content_or_the_corners_to_their_limit(
      double hx,
      double hy,
      double hz,
      double ax,
      double ay,
      int width,
      int height,
      boolean orthographic) {
    VolumeCanvas renderer = mock(VolumeCanvas.class);
    when(renderer.getAspectRatio()).thenReturn(width / (double) height);
    when(renderer.getWidth()).thenReturn(width);
    when(renderer.getHeight()).thenReturn(height);
    when(renderer.getSurfaceHeight()).thenReturn(height);
    try (MockedStatic<EventManager> em = mockStatic(EventManager.class)) {
      em.when(EventManager::getInstance).thenReturn(mock(EventManager.class));
      Camera camera = new Camera(renderer, CameraView.INITIAL);
      camera.setOrthographicProjection(orthographic);
      Quaterniond rotation =
          new Quaterniond().rotateX(Math.toRadians(ax)).rotateY(Math.toRadians(ay));
      double coverage = 0.92;
      double cornerLimit = 1.5;

      double zoom =
          camera.getFitZoom(
              new Vector3d(hx, hy, hz), new Vector3d(), rotation, coverage, cornerLimit);
      camera.set(new Vector3d(), rotation, zoom, false);

      // The extent of the view along x and y, from the rays the renderer casts
      Vector3d up = camera.rayInView(new Vector2d(0, 1));
      Vector3d right = camera.rayInView(new Vector2d(1, 0));
      double tanY = up.y / -up.z;
      double tanX = right.x / -right.z;
      Matrix4d view = camera.getViewMatrix();
      double corners = 0.0;
      for (int i = 0; i < 8; i++) {
        corners =
            Math.max(
                corners,
                reach(
                    view,
                    (i & 1) == 0 ? -hx : hx,
                    (i & 2) == 0 ? -hy : hy,
                    (i & 4) == 0 ? -hz : hz,
                    tanX,
                    tanY));
      }
      double ellipsoid = 0.0;
      int n = 400;
      for (int i = 0; i <= n; i++) {
        double theta = Math.PI * i / n;
        for (int j = 0; j < 2 * n; j++) {
          double phi = Math.PI * j / n;
          ellipsoid =
              Math.max(
                  ellipsoid,
                  reach(
                      view,
                      hx * Math.sin(theta) * Math.cos(phi),
                      hy * Math.sin(theta) * Math.sin(phi),
                      hz * Math.cos(theta),
                      tanX,
                      tanY));
        }
      }
      assertTrue(corners <= cornerLimit + 1e-6, "corners " + corners);
      assertTrue(ellipsoid <= coverage + 1e-6, "content " + ellipsoid);
      assertTrue(
          Math.abs(corners - cornerLimit) < 1e-6 || Math.abs(ellipsoid - coverage) < 1e-3,
          "one limit is reached: corners " + corners + ", content " + ellipsoid);
    }
  }

  /** Part of the view a world point reaches along its larger screen axis, through the rays. */
  private static double reach(
      Matrix4d view, double x, double y, double z, double tanX, double tanY) {
    Vector4d v = view.transform(new Vector4d(x, y, z, 1));
    return Math.max(Math.abs(v.y / -v.z) / tanY, Math.abs(v.x / -v.z) / tanX);
  }
}
