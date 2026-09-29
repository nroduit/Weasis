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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class EnvironmentMapTest {

  @Test
  void noneHasNoBakedData() {
    assertNull(EnvironmentMap.NONE.bake());
    assertSame(EnvironmentMap.STUDIO, EnvironmentMap.fromName("bogus"));
    assertSame(EnvironmentMap.COOL, EnvironmentMap.fromName("COOL"));
  }

  @Test
  void bakedLevelsHalveDownToTheLastMip() {
    EnvironmentMap.Baked baked = EnvironmentMap.OVERCAST.bake();
    assertNotNull(baked);
    assertEquals(EnvironmentMap.LEVELS, baked.levels().size());
    for (int level = 0; level < EnvironmentMap.LEVELS; level++) {
      int w = EnvironmentMap.BASE_WIDTH >> level;
      assertEquals(w * (w / 2) * 3, baked.levels().get(level).length, "level " + level);
    }
    assertSame(baked, EnvironmentMap.OVERCAST.bake(), "baked once, then cached");
  }

  @Test
  void texelDirectionsAreUnitAndCoverTheSphere() {
    double omega = 0;
    for (int y = 0; y < 16; y++) {
      omega += EnvironmentMap.solidAngle(y, 32, 16) * 32;
      for (int x = 0; x < 32; x++) {
        assertEquals(1.0, EnvironmentMap.direction(x, y, 32, 16).length(), 1e-6);
      }
    }
    assertEquals(4 * Math.PI, omega, 0.05);
    Vector3f up = EnvironmentMap.direction(0, 0, 32, 16);
    assertTrue(up.y > 0.99, "row 0 is the zenith");
  }

  @Test
  void overcastIrradianceIsBrighterFromAbove() {
    // Overcast has no lights: the DC term must equal the sky's mean radiance over π-normalized SH,
    // and the vertical band must point up (brighter zenith than ground).
    float[] sh = EnvironmentMap.OVERCAST.bake().irradianceSh();
    assertEquals(27, sh.length);
    float dc = sh[0] * 0.886227f; // c4 * L00 is the irradiance of the flat part
    assertAll(
        () -> assertTrue(dc > 0.4f && dc < 0.9f, "mean radiance in the sky range: " + dc),
        () -> assertTrue(sh[1 * 3] > 0f, "L1-1 (y band) points to the brighter zenith"),
        () -> assertEquals(sh[0], sh[1], 1e-6f, "gray environment is achromatic"));
  }
}
