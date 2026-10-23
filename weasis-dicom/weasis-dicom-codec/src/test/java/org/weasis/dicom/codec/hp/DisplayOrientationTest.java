/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.hp.DisplayOrientation.Framing;

class DisplayOrientationTest {

  private static String[] po(String row, String col) {
    return new String[] {row, col};
  }

  @Test
  void anImageAlreadyInTheWantedOrientationIsLeftAsItIs() {
    assertAll(
        () -> assertEquals(Framing.NONE, DisplayOrientation.framing(po("P", "L"), po("P", "L"))),
        () -> assertEquals(Framing.NONE, DisplayOrientation.framing(po("A", "FR"), po("A", "F"))));
  }

  @Test
  void aLeftMammogramStoredLikeARightOneIsFlipped() {
    assertAll(
        () ->
            assertEquals(
                new Framing(0, true),
                DisplayOrientation.framing(po("P", "R"), po("A", "R")),
                "LCC"), // NON-NLS
        () ->
            assertEquals(
                new Framing(0, true),
                DisplayOrientation.framing(po("P", "F"), po("A", "F")),
                "LMLO")); // NON-NLS
  }

  @Test
  void upsideDownAndSwappedAxesNeedARotation() {
    assertAll(
        () ->
            assertEquals(
                new Framing(180, false), DisplayOrientation.framing(po("A", "H"), po("P", "F"))),
        () ->
            assertEquals(
                new Framing(90, false), DisplayOrientation.framing(po("F", "A"), po("P", "F"))),
        () ->
            assertEquals(
                new Framing(180, true), DisplayOrientation.framing(po("P", "H"), po("P", "F"))));
  }

  @Test
  void theScreenDirectionsFollowARotationThenAFlip() {
    assertAll(
        () ->
            assertArrayEquals(
                po("H", "L"), DisplayOrientation.screenDirections("L", "F", 90, false)),
        () ->
            assertArrayEquals(
                po("F", "L"), DisplayOrientation.screenDirections("L", "F", 90, true)));
  }

  @Test
  void anUnknownOrientationGivesNoFraming() {
    assertAll(
        () -> assertEquals(Framing.NONE, DisplayOrientation.framing(null, po("P", "L"))),
        () -> assertEquals(Framing.NONE, DisplayOrientation.framing(po("P", "L"), null)),
        () -> assertEquals(Framing.NONE, DisplayOrientation.framing(po("X", ""), po("P", "L"))),
        () -> assertEquals("RT", DisplayOrientation.direction(po("RTV", "D"), 0)),
        () -> assertEquals("F", DisplayOrientation.direction(po("P", "FL"), 1)));
  }
}
