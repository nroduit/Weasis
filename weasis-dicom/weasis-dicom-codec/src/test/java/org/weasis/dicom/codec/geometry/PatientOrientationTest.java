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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.geometry.PatientOrientation.Biped;
import org.weasis.dicom.codec.geometry.PatientOrientation.Quadruped;

class PatientOrientationTest {

  // -- Biped direction mapping ---------------------------------------------

  @Test
  void getBipedXOrientation_negativeXIsRight() {
    assertEquals(Biped.R, PatientOrientation.getBipedXOrientation(new Vector3d(-1, 0, 0)));
  }

  @Test
  void getBipedXOrientation_positiveXIsLeft() {
    assertEquals(Biped.L, PatientOrientation.getBipedXOrientation(new Vector3d(1, 0, 0)));
  }

  @Test
  void getBipedXOrientation_zeroIsLeft() {
    // The implementation treats 0 as non-negative, so x=0 maps to L.
    assertEquals(Biped.L, PatientOrientation.getBipedXOrientation(new Vector3d(0, 0, 0)));
  }

  @Test
  void getBipedYOrientation_negativeYIsAnterior() {
    assertEquals(Biped.A, PatientOrientation.getBipedYOrientation(new Vector3d(0, -1, 0)));
  }

  @Test
  void getBipedYOrientation_positiveYIsPosterior() {
    assertEquals(Biped.P, PatientOrientation.getBipedYOrientation(new Vector3d(0, 1, 0)));
  }

  @Test
  void getBipedZOrientation_negativeZIsFoot() {
    assertEquals(Biped.F, PatientOrientation.getBipedZOrientation(new Vector3d(0, 0, -1)));
  }

  @Test
  void getBipedZOrientation_positiveZIsHead() {
    assertEquals(Biped.H, PatientOrientation.getBipedZOrientation(new Vector3d(0, 0, 1)));
  }

  // -- Quadruped direction mapping -----------------------------------------

  @Test
  void getQuadrupedXOrientation_negativeXIsRT() {
    assertEquals(Quadruped.RT, PatientOrientation.getQuadrupedXOrientation(new Vector3d(-1, 0, 0)));
  }

  @Test
  void getQuadrupedXOrientation_positiveXIsLE() {
    assertEquals(Quadruped.LE, PatientOrientation.getQuadrupedXOrientation(new Vector3d(1, 0, 0)));
  }

  @Test
  void getQuadrupedYOrientation_negativeYIsVentral() {
    assertEquals(Quadruped.V, PatientOrientation.getQuadrupedYOrientation(new Vector3d(0, -1, 0)));
  }

  @Test
  void getQuadrupedYOrientation_positiveYIsDorsal() {
    assertEquals(Quadruped.D, PatientOrientation.getQuadrupedYOrientation(new Vector3d(0, 1, 0)));
  }

  @Test
  void getQuadrupedZOrientation_negativeZIsCaudal() {
    assertEquals(Quadruped.CD, PatientOrientation.getQuadrupedZOrientation(new Vector3d(0, 0, -1)));
  }

  @Test
  void getQuadrupedZOrientation_positiveZIsCranial() {
    assertEquals(Quadruped.CR, PatientOrientation.getQuadrupedZOrientation(new Vector3d(0, 0, 1)));
  }

  // -- Biped enum metadata --------------------------------------------------

  @Test
  void biped_axisPairsShareColors() {
    // R/L share the X-axis color (blue), A/P share Y (red), F/H share Z (green).
    assertAll(
        () -> assertEquals(Biped.R.getColor(), Biped.L.getColor()),
        () -> assertEquals(Biped.A.getColor(), Biped.P.getColor()),
        () -> assertEquals(Biped.F.getColor(), Biped.H.getColor()),
        () -> assertNotEquals(Biped.R.getColor(), Biped.A.getColor()),
        () -> assertNotEquals(Biped.A.getColor(), Biped.F.getColor()),
        () -> assertNotEquals(Biped.R.getColor(), Biped.F.getColor()));
  }

  @Test
  void biped_fullNamesMatchClinicalLabels() {
    assertAll(
        () -> assertEquals("Right", Biped.R.getFullName()),
        () -> assertEquals("Left", Biped.L.getFullName()),
        () -> assertEquals("Anterior", Biped.A.getFullName()),
        () -> assertEquals("Posterior", Biped.P.getFullName()),
        () -> assertEquals("Foot", Biped.F.getFullName()),
        () -> assertEquals("Head", Biped.H.getFullName()));
  }

  @Test
  void biped_toStringReturnsFullName() {
    assertEquals("Left", Biped.L.toString());
  }

  // -- Quadruped enum metadata ---------------------------------------------

  @Test
  void quadruped_axisPairsShareColors() {
    assertAll(
        () -> assertEquals(Quadruped.RT.getColor(), Quadruped.LE.getColor()),
        () -> assertEquals(Quadruped.V.getColor(), Quadruped.D.getColor()),
        () -> assertEquals(Quadruped.CD.getColor(), Quadruped.CR.getColor()));
  }

  @Test
  void quadruped_fullNamesMatchClinicalLabels() {
    assertAll(
        () -> assertEquals("Right", Quadruped.RT.getFullName()),
        () -> assertEquals("Left", Quadruped.LE.getFullName()),
        () -> assertEquals("Ventral", Quadruped.V.getFullName()),
        () -> assertEquals("Dorsal", Quadruped.D.getFullName()),
        () -> assertEquals("Caudal", Quadruped.CD.getFullName()),
        () -> assertEquals("Cranial", Quadruped.CR.getFullName()));
  }

  @Test
  void biped_sharesXAxisColorWithQuadrupedEquivalent() {
    // Visualizers reuse the same axis colors across biped/quadruped views.
    assertAll(
        () -> assertEquals(Biped.L.getColor(), Quadruped.LE.getColor()),
        () -> assertEquals(Biped.P.getColor(), Quadruped.D.getColor()),
        () -> assertEquals(Biped.H.getColor(), Quadruped.CR.getColor()));
  }

  // -- Lookup by string -----------------------------------------------------

  @Test
  void biped_getBipedResolvesAllEnumNames() {
    assertAll(
        () -> assertEquals(Biped.R, Biped.getBiped("R")),
        () -> assertEquals(Biped.L, Biped.getBiped("L")),
        () -> assertEquals(Biped.A, Biped.getBiped("A")),
        () -> assertEquals(Biped.P, Biped.getBiped("P")),
        () -> assertEquals(Biped.F, Biped.getBiped("F")),
        () -> assertEquals(Biped.H, Biped.getBiped("H")));
  }

  @Test
  void biped_getBipedReturnsNullOnUnknown() {
    assertNull(Biped.getBiped("XYZ"));
  }

  @Test
  void biped_getBipedReturnsNullOnEmptyOrNull() {
    assertAll(() -> assertNull(Biped.getBiped("")), () -> assertNull(Biped.getBiped(null)));
  }

  @Test
  void biped_getBipedIsCaseSensitive() {
    // enum names are upper-case; lower-case must not match.
    assertNull(Biped.getBiped("l"));
  }

  @Test
  void quadruped_getQuadrupedResolvesAllEnumNames() {
    assertAll(
        () -> assertEquals(Quadruped.RT, Quadruped.getQuadruped("RT")),
        () -> assertEquals(Quadruped.LE, Quadruped.getQuadruped("LE")),
        () -> assertEquals(Quadruped.V, Quadruped.getQuadruped("V")),
        () -> assertEquals(Quadruped.D, Quadruped.getQuadruped("D")),
        () -> assertEquals(Quadruped.CD, Quadruped.getQuadruped("CD")),
        () -> assertEquals(Quadruped.CR, Quadruped.getQuadruped("CR")));
  }

  @Test
  void quadruped_getQuadrupedReturnsNullOnUnknown() {
    assertNull(Quadruped.getQuadruped("XYZ"));
  }

  @Test
  void quadruped_getQuadrupedReturnsNullOnEmptyOrNull() {
    assertAll(
        () -> assertNull(Quadruped.getQuadruped("")),
        () -> assertNull(Quadruped.getQuadruped(null)));
  }

  // -- Opposite orientation -------------------------------------------------

  @Test
  void getOppositeOrientation_bipedAxisPairsAreSwapped() {
    assertAll(
        () -> assertEquals(Biped.L, PatientOrientation.getOppositeOrientation(Biped.R)),
        () -> assertEquals(Biped.R, PatientOrientation.getOppositeOrientation(Biped.L)),
        () -> assertEquals(Biped.P, PatientOrientation.getOppositeOrientation(Biped.A)),
        () -> assertEquals(Biped.A, PatientOrientation.getOppositeOrientation(Biped.P)),
        () -> assertEquals(Biped.H, PatientOrientation.getOppositeOrientation(Biped.F)),
        () -> assertEquals(Biped.F, PatientOrientation.getOppositeOrientation(Biped.H)));
  }

  @Test
  void getOppositeOrientation_bipedIsInvolutive() {
    for (Biped value : Biped.values()) {
      Biped opposite = PatientOrientation.getOppositeOrientation(value);
      assertEquals(value, PatientOrientation.getOppositeOrientation(opposite), value.name());
    }
  }

  @Test
  void getOppositeOrientation_quadrupedAxisPairsAreSwapped() {
    assertAll(
        () -> assertEquals(Quadruped.LE, PatientOrientation.getOppositeOrientation(Quadruped.RT)),
        () -> assertEquals(Quadruped.RT, PatientOrientation.getOppositeOrientation(Quadruped.LE)),
        () -> assertEquals(Quadruped.D, PatientOrientation.getOppositeOrientation(Quadruped.V)),
        () -> assertEquals(Quadruped.V, PatientOrientation.getOppositeOrientation(Quadruped.D)),
        () -> assertEquals(Quadruped.CR, PatientOrientation.getOppositeOrientation(Quadruped.CD)),
        () -> assertEquals(Quadruped.CD, PatientOrientation.getOppositeOrientation(Quadruped.CR)));
  }

  @Test
  void getOppositeOrientation_quadrupedIsInvolutive() {
    for (Quadruped value : Quadruped.values()) {
      Quadruped opposite = PatientOrientation.getOppositeOrientation(value);
      assertEquals(value, PatientOrientation.getOppositeOrientation(opposite), value.name());
    }
  }
}
