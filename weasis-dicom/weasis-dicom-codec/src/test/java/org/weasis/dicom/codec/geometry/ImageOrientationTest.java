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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.geometry.ImageOrientation.Plan;

class ImageOrientationTest {

  // -- getPlan(Vector3d, Vector3d) -----------------------------------------

  @Test
  void getPlan_axialFromCanonicalAxes() {
    Plan plan = ImageOrientation.getPlan(new Vector3d(1, 0, 0), new Vector3d(0, 1, 0));

    assertEquals(Plan.AXIAL, plan);
  }

  @Test
  void getPlan_axialFromReversedRowAndColumn() {
    // Even with row/column swapped, the pair X+Y still spans the axial plane.
    Plan plan = ImageOrientation.getPlan(new Vector3d(0, 1, 0), new Vector3d(1, 0, 0));

    assertEquals(Plan.AXIAL, plan);
  }

  @Test
  void getPlan_sagittalFromCanonicalAxes() {
    Plan plan = ImageOrientation.getPlan(new Vector3d(0, 1, 0), new Vector3d(0, 0, 1));

    assertEquals(Plan.SAGITTAL, plan);
  }

  @Test
  void getPlan_coronalFromCanonicalAxes() {
    Plan plan = ImageOrientation.getPlan(new Vector3d(1, 0, 0), new Vector3d(0, 0, 1));

    assertEquals(Plan.CORONAL, plan);
  }

  @Test
  void getPlan_axialAcceptsSmallObliquityBelowThreshold() {
    // Dominant component 0.95 (> 0.8) keeps the slice classified as axial.
    Plan plan =
        ImageOrientation.getPlan(new Vector3d(0.95, 0.2, 0.1), new Vector3d(0.1, 0.95, 0.2));

    assertEquals(Plan.AXIAL, plan);
  }

  @Test
  void getPlan_obliqueWhenNoComponentExceedsThreshold() {
    // No component above 0.8 -> rows/columns have no dominant axis -> oblique.
    Plan plan =
        ImageOrientation.getPlan(new Vector3d(0.79, 0.0, 0.61), new Vector3d(0.61, 0.0, -0.79));

    assertEquals(Plan.OBLIQUE, plan);
  }

  @Test
  void getPlan_obliqueWhenRowAndColumnShareSameDominantAxis() {
    // Both row and column dominant on X -> not an orthogonal plane combination.
    Plan plan = ImageOrientation.getPlan(new Vector3d(1, 0, 0), new Vector3d(0.95, 0.2, 0));

    assertEquals(Plan.OBLIQUE, plan);
  }

  // -- getOrientation(Vector3d, boolean) -----------------------------------

  @Test
  void getOrientation_canonicalXReturnsLeft() {
    // Note the trailing space — the static helper joins letters with StringUtil.SPACE.
    assertEquals("L ", ImageOrientation.getOrientation(new Vector3d(1, 0, 0), false));
  }

  @Test
  void getOrientation_canonicalNegativeXReturnsRight() {
    assertEquals("R ", ImageOrientation.getOrientation(new Vector3d(-1, 0, 0), false));
  }

  @Test
  void getOrientation_canonicalYReturnsPosterior() {
    assertEquals("P ", ImageOrientation.getOrientation(new Vector3d(0, 1, 0), false));
  }

  @Test
  void getOrientation_canonicalNegativeZReturnsFoot() {
    assertEquals("F ", ImageOrientation.getOrientation(new Vector3d(0, 0, -1), false));
  }

  @Test
  void getOrientation_obliqueLetsAllThreeAxesContribute() {
    // |x|=0.8 > |y|=0.5 > |z|=0.3 with all positive -> L P H, descending dominance.
    assertEquals("L P H ", ImageOrientation.getOrientation(new Vector3d(0.8, 0.5, 0.3), false));
  }

  @Test
  void getOrientation_quadrupedSwitchesToQuadrupedLetters() {
    // x>0=LE, y<0=V, z>0=CR; dominance |x|>|y|>|z|.
    assertEquals("LE V CR ", ImageOrientation.getOrientation(new Vector3d(0.7, -0.5, 0.4), true));
  }

  @Test
  void getOrientation_tieBetweenComponentsStillProducesOutput() {
    // Unlike GeometryOfSlice.getOrientation, this overload uses >= so ties resolve in X-then-Y
    // order rather than being skipped.
    assertEquals("L P ", ImageOrientation.getOrientation(new Vector3d(0.7, 0.7, 0), false));
  }

  // -- getImageOrientationOpposite -----------------------------------------

  @Test
  void getImageOrientationOpposite_bipedLeftIsRight() {
    assertEquals("R", ImageOrientation.getImageOrientationOpposite("L", false));
  }

  @Test
  void getImageOrientationOpposite_bipedAnteriorIsPosterior() {
    assertEquals("P", ImageOrientation.getImageOrientationOpposite("A", false));
  }

  @Test
  void getImageOrientationOpposite_bipedHeadIsFoot() {
    assertEquals("F", ImageOrientation.getImageOrientationOpposite("H", false));
  }

  @Test
  void getImageOrientationOpposite_quadrupedLeftIsRight() {
    assertEquals("RT", ImageOrientation.getImageOrientationOpposite("LE", true));
  }

  @Test
  void getImageOrientationOpposite_quadrupedCranialIsCaudal() {
    assertEquals("CD", ImageOrientation.getImageOrientationOpposite("CR", true));
  }

  @Test
  void getImageOrientationOpposite_unknownInputReturnsEmptyString() {
    assertEquals("", ImageOrientation.getImageOrientationOpposite("XYZ", false));
  }

  @Test
  void getImageOrientationOpposite_emptyInputReturnsEmptyString() {
    assertEquals("", ImageOrientation.getImageOrientationOpposite("", false));
  }

  @Test
  void getImageOrientationOpposite_quadrupedUnknownInputReturnsEmptyString() {
    assertEquals("", ImageOrientation.getImageOrientationOpposite("XYZ", true));
  }

  // -- hasSameOrientation(Vector3d×4) --------------------------------------

  @Test
  void hasSameOrientation_identicalAxialVectorsAreEqual() {
    Vector3d row = new Vector3d(1, 0, 0);
    Vector3d column = new Vector3d(0, 1, 0);

    assertTrue(ImageOrientation.hasSameOrientation(row, column, row, column));
  }

  @Test
  void hasSameOrientation_axialAndSagittalAreDifferent() {
    Vector3d axialRow = new Vector3d(1, 0, 0);
    Vector3d axialColumn = new Vector3d(0, 1, 0);
    Vector3d sagittalRow = new Vector3d(0, 1, 0);
    Vector3d sagittalColumn = new Vector3d(0, 0, 1);

    assertFalse(
        ImageOrientation.hasSameOrientation(axialRow, axialColumn, sagittalRow, sagittalColumn));
  }

  @Test
  void hasSameOrientation_twoAxialSlicesEvenWithSwappedRowColumnAreEqual() {
    // Both pairs classify as AXIAL regardless of row/column swap.
    assertTrue(
        ImageOrientation.hasSameOrientation(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(1, 0, 0)));
  }

  @Test
  void hasSameOrientation_smallObliqueDeviationConsideredSame() {
    // Two oblique slices whose normals nearly coincide (dot product > 0.95) -> same orientation.
    // Slight rotation around X axis (~5°): row stays X, column tilts a few degrees off Y.
    Vector3d row1 = new Vector3d(0.5, 0.5, 0.5).normalize();
    Vector3d col1 = new Vector3d(-0.5, 0.5, 0.0).normalize();
    Vector3d row2 = new Vector3d(0.51, 0.5, 0.5).normalize();
    Vector3d col2 = new Vector3d(-0.5, 0.5, 0.0).normalize();

    assertTrue(ImageOrientation.hasSameOrientation(row1, col1, row2, col2));
  }

  @Test
  void hasSameOrientation_obliqueNormalsBelowTolerance() {
    // Two oblique slices whose normals diverge by ~48° (dot product ≈ 0.67 < 0.95).
    Vector3d row1 = new Vector3d(0.5, 0.5, 0.5).normalize();
    Vector3d col1 = new Vector3d(-0.5, 0.5, 0.0).normalize();
    Vector3d row2 = new Vector3d(0.5, -0.5, 0.5).normalize();
    Vector3d col2 = new Vector3d(0.5, 0.5, 0.0).normalize();

    assertFalse(ImageOrientation.hasSameOrientation(row1, col1, row2, col2));
  }

  // -- hasSameOrientation argument-order asymmetry (OBLIQUE vs non-OBLIQUE) ---
  // The method's first branch short-circuits on plan1; when plan1 is a named
  // plane (e.g. AXIAL) and plan2 is OBLIQUE, it returns false WITHOUT consulting
  // the normals. The reverse ordering skips the short-circuit and falls back to
  // a dot-product compare against the OBLIQUE plane's normal. These two tests
  // pin that asymmetric behaviour so a future refactor that "fixes" the
  // short-circuit (or one that further restricts the normal-compare branch)
  // surfaces as a deliberate test update.

  @Test
  void hasSameOrientation_obliqueFirstWithNearAxialNormalReturnsTrueViaNormalCompare() {
    // X just under the 0.8 obliquity threshold so getPlan returns OBLIQUE,
    // yet the resulting slice normal is almost +Z (≈0.997). Compared against
    // a canonical axial pair (normal exactly +Z) the dot product is > 0.95.
    Vector3d obliqueRow = new Vector3d(0.799, 0, 0.0628);
    Vector3d obliqueCol = new Vector3d(0, 1, 0);
    Vector3d axialRow = new Vector3d(1, 0, 0);
    Vector3d axialCol = new Vector3d(0, 1, 0);

    assertTrue(ImageOrientation.hasSameOrientation(obliqueRow, obliqueCol, axialRow, axialCol));
  }

  @Test
  void hasSameOrientation_axialFirstObliqueSecondShortCircuitsToFalseEvenWhenNormalsAlmostAlign() {
    Vector3d axialRow = new Vector3d(1, 0, 0);
    Vector3d axialCol = new Vector3d(0, 1, 0);
    Vector3d obliqueRow = new Vector3d(0.799, 0, 0.0628);
    Vector3d obliqueCol = new Vector3d(0, 1, 0);

    // Plan1 is AXIAL (not OBLIQUE) -> returns AXIAL.equals(OBLIQUE) = false,
    // bypassing the normal-compare path. This is the inverse-argument call of
    // the test above and the documented asymmetry of the method.
    assertFalse(ImageOrientation.hasSameOrientation(axialRow, axialCol, obliqueRow, obliqueCol));
  }

  // -- getPlan quadruped behaviour --------------------------------------------

  @Test
  void getPlan_speciesAgnostic_becauseBipedAndQuadrupedAxisColorsMatchOnEachAxis() {
    // getPlan() hardcodes quadruped=false when delegating to getPatientOrientation.
    // That hardcoding is safe ONLY because the Biped and Quadruped enums share
    // the same Color tag per axis (X=blue, Y=red, Z=green). If a future change
    // splits these colours, quadruped images would silently misclassify as
    // OBLIQUE. Pin the colour equivalence so such a split fails this test.
    Vector3d unitX = new Vector3d(1, 0, 0);
    Vector3d unitY = new Vector3d(0, 1, 0);
    Vector3d unitZ = new Vector3d(0, 0, 1);

    assertEquals(
        PatientOrientation.getBipedXOrientation(unitX).getColor(),
        PatientOrientation.getQuadrupedXOrientation(unitX).getColor());
    assertEquals(
        PatientOrientation.getBipedYOrientation(unitY).getColor(),
        PatientOrientation.getQuadrupedYOrientation(unitY).getColor());
    assertEquals(
        PatientOrientation.getBipedZOrientation(unitZ).getColor(),
        PatientOrientation.getQuadrupedZOrientation(unitZ).getColor());
  }
}
