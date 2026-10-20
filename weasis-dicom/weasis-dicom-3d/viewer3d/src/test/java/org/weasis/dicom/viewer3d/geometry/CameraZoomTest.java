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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.joml.Matrix4d;
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
}
