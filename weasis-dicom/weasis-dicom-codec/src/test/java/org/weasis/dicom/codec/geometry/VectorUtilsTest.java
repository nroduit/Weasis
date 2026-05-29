/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.geometry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class VectorUtilsTest {

  private static final double EPS = 1.0e-9;

  private static void assertVectorEquals(Vector3d expected, Vector3d actual) {
    assertAll(
        () -> assertEquals(expected.x, actual.x, EPS, "x"),
        () -> assertEquals(expected.y, actual.y, EPS, "y"),
        () -> assertEquals(expected.z, actual.z, EPS, "z"));
  }

  @Test
  void computeNormalOfSurface_axialRowAndColumn_yieldsHeadDirection() {
    // DICOM LPS+: axial plane has row along +X (L) and column along +Y (P); normal is +Z (Head).
    Vector3d row = new Vector3d(1, 0, 0);
    Vector3d column = new Vector3d(0, 1, 0);

    Vector3d normal = VectorUtils.computeNormalOfSurface(row, column);

    assertVectorEquals(new Vector3d(0, 0, 1), normal);
  }

  @Test
  void computeNormalOfSurface_sagittalRowAndColumn_yieldsLeftDirection() {
    // Sagittal: row along +Y (P), column along +Z (H); cross = (+1, 0, 0) = Left.
    Vector3d row = new Vector3d(0, 1, 0);
    Vector3d column = new Vector3d(0, 0, 1);

    Vector3d normal = VectorUtils.computeNormalOfSurface(row, column);

    assertVectorEquals(new Vector3d(1, 0, 0), normal);
  }

  @Test
  void computeNormalOfSurface_coronalRowAndColumn_yieldsAnteriorDirection() {
    // Coronal: row along +X (L), column along +Z (H); cross = (0, -1, 0) = Anterior.
    Vector3d row = new Vector3d(1, 0, 0);
    Vector3d column = new Vector3d(0, 0, 1);

    Vector3d normal = VectorUtils.computeNormalOfSurface(row, column);

    assertVectorEquals(new Vector3d(0, -1, 0), normal);
  }

  @Test
  void computeNormalOfSurface_normalIsUnitLength() {
    Vector3d row = new Vector3d(0.6, 0.8, 0).normalize();
    Vector3d column = new Vector3d(-0.8, 0.6, 0).normalize();

    Vector3d normal = VectorUtils.computeNormalOfSurface(row, column);

    assertEquals(1.0, normal.length(), EPS);
  }

  @Test
  void computeNormalOfSurface_parallelVectorsReturnZeroVector() {
    Vector3d row = new Vector3d(1, 0, 0);
    Vector3d column = new Vector3d(2, 0, 0); // parallel

    Vector3d normal = VectorUtils.computeNormalOfSurface(row, column);

    assertVectorEquals(new Vector3d(0, 0, 0), normal);
  }

  @Test
  void computeNormalOfSurface_threePointOverload_matchesTwoPointOverloadWhenOriginAtZero() {
    Vector3d origin = new Vector3d(0, 0, 0);
    Vector3d v1 = new Vector3d(1, 0, 0);
    Vector3d v2 = new Vector3d(0, 1, 0);

    Vector3d expected = VectorUtils.computeNormalOfSurface(v1, v2);
    Vector3d actual = VectorUtils.computeNormalOfSurface(origin, v1, v2);

    assertVectorEquals(expected, actual);
  }

  @Test
  void computeNormalOfSurface_threePointOverload_handlesNonZeroOrigin() {
    Vector3d origin = new Vector3d(10, 20, 30);
    Vector3d p1 = new Vector3d(11, 20, 30); // origin + (1,0,0)
    Vector3d p2 = new Vector3d(10, 21, 30); // origin + (0,1,0)

    Vector3d normal = VectorUtils.computeNormalOfSurface(origin, p1, p2);

    assertVectorEquals(new Vector3d(0, 0, 1), normal);
  }

  @Test
  void computeNormalOfSurface_threePointOverload_doesNotMutateInputs() {
    Vector3d origin = new Vector3d(1, 2, 3);
    Vector3d v1 = new Vector3d(4, 5, 6);
    Vector3d v2 = new Vector3d(7, 8, 9);
    Vector3d originCopy = new Vector3d(origin);
    Vector3d v1Copy = new Vector3d(v1);
    Vector3d v2Copy = new Vector3d(v2);

    VectorUtils.computeNormalOfSurface(origin, v1, v2);

    assertAll(
        () -> assertVectorEquals(originCopy, origin),
        () -> assertVectorEquals(v1Copy, v1),
        () -> assertVectorEquals(v2Copy, v2));
  }

  @Test
  void orientNormalToDominantPositiveAxis_keepsPositiveDominantX() {
    Vector3d normal = new Vector3d(0.9, -0.3, 0.2);

    Vector3d result = VectorUtils.orientNormalToDominantPositiveAxis(normal);

    assertVectorEquals(new Vector3d(0.9, -0.3, 0.2), result);
  }

  @Test
  void orientNormalToDominantPositiveAxis_flipsNegativeDominantX() {
    Vector3d normal = new Vector3d(-0.9, 0.3, -0.2);

    Vector3d result = VectorUtils.orientNormalToDominantPositiveAxis(normal);

    assertVectorEquals(new Vector3d(0.9, -0.3, 0.2), result);
  }

  @Test
  void orientNormalToDominantPositiveAxis_flipsNegativeDominantY() {
    Vector3d normal = new Vector3d(0.2, -0.9, 0.3);

    Vector3d result = VectorUtils.orientNormalToDominantPositiveAxis(normal);

    assertVectorEquals(new Vector3d(-0.2, 0.9, -0.3), result);
  }

  @Test
  void orientNormalToDominantPositiveAxis_flipsNegativeDominantZ() {
    Vector3d normal = new Vector3d(0.1, 0.2, -0.95);

    Vector3d result = VectorUtils.orientNormalToDominantPositiveAxis(normal);

    assertVectorEquals(new Vector3d(-0.1, -0.2, 0.95), result);
  }

  @Test
  void orientNormalToDominantPositiveAxis_returnsSameInstance() {
    Vector3d normal = new Vector3d(-1, 0, 0);

    Vector3d result = VectorUtils.orientNormalToDominantPositiveAxis(normal);

    assertTrue(result == normal, "must mutate in place and return the same instance");
  }

  @Test
  void orientNormalToDominantPositiveAxis_nullInputReturnsNull() {
    assertNull(VectorUtils.orientNormalToDominantPositiveAxis(null));
  }

  @Test
  void orientNormalToDominantPositiveAxis_zeroVectorIsUnchanged() {
    Vector3d normal = new Vector3d(0, 0, 0);

    Vector3d result = VectorUtils.orientNormalToDominantPositiveAxis(normal);

    assertVectorEquals(new Vector3d(0, 0, 0), result);
  }

  @Test
  void orientNormalToDominantPositiveAxis_tieBreakingPrefersX() {
    // |x| == |y| == |z|, all negative -> dominant axis resolves to X by the >= chain.
    Vector3d normal = new Vector3d(-0.5, -0.5, -0.5);

    Vector3d result = VectorUtils.orientNormalToDominantPositiveAxis(normal);

    assertVectorEquals(new Vector3d(0.5, 0.5, 0.5), result);
  }
}
