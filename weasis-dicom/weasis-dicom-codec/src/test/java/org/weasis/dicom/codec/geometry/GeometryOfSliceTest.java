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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class GeometryOfSliceTest {

  private static final double EPS = 1.0e-9;

  private static GeometryOfSlice axialSlice() {
    return new GeometryOfSlice(
        new Vector3d(1, 0, 0), // row along +X (L)
        new Vector3d(0, 1, 0), // column along +Y (P)
        new Vector3d(-100, -100, 50), // TLHC in patient space (mm)
        new Vector3d(0.5, 0.5, 1.0), // voxel spacing (mm)
        1.0,
        new Vector3d(512, 512, 1));
  }

  private static void assertVectorEquals(Vector3d expected, Vector3d actual) {
    assertAll(
        () -> assertEquals(expected.x, actual.x, EPS, "x"),
        () -> assertEquals(expected.y, actual.y, EPS, "y"),
        () -> assertEquals(expected.z, actual.z, EPS, "z"));
  }

  // -- Construction & accessors ---------------------------------------------

  @Test
  void constructor_storesAllFields() {
    GeometryOfSlice g = axialSlice();

    assertAll(
        () -> assertVectorEquals(new Vector3d(1, 0, 0), g.getRow()),
        () -> assertVectorEquals(new Vector3d(0, 1, 0), g.getColumn()),
        () -> assertVectorEquals(new Vector3d(-100, -100, 50), g.getTLHC()),
        () -> assertVectorEquals(new Vector3d(0.5, 0.5, 1.0), g.getVoxelSpacing()),
        () -> assertEquals(1.0, g.getSliceThickness(), EPS),
        () -> assertVectorEquals(new Vector3d(512, 512, 1), g.getDimensions()));
  }

  @Test
  void copyConstructor_deepCopiesAllVectors() {
    GeometryOfSlice original = axialSlice();

    GeometryOfSlice copy = new GeometryOfSlice(original);

    assertAll(
        () -> assertEquals(original, copy),
        () -> assertNotSame(original.getRow(), copy.getRow()),
        () -> assertNotSame(original.getColumn(), copy.getColumn()),
        () -> assertNotSame(original.getTLHC(), copy.getTLHC()),
        () -> assertNotSame(original.getVoxelSpacing(), copy.getVoxelSpacing()),
        () -> assertNotSame(original.getDimensions(), copy.getDimensions()));
  }

  @Test
  void copyConstructor_mutatingCopyDoesNotAffectOriginal() {
    GeometryOfSlice original = axialSlice();
    Vector3d originalTlhc = new Vector3d(original.getTLHC());

    GeometryOfSlice copy = new GeometryOfSlice(original);
    copy.getTLHC().set(0, 0, 0);

    assertVectorEquals(originalTlhc, original.getTLHC());
  }

  @Test
  void getNormal_axialSliceYieldsHeadDirection() {
    GeometryOfSlice g = axialSlice();

    Vector3d normal = g.getNormal();

    assertVectorEquals(new Vector3d(0, 0, 1), normal);
  }

  // -- Orthogonality --------------------------------------------------------

  @Test
  void isRowColumnOrthogonal_trueForAxialAxes() {
    assertTrue(axialSlice().isRowColumnOrthogonal());
  }

  @Test
  void isRowColumnOrthogonal_falseWhenRowAndColumnAreNearlyParallel() {
    GeometryOfSlice g =
        new GeometryOfSlice(
            new Vector3d(1, 0, 0),
            new Vector3d(0.9, 0.1, 0), // not orthogonal: dot product = 0.9
            new Vector3d(0, 0, 0),
            new Vector3d(1, 1, 1),
            1.0,
            new Vector3d(1, 1, 1));

    assertFalse(g.isRowColumnOrthogonal());
  }

  @Test
  void isRowColumnOrthogonal_tolerantUpTo005() {
    // dot = 0.004 (just within tolerance) -> orthogonal
    GeometryOfSlice g =
        new GeometryOfSlice(
            new Vector3d(1, 0, 0),
            new Vector3d(0.004, 1, 0),
            new Vector3d(0, 0, 0),
            new Vector3d(1, 1, 1),
            1.0,
            new Vector3d(1, 1, 1));

    assertTrue(g.isRowColumnOrthogonal());
  }

  // -- Pixel <-> mm conversion (safety-critical) ----------------------------

  @Test
  void getPosition_axialOriginPixelMapsToTLHC() {
    GeometryOfSlice g = axialSlice();

    Vector3d position = g.getPosition(new Point2D.Double(0, 0));

    assertVectorEquals(g.getTLHC(), position);
  }

  @Test
  void getPosition_axialPixelMapsToExpectedMillimeters() {
    // axial slice with 0.5 mm spacing, TLHC=(-100,-100,50); pixel (col=10, row=20)
    // -> +10*0.5 mm along row (+X), +20*0.5 mm along column (+Y).
    GeometryOfSlice g = axialSlice();

    Vector3d position = g.getPosition(new Point2D.Double(10, 20));

    assertVectorEquals(new Vector3d(-95.0, -90.0, 50.0), position);
  }

  @Test
  void getPosition_thenGetImagePosition_isIdentityForSquarePixels() {
    GeometryOfSlice g = axialSlice();
    Point2D.Double p = new Point2D.Double(123.5, 256.0);

    Vector3d position = g.getPosition(p);
    Point2D image = g.getImagePosition(position);

    assertAll(
        () -> assertEquals(p.x, image.getX(), 1.0e-6, "column index round-trip"),
        () -> assertEquals(p.y, image.getY(), 1.0e-6, "row index round-trip"));
  }

  @Test
  void getPosition_thenGetImagePosition_isIdentityForNonSquarePixels() {
    // Pins the field-naming convention: voxelSpacing.x is the mm/px ratio applied to Point2D.x
    // (column index), voxelSpacing.y is applied to Point2D.y (row index). Round-trip MUST hold.
    GeometryOfSlice g =
        new GeometryOfSlice(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 0),
            new Vector3d(0.4, 1.2, 1.0), // non-square pixels
            1.0,
            new Vector3d(256, 256, 1));
    Point2D.Double p = new Point2D.Double(37.5, 14.25);

    Vector3d position = g.getPosition(p);
    Point2D image = g.getImagePosition(position);

    assertAll(
        () -> assertEquals(p.x, image.getX(), 1.0e-6, "column index round-trip"),
        () -> assertEquals(p.y, image.getY(), 1.0e-6, "row index round-trip"));
  }

  @Test
  void getPosition_thenGetImagePosition_isIdentityForObliqueOrientation() {
    // 45° oblique row/column in axial plane, off-origin TLHC.
    double s = Math.sqrt(0.5);
    GeometryOfSlice g =
        new GeometryOfSlice(
            new Vector3d(s, s, 0),
            new Vector3d(-s, s, 0),
            new Vector3d(12.34, -5.6, 7.8),
            new Vector3d(0.7, 0.9, 1.0),
            1.0,
            new Vector3d(256, 256, 1));
    Point2D.Double p = new Point2D.Double(42.0, 99.5);

    Vector3d position = g.getPosition(p);
    Point2D image = g.getImagePosition(position);

    assertAll(
        () -> assertEquals(p.x, image.getX(), 1.0e-6),
        () -> assertEquals(p.y, image.getY(), 1.0e-6));
  }

  @Test
  void getImagePosition_returnsNullWhenRowSpacingBelowMinSpacing() {
    GeometryOfSlice g =
        new GeometryOfSlice(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 0),
            new Vector3d(GeometryOfSlice.MIN_SPACING / 2.0, 1.0, 1.0),
            1.0,
            new Vector3d(1, 1, 1));

    assertNull(g.getImagePosition(new Vector3d(1, 1, 0)));
  }

  @Test
  void getImagePosition_returnsNullWhenColumnSpacingBelowMinSpacing() {
    GeometryOfSlice g =
        new GeometryOfSlice(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 0),
            new Vector3d(1.0, GeometryOfSlice.MIN_SPACING / 2.0, 1.0),
            1.0,
            new Vector3d(1, 1, 1));

    assertNull(g.getImagePosition(new Vector3d(1, 1, 0)));
  }

  @Test
  void getImagePosition_returnsValidPointAtExactRowMinSpacingBoundary() {
    // The guard is strict `<`: spacing exactly at MIN_SPACING must NOT trip the
    // null-guard, otherwise a clinically-valid sub-millimetre acquisition would
    // silently drop its pixel↔mm mapping at the boundary.
    GeometryOfSlice g =
        new GeometryOfSlice(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 0),
            new Vector3d(GeometryOfSlice.MIN_SPACING, 1.0, 1.0),
            1.0,
            new Vector3d(1, 1, 1));

    assertNotNull(g.getImagePosition(new Vector3d(1, 1, 0)));
  }

  @Test
  void getImagePosition_returnsValidPointAtExactColumnMinSpacingBoundary() {
    GeometryOfSlice g =
        new GeometryOfSlice(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 0),
            new Vector3d(1.0, GeometryOfSlice.MIN_SPACING, 1.0),
            1.0,
            new Vector3d(1, 1, 1));

    assertNotNull(g.getImagePosition(new Vector3d(1, 1, 0)));
  }

  // -- Orientation letters --------------------------------------------------

  @Test
  void getRowOrientation_axialBipedIsLeft() {
    assertEquals("L", axialSlice().getRowOrientation());
  }

  @Test
  void getColumnOrientation_axialBipedIsPosterior() {
    assertEquals("P", axialSlice().getColumnOrientation());
  }

  @Test
  void getRowOrientation_quadrupedUsesLowerLetters() {
    assertEquals("Le", axialSlice().getRowOrientation(true));
  }

  @Test
  void getColumnOrientation_quadrupedUsesLowerLetters() {
    assertEquals("D", axialSlice().getColumnOrientation(true));
  }

  @Test
  void getOrientation_negativeXReturnsRight() {
    assertEquals("R", GeometryOfSlice.getOrientation(new Vector3d(-1, 0, 0), false));
  }

  @Test
  void getOrientation_negativeYReturnsAnterior() {
    assertEquals("A", GeometryOfSlice.getOrientation(new Vector3d(0, -1, 0), false));
  }

  @Test
  void getOrientation_negativeZReturnsFoot() {
    assertEquals("F", GeometryOfSlice.getOrientation(new Vector3d(0, 0, -1), false));
  }

  @Test
  void getOrientation_obliqueOrdersByDominantAxisDescending() {
    // |x|=0.8 > |y|=0.5 > |z|=0.3 -> "LPH" (x>0=L, y>0=P, z>0=H)
    assertEquals("LPH", GeometryOfSlice.getOrientation(new Vector3d(0.8, 0.5, 0.3), false));
  }

  @Test
  void getOrientation_obliqueMixedSigns() {
    // |x|=0.8 > |y|=0.5 > |z|=0.3 with x<0, y>0, z<0 -> "RPF"
    assertEquals("RPF", GeometryOfSlice.getOrientation(new Vector3d(-0.8, 0.5, -0.3), false));
  }

  @Test
  void getOrientation_quadrupedObliqueUsesQuadrupedLetters() {
    // x>0=Le, y<0=V, z>0=Cr; |x|=0.7>|y|=0.5>|z|=0.4
    assertEquals("LeVCr", GeometryOfSlice.getOrientation(new Vector3d(0.7, -0.5, 0.4), true));
  }

  @Test
  void getOrientation_strictTieResolvesToEmptyString() {
    // Static getOrientation uses strict > comparisons; |x|==|y| yields no winner.
    assertEquals("", GeometryOfSlice.getOrientation(new Vector3d(0.7, 0.7, 0), false));
  }

  @Test
  void getOrientation_nullVectorReturnsEmptyString() {
    assertEquals("", GeometryOfSlice.getOrientation(null, false));
  }

  @Test
  void getOrientation_zeroVectorReturnsEmptyString() {
    assertEquals("", GeometryOfSlice.getOrientation(new Vector3d(0, 0, 0), false));
  }

  // -- equals / hashCode ----------------------------------------------------

  @Test
  void equals_identicalGeometriesAreEqual() {
    assertEquals(axialSlice(), axialSlice());
  }

  @Test
  void hashCode_identicalGeometriesShareHash() {
    assertEquals(axialSlice().hashCode(), axialSlice().hashCode());
  }

  @Test
  void equals_differentSliceThicknessIsNotEqual() {
    GeometryOfSlice a = axialSlice();
    GeometryOfSlice b =
        new GeometryOfSlice(
            new Vector3d(a.getRow()),
            new Vector3d(a.getColumn()),
            new Vector3d(a.getTLHC()),
            new Vector3d(a.getVoxelSpacing()),
            a.getSliceThickness() + 1.0,
            new Vector3d(a.getDimensions()));

    assertNotEquals(a, b);
  }

  @Test
  void equals_differentTlhcIsNotEqual() {
    GeometryOfSlice a = axialSlice();
    GeometryOfSlice b =
        new GeometryOfSlice(
            new Vector3d(a.getRow()),
            new Vector3d(a.getColumn()),
            new Vector3d(0, 0, 0),
            new Vector3d(a.getVoxelSpacing()),
            a.getSliceThickness(),
            new Vector3d(a.getDimensions()));

    assertNotEquals(a, b);
  }

  @Test
  void equals_differentTypeIsNotEqual() {
    assertNotEquals(axialSlice(), "not a geometry");
  }
}
