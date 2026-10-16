/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** The hoisted placement gives, bit for bit, the position the JOML path of the splat gave. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SlicePlacementTest {

  private static final Vector3d RATIO = new Vector3d(0.703125, 0.703125, 2.5);
  private static final int SIZE_Z = 217;

  // The computation of the splat before the placement was hoisted
  private static Vector4d reference(
      Matrix4d transform, int x, int y, int z, boolean basic, boolean axial) {
    Vector4d p = new Vector4d(x, y, 0.0, 1.0);
    if (basic) {
      p.set(x, y, z, 1.0);
    }
    transform.transform(p);
    if (!basic) {
      p.div(new Vector4d(RATIO.x(), RATIO.y(), RATIO.z(), 1.0));
      p.z = axial ? -p.z : SIZE_Z - p.z;
    }
    return p;
  }

  private static Matrix4d randomTransform(Random random, int kind) {
    Matrix4d m = new Matrix4d();
    switch (kind) {
      case 0 -> m.translation(random.nextGaussian() * 200, random.nextGaussian() * 200, 0);
      case 1 ->
          m.rotation(
                  random.nextDouble() * Math.PI,
                  new Vector3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                      .normalize())
              .translate(
                  random.nextGaussian() * 300,
                  random.nextGaussian() * 300,
                  random.nextGaussian() * 300)
              .scale(0.3 + random.nextDouble());
      default -> {
        double[] values = new double[16];
        for (int i = 0; i < 16; i++) {
          values[i] = random.nextGaussian() * 100;
        }
        values[15] = 1;
        m.set(values);
      }
    }
    return m;
  }

  @Test
  void every_kind_of_transform_gives_the_joml_position_exactly() {
    Random random = new Random(20261017);
    for (int trial = 0; trial < 300; trial++) {
      Matrix4d transform = randomTransform(random, trial % 3);
      boolean basic = trial % 5 == 0;
      boolean axial = trial % 2 == 0;
      int z = random.nextInt(SIZE_Z);
      SlicePlacement placement =
          basic
              ? new SlicePlacement(transform, z, null, false, SIZE_Z)
              : new SlicePlacement(transform, 0.0, RATIO, axial, SIZE_Z);
      for (int i = 0; i < 200; i++) {
        int x = random.nextInt(1024);
        int y = random.nextInt(1024);
        Vector4d expected = reference(transform, x, y, z, basic, axial);
        assertEquals(expected.x, placement.x(x, y), "x, trial " + trial);
        assertEquals(expected.y, placement.y(x, y), "y, trial " + trial);
        assertEquals(expected.z, placement.z(x, y), "z, trial " + trial);
      }
    }
  }

  @Test
  void floor_matches_math_floor() {
    double[] values = {
      0,
      -0.0,
      0.5,
      -0.5,
      1,
      -1,
      1e-300,
      -1e-300,
      2.999999,
      -2.999999,
      1e10,
      -1e10,
      Integer.MAX_VALUE + 0.5,
      Integer.MIN_VALUE - 0.5,
      Integer.MIN_VALUE,
      Double.NaN,
      Double.POSITIVE_INFINITY,
      Double.NEGATIVE_INFINITY
    };
    for (double v : values) {
      assertEquals((int) Math.floor(v), SlicePlacement.floor(v), "value " + v);
    }
    Random random = new Random(7);
    for (int i = 0; i < 100_000; i++) {
      double v = (random.nextDouble() - 0.5) * 4096;
      assertEquals((int) Math.floor(v), SlicePlacement.floor(v), "value " + v);
    }
  }

  @Test
  void source_values_are_unsigned_only_for_unsigned_types() {
    var unsignedShort = new VolumeShort(2, 2, 2, false, 1, null);
    var signedShort = new VolumeShort(2, 2, 2, true, 1, null);
    var unsignedByte = new VolumeByte(2, 2, 2, false, 1, null);
    var signedByte = new VolumeByte(2, 2, 2, true, 1, null);
    assertEquals(65535f, unsignedShort.sourceValue(new short[] {-1}, 0));
    assertEquals(-1f, signedShort.sourceValue(new short[] {-1}, 0));
    assertEquals(255f, unsignedByte.sourceValue(new byte[] {-1}, 0));
    assertEquals(-1f, signedByte.sourceValue(new byte[] {-1}, 0));
    assertEquals(
        (float) 123456789.0, new VolumeInt(2, 2, 2, 1, null).sourceValue(new int[] {123456789}, 0));
    assertEquals(
        (float) 0.1, new VolumeDouble(2, 2, 2, 1, null).sourceValue(new double[] {0.1}, 0));
  }
}
