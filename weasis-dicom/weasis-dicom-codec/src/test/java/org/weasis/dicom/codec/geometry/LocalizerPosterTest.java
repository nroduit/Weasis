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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.util.List;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class LocalizerPosterTest {

  private static final double EPS = 1.0e-9;
  private static final double IMG_EPS = 1.0e-6;

  // 100×100 axial localizer at patient z=0, 1mm isotropic. Image fills [-50, +50] in X and Y.
  private static IntersectSlice axialLocalizer() {
    return new IntersectSlice(
        new Vector3d(1, 0, 0),
        new Vector3d(0, 1, 0),
        new Vector3d(-50, -50, 0),
        new Vector3d(1, 1, 1),
        new Vector3d(100, 100, 1));
  }

  private static GeometryOfSlice axialSliceAt(double z) {
    return new GeometryOfSlice(
        new Vector3d(1, 0, 0),
        new Vector3d(0, 1, 0),
        new Vector3d(-50, -50, z),
        new Vector3d(1, 1, 1),
        1.0,
        new Vector3d(100, 100, 1));
  }

  private static GeometryOfSlice sagittalSliceAtX(double x) {
    return new GeometryOfSlice(
        new Vector3d(0, 1, 0),
        new Vector3d(0, 0, 1),
        new Vector3d(x, -50, -50),
        new Vector3d(1, 1, 1),
        1.0,
        new Vector3d(100, 100, 1));
  }

  // -- validateDirectionCosines --------------------------------------------

  @Test
  void validateDirectionCosines_acceptsCanonicalAxes() {
    assertDoesNotThrow(
        () ->
            LocalizerPoster.validateDirectionCosines(new Vector3d(1, 0, 0), new Vector3d(0, 1, 0)));
  }

  @Test
  void validateDirectionCosines_acceptsRotatedOrthogonalPair() {
    double s = Math.sqrt(0.5);
    assertDoesNotThrow(
        () ->
            LocalizerPoster.validateDirectionCosines(
                new Vector3d(s, s, 0), new Vector3d(-s, s, 0)));
  }

  @Test
  void validateDirectionCosines_rejectsNonUnitRow() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                LocalizerPoster.validateDirectionCosines(
                    new Vector3d(2, 0, 0), new Vector3d(0, 1, 0)));
    assertTrue(ex.getMessage().contains("Row"), ex.getMessage());
  }

  @Test
  void validateDirectionCosines_rejectsNonUnitColumn() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                LocalizerPoster.validateDirectionCosines(
                    new Vector3d(1, 0, 0), new Vector3d(0, 2, 0)));
    assertTrue(ex.getMessage().contains("Column"), ex.getMessage());
  }

  @Test
  void validateDirectionCosines_rejectsNonOrthogonalPair() {
    // row · column = 0.9 > 0.005
    Vector3d column = new Vector3d(0.9, Math.sqrt(1.0 - 0.81), 0);
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> LocalizerPoster.validateDirectionCosines(new Vector3d(1, 0, 0), column));
    assertTrue(ex.getMessage().toLowerCase().contains("orthogonal"), ex.getMessage());
  }

  // -- getCornersOfSourceRectangleInSourceSpace ----------------------------

  @Test
  void getCornersOfSourceRectangle_axialReturnsTlhcTrhcBrhcBlhcInOrder() {
    Vector3d[] corners =
        LocalizerPoster.getCornersOfSourceRectangleInSourceSpace(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(-50, -50, 0),
            new Vector3d(1, 1, 1),
            new Vector3d(100, 100, 1));

    // Order is {TLHC, TRHC, BRHC, BLHC}: TRHC = TLHC + row*length, BLHC = TLHC + column*length.
    assertAll(
        () -> assertEquals(4, corners.length),
        () -> assertVectorEquals(new Vector3d(-50, -50, 0), corners[0]),
        () -> assertVectorEquals(new Vector3d(50, -50, 0), corners[1]),
        () -> assertVectorEquals(new Vector3d(50, 50, 0), corners[2]),
        () -> assertVectorEquals(new Vector3d(-50, 50, 0), corners[3]));
  }

  @Test
  void getCornersOfSourceRectangle_propagatesInvalidCosines() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LocalizerPoster.getCornersOfSourceRectangleInSourceSpace(
                new Vector3d(2, 0, 0), // not unit
                new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 0),
                new Vector3d(1, 1, 1),
                new Vector3d(1, 1, 1)));
  }

  // -- getCornersOfSourceCubeInSourceSpace ---------------------------------

  @Test
  void getCornersOfSourceCube_axialReturnsEightCornersCenteredOnTlhc() {
    Vector3d[] corners =
        LocalizerPoster.getCornersOfSourceCubeInSourceSpace(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(-50, -50, 0),
            new Vector3d(1, 1, 1),
            1.0,
            new Vector3d(100, 100, 5));

    // Half-thickness along normal = dim.z/2 * sliceThickness = 5/2 * 1 = 2.5
    assertAll(
        () -> assertEquals(8, corners.length),
        () -> assertEquals(2.5, corners[0].z, EPS, "top face z"),
        () -> assertEquals(-2.5, corners[4].z, EPS, "bottom face z"),
        // Top TLHC sits half a thickness above patient TLHC.
        () -> assertVectorEquals(new Vector3d(-50, -50, 2.5), corners[0]),
        () -> assertVectorEquals(new Vector3d(50, 50, 2.5), corners[2]),
        () -> assertVectorEquals(new Vector3d(-50, -50, -2.5), corners[4]),
        () -> assertVectorEquals(new Vector3d(50, 50, -2.5), corners[6]));
  }

  // -- IntersectSlice ------------------------------------------------------

  @Test
  void intersectSlice_constructorPropagatesInvalidCosines() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IntersectSlice(
                new Vector3d(2, 0, 0),
                new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 0),
                new Vector3d(1, 1, 1),
                new Vector3d(1, 1, 1)));
  }

  @Test
  void intersectSlice_coplanarTargetDrawsRectangleOutline() {
    IntersectSlice localizer = axialLocalizer();

    List<Point2D> outline = localizer.getOutlineOnLocalizerForThisGeometry(axialSliceAt(0));

    assertNotNull(outline);
    assertEquals(4, outline.size(), "expected 4 corners");
    // Corners should be at the image-pixel corners (0.5 .. 99.5).
    assertAll(
        () -> assertPointEquals(0.5, 0.5, outline.get(0)),
        () -> assertPointEquals(99.5, 0.5, outline.get(1)),
        () -> assertPointEquals(99.5, 99.5, outline.get(2)),
        () -> assertPointEquals(0.5, 99.5, outline.get(3)));
  }

  @Test
  void intersectSlice_parallelOffsetTargetReturnsNull() {
    IntersectSlice localizer = axialLocalizer();

    // Axial slice 5mm above the localizer plane — no edges cross z=0.
    List<Point2D> outline = localizer.getOutlineOnLocalizerForThisGeometry(axialSliceAt(5));

    assertNull(outline);
  }

  @Test
  void intersectSlice_orthogonalSagittalTargetDrawsVerticalLine() {
    IntersectSlice localizer = axialLocalizer();

    // Sagittal slice at x=0 cuts the axial localizer along the y-axis at column 50.
    List<Point2D> outline = localizer.getOutlineOnLocalizerForThisGeometry(sagittalSliceAtX(0));

    assertNotNull(outline);
    assertEquals(2, outline.size(), "expected a two-point line segment");
    // Two points sharing image column ~50 and spanning the full image height with the
    // sub-pixel anchor (image rows 0.5 .. 99.5 → height 99.0).
    assertAll(
        () -> assertEquals(50.0, outline.get(0).getX(), IMG_EPS, "first point column"),
        () -> assertEquals(50.0, outline.get(1).getX(), IMG_EPS, "second point column"),
        () ->
            assertEquals(
                99.0,
                Math.abs(outline.get(0).getY() - outline.get(1).getY()),
                IMG_EPS,
                "segment spans the image with (n-1)/n sub-pixel scaling"));
  }

  @Test
  void intersectSlice_sagittalShiftedOnXProjectsToShiftedColumn() {
    IntersectSlice localizer = axialLocalizer();

    // Sagittal slice at patient x=20 -> column index 20 + 0.5 sub-pixel anchor = 20.5
    List<Point2D> outline = localizer.getOutlineOnLocalizerForThisGeometry(sagittalSliceAtX(20));

    assertNotNull(outline);
    assertEquals(2, outline.size());
    double expectedColumn = 20 - (-50); // patient x relative to localizer TLHC x
    assertAll(
        () ->
            assertEquals(
                expectedColumn * (99.0 / 100.0) + 0.5,
                outline.get(0).getX(),
                IMG_EPS,
                "first point on shifted column"),
        () ->
            assertEquals(
                expectedColumn * (99.0 / 100.0) + 0.5,
                outline.get(1).getX(),
                IMG_EPS,
                "second point on shifted column"));
  }

  // -- IntersectVolume -----------------------------------------------------

  @Test
  void intersectVolume_axialVolumeStraddlingLocalizerProjectsFullRectangle() {
    IntersectSlice localizer = axialLocalizer();

    // Volume centered on z=0 with 5 slices, 1mm thick: extends z=[-2.5..+2.5].
    GeometryOfSlice volume =
        new GeometryOfSlice(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(-50, -50, 0),
            new Vector3d(1, 1, 1),
            1.0,
            new Vector3d(100, 100, 5));
    IntersectVolume poster =
        new IntersectVolume(
            localizer.localizerRow,
            localizer.localizerColumn,
            localizer.localizerTLHC,
            localizer.localizerVoxelSpacing,
            localizer.localizerDimensions);

    List<Point2D> outline = poster.getOutlineOnLocalizerForThisGeometry(volume);

    assertNotNull(outline);
    // Volume's 4 vertical edges cross the localizer plane at the 4 patient-XY corners.
    assertEquals(4, outline.size(), "expected 4 intersection points");
    // After centroid-angle sort, points form a convex quad covering the image corners.
    assertCornersCoverImage(outline);
  }

  @Test
  void intersectVolume_volumeOutsideLocalizerReturnsNullOrEmpty() {
    // Volume entirely above localizer plane (z > 0).
    IntersectVolume poster =
        new IntersectVolume(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(-50, -50, 0),
            new Vector3d(1, 1, 1),
            new Vector3d(100, 100, 1));
    GeometryOfSlice volume =
        new GeometryOfSlice(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(-50, -50, 50),
            new Vector3d(1, 1, 1),
            1.0,
            new Vector3d(100, 100, 5));

    List<Point2D> outline = poster.getOutlineOnLocalizerForThisGeometry(volume);

    assertTrue(outline == null || outline.isEmpty(), "expected no intersection");
  }

  // -- helpers --------------------------------------------------------------

  private static void assertVectorEquals(Vector3d expected, Vector3d actual) {
    assertAll(
        () -> assertEquals(expected.x, actual.x, EPS, "x"),
        () -> assertEquals(expected.y, actual.y, EPS, "y"),
        () -> assertEquals(expected.z, actual.z, EPS, "z"));
  }

  private static void assertPointEquals(double x, double y, Point2D actual) {
    assertAll(
        () -> assertEquals(x, actual.getX(), IMG_EPS, "x"),
        () -> assertEquals(y, actual.getY(), IMG_EPS, "y"));
  }

  /** Asserts the polygon covers all four image corners (each near 0.5 or 99.5). */
  private static void assertCornersCoverImage(List<Point2D> polygon) {
    boolean[] seen = new boolean[4]; // [BL, BR, TR, TL]
    for (Point2D p : polygon) {
      boolean left = Math.abs(p.getX() - 0.5) < 1.0e-3;
      boolean right = Math.abs(p.getX() - 99.5) < 1.0e-3;
      boolean top = Math.abs(p.getY() - 0.5) < 1.0e-3;
      boolean bottom = Math.abs(p.getY() - 99.5) < 1.0e-3;
      if (left && top) seen[0] = true;
      else if (right && top) seen[1] = true;
      else if (right && bottom) seen[2] = true;
      else if (left && bottom) seen[3] = true;
    }
    for (int i = 0; i < 4; i++) {
      assertTrue(seen[i], "missing corner index " + i + " in polygon " + polygon);
    }
  }
}
