/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ShadingOptionsTest {

  private static final float EPS = 1e-6f;

  private static void assertDirection(Vector3f expected, Vector3f actual) {
    assertAll(
        () -> assertEquals(expected.x, actual.x, EPS),
        () -> assertEquals(expected.y, actual.y, EPS),
        () -> assertEquals(expected.z, actual.z, EPS));
  }

  @Test
  void keyLightDirectionIsHeadlightAtZeroAngles() {
    ShadingOptions options = new ShadingOptions(new RenderingLayer<>());
    options.setLightAzimuth(0f);
    options.setLightElevation(0f);
    assertDirection(new Vector3f(0, 0, 1), options.getKeyLightDirection());
  }

  @Test
  void keyLightDirectionFollowsAzimuthAndElevation() {
    ShadingOptions options = new ShadingOptions(new RenderingLayer<>());
    options.setLightElevation(0f);
    options.setLightAzimuth(90f);
    assertDirection(new Vector3f(1, 0, 0), options.getKeyLightDirection());
    options.setLightAzimuth(0f);
    options.setLightElevation(90f);
    assertDirection(new Vector3f(0, 1, 0), options.getKeyLightDirection());
  }

  @Test
  void defaultKeyLightIsUnitLengthFromUpperLeft() {
    Vector3f dir = new ShadingOptions(new RenderingLayer<>()).getKeyLightDirection();
    assertAll(
        () -> assertEquals(1f, dir.length(), EPS),
        () -> assertTrue(dir.x < 0, "from the viewer's left"),
        () -> assertTrue(dir.y > 0, "from above"),
        () -> assertTrue(dir.z > 0, "in front of the volume"));
  }

  @Test
  void cinematicChangesNotifyTheLayerOnce() {
    RenderingLayer<?> layer = new RenderingLayer<>();
    AtomicInteger changes = new AtomicInteger();
    layer.addLayerChangeListener(l -> changes.incrementAndGet());
    ShadingOptions options = layer.getShadingOptions();

    options.setShadowStrength(0.5f);
    options.setShadowStrength(0.5f);
    assertEquals(1, changes.get());

    layer.setCinematic(true);
    layer.setCinematic(true);
    assertAll(() -> assertTrue(layer.isCinematic()), () -> assertEquals(2, changes.get()));
    assertFalse(new RenderingLayer<>().isCinematic());
  }
}
